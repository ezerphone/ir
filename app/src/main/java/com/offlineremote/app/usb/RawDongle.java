package com.offlineremote.app.usb;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Clean-room driver for the Tiqiaa USB-C IR dongle (reverse-engineered ASCII protocol,
 * no Tiqiaa native libraries → no signature gate).
 *
 * HALF-DUPLEX design: we NEVER read and write at the same time. A background reader
 * racing the OUT endpoint during a long multi-chunk send deadlocked the Android USB
 * stack (big sends hung the connection; small ones survived). So every operation is
 * strictly write-commands-then-read-responses.
 *
 * Wire protocol:
 *   USB packet: [0x02 out / 0x01 in][payloadLen+3][seq][totalChunks][chunkIdx] + payload
 *   mode:  "ST" <seq> <'L'/'S'/'R'> "EN"
 *   send:  "ST" <seq> 'D' <freqIdx> <durationBytes> "EN"
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
        innerSeq = 0; usbSeq = 0;
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
        return true;
    }

    public synchronized void close() {
        if (conn != null) {
            try { if (iface != null) conn.releaseInterface(iface); } catch (Throwable ignore) {}
            try { conn.close(); } catch (Throwable ignore) {}
        }
        conn = null; iface = null;
    }

    public synchronized boolean reopen() {
        close();
        try { Thread.sleep(200); } catch (InterruptedException ignore) {}
        return open();
    }

    // ---- framing ----
    private int nextSeq() { innerSeq = (innerSeq + 1) & 0xFF; return innerSeq; }
    private int nextUsbSeq() { usbSeq = (usbSeq >= 15) ? 1 : usbSeq + 1; return usbSeq; }

    private byte[] chunk(byte[] payload, int off, int clen, int useq, int total, int idx) {
        byte[] pkt = new byte[5 + clen];
        pkt[0] = 0x02; pkt[1] = (byte) (clen + 3);
        pkt[2] = (byte) useq; pkt[3] = (byte) total; pkt[4] = (byte) idx;
        System.arraycopy(payload, off, pkt, 5, clen);
        return pkt;
    }

    private int bulkOut(byte[] pkt) {
        if (conn == null || epOut == null) return -99;
        return conn.bulkTransfer(epOut, pkt, pkt.length, 1000);
    }

    /** Write a payload as 1+ chunks (OUT only — no reading in between). */
    private void writePayload(byte[] payload) {
        int total = Math.max(1, (payload.length + 55) / 56);
        int useq = nextUsbSeq();
        int off = 0;
        for (int idx = 1; idx <= total; idx++) {
            int clen = Math.min(56, payload.length - off);
            bulkOut(chunk(payload, off, clen, useq, total, idx));
            off += clen;
            try { Thread.sleep(4); } catch (InterruptedException ignore) {}
        }
    }

    private void writeMode(char modeChar) {
        writePayload(new byte[]{'S','T',(byte) nextSeq(),(byte) modeChar,'E','N'});
    }

    /** Read + reassemble ONE inner payload (IN only). null on timeout. */
    private byte[] readFrame(int timeoutMs) {
        if (conn == null || epIn == null) return null;
        long deadline = System.currentTimeMillis() + timeoutMs;
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        int curSeq = -1, needTotal = -1;
        byte[] buf = new byte[64];
        while (System.currentTimeMillis() < deadline) {
            int to = (int) Math.max(1, deadline - System.currentTimeMillis());
            int n;
            try { n = conn.bulkTransfer(epIn, buf, buf.length, Math.min(to, 500)); }
            catch (Throwable t) { return null; }
            if (n < 5) continue;
            int b0 = buf[0] & 0xff;
            if (b0 != 0x01 && b0 != 0x02) continue;
            int len = (buf[1] & 0xff) - 3;
            int useq = buf[2] & 0xff, total = buf[3] & 0xff, idx = buf[4] & 0xff;
            if (len < 0 || len > 56 || 5 + len > n) continue;
            if (idx == 1) { acc.reset(); curSeq = useq; needTotal = total; }
            if (useq != curSeq) continue;
            acc.write(buf, 5, len);
            if (idx >= needTotal) return acc.toByteArray();
        }
        return null;
    }

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

    // ---- high-level ops (strict half-duplex) ----

    /** Learn one IR signal. Returns inner learned payload ("ST" b 'D' <dur> "EN"), or null. */
    /**
     * Reliable learn used by the UI. The dongle's receive state goes stale after a
     * capture or a send, so a second learn on the same connection captures nothing
     * ("works once then stops"). Starting from a fresh USB open — and retrying once
     * with a reopen if nothing arrives — fixes it. This mirrors the diagnostics path
     * the user confirmed works repeatedly.
     */
    public byte[] learnReliable(int timeoutMs) {
        return learnLogged(timeoutMs, new java.util.ArrayList<String>());
    }

    /**
     * Canonical reliable learn with a verbose log. The Learn UI and the Diagnostics
     * screen both call this, so they behave identically: try once, and if nothing is
     * captured reopen the dongle and retry once. A null return means no IR was seen.
     */
    public byte[] learnLogged(int timeoutMs, List<String> log) {
        byte[] p = learnVerbose(timeoutMs, log);
        if (p == null) {
            log.add("no frames — reopening dongle and retrying once...");
            reopen();
            p = learnVerbose(timeoutMs, log);
        }
        if (p == null) log.add("NO IR captured");
        else log.add("captured OK: " + p.length + " bytes");
        return p;
    }

    public byte[] learn(int timeoutMs) {
        if (conn == null) return null;
        writeMode('L'); readFrame(500);   // ack
        writeMode('R'); readFrame(500);   // ack
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] f = readFrame((int) Math.max(1, deadline - System.currentTimeMillis()));
            if (f != null && f.length >= 4 && f[3] == 0x44) return f;  // 'D' = IR data
        }
        return null;
    }

    /** Transmit a learned payload (captured frame). */
    public boolean send(byte[] learnedPayload) { return send(learnedPayload, 38000); }

    /** Transmit a learned frame at the given carrier (detected when the code was learned). */
    public boolean send(byte[] learnedPayload, int freqHz) {
        byte[] frame = buildSendFrame(learnedPayload, freqIndex(freqHz));
        lastSendFrame = frame;
        if (frame == null) return false;
        writeMode('S'); readFrame(400);     // ack
        writePayload(frame);                // all chunks OUT, no concurrent reads
        readFrame(1500);                    // transmit ack
        return true;
    }

    /**
     * Encode raw microsecond on/off timings into Tiqiaa duration bytes.
     * Each byte: low7 = round(us/16), bit7 = mark(1)/space(0).
     * Durations >= 2032us become a run of 0xff (mark) / 0x7f (space) + remainder byte.
     * (Derived from the _t encoder in libtiqiaadev.)
     */
    public static byte[] encodeTimings(int[] micros) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < micros.length; i++) {
            boolean mark = (i % 2) == 0;   // first timing is a mark
            int v = micros[i];
            if (v >= 2032) {
                int runs = v / 2032;
                int cnt = Math.max(runs, 1);
                for (int k = 0; k < cnt; k++) out.write(mark ? 0xff : 0x7f);
                v -= runs * 2032;
            }
            if (v >= 8) {
                int b = (v + 8) >> 4;
                if (b > 0x7f) b = 0x7f;
                if (mark) b |= 0x80;
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    /** Transmit a raw signal (from an open DB like SmartIR): freq + microsecond timings. */
    public boolean sendRawSignal(int freqHz, int[] micros) {
        if (micros == null || micros.length < 2) return false;
        byte[] dur = encodeTimings(micros);
        int fi = freqIndex(freqHz);
        byte[] inner = new byte[5 + dur.length + 2];
        inner[0] = 'S'; inner[1] = 'T'; inner[2] = (byte) nextSeq(); inner[3] = 'D';
        inner[4] = (byte) fi;
        System.arraycopy(dur, 0, inner, 5, dur.length);
        inner[5 + dur.length] = 'E'; inner[6 + dur.length] = 'N';
        lastSendFrame = inner;
        writeMode('S'); readFrame(400);
        writePayload(inner);
        readFrame(1500);
        return true;
    }

    /** Unified send: learned native frame if present, else a raw signal. */
    public boolean transmit(int freqHz, byte[] nativeFrame, int[] micros) {
        if (nativeFrame != null && nativeFrame.length >= 6) return send(nativeFrame, freqHz);
        return sendRawSignal(freqHz, micros);
    }

    public byte[] learnVerbose(int timeoutMs, List<String> log) {
        writeMode('L'); byte[] a1 = readFrame(600);
        writeMode('R'); byte[] a2 = readFrame(600);
        log.add("handshake L ack=" + (a1 == null ? "none" : hex(a1)) + " R ack=" + (a2 == null ? "none" : hex(a2)));
        log.add("press the remote now...");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] f = readFrame((int) Math.max(1, deadline - System.currentTimeMillis()));
            if (f == null) continue;
            if (f.length >= 4 && f[3] == 0x44) { log.add("got IR frame: " + f.length + " bytes"); return f; }
        }
        log.add("(no IR frame)");
        return null;
    }

    public void sendVerbose(byte[] learnedPayload, List<String> log) {
        byte[] frame = buildSendFrame(learnedPayload, 0);
        lastSendFrame = frame;
        if (frame == null) { log.add("no send frame"); return; }
        int r = bulkOut(chunk(new byte[]{'S','T',(byte) nextSeq(),'S','E','N'}, 0, 6, nextUsbSeq(), 1, 1));
        log.add("mode S ret=" + r);
        readFrame(400);
        int total = Math.max(1, (frame.length + 55) / 56);
        int useq = nextUsbSeq(), off = 0;
        for (int idx = 1; idx <= total; idx++) {
            int clen = Math.min(56, frame.length - off);
            r = bulkOut(chunk(frame, off, clen, useq, total, idx));
            off += clen;
            try { Thread.sleep(4); } catch (InterruptedException ignore) {}
        }
        log.add("sent " + total + " chunks");
        byte[] resp = readFrame(1500);
        log.add("send response: " + (resp == null ? "none" : hex(resp)));
        byte[] probe = pokeProbe();
        log.add("probe after send: " + (probe == null ? "none (dead)" : hex(probe) + " (alive)"));
    }

    private byte[] pokeProbe() {
        bulkOut(chunk(new byte[]{'S','T',(byte) nextSeq(),'L','E','N'}, 0, 6, nextUsbSeq(), 1, 1));
        return readFrame(800);
    }

    private static String hex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(a.length, 48);
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", a[i] & 0xff)).append(i % 2 == 1 ? " " : "");
        return sb.toString().trim();
    }
}
