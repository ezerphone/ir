package com.tiqiaa.local;

import android.content.Context;

import java.util.Locale;

/**
 * JNI-boundary class for the offline IR database baked into liblocalirdb.so.
 *
 * The native library constructs the nested Db* / MachineTypeInfo objects and reads the
 * native-method return types BY EXACT NAME. Every nested class name, field name, field
 * type and constructor signature below is reproduced verbatim from the decompiled
 * ZaZaRemote 5.4.0 class. Do not rename or reorder them.
 *
 * The database is embedded in the .so itself: irdbOpen(ctx, null) opens it with no
 * external file and no network — this is the whole point of the offline design.
 *
 * The public wrapper methods + getters at the bottom are our own thin layer so the rest
 * of the app can use the DB without touching the obfuscated field names.
 */
public final class LocalIrDb {

    static {
        try {
            try { System.loadLibrary("tiqiaautil"); } catch (Throwable ignore) {}
            try { System.loadLibrary("tiqiaadev"); } catch (Throwable ignore) {}
            System.loadLibrary("localirdb");
        } catch (Throwable t) {
            loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static LocalIrDb singleton = null;
    public static volatile String loadError = null; // set if a native lib failed to load
    public volatile boolean lastOpen = false;       // result of the last irdbOpen

    private final Context f56612a;
    private boolean inited = false;

    private LocalIrDb(Context context) {
        this.f56612a = context.getApplicationContext();
    }

    // =================== nested JNI data classes (VERBATIM) ===================

    public static final class DbBrand {
        long f56618a;
        String f56619b;
        String f56620c;
        String f56621d;
        String f56622e;
        String f56623f;

        private DbBrand(long j10, String str, String str2, String str3, String str4, String str5) {
            this.f56619b = "";
            this.f56620c = "";
            this.f56621d = "";
            this.f56622e = "";
            this.f56623f = "";
            this.f56618a = j10;
            if (str != null) this.f56619b = str;
            if (str2 != null) this.f56620c = str2;
            if (str3 != null) this.f56621d = str3;
            if (str4 != null) this.f56622e = str4;
            if (str5 != null) this.f56623f = str5;
        }

        public long id() { return f56618a; }
        public String cn() { return f56619b; }
        public String en() { return f56620c; }
        public String tw() { return f56621d; }
        public String pinyin() { return f56622e; }
        public String remarks() { return f56623f; }

        /** Best display name for the current locale. */
        public String display() {
            if (f56620c != null && !f56620c.isEmpty()) return f56620c;
            if (f56619b != null && !f56619b.isEmpty()) return f56619b;
            return f56621d != null ? f56621d : "";
        }
    }

    public static final class DbInfrared {
        int f56624a;
        byte[] f56625b;
        int f56626c;
        int f56627d;
        int f56628e;
        int f56629f = 0;
        int f56630g;

        private DbInfrared(int i10, int i11, int i12, byte[] bArr, int i13, int i14) {
            this.f56627d = i10;
            this.f56626c = i11;
            this.f56624a = i12;
            this.f56625b = bArr;
            this.f56630g = i13;
            this.f56628e = i14;
        }

        public int func() { return f56624a; }
        public byte[] data() { return f56625b; }
        public int freq() { return f56626c == 0 ? 38000 : f56626c; }
        public int mark() { return f56627d; }
        public int irMark() { return f56628e; }
        public int quality() { return f56629f; }
        public int priority() { return f56630g; }
    }

    public static final class DbIrKey {
        int f56631a;
        String f56632b;
        DbInfrared[] f56633c;
        int f56634d;

        private DbIrKey(int i10, String str, DbInfrared[] dbInfraredArr, int i11) {
            this.f56632b = "";
            this.f56634d = 0;
            this.f56631a = i10;
            if (str != null) this.f56632b = str;
            this.f56633c = dbInfraredArr;
            this.f56634d = i11;
        }

        public int type() { return f56631a; }
        public String name() { return f56632b; }
        public DbInfrared[] infrareds() { return f56633c; }
        public int protocol() { return f56634d; }
    }

    public static final class DbRemote {
        String f56635a;
        int f56636b;
        long f56637c;
        String f56638d;
        long f56639e;
        int f56640f;
        int f56641g;
        int f56642h;
        String f56643i;
        DbBrand f56644j;
        DbIrKey[] f56645k;

        private DbRemote(String str, int i10, DbIrKey[] dbIrKeyArr) {
            this.f56636b = -1;
            this.f56637c = 0L;
            this.f56638d = "";
            this.f56639e = 0L;
            this.f56640f = 0;
            this.f56641g = 0;
            this.f56643i = "86";
            this.f56635a = str;
            this.f56642h = i10;
            this.f56645k = dbIrKeyArr;
        }

        public String id() { return f56635a; }
        public int type() { return f56636b; }
        public long brandId() { return f56637c; }
        public String model() { return f56638d; }
        public int layoutId() { return f56640f; }
        public DbBrand brand() { return f56644j; }
        public DbIrKey[] keys() { return f56645k; }
    }

    public static class IRPResult {
        public boolean f56646a;
        public boolean f56647b;
        public String f56648c;
    }

    public static class MachineTypeInfo {
        public int f56649a;
        public String f56650b;
        public String f56651c;
        public String f56652d;

        public MachineTypeInfo(int i10, String str, String str2, String str3) {
            this.f56649a = i10;
            this.f56650b = str;
            this.f56651c = str2;
            this.f56652d = str3;
        }

        public int type() { return f56649a; }
        public String cn() { return f56650b; }
        public String en() { return f56651c; }
        public String tw() { return f56652d; }
        public String display() {
            if (f56651c != null && !f56651c.isEmpty()) return f56651c;
            if (f56650b != null && !f56650b.isEmpty()) return f56650b;
            return f56652d != null ? f56652d : ("type " + f56649a);
        }
    }

    public static class PhysicalRemoteMatchResult {
        public int f56653a;
        public java.util.List<String> f56654b;
    }

    public static class SearchCount {
        public int f56655a;
        public int f56656b;
    }

    // =================== native methods (EXACT signatures) ===================

    private native DbRemote[] fetchMatchRemotes(Context context, int i10, int i11);
    private native DbRemote fetchRemote(Context context, String str, long j10, int i10);
    private native DbIrKey[] finishfixIr(Context context);
    private native void fixIr(int i10, byte[] bArr, byte[] bArr2);
    private native String[] getAirIRPNR(Context context, byte[] bArr, long j10);
    public native int getAirRemoteFeatureMask(int i10);
    public native int getAirRemoteModeMask(int i10);
    public native MachineTypeInfo[] getAllMachineTypeInfo();
    public native long getBrandId(String str);
    private native DbBrand[] getBrandListByMachineType(Context context, int i10);
    private native String getIRMD(Context context, byte[] bArr, int i10);
    private native String getIRPN(Context context, byte[] bArr, int i10);
    public native MachineTypeInfo getMachineTypeInfo(int i10);
    private native void initSdk(Context context, int i10);
    private native void irdbClose();
    private native DbInfrared irdbGetAirCode(Context context, int i10, int i11, int i12, int i13, byte[] bArr);
    private native DbIrKey[] irdbGetTestKeys(Context context, String str);
    private native int irdbInitKeyMatch(Context context, int i10, int i11, long j10, int[] iArr, int[] iArr2, int i12, int i13);
    private native int irdbInitKeywordSearch(Context context, int i10, int i11, long j10, String[] strArr, int i12);
    private native boolean irdbOpen(Context context, String str);
    private native String[] irdbPhysicalRemoteMatch(Context context, int i10, int i11, long j10, long j11, int[] iArr, String[] strArr);
    public static native long nextId();
    private native DbRemote nsadl(Context context, String str, long j10, int i10);
    private native DbRemote[] nsali(Context context, long j10);
    private native void preparefixIr(int i10);
    public native byte[] up(int i10, byte[] bArr);

    // =================== public wrapper API (our own) ===================

    public static synchronized LocalIrDb get(Context context) {
        if (singleton == null) {
            singleton = new LocalIrDb(context);
        }
        if (!singleton.inited) {
            // ZaZa initialises with w(9, 3): initSdk(appVer=9, dbVer=3) THEN irdbOpen.
            // The DB returns no data unless initSdk runs first.
            try {
                singleton.initSdk(singleton.f56612a, (9 & 0xFFFF) | ((3 & 0xFFFF) << 16));
            } catch (Throwable t) {
                loadError = "initSdk " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            singleton.inited = true;
        }
        singleton.lastOpen = singleton.irdbOpen(singleton.f56612a, null);
        return singleton;
    }

    /** Optional: initialise SDK version metadata (appVer, dbVer) then open. */
    public synchronized void init(int appVer, int dbVer) {
        initSdk(this.f56612a, (appVer & 0xFFFF) | ((dbVer & 0xFFFF) << 16));
        irdbOpen(this.f56612a, null);
    }

    public synchronized void close() {
        try { irdbClose(); } catch (Throwable ignore) {}
    }

    /** Language id used by the DB (mirrors the original locale mapping; 0 = English). */
    public static int lang() {
        String l = Locale.getDefault().getLanguage();
        if ("zh".equals(l)) return 1;
        if ("iw".equals(l) || "he".equals(l)) return 0; // DB has no Hebrew; fall back to English strings
        return 0;
    }

    public MachineTypeInfo[] machineTypes() {
        MachineTypeInfo[] r = getAllMachineTypeInfo();
        return r == null ? new MachineTypeInfo[0] : r;
    }

    public DbBrand[] brands(int machineType) {
        DbBrand[] r = getBrandListByMachineType(this.f56612a, machineType);
        return r == null ? new DbBrand[0] : r;
    }

    public long brandId(String name) {
        return getBrandId(name);
    }

    /** Begin the "press a key, narrow the candidates" match flow. Returns candidate count. */
    public int startKeyMatch(int machineType, int lang, long brandId,
                             int[] okMarks, int[] wrongMarks, int nextKey, int flag) {
        return irdbInitKeyMatch(this.f56612a, machineType, lang, brandId,
                okMarks, wrongMarks, nextKey, flag << 8);
    }

    public DbRemote[] matchRemotes(int from, int count) {
        DbRemote[] r = fetchMatchRemotes(this.f56612a, from, count);
        return r == null ? new DbRemote[0] : r;
    }

    public int startKeywordSearch(int machineType, int lang, long brandId, String[] words, int flag) {
        return irdbInitKeywordSearch(this.f56612a, machineType, lang, brandId, words, flag);
    }

    public DbIrKey[] testKeys(String remoteId) {
        DbIrKey[] r = irdbGetTestKeys(this.f56612a, remoteId);
        return r == null ? new DbIrKey[0] : r;
    }

    /** Fetch a full remote (all keys + IR) by id, with the nsadl fallback the app uses. */
    public DbRemote remote(String id, long brandId) {
        if (id == null || id.isEmpty()) return null;
        int langId = lang();
        DbRemote r = fetchRemote(this.f56612a, id, brandId, langId);
        if (r == null) r = nsadl(this.f56612a, id, brandId, langId);
        return r;
    }

    public DbRemote[] remotesForBrand(long brandId) {
        DbRemote[] r = nsali(this.f56612a, brandId);
        return r == null ? new DbRemote[0] : r;
    }

    /** Air-conditioner stateful code generation: returns the IR for a given AC state. */
    public DbInfrared airCode(int machineType, int a, int b, int c, byte[] state) {
        if (machineType <= 0 || state == null || state.length == 0) return null;
        return irdbGetAirCode(this.f56612a, machineType, a, b, c, state);
    }

    public String[] airPNR(byte[] data, long brandId) {
        return getAirIRPNR(this.f56612a, data, brandId);
    }

    public int acFeatureMask(int pnr) { return getAirRemoteFeatureMask(pnr); }
    public int acModeMask(int pnr) { return getAirRemoteModeMask(pnr); }
}
