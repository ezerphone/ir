package com.icontrol.dev;

import android.content.Context;

/** Abstract device base (faithful): the learn/send API the app calls. */
public abstract class v implements w {

    public static final String f23247e = "intent_action_check_devices_request";

    public interface a {
        int f23259m0 = 0;
        int f23260n0 = 1;
        int f23261o0 = 2;
        void H8(Object obj, int i10);
    }

    private m f23255a = m.local;
    protected n f23256b;
    protected Context f23257c;
    protected String f23258d;

    protected v(Context context, n nVar) {
        this.f23257c = context.getApplicationContext();
        this.f23256b = nVar;
    }

    @Override public int[] d() { return null; }
    @Override public int e() { return 0; }
    @Override public boolean f(byte[] bArr) { return false; }

    public abstract void h();
    public abstract void i();

    public m j() { return this.f23255a; }
    public n k() { return this.f23256b; }
    public String l() { return ""; }

    public abstract boolean o();
    public boolean p() { return false; }
    public abstract boolean q();
    public IControlIRData r() { return null; }
    public abstract boolean s(int i10, byte[] bArr);
    public void t(m mVar) { this.f23255a = mVar; }
    public void u(String str) { this.f23258d = str; }
    public void g() {}
}
