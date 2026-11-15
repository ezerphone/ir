package com.icontrol.dev;

/** Mode enum (faithful). */
public enum m {
    local(0),
    control(1),
    diy(2);

    private final int f23179a;

    m(int i10) { this.f23179a = i10; }

    public static m a(int i10) {
        if (i10 == 1) return control;
        if (i10 == 2) return diy;
        return local;
    }

    public int b() { return this.f23179a; }
}
