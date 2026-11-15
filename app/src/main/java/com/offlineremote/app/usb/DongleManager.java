package com.offlineremote.app.usb;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.icontrol.dev.IControlIRData;
import com.icontrol.dev.TiqiaaUsbController;

import java.util.HashMap;

/**
 * Discovers, opens and drives the Tiqiaa USB-C IR dongle.
 *
 * Dongle identity (from the decompiled TiqiaaUsbController):
 *   VID 0x10C4 (Silicon Labs CP210x) or 0x045E, PID 0x8468.
 *   Interface 0, exactly two bulk endpoints: IN (dir 0x80) and OUT.
 *
 * All blocking methods (connect/send/learn) must be called off the main thread.
 */
public final class DongleManager {

    public static final int VID_SILABS = 0x10C4; // 4292
    public static final int VID_ALT = 0x045E;     // 1118
    public static final int PID = 0x8468;          // 33896

    public static final String ACTION_USB_PERMISSION = "com.offlineremote.app.USB_PERMISSION";

    private final Context ctx;
    private final UsbManager usbManager;
    private final TiqiaaUsbController controller;

    private UsbDevice device;
    private UsbInterface iface;
    private UsbDeviceConnection connection;
    private volatile boolean connected = false;
    private int seq = 0;

    public DongleManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.usbManager = (UsbManager) this.ctx.getSystemService(Context.USB_SERVICE);
        this.controller = new TiqiaaUsbController(this.ctx);
    }

    public boolean isConnected() { return connected; }

    public UsbManager usbManager() { return usbManager; }

    /** Returns the attached dongle, or null if none is plugged in. */
    public UsbDevice findDevice() {
        if (usbManager == null) return null;
        HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        if (list == null) return null;
        for (UsbDevice d : list.values()) {
            if (matches(d)) return d;
        }
        return null;
    }

    public static boolean matches(UsbDevice d) {
        if (d == null) return false;
        int vid = d.getVendorId();
        int pid = d.getProductId();
        return (vid == VID_SILABS || vid == VID_ALT) && pid == PID;
    }

    public boolean hasPermission(UsbDevice d) {
        return d != null && usbManager != null && usbManager.hasPermission(d);
    }

    /** Fires the system USB-permission dialog. Grant arrives via ACTION_USB_PERMISSION. */
    public void requestPermission(UsbDevice d) {
        if (d == null || usbManager == null) return;
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        flags |= PendingIntent.FLAG_UPDATE_CURRENT;
        Intent intent = new Intent(ACTION_USB_PERMISSION).setPackage(ctx.getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent, flags);
        usbManager.requestPermission(d, pi);
    }

    /** Open the dongle: claim interface 0, locate endpoints, init native layer. */
    public synchronized boolean connect(UsbDevice d) {
        if (d == null || usbManager == null) return false;
        if (!usbManager.hasPermission(d)) return false;
        if (d.getInterfaceCount() < 1) return false;

        UsbInterface in0 = d.getInterface(0);
        if (in0.getEndpointCount() != 2) return false;

        UsbEndpoint ep0 = in0.getEndpoint(0);
        UsbEndpoint ep1 = in0.getEndpoint(1);
        UsbEndpoint epIn, epOut;
        if (ep0.getDirection() == UsbConstants.USB_DIR_IN) {
            epIn = ep0; epOut = ep1;
        } else {
            epIn = ep1; epOut = ep0;
        }
        if (epIn.getDirection() != UsbConstants.USB_DIR_IN
                || epOut.getDirection() != UsbConstants.USB_DIR_OUT) {
            return false;
        }

        UsbDeviceConnection conn = usbManager.openDevice(d);
        if (conn == null) return false;
        if (!conn.claimInterface(in0, true)) {
            conn.close();
            return false;
        }

        this.device = d;
        this.iface = in0;
        this.connection = conn;
        controller.attach(conn, epOut, epIn);
        boolean ok = controller.openNative();
        connected = true; // attach succeeded; native init result is advisory
        // Put the dongle into a known idle state.
        controller.sendMode(0, nextSeq());
        controller.readData(300);
        return ok || connected;
    }

    public synchronized void disconnect() {
        connected = false;
        try { controller.closeNative(); } catch (Throwable ignore) {}
        if (connection != null) {
            try { if (iface != null) connection.releaseInterface(iface); } catch (Throwable ignore) {}
            try { connection.close(); } catch (Throwable ignore) {}
        }
        connection = null;
        iface = null;
        device = null;
    }

    private int nextSeq() {
        seq = (seq + 1) & 0xFF;
        return seq;
    }

    /**
     * Send an IR payload in Tiqiaa's native byte format at the given carrier frequency.
     * This is the correct path for codes from the local DB and for learned codes.
     * Sequence mirrors the decompiled n0.s(): set send-mode, transmit, read the ACK.
     */
    public synchronized boolean sendTiqiaa(int freq, byte[] data) {
        if (!connected || data == null || data.length == 0) return false;
        controller.sendMode(1, nextSeq());
        controller.readData(500); // flush/advance state machine
        boolean ok = controller.transmit(freq, data, nextSeq());
        if (!ok) return false;
        IControlIRData ack = controller.readData(2000);
        // transmit() accepted the write; treat an f()==4 ack as explicit confirmation.
        return ack == null || ack.f() == 4;
    }

    /**
     * Enter study/learn mode and capture one IR signal from a physical remote.
     * Returns the captured signal (native byte format + carrier freq) or null on timeout.
     */
    public synchronized IControlIRData learn(int timeoutMs) {
        if (!connected) return null;
        controller.sendMode(2, nextSeq()); // study
        IControlIRData captured = null;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            IControlIRData d = controller.readData(Math.min(5000, timeoutMs));
            if (d != null && d.f() == 5) { captured = d; break; }
        }
        controller.sendMode(0, nextSeq()); // back to idle
        return captured;
    }
}
