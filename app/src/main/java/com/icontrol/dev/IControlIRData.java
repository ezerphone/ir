package com.icontrol.dev;

/**
 * JNI-boundary class. The Tiqiaa native library (libtiqiaadev.so) constructs and
 * reads instances of this class by its exact fully-qualified name, field names and
 * constructor signatures. Do NOT rename fields, change their types/order, or alter
 * the constructor signatures — the native code looks them up by name via JNI and a
 * mismatch will crash or silently fail.
 *
 * Reconstructed verbatim from the decompiled ZaZaRemote 5.4.0 class of the same name.
 *
 * Field semantics (from decompilation):
 *   f22838a : type   (-1 unknown, 4 = send ACK, 5 = received IR signal, ...)
 *   f22839b : data   (IR payload bytes in Tiqiaa's internal format)
 *   f22840c : freq   (carrier frequency in Hz, e.g. 38000)
 *   f22843f : state  (device state reported back; read via c())
 *   f22845h : seq / aux
 */
public final class IControlIRData {

    static final int f22824i = -1;
    static final int f22825j = 0;
    static final int f22826k = 1;
    static final int f22827l = 2;
    static final int f22828m = 3;
    static final int f22829n = 4;
    static final int f22830o = 5;
    static final int f22831p = 6;
    static final int f22832q = 7;
    static final int f22833r = 0;
    static final int f22834s = 1;
    static final int f22835t = 2;
    static final int f22836u = 6;
    static final int f22837v = 7;

    private int f22838a;
    byte[] f22839b;
    int f22840c;
    private boolean f22841d;
    private boolean f22842e;
    private int f22843f;
    private final int f22844g;
    private int f22845h;

    public IControlIRData() {
        this.f22838a = -1;
        this.f22841d = true;
        this.f22842e = true;
        this.f22843f = 0;
        this.f22844g = 0;
    }

    IControlIRData(int i10, int i11, int i12, byte[] bArr, int i13, int i14, boolean z9, boolean z10) {
        this.f22838a = i10;
        this.f22840c = i11;
        this.f22845h = i12;
        this.f22839b = bArr;
        this.f22844g = i13;
        this.f22843f = i14;
        this.f22841d = z9;
        this.f22842e = z10;
    }

    public IControlIRData(int i10, int i11, byte[] bArr) {
        this.f22838a = 5;
        this.f22840c = i10;
        this.f22845h = i11;
        this.f22839b = bArr;
        this.f22844g = 0;
        this.f22843f = 2;
        this.f22841d = true;
        this.f22842e = true;
    }

    public byte[] a() { return this.f22839b; }      // data
    public int b() { return this.f22840c; }         // freq
    public int c() { return this.f22843f; }         // state
    public int d() { return this.f22844g; }
    public int e() { return this.f22845h; }
    public int f() { return this.f22838a; }         // type
    boolean g() { return this.f22841d; }
    boolean h() { return this.f22842e; }
    public void i(byte[] bArr) { this.f22839b = bArr; }
    public void j(int i10) { this.f22840c = i10; }
    public void k(int i10) { this.f22845h = i10; }
}
