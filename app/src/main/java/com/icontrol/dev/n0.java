package com.icontrol.dev;

import android.content.Context;

/**
 * Faithful reproduction of ZaZa's n0 — the USB Tiqiaa device. Drives the controller's
 * state machine: x(mode) sends a mode command and reads back the state; r() learns;
 * s(freq,bytes) transmits. This exact sequencing is what the native layer expects.
 */
public final class n0 extends v implements v.a {

    private static n0 f23192p;

    private final TiqiaaUsbController f23193m;
    private int f23194n;
    private volatile int f23195o;

    private n0(Context context) {
        super(context, n.USB_TIQIAA);
        this.f23195o = 0;
        this.f23193m = new TiqiaaUsbController(context, this);
    }

    public static synchronized n0 w(Context context) {
        if (f23192p == null) f23192p = new n0(context);
        return f23192p;
    }

    private void x(int i10) {
        int i11 = (this.f23194n + 1) & 255;
        this.f23194n = i11;
        IControlIRData q9;
        if (this.f23193m.s(i10, i11) && (q9 = this.f23193m.q(500)) != null) {
            this.f23195o = q9.c();
        }
    }

    @Override public void H8(Object obj, int i10) {
        if (i10 == 1) {
            if (o()) x(0);
        } else if (i10 == 0) {
            h();
        }
        this.f23195o = 0;
    }

    @Override public int[] d() {
        x(2);
        if (this.f23195o != 2) return null;
        IControlIRData q9 = this.f23193m.q(250);
        if (q9 != null) this.f23195o = q9.c();
        if (q9 == null || q9.f() != 5) return null;
        return IrData.p(this.f23257c, 0, 0, q9.f22839b);
    }

    @Override public int e() { return 3; }

    @Override public boolean f(byte[] bArr) {
        if (this.f23195o != 1) x(1);
        int i10 = (this.f23194n + 1) & 255;
        this.f23194n = i10;
        IControlIRData q9;
        if (this.f23193m.v(bArr, i10) && (q9 = this.f23193m.q(20)) != null) {
            this.f23195o = q9.c();
            if (q9.f() == 4) return true;
        }
        return false;
    }

    @Override public void g() { this.f23193m.f(); }

    @Override public void h() {
        g();
        x(0);
        this.f23193m.g();
        this.f23195o = 0;
    }

    @Override public void i() {
        h();
        this.f23193m.k();
        f23192p = null;
    }

    @Override public boolean o() {
        return this.f23193m != null && this.f23193m.n();
    }

    @Override public boolean q() {
        if (!this.f23193m.p()) { h(); return false; }
        if (o()) x(0); else this.f23193m.l();
        return o();
    }

    @Override public IControlIRData r() {
        x(2);
        if (this.f23195o != 2) return null;
        IControlIRData q9 = this.f23193m.q(30000);
        if (q9 != null) {
            this.f23195o = q9.c();
        } else if (this.f23195o == 2) {
            x(6);
        }
        if (this.f23195o != 0) x(0);
        if (q9 == null || q9.f() != 5) return null;
        return q9;
    }

    @Override public boolean s(int i10, byte[] bArr) {
        if (this.f23195o != 1) {
            if (this.f23195o == 2) g();
            x(1);
        }
        if (this.f23195o != 1) return false;
        int i11 = (this.f23194n + 1) & 255;
        this.f23194n = i11;
        IControlIRData q9;
        if (this.f23193m.t(i10, bArr, i11) && (q9 = this.f23193m.q(2000)) != null) {
            this.f23195o = q9.c();
            if (q9.f() == 4) return true;
        }
        return false;
    }
}
