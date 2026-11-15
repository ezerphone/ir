package com.offlineremote.app.usb;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Clean-room driver for the Tiqiaa USB-C IR dongle — reverse-engineered ASCII protocol,
 * no Tiqiaa native libraries (no signature gate).
 *
 * Architecture mirrors ZaZa's own: ONE persistent connection plus a background reader
 * thread that continuously drains the IN endpoint, reassembles multi-chunk messages and
 * queues the completed inner payloads. This keeps the dongle's buffer clear so it stays
 * responsive across many learn/send cycles (reading only on demand made it hang after one).
 *
 * Wire protocol:
 *   USB packet: [0x02 out / 0x01 in][payloadLen+3][seq][totalChunks][chunkIdx] + payload
 *   Inner payloads: "ST" + <byte> + <cmd> ... + "EN"
 *     mode:  "ST" <seq> <modeChar 'L'/'S'/'R'/...> "EN"
 *     send:  "ST" <seq> 'D' <freqIdx> <durationBytes> "EN"
 *   Duration byte: low7 = microseconds/16, bit7 = mark(1)/space(0); long runs as 0x7f/0xff.
 */
public final class RawDongle {

    public static final int[] FREQ_TABLE = {
            38000,37900,37917,36000,40000,39700,35750,36400,36700,37000,
            37700,38380,38400,38462,38740,39200,42000,43600,44000,33000,
            33500,34000,34500,35000,40500,41000,41500,42500,43000,45000
    };

    public static int freqIndex(int hz) {
        int best = 0, bestd = Integer.MAX_VALUE;
        for (int i = 0; i < FREQ_TABLE.length; i++) {
            int d = Math.abs(FREQ_TABLE[i] - hz);
            if (d < bestd) { bestd = d; best = i; }
        }
        return best;
    }

    private final Context ctx;
    private final UsbManager um;
    private UsbInterface iface;
    private UsbDeviceConnection conn;
    private UsbEndpoint epIn, epOut;

    private volatile boolean running;
    private Thread reader;
    private final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();

    private int innerSeq = 0;
    private int usbSeq = 0;

    public byte[] lastSendFrame;

    public RawDongle(Context c) {
        this.ctx = c.getApplicationContext();
        this.um = (UsbManager) this.ctx.getSystemService(Context.USB_SERVICE);
    }

    public boolean isOpen() { return conn != null; }

    public synchronized boolean open() {
        if (conn != null) return true;
        if (um == null) return false;
        UsbDevice dev = null;
        java.util.HashMap<String, UsbDevice> list = um.getDeviceList();
        if (list != null) for (UsbDevice d : list.values()) if (DongleManager.matches(d)) { dev = d; break; }
        if (dev == null || !um.hasPermission(dev) || dev.getInterfaceCount() < 1) return false;
        UsbInterface in0 = dev.getInterface(0);
        if (in0.getEndpointCount() != 2) return false;
        UsbEndpoint e0 = in0.getEndpoint(0), e1 = in0.getEndpoint(1);
        if (e0.getDirection() == UsbConstants.USB_DIR_IN) { epIn = e0; epOut = e1; }
        else { epIn = e1; epOut = e0; }
        UsbDeviceConnection c = um.openDevice(dev);
        if (c == null) return false;
        if (!c.claimInterface(in0, true)) { c.close(); return false; }
        this.conn = c; this.iface = in0;
        startReader();
        return true;
    }

    public synchronized void close() {
        running = false;
        if (reader != null) { try { reader.join(400); } catch (InterruptedException ignore) {} reader = null; }
        if (conn != null) {
            try { if (iface != null) conn.releaseInterface(iface); } catch (Throwable ignore) {}
            try { conn.close(); } catch (Throwable ignore) {}
        }
        conn = null; iface = null;
    }

    private void startReader() {
        running = true;
        reader = new Thread(() -> {
            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            int curSeq = -1, needTotal = -1;
            byte[] buf = new byte[64];
            while (running) {
                UsbDeviceConnection c = conn;
                UsbEndpoint in = epIn;
                if (c == null || in == null) break;
                int n;
                try { n = c.bulkTransfer(in, buf, buf.length, 250); } catch (Throwable t) { continue; }
                if (n < 5) continue;
                int b0 = buf[0] & 0xff;
                if (b0 != 0x01 && b0 != 0x02) continue;
                int len = (buf[1] & 0xff) - 3;
                int useq = buf[2] & 0xff, total = buf[3] & 0xff, idx = buf[4] & 0xff;
                if (len < 0 || len > 56 || 5 + len > n) continue;
                if (idx == 1) { acc.reset(); curSeq = useq; needTotal = total; }
                if (useq != curSeq) continue;
                acc.write(buf, 5, len);
                if (idx >= needTotal) {
                    frames.offer(acc.toByteArray());
                    acc.reset(); curSeq = -1;
                }
            }
        }, "dongle-reader");
        reader.setDaemon(true);
        reader.start();
    }

    // ---- framing ----
    private int nextSeq() { innerSeq = (innerSeq + 1) & 0xFF; return innerSeq; }
    private int nextUsbSeq() { usbSeq = (usbSeq >= 15) ? 1 : usbSeq + 1; return usbSeq; }

    private byte[] wrapChunk(byte[] payload, int off, int clen, int useq, int total, int idx) {
        byte[] pkt = new byte[5 + clen];
        pkt[0] = 0x02; pkt[1] = (byte) (clen + 3);
        pkt[2] = (byte) useq; pkt[3] = (byte) total; pkt[4] = (byte) idx;
        System.arraycopy(payload, off, pkt, 5, clen);
        return pkt;
    }

    private void sendPayload(byte[] payload) {
        if (conn == null || epOut == null) return;
        int total = Math.max(1, (payload.length + 55) / 56);
        int useq = nextUsbSeq();
        int off = 0;
        for (int idx = 1; idx <= total; idx++) {
            int clen = Math.min(56, payload.length - off);
            conn.bulkTransfer(epOut, wrapChunk(payload, off, clen, useq, total, idx), 5 + clen, 1000);
            off += clen;
            try { Thread.sleep(4); } catch (InterruptedException ignore) {}
        }
    }

    public void sendMode(char modeChar) {
        sendPayload(new byte[]{'S','T',(byte) nextSeq(),(byte) modeChar,'E','N'});
    }

    /** Convert a learned frame ("ST" b 'D' <dur> "EN") to a send frame (adds freqIdx). */
    public byte[] buildSendFrame(byte[] learned, int freqIdx) {
        if (learned == null || learned.length < 6) return null;
        int durLen = learned.length - 6;
        if (durLen < 1) return null;
        byte[] inner = new byte[5 + durLen + 2];
        inner[0] = 'S'; inner[1] = 'T'; inner[2] = (byte) nextSeq(); inner[3] = 'D';
        inner[4] = (byte) freqIdx;
        System.arraycopy(learned, 4, inner, 5, durLen);
        inner[5 + durLen] = 'E'; inner[6 + durLen] = 'N';
        return inner;
    }

    // ---- high-level ops (connection stays open; reader keeps draining) ----

    /** Learn one IR signal. Returns the inner learned payload ("ST" b 'D' <dur> "EN"). */
    public byte[] learn(int timeoutMs) {
        frames.clear();
        sendMode('L');
        try { Thread.sleep(150); } catch (InterruptedException ignore) {}
        sendMode('R');
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] f;
            try { f = frames.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { break; }
            if (f != null && f.length >= 4 && f[3] == 0x44) return f; // 'D' = IR data
        }
        return null;
    }

    /** Transmit a learned payload (rebuilds as a send frame first). */
    public void send(byte[] learnedPayload) {
        byte[] frame = buildSendFrame(learnedPayload, 0); // 0 = 38 kHz
        lastSendFrame = frame;
        if (frame == null) return;
        sendMode('S');
        try { Thread.sleep(120); } catch (InterruptedException ignore) {}
        sendPayload(frame);
    }

    /** Verbose learn for diagnostics. */
    public byte[] learnVerbose(int timeoutMs, List<String> log) {
        frames.clear();
        sendMode('L');
        try { Thread.sleep(150); } catch (InterruptedException ignore) {}
        sendMode('R');
        log.add("sent L+R handshake; waiting for IR (press the remote)...");
        long deadline = System.currentTimeMillis() + timeoutMs;
        int seen = 0;
        while (System.currentTimeMillis() < deadline) {
            byte[] f;
            try { f = frames.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { break; }
            if (f == null) continue;
            seen++;
            if (seen <= 6) log.add("frame " + hex(f));
            if (f.length >= 4 && f[3] == 0x44) { log.add("got IR frame: " + f.length + " bytes"); return f; }
        }
        log.add("(no IR frame; frames seen=" + seen + ")");
        return null;
    }

    private static String hex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(a.length, 48);
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", a[i] & 0xff)).append(i % 2 == 1 ? " " : "");
        return sb.toString().trim();
    }
}
