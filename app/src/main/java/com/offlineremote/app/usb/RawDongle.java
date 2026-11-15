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
    private int seq = 0;

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

    /** Build a mode packet: "ST" + seq + modeChar + "EN". */
    public byte[] modePacket(char modeChar) {
        return new byte[]{'S','T',(byte) nextSeq(),(byte) modeChar,'E','N'};
    }

    public int sendRaw(byte[] pkt) {
        if (conn == null || epOut == null) return -1;
        return conn.bulkTransfer(epOut, pkt, pkt.length, 1000);
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
