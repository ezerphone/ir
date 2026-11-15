package com.icontrol.dev;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbRequest;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Faithful reproduction of ZaZa's TiqiaaUsbController. Binds to libtiqiaadev.so via the
 * private native methods (d/l/o/s/t/x) by exact name+signature. The threaded reader and
 * the o0 overrides reproduce the original driving logic exactly — this is what makes
 * send and learn actually work.
 */
public final class TiqiaaUsbController extends o0 implements Runnable {
    static final int F = 4292;   // 0x10C4
    static final int G = 1118;   // 0x045E
    static final int H = 33896;  // 0x8468

    private final AtomicBoolean C;
    private final Object D;
    private final ConcurrentLinkedQueue<IControlIRData> E;

    static {
        try { System.loadLibrary("tiqiaautil"); } catch (Throwable ignore) {}
        System.loadLibrary("tiqiaadev");
    }

    public TiqiaaUsbController(Context context, v.a aVar) {
        super(context, aVar);
        this.C = new AtomicBoolean();
        this.D = new Object();
        this.E = new ConcurrentLinkedQueue<>();
    }

    private native IControlIRData d(byte[] bArr);
    private native boolean l(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, byte[] bArr, int i10);
    private native boolean o(Context context);
    private native boolean s(UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, int i10, int i11);
    private native boolean t(Context context, UsbDeviceConnection usbDeviceConnection, UsbEndpoint usbEndpoint, int i10, byte[] bArr, int i11);
    private native void x();

    private IControlIRData w(int i10) {
        if (this.f23215f == null) return null;
        IControlIRData poll = this.E.poll();
        if (poll != null) return poll;
        if (this.C.compareAndSet(false, true)) {
            new Thread(this).start();
        }
        synchronized (this.D) {
            try { this.D.wait(i10); } catch (InterruptedException ignore) {} finally { }
        }
        return this.E.poll();
    }

    @Override public void f() {
        synchronized (this.D) { this.D.notify(); }
    }

    @Override public synchronized void g() {
        super.g();
        try { x(); } catch (Throwable ignore) {}
        this.C.set(false);
        this.E.clear();
    }

    @Override public boolean m(UsbDevice usbDevice) {
        if (usbDevice == null) return false;
        int vid = usbDevice.getVendorId();
        int pid = usbDevice.getProductId();
        return (vid == F || vid == G) && pid == H;
    }

    @Override public synchronized boolean p() { return o(this.f23211b); }

    @Override public IControlIRData q(int i10) { return w(i10); }

    @Override public void run() {
        synchronized (this) {
            if (!n()) { endThread(); return; }
            UsbRequest usbRequest = new UsbRequest();
            if (!usbRequest.initialize(this.f23215f, this.f23217h)) { endThread(); return; }
            byte[] bArr = new byte[1024];
            ByteBuffer allocate = ByteBuffer.allocate(64);
            while (true) {
                int i10 = 0;
                while (true) {
                    synchronized (this) {
                        if (!n()) { endThread(); return; }
                        allocate.clear();
                        if (!usbRequest.queue(allocate, 64)) { endThread(); return; }
                        try {
                            UsbRequest requestWait = this.f23215f.requestWait();
                            if (requestWait != null && requestWait.equals(usbRequest)) {
                                byte[] array = allocate.array();
                                int i11 = array[1] - 3;
                                byte b10 = array[3];
                                byte b11 = array[4];
                                if (array[0] == 1 && i11 >= 0 && i11 <= 56) {
                                    System.arraycopy(array, 5, bArr, i10, i11);
                                    i10 += i11;
                                    if (i11 < 56 || b10 == b11) break;
                                }
                            }
                        } catch (Exception e) {
                            endThread();
                            return;
                        }
                    }
                }
                IControlIRData d10 = d(bArr);
                if (d10 != null) this.E.add(d10);
                synchronized (this.D) { this.D.notify(); }
            }
        }
    }

    private void endThread() {
        this.C.set(false);
        synchronized (this.D) { this.D.notify(); }
    }

    @Override public boolean s(int i10, int i11) {
        this.E.clear();
        return s(this.f23215f, this.f23216g, i10, i11);
    }

    @Override public boolean t(int i10, byte[] bArr, int i11) {
        this.E.clear();
        return t(this.f23211b, this.f23215f, this.f23216g, i10, bArr, i11);
    }

    public boolean v(byte[] bArr, int i10) {
        this.E.clear();
        return l(this.f23215f, this.f23216g, bArr, i10);
    }
}
