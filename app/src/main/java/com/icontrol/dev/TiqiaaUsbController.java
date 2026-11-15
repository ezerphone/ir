package com.icontrol.dev;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.SystemClock;

import java.nio.ByteBuffer;

/**
 * JNI-boundary class for the Tiqiaa USB-C IR dongle (CP210x bridge, VID 0x10C4/0x045E,
 * PID 0x8468). The native methods below are implemented in libtiqiaadev.so and are bound
 * by their exact class name + method name + signature. Keep the native method
 * declarations byte-for-byte identical to the originals.
 *
 * The surrounding Java (connection handling, read loop) is our own reimplementation of
 * the behaviour observed in the decompiled ZaZaRemote classes (TiqiaaUsbController + n0),
 * simplified into a synchronous API that DongleManager drives.
 *
 * Protocol recap (from decompilation):
 *   - Control/mode command : s(conn, outEp, mode, seq)
 *   - Transmit IR          : t(ctx, conn, outEp, freq, patternBytes, seq)
 *   - "fix"/raw send       : l(conn, outEp, bytes, seq)
 *   - Decode a read buffer : d(buffer) -> IControlIRData
 *   - Open/close native    : o(ctx) / x()
 *   Responses are read from the IN endpoint in 64-byte USB packets:
 *     [0]==1 marks a data packet, [1]-3 = payload length (<=56), payload at offset 5,
 *     [3]/[4] are sequence markers; packets are concatenated until a short packet or
 *     matching markers signal the end, then handed to d().
 *   A response's type f()==4 means "sent OK", f()==5 means "IR received" (learn).
 */
public final class TiqiaaUsbController {

    static {
        // Dependency order: util before dev.
        try { System.loadLibrary("tiqiaautil"); } catch (Throwable ignore) {}
        System.loadLibrary("tiqiaadev");
    }

    private final Context ctx;
    private UsbDeviceConnection conn;
    private UsbEndpoint outEp; // host -> dongle
    private UsbEndpoint inEp;   // dongle -> host

    public TiqiaaUsbController(Context context) {
        this.ctx = context.getApplicationContext();
    }

    // ----- native methods (EXACT signatures — bound by libtiqiaadev.so) -----
    private native IControlIRData d(byte[] bArr);
    private native boolean l(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, byte[] bArr, int i10);
    private native boolean o(Context context);
    private native boolean s(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, int i10, int i11);
    private native boolean t(Context context, UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, int i10, byte[] bArr, int i11);
    private native void x();

    // ----- public API used by DongleManager / sources -----

    /** Supply the opened USB connection and the two bulk endpoints. */
    public void attach(UsbDeviceConnection connection, UsbEndpoint out, UsbEndpoint in) {
        this.conn = connection;
        this.outEp = out;
        this.inEp = in;
    }

    public boolean isAttached() {
        return conn != null && outEp != null && inEp != null;
    }

    /** Initialise native state. Call once after attach(). */
    public boolean openNative() {
        return o(this.ctx);
    }

    /** Send a control/mode command (mode 0 stop, 1 send-ready, 2 study/learn). */
    public boolean sendMode(int mode, int seq) {
        if (conn == null || outEp == null) return false;
        return s(conn, outEp, mode, seq);
    }

    /** Transmit an IR pattern (Tiqiaa internal byte format) at the given carrier frequency. */
    public boolean transmit(int freq, byte[] patternBytes, int seq) {
        if (conn == null || outEp == null) return false;
        return t(ctx, conn, outEp, freq, patternBytes, seq);
    }

    /** Raw/"fix" send variant. */
    public boolean rawSend(byte[] bytes, int seq) {
        if (conn == null || outEp == null) return false;
        return l(conn, outEp, bytes, seq);
    }

    public void closeNative() {
        try { x(); } catch (Throwable ignore) {}
    }

    /**
     * Read one response from the dongle, reassembling multi-packet payloads, and decode
     * it via the native d(). Returns null on timeout or error.
     */
    public IControlIRData readData(int timeoutMs) {
        if (conn == null || inEp == null) return null;
        UsbRequest req = new UsbRequest();
        if (!req.initialize(conn, inEp)) {
            req.close();
            return null;
        }
        byte[] acc = new byte[1024];
        ByteBuffer buf = ByteBuffer.allocate(64);
        int off = 0;
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        try {
            while (SystemClock.uptimeMillis() < deadline) {
                buf.clear();
                if (!req.queue(buf, 64)) break;
                UsbRequest done;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long remaining = deadline - SystemClock.uptimeMillis();
                    if (remaining <= 0) break;
                    done = conn.requestWait(remaining);
                } else {
                    done = conn.requestWait();
                }
                if (done == null || done != req) break;
                byte[] a = buf.array();
                int len = a[1] - 3;
                byte b10 = a[3];
                byte b11 = a[4];
                if (a[0] == 1 && len >= 0 && len <= 56) {
                    System.arraycopy(a, 5, acc, off, len);
                    off += len;
                    if (len < 56 || b10 == b11) {
                        return d(acc);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        } finally {
            try { req.close(); } catch (Exception ignore) {}
        }
        return null;
    }
}
