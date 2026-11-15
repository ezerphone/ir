package com.offlineremote.app.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.icontrol.dev.IControlIRData;
import com.icontrol.dev.TiqiaaUsbController;
import com.icontrol.dev.n0;

/**
 * Thin wrapper over ZaZa's real device stack (n0 / TiqiaaUsbController / o0). n0 handles
 * discovery, permission, opening and the native handshake itself; we just expose the few
 * operations the UI needs. All blocking calls must run off the main thread.
 *
 * Dongle: VID 0x10C4 (CP210x) or 0x045E, PID 0x8468.
 */
public final class DongleManager {

    public static final int VID_SILABS = 0x10C4;
    public static final int VID_ALT = 0x045E;
    public static final int PID = 0x8468;
    public static final String ACTION_USB_PERMISSION = "com.offlineremote.app.USB_PERMISSION";

    private final Context ctx;
    private final UsbManager usbManager;
    private volatile n0 device;

    public DongleManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.usbManager = (UsbManager) this.ctx.getSystemService(Context.USB_SERVICE);
    }

    /** Lazily create the n0 singleton. Constructing it registers receivers, scans and,
     *  if a dongle is present, requests permission and opens it. Call off the main thread. */
    public synchronized n0 device() {
        if (device == null) device = n0.w(ctx);
        return device;
    }

    public boolean isConnected() {
        return device != null && device.o();
    }

    public UsbManager usbManager() { return usbManager; }

    public UsbDevice findDevice() {
        if (usbManager == null) return null;
        java.util.HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        if (list == null) return null;
        for (UsbDevice d : list.values()) if (matches(d)) return d;
        return null;
    }

    public static boolean matches(UsbDevice d) {
        if (d == null) return false;
        int vid = d.getVendorId();
        return (vid == VID_SILABS || vid == VID_ALT) && d.getProductId() == PID;
    }

    public boolean hasPermission(UsbDevice d) {
        return d != null && usbManager != null && usbManager.hasPermission(d);
    }

    /** Ensure the device exists (which drives the permission request inside o0). */
    public void requestPermission(UsbDevice d) {
        device(); // constructing n0 scans + requests permission as needed
    }

    /** Try to (re)connect. Returns connection state. Call off the main thread. */
    public boolean connect(UsbDevice d) {
        n0 dev = device();
        try { dev.q(); } catch (Throwable ignore) {}
        return dev.o();
    }

    public void disconnect() {
        if (device != null) {
            try { device.h(); } catch (Throwable ignore) {}
        }
    }

    /** Transmit a Tiqiaa-format code at the given carrier frequency. */
    public synchronized boolean sendTiqiaa(int freq, byte[] data) {
        if (data == null || data.length == 0) return false;
        n0 dev = device();
        if (!dev.o()) return false;
        return dev.s(freq, data);
    }

    /** Enter study mode and capture one IR signal (native format + freq). */
    public synchronized IControlIRData learn(int timeoutMsIgnored) {
        n0 dev = device();
        if (!dev.o()) return null;
        return dev.r();
    }
}
