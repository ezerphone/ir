package com.offlineremote.app.ir;

import com.offlineremote.app.model.Code;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands a compact IR code (protocol + address + command, as stored in the bundled
 * library) into carrier frequency + microsecond on/off timings, which the dongle sends.
 * The output is a whole short button press, as a real remote sends it: NEC frame + repeat
 * code, Sony x3, RC5 x2, Samsung x2. ZaZa's codes are recordings of real remotes and carry
 * these repeats; a single bare frame is much easier to miss at a distance.
 * Supported: NEC, NECext, NEC42, Sony SIRC/SIRC15/SIRC20, Samsung32, RC5, RC5X.
 * Unsupported protocols return null (those buttons were dropped at build time, or come
 * as raw timings instead).
 */
public final class IrGen {

    private IrGen() {}

    private static int hi(String hex) { return Integer.parseInt(hex.substring(0, 2), 16); }  // byte0
    private static int lo(String hex) { return Integer.parseInt(hex.substring(2, 4), 16); }  // byte1

    /** Build a Code (SIGNAL) from a stored protocol code, or null if unsupported. */
    public static Code toCode(String proto, String addrHex, String cmdHex) {
        if (proto == null || addrHex == null || cmdHex == null) return null;
        int a0 = hi(addrHex), a1 = lo(addrHex);
        int c0 = hi(cmdHex),  c1 = lo(cmdHex);
        Code one;
        switch (proto) {
            case "NEC":       one = nec(38000, new int[]{a0, inv(a0), c0, inv(c0)}); return withNecRepeat(one);
            case "NECext":    one = nec(38000, new int[]{a0, a1, c0, c1}); return withNecRepeat(one);
            case "NEC42":     one = nec42(a0, a1, c0); return withNecRepeat(one);
            case "Samsung32": one = samsung(a0, c0); return repeat(one, 108000, 2);
            case "SIRC":      one = sirc(c0, a0, 5); return repeat(one, 45000, 3);
            case "SIRC15":    one = sirc(c0, a0, 8); return repeat(one, 45000, 3);
            case "SIRC20":    one = sirc20(c0, a0, a1); return repeat(one, 45000, 3);
            case "RC5":       one = rc5(a0, c0, true); return repeat(one, 113778, 2);
            case "RC5X":      one = rc5(a0, c0, false); return repeat(one, 113778, 2);
            default:          return null;
        }
    }

    // ---- turning a single frame into a realistic button press ----

    private static int sum(int[] a) { int s = 0; for (int v : a) s += v; return s; }

    /** Append a space to a mark/space list (merging with a trailing space). */
    private static void addSpace(List<Integer> o, int us) {
        if (us <= 0) return;
        if (o.size() % 2 == 0 && !o.isEmpty()) o.set(o.size() - 1, o.get(o.size() - 1) + us);
        else o.add(us);
    }

    /** The frame sent `count` times, one every `periodUs` (start to start). */
    private static Code repeat(Code one, int periodUs, int count) {
        if (one == null) return null;
        int[] f = one.micros;
        List<Integer> o = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            if (n > 0) addSpace(o, Math.max(periodUs - sum(f), 10000));
            for (int v : f) o.add(v);
        }
        return Code.signal(one.freq, toArr(o));
    }

    /** NEC frame followed by one repeat code, 108 ms after the frame started. */
    private static Code withNecRepeat(Code one) {
        if (one == null) return null;
        List<Integer> o = new ArrayList<>();
        for (int v : one.micros) o.add(v);
        addSpace(o, Math.max(108000 - sum(one.micros), 10000));
        o.add(9000); o.add(2250); o.add(560);
        return Code.signal(one.freq, toArr(o));
    }

    private static int inv(int b) { return (~b) & 0xff; }

    // ---- NEC family (mark 560, space 560/1690, trailer 560) ----
    private static Code nec(int freq, int[] bytesLsb) {
        List<Integer> o = new ArrayList<>();
        o.add(9000); o.add(4500);
        for (int b : bytesLsb) pwmByte(o, b);
        o.add(560);
        return Code.signal(freq, toArr(o));
    }

    private static Code samsung(int a0, int c0) {
        List<Integer> o = new ArrayList<>();
        o.add(4500); o.add(4500);
        pwmByte(o, a0); pwmByte(o, a0); pwmByte(o, c0); pwmByte(o, inv(c0));
        o.add(560);
        return Code.signal(38000, toArr(o));
    }

    private static Code nec42(int a0, int a1, int c0) {
        int addr = (a0 | (a1 << 8)) & 0x1FFF;      // 13-bit
        int cmd = c0 & 0xFF;
        List<Integer> o = new ArrayList<>();
        o.add(9000); o.add(4500);
        pwmBits(o, addr, 13);
        pwmBits(o, (~addr) & 0x1FFF, 13);
        pwmBits(o, cmd, 8);
        pwmBits(o, (~cmd) & 0xFF, 8);
        o.add(560);
        return Code.signal(38000, toArr(o));
    }

    private static void pwmByte(List<Integer> o, int b) { pwmBits(o, b, 8); }

    private static void pwmBits(List<Integer> o, int value, int n) {
        for (int i = 0; i < n; i++) {
            int bit = (value >> i) & 1;        // LSB first
            o.add(560);
            o.add(bit == 1 ? 1690 : 560);
        }
    }

    // ---- Sony SIRC (40kHz, leader 2400/600, bit mark 1200/600 + space 600) ----
    private static Code sirc(int cmd, int addr, int addrBits) {
        List<Integer> o = new ArrayList<>();
        o.add(2400); o.add(600);
        sircBits(o, cmd & 0x7F, 7);
        sircBits(o, addr, addrBits);
        return Code.signal(40000, toArr(o));
    }

    private static Code sirc20(int cmd, int a0, int a1) {
        int addr13 = (a0 | (a1 << 8)) & 0x1FFF;
        List<Integer> o = new ArrayList<>();
        o.add(2400); o.add(600);
        sircBits(o, cmd & 0x7F, 7);
        sircBits(o, addr13, 13);
        return Code.signal(40000, toArr(o));
    }

    private static void sircBits(List<Integer> o, int value, int n) {
        for (int i = 0; i < n; i++) {
            int bit = (value >> i) & 1;        // LSB first
            o.add(bit == 1 ? 1200 : 600);
            o.add(600);
        }
    }

    // ---- Philips RC5 / RC5X (36kHz, Manchester, half-bit 889) ----
    private static Code rc5(int addr, int cmd, boolean plain) {
        int s2 = plain ? 1 : (1 - ((cmd >> 6) & 1));   // field bit
        int a = addr & 0x1F;                            // 5 bits
        int c = cmd & 0x3F;                             // 6 bits
        List<Integer> bitsMsb = new ArrayList<>();
        bitsMsb.add(1);          // S1
        bitsMsb.add(s2);         // S2 / field
        bitsMsb.add(0);          // toggle (fixed for one-shot)
        for (int i = 4; i >= 0; i--) bitsMsb.add((a >> i) & 1);
        for (int i = 5; i >= 0; i--) bitsMsb.add((c >> i) & 1);

        // half-bit levels: 1 -> space,mark ; 0 -> mark,space  (0=space,1=mark)
        List<Integer> lv = new ArrayList<>();
        for (int b : bitsMsb) {
            if (b == 1) { lv.add(0); lv.add(1); }
            else { lv.add(1); lv.add(0); }
        }
        // merge equal runs into durations of count*889, dropping leading spaces
        List<Integer> o = new ArrayList<>();
        int i = 0, n = lv.size();
        while (i < n && lv.get(i) == 0) i++;            // skip pre-signal spaces
        while (i < n) {
            int level = lv.get(i), cnt = 0;
            while (i < n && lv.get(i) == level) { cnt++; i++; }
            o.add(cnt * 889);
        }
        if (o.isEmpty()) return null;
        return Code.signal(36000, toArr(o));
    }

    private static int[] toArr(List<Integer> o) {
        int[] a = new int[o.size()];
        for (int i = 0; i < a.length; i++) a[i] = o.get(i);
        return a;
    }
}
