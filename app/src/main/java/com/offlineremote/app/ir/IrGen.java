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

    // ---- irdb codes: protocol name + device / subdevice / function (IRP definitions) ----

    /** Expand an irdb row; s < 0 means "default subdevice" for the protocol. */
    public static Code irdb(String q, int d, int s, int f) {
        if (q == null) return null;
        switch (q) {
            case "NEC1": case "NEC2":
                return withNecRepeat(nec(38000, new int[]{d & 255, (s < 0 ? 255 - d : s) & 255, f & 255, inv(f)}));
            case "NEC1F16":
                return withNecRepeat(nec(38000, new int[]{d & 255, (s < 0 ? 255 - d : s) & 255, f & 255, (f >> 8) & 255}));
            case "NECX":
                return repeat(necx(d & 255, (s < 0 ? d : s) & 255, f & 255), 108000, 2);
            case "PIONEER":
                return repeat(nec(40000, new int[]{d & 255, (s < 0 ? 255 - d : s) & 255, f & 255, inv(f)}), 108000, 2);
            case "RC5":
                return repeat(rc5(d, f, false), 113778, 2);
            case "RC6":
                return repeat(rc6(0, new int[][]{{d & 255, 8}, {f & 255, 8}}), 107000, 2);
            case "MCE":
                return repeat(rc6(6, new int[][]{{128, 8}, {(s < 0 ? 15 : s) & 255, 8}, {0, 1}, {d & 127, 7}, {f & 255, 8}}), 107000, 2);
            case "JVC": return jvc(d & 255, f & 255);
            case "Sony12": return repeat(sirc(f, d & 31, 5), 45000, 3);
            case "Sony15": return repeat(sirc(f, d & 255, 8), 45000, 3);
            case "Sony20": return repeat(sirc((f & 127), (d & 31) | (((s < 0 ? 0 : s) & 255) << 5), 13), 45000, 3);
            case "MITSUBISHI": return mitsubishi(d & 255, f & 255);
            case "AIWA": return aiwa(d & 255, (s < 0 ? 0 : s) & 31, f & 255);
            case "PANASONIC": return repeat(panasonic(d & 255, (s < 0 ? 0 : s) & 255, f & 255), 108000, 2);
            case "DENON": return sharpDenon(d & 31, f & 255, new int[]{0, 0}, new int[]{1, 1});
            case "SHARP": return sharpDenon(d & 31, f & 255, new int[]{1, 0}, new int[]{0, 1});
            default: return null;
        }
    }

    /** Generic pulse-distance bits, LSB first. */
    private static void pdBits(List<Integer> o, int value, int n, int mark, int s0, int s1) {
        for (int i = 0; i < n; i++) { o.add(mark); o.add(((value >> i) & 1) == 1 ? s1 : s0); }
    }

    private static Code necx(int d, int s, int f) {
        List<Integer> o = new ArrayList<>();
        o.add(4512); o.add(4512);
        for (int b : new int[]{d, s, f, inv(f)}) pwmByte(o, b);
        o.add(560);
        return Code.signal(38000, toArr(o));
    }

    private static Code jvc(int d, int f) {
        List<Integer> o = new ArrayList<>();
        o.add(8400); o.add(4200);
        pdBits(o, d, 8, 525, 525, 1575); pdBits(o, f, 8, 525, 525, 1575);
        o.add(525); o.add(23625);
        pdBits(o, d, 8, 525, 525, 1575); pdBits(o, f, 8, 525, 525, 1575);
        o.add(525);
        return Code.signal(38000, toArr(o));
    }

    private static Code mitsubishi(int d, int f) {
        List<Integer> o = new ArrayList<>();
        for (int n = 0; n < 2; n++) {
            if (n > 0) o.add(24000);
            pdBits(o, d, 8, 300, 900, 2100); pdBits(o, f, 8, 300, 900, 2100);
            o.add(300);
        }
        return Code.signal(33000, toArr(o));
    }

    private static Code aiwa(int d, int s, int f) {
        List<Integer> o = new ArrayList<>();
        o.add(8800); o.add(4400);
        pdBits(o, d, 8, 550, 550, 1650); pdBits(o, s, 5, 550, 550, 1650);
        pdBits(o, (~d) & 255, 8, 550, 550, 1650); pdBits(o, (~s) & 31, 5, 550, 550, 1650);
        pdBits(o, f, 8, 550, 550, 1650); pdBits(o, inv(f), 8, 550, 550, 1650);
        o.add(550); o.add(23100);
        o.add(8800); o.add(4400); o.add(550);
        return Code.signal(38000, toArr(o));
    }

    private static Code panasonic(int d, int s, int f) {
        List<Integer> o = new ArrayList<>();
        o.add(3456); o.add(1728);
        for (int b : new int[]{0x02, 0x20, d, s, f, d ^ s ^ f}) pdBits(o, b, 8, 432, 432, 1296);
        o.add(432);
        return Code.signal(37000, toArr(o));
    }

    private static Code sharpDenon(int d, int f, int[] extA, int[] extB) {
        List<Integer> o = new ArrayList<>();
        pdBits(o, d, 5, 264, 792, 1848); pdBits(o, f, 8, 264, 792, 1848);
        for (int b : extA) { o.add(264); o.add(b == 1 ? 1848 : 792); }
        o.add(264); o.add(43560);
        pdBits(o, d, 5, 264, 792, 1848); pdBits(o, inv(f), 8, 264, 792, 1848);
        for (int b : extB) { o.add(264); o.add(b == 1 ? 1848 : 792); }
        o.add(264);
        return Code.signal(38000, toArr(o));
    }

    /** RC6 (Manchester, 444 us units, MSB first): leader, start 1, mode, trailer 0, payload. */
    private static Code rc6(int mode, int[][] payload) {
        List<Integer> lv = new ArrayList<>();        // 1 = mark, 0 = space, one entry per 444 us
        for (int i = 0; i < 6; i++) lv.add(1);
        for (int i = 0; i < 2; i++) lv.add(0);
        rc6Bit(lv, 1, 1);
        for (int i = 2; i >= 0; i--) rc6Bit(lv, (mode >> i) & 1, 1);
        rc6Bit(lv, 0, 2);                            // trailer / toggle, double width
        for (int[] field : payload)
            for (int i = field[1] - 1; i >= 0; i--) rc6Bit(lv, (field[0] >> i) & 1, 1);
        List<Integer> o = new ArrayList<>();
        int i = 0, n = lv.size();
        while (i < n) {
            int level = lv.get(i), cnt = 0;
            while (i < n && lv.get(i) == level) { cnt++; i++; }
            o.add(cnt * 444);
        }
        return Code.signal(36000, toArr(o));
    }

    private static void rc6Bit(List<Integer> lv, int bit, int width) {
        int first = bit == 1 ? 1 : 0, second = 1 - first;   // RC6: 1 = mark then space
        for (int k = 0; k < width; k++) lv.add(first);
        for (int k = 0; k < width; k++) lv.add(second);
    }

    // ---- carrier frequency of a learned code, guessed from its timing pattern ----

    /** Dongle learn frame: "ST" seq 'D' <duration bytes> "EN" -> carrier Hz (38000 if unknown). */
    public static int detectCarrier(byte[] frame) {
        if (frame == null || frame.length < 8) return 38000;
        List<int[]> runs = new ArrayList<>();           // {isMark, us}
        for (int i = 4; i < frame.length - 2; i++) {
            int b = frame[i] & 0xff;
            int mark = (b & 0x80) != 0 ? 1 : 0;
            int us = (b & 0x7f) * 16;
            if (!runs.isEmpty() && runs.get(runs.size() - 1)[0] == mark) runs.get(runs.size() - 1)[1] += us;
            else runs.add(new int[]{mark, us});
        }
        while (!runs.isEmpty() && runs.get(0)[0] == 0) runs.remove(0);
        if (runs.size() < 4) return 38000;
        int m = runs.get(0)[1], sp = runs.get(1)[1];
        if (near(m, 2400) && near(sp, 600)) return 40000;           // Sony
        if (near(m, 2664) && near(sp, 888)) return 36000;           // RC6 / MCE
        if (near(m, 3456) && near(sp, 1728)) return 36700;          // Panasonic / Kaseikyo
        if (near(m, 9000) && near(sp, 4500)) return 38000;          // NEC
        if (near(m, 4500) && near(sp, 4500)) return 38000;          // Samsung
        // RC5: no leader, every pulse 889 or 1778 us
        boolean rc5 = true;
        for (int k = 0; k < Math.min(runs.size() - 1, 24); k++) {
            int v = runs.get(k)[1];
            if (!(near(v, 889) || near(v, 1778))) { rc5 = false; break; }
        }
        if (rc5) return 36000;
        return 38000;
    }

    private static boolean near(int v, int target) { return Math.abs(v - target) <= target * 0.2; }

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
