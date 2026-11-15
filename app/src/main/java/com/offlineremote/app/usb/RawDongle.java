package com.offlineremote.app.usb;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room driver for the Tiqiaa dongle — talks to it DIRECTLY with the reverse-engineered
 * ASCII protocol, using NONE of Tiqiaa's native libraries (so no signature gate).
 *
 * Protocol (recovered by disassembling libtiqiaadev.so, for interoperability):
 *   Every packet: "ST" + <byte> + <cmd/payload...> + "EN", sent via bulkTransfer to OUT.
 *   Mode command: "ST" + <seq> + <modeChar> + "EN"
 *     modeChar: 0->'L' 1->'S'(send) 2->'R'(receive/learn) 3->'H' 4->'O' 6->'C' 7->'V'
 *   Send IR:      "ST" + <repeat> + 'D' + <freqIndex> + <duration bytes...> + "EN"
 *   Responses arrive on IN as 64-byte packets: [0]==1, len=[1]-3, payload at [5].
 *
 * This class is a raw console/recorder: open, send a mode command, and capture whatever
 * the dongle returns — used to validate the protocol and gather learn data.
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
    private int seq = 0;       // inner payload seq (0..255)
    private int usbSeq = 0;    // outer USB-frame seq (1..15)

    public RawDongle(Context c) {
        this.ctx = c.getApplicationContext();
        this.um = (UsbManager) this.ctx.getSystemService(Context.USB_SERVICE);
    }

    public boolean isOpen() { return conn != null; }

    public boolean open() {
        UsbDevice dev = null;
        if (um == null) return false;
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

    public void close() {
        if (conn != null) {
            try { if (iface != null) conn.releaseInterface(iface); } catch (Throwable ignore) {}
            try { conn.close(); } catch (Throwable ignore) {}
        }
        conn = null; iface = null;
    }

    private int nextSeq() { seq = (seq + 1) & 0xFF; return seq; }
    private int nextUsbSeq() { usbSeq = (usbSeq >= 15) ? 1 : usbSeq + 1; return usbSeq; }

    /**
     * Wrap a payload in the dongle's USB framing (single chunk, payload must be <=56 bytes):
     *   [0x02][payloadLen+3][usbSeq][totalChunks=1][chunkIdx=1] + payload
     */
    public byte[] wrap(byte[] payload) {
        int n = payload.length;
        byte[] pkt = new byte[5 + n];
        pkt[0] = 0x02;
        pkt[1] = (byte) (n + 3);
        pkt[2] = (byte) nextUsbSeq();
        pkt[3] = 1;   // total chunks
        pkt[4] = 1;   // chunk index
        System.arraycopy(payload, 0, pkt, 5, n);
        return pkt;
    }

    /** Full USB packet for a mode command: wrap("ST" + innerSeq + modeChar + "EN"). */
    public byte[] modePacket(char modeChar) {
        byte[] payload = new byte[]{'S','T',(byte) nextSeq(),(byte) modeChar,'E','N'};
        return wrap(payload);
    }

    public int sendRaw(byte[] pkt) {
        if (conn == null || epOut == null) return -1;
        return conn.bulkTransfer(epOut, pkt, pkt.length, 1000);
    }

    /** Synchronous bulk read of one packet; returns bytes read or null on timeout. */
    public byte[] readBulk(int timeoutMs) {
        if (conn == null || epIn == null) return null;
        byte[] buf = new byte[64];
        int n = conn.bulkTransfer(epIn, buf, buf.length, timeoutMs);
        if (n <= 0) return null;
        byte[] out = new byte[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    private static String hex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) sb.append(String.format("%02x", a[i] & 0xff)).append(i % 2 == 1 ? " " : "");
        return sb.toString().trim();
    }

    /** Wrap a payload as one chunk of a multi-chunk message. */
    private byte[] wrapChunk(byte[] payload, int off, int clen, int useq, int total, int idx) {
        byte[] pkt = new byte[5 + clen];
        pkt[0] = 0x02;
        pkt[1] = (byte) (clen + 3);
        pkt[2] = (byte) useq;
        pkt[3] = (byte) total;
        pkt[4] = (byte) idx;
        System.arraycopy(payload, off, pkt, 5, clen);
        return pkt;
    }

    /** Transmit a captured IR payload ("ST..EN", inner) by re-chunking + wrapping + sending. */
    public void transmitPayload(byte[] payload) {
        int total = (payload.length + 55) / 56;
        if (total < 1) total = 1;
        int useq = nextUsbSeq();
        int off = 0;
        for (int idx = 1; idx <= total; idx++) {
            int clen = Math.min(56, payload.length - off);
            byte[] pkt = wrapChunk(payload, off, clen, useq, total, idx);
            sendRaw(pkt);
            off += clen;
            try { Thread.sleep(5); } catch (InterruptedException ignore) {}
        }
    }

    /** Drain/ignore responses for a short time. */
    private void drain(int ms) {
        long d = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < d) { if (readBulk(150) == null) {} }
    }

    /**
     * Read IN packets and reassemble a multi-chunk message whose payload is a learned IR
     * frame ("ST" <seq> 'D' ...). Returns the inner payload, or null on timeout.
     */
    public byte[] readLearned(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        java.io.ByteArrayOutputStream acc = new java.io.ByteArrayOutputStream();
        int curSeq = -1, needTotal = -1, haveIdx = 0;
        while (System.currentTimeMillis() < deadline) {
            byte[] p = readBulk(1500);
            if (p == null || p.length < 5) continue;
            if ((p[0] & 0xff) != 0x01) continue;
            int len = (p[1] & 0xff) - 3;
            int useq = p[2] & 0xff, total = p[3] & 0xff, idx = p[4] & 0xff;
            if (len < 0 || len > 56 || 5 + len > p.length) continue;
            if (idx == 1) { acc.reset(); curSeq = useq; needTotal = total; haveIdx = 0; }
            if (useq != curSeq) continue;
            acc.write(p, 5, len);
            haveIdx = idx;
            if (idx >= needTotal) {
                byte[] payload = acc.toByteArray();
                // learned IR frames carry 'D' (0x44) at payload[3]
                if (payload.length >= 4 && payload[3] == 0x44) return payload;
                // otherwise (ACK) keep listening
                acc.reset(); curSeq = -1;
            }
        }
        return null;
    }

    /** End-to-end test: handshake, learn one signal, then replay it. */
    public List<String> learnAndReplay(int learnMs) {
        List<String> log = new ArrayList<>();
        sendRaw(modePacket('L')); drain(300);
        sendRaw(modePacket('R')); drain(300);
        log.add("point the ORIGINAL remote at the dongle and press ONE button...");
        byte[] learned = readLearned(learnMs);
        if (learned == null) { log.add("no IR captured (null)"); return log; }
        log.add("LEARNED ok, " + learned.length + " bytes: " + hex(learned));
        try { Thread.sleep(2000); } catch (InterruptedException ignore) {}
        log.add("replaying now (watch your device)...");
        // ensure send mode, then transmit
        sendRaw(modePacket('S')); drain(200);
        transmitPayload(learned);
        log.add("replay sent.");
        return log;
    }

    /** Full probe: handshake (L), then try S and R, logging every TX/RX, then record IR. */
    public List<String> probe(int recordMs) {
        List<String> log = new ArrayList<>();
        char[] modes = {'L', 'S', 'R'};
        for (char mc : modes) {
            byte[] pkt = modePacket(mc);
            int ret = sendRaw(pkt);
            log.add("TX '" + mc + "' [" + hex(pkt) + "] ret=" + ret);
            // read up to 3 quick replies to this command
            for (int k = 0; k < 3; k++) {
                byte[] r = readBulk(600);
                if (r == null) break;
                log.add("  RX " + hex(r));
            }
        }
        log.add(">>> now point a remote & press buttons (" + (recordMs / 1000) + "s) <<<");
        long deadline = System.currentTimeMillis() + recordMs;
        int pkts = 0;
        while (System.currentTimeMillis() < deadline) {
            byte[] r = readBulk(1500);
            if (r == null) continue;
            log.add("RX " + hex(r));
            if (++pkts > 80) break;
        }
        log.add("-- recorded " + pkts + " IR packets --");
        return log;
    }

    /** Read one 64-byte IN packet; returns the bytes actually read, or null on timeout. */
    public byte[] readOnce(int timeoutMs) {
        if (conn == null || epIn == null) return null;
        UsbRequest req = new UsbRequest();
        if (!req.initialize(conn, epIn)) { req.close(); return null; }
        ByteBuffer buf = ByteBuffer.allocate(64);
        try {
            if (!req.queue(buf, 64)) return null;
            long deadline = SystemClock.uptimeMillis() + timeoutMs;
            UsbRequest done;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                long rem = deadline - SystemClock.uptimeMillis();
                done = conn.requestWait(rem < 1 ? 1 : rem);
            } else {
                done = conn.requestWait();
            }
            if (done == null || done != req) return null;
            byte[] a = buf.array();
            return a.clone();
        } catch (Exception e) {
            return null;
        } finally {
            try { req.close(); } catch (Exception ignore) {}
        }
    }

    /**
     * Enter learn mode and record everything the dongle returns for up to timeoutMs.
     * Returns a human-readable hex log of every IN packet.
     */
    public List<String> recordLearn(int timeoutMs) {
        List<String> log = new ArrayList<>();
        int sent = sendRaw(modePacket('R'));
        log.add("TX mode R -> bulk ret=" + sent);
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        int pkts = 0;
        while (SystemClock.uptimeMillis() < deadline) {
            byte[] a = readOnce(2000);
            if (a == null) continue;
            // trim: how many meaningful bytes? show first 32 bytes
            StringBuilder sb = new StringBuilder("RX ");
            int n = Math.min(a.length, 40);
            for (int i = 0; i < n; i++) sb.append(String.format("%02x", a[i] & 0xff)).append(i % 2 == 1 ? " " : "");
            log.add(sb.toString().trim());
            if (++pkts > 60) break;
        }
        log.add("-- recorded " + pkts + " packets --");
        return log;
    }
}
