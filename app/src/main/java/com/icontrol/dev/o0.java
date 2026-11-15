package com.icontrol.dev;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

/**
 * Abstract USB device base (faithful reproduction of ZaZa's o0). Handles device
 * discovery, the permission flow, opening interface 0 + its two bulk endpoints, and
 * calling p() (native init) on open. The native layer in TiqiaaUsbController reads the
 * protected connection/endpoint fields set here.
 */
public abstract class o0 {
    public static final String A = "com.offlineremote.app.USB_CONNECTED";
    public static final String B = "com.offlineremote.app.USB_DISCONNECTED";

    public static final String f23208y = "com.offlineremote.app.USBService.USB_PERMISSION";
    public static final String f23209z = "com.offlineremote.app.intent.action.usb_inserted";

    protected Context f23211b;
    private final UsbManager f23212c;
    private UsbDevice f23213d;
    protected UsbInterface f23214e;
    protected UsbDeviceConnection f23215f;
    protected UsbEndpoint f23216g; // OUT
    protected UsbEndpoint f23217h; // IN
    private final PendingIntent f23218i;
    private v.a f23220k;
    private boolean f23219j = false;
    private final BroadcastReceiver f23221l = new a();
    private boolean f23210a = false;

    class a extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (action.equals(f23209z)) {
                if (o0.this.f23210a) o0.this.u(true); else o0.this.l();
            }
            if (o0.this.f23212c != null) {
                if (action.equals(v.f23247e)) {
                    if (o0.this.f23210a) o0.this.u(true); else o0.this.l();
                    return;
                }
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice != null && o0.this.m(usbDevice)) {
                    if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                        if (o0.this.f23210a) o0.this.u(false);
                        o0.this.g();
                        if (o0.this.f23220k != null) o0.this.f23220k.H8(usbDevice, 0);
                    } else if (f23208y.equals(action)) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            o0.this.j(usbDevice);
                        }
                    } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                        o0.this.j(usbDevice);
                    }
                }
            }
        }
    }

    protected o0(Context context, v.a aVar) {
        Context app = context.getApplicationContext();
        this.f23211b = app;
        this.f23220k = aVar;
        this.f23212c = (UsbManager) app.getSystemService(Context.USB_SERVICE);
        int piFlags = Build.VERSION.SDK_INT >= 23 ? 0x4000000 /* FLAG_IMMUTABLE */ : 0;
        this.f23218i = PendingIntent.getBroadcast(this.f23211b, 0,
                new Intent(f23208y).setPackage(app.getPackageName()), piFlags);
        r();
        l();
    }

    int j(UsbDevice usbDevice) {
        int i10 = i(usbDevice);
        if (i10 == 0) {
            u(true);
            if (this.f23220k != null) this.f23220k.H8(usbDevice, 1);
        } else if (i10 == 3) {
            this.f23212c.requestPermission(usbDevice, this.f23218i);
        }
        return i10;
    }

    private void r() {
        if (this.f23219j) return;
        IntentFilter f = new IntentFilter();
        f.addAction(v.f23247e);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        f.addAction(f23208y);
        f.addAction(f23209z);
        o.m(this.f23211b, this.f23221l, f, true);
        this.f23219j = true;
    }

    void u(boolean z9) {
        this.f23211b.sendBroadcast(new Intent(z9 ? A : B).setPackage(this.f23211b.getPackageName()));
    }

    public abstract void f();

    public void g() {
        this.f23210a = false;
        this.f23213d = null;
        UsbDeviceConnection c = this.f23215f;
        if (c != null) {
            this.f23215f = null;
            try { c.releaseInterface(this.f23214e); } catch (Throwable ignore) {}
            try { c.close(); } catch (Throwable ignore) {}
        }
        this.f23215f = null;
        this.f23214e = null;
        this.f23216g = null;
        this.f23217h = null;
    }

    public int h() {
        UsbDevice d;
        if (this.f23212c != null && (d = this.f23213d) != null) return i(d);
        return -1;
    }

    public int i(UsbDevice usbDevice) {
        this.f23210a = false;
        if (!this.f23212c.hasPermission(usbDevice)) return 3;
        if (usbDevice.getInterfaceCount() < 1) return -1;
        UsbInterface usbInterface = usbDevice.getInterface(0);
        if (usbInterface.getEndpointCount() != 2) return -2;
        UsbEndpoint endpoint = usbInterface.getEndpoint(0);
        UsbEndpoint usbEndpoint = null; // OUT
        if (endpoint.getDirection() != 128) { usbEndpoint = endpoint; endpoint = null; }
        UsbEndpoint endpoint2 = usbInterface.getEndpoint(1);
        if (endpoint2.getDirection() == 128) endpoint = endpoint2; else usbEndpoint = endpoint2;
        if (endpoint != null && usbEndpoint != null) {
            UsbDeviceConnection openDevice = this.f23212c.openDevice(usbDevice);
            if (openDevice == null) return 4;
            if (!openDevice.claimInterface(usbInterface, true)) {
                openDevice.releaseInterface(usbInterface);
                openDevice.close();
                return 4;
            }
            this.f23213d = usbDevice;
            this.f23215f = openDevice;
            this.f23214e = usbInterface;
            this.f23216g = usbEndpoint; // OUT
            this.f23217h = endpoint;    // IN
            this.f23210a = p();
            return 0;
        }
        return -3;
    }

    public void k() {
        this.f23220k = null;
        g();
        if (this.f23219j) {
            try { this.f23211b.unregisterReceiver(this.f23221l); } catch (Throwable ignore) {}
            this.f23219j = false;
        }
    }

    int l() {
        UsbManager usbManager = this.f23212c;
        if (usbManager == null) return 1;
        java.util.HashMap<String, UsbDevice> map;
        try { map = usbManager.getDeviceList(); } catch (Throwable t) { map = null; }
        if (map != null && !map.isEmpty()) {
            for (UsbDevice d : map.values()) if (m(d)) return j(d);
            return 5;
        }
        return 2;
    }

    public abstract boolean m(UsbDevice usbDevice);

    public boolean n() { return this.f23210a; }

    public boolean o() {
        UsbManager usbManager = this.f23212c;
        if (usbManager == null) return false;
        java.util.HashMap<String, UsbDevice> map;
        try { map = usbManager.getDeviceList(); } catch (Throwable t) { map = null; }
        if (map == null || map.isEmpty()) return false;
        for (UsbDevice d : map.values()) if (m(d)) return true;
        return false;
    }

    public abstract boolean p();
    public abstract IControlIRData q(int i10);
    public boolean s(int i10, int i11) { return true; }
    public abstract boolean t(int i10, byte[] bArr, int i11);
}
