package com.icontrol.dev;

/** Device-type enum (faithful). Only USB_TIQIAA is used by this app. */
public enum n {
    USB_TIQIAA(0),
    BLUE_STD(1),
    SMART_ZAZA(10),
    TIQIAA_CIR(52),
    TIQIAA_DEVICE_G(100);

    private final int f23191a;

    n(int i10) { this.f23191a = i10; }

    public static n a(int i10) { return b(i10, SMART_ZAZA); }

    public static n b(int i10, n nVar) {
        for (n v : values()) if (v.f23191a == i10) return v;
        return nVar;
    }

    public int c() { return this.f23191a; }
}
