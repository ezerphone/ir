package com.offlineremote.app.sources;

import android.content.Context;

import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.SavedRemote;
import com.tiqiaa.local.LocalIrDb;

import java.util.ArrayList;
import java.util.List;

/**
 * The IR database embedded in liblocalirdb.so (ZaZa's offline subset). Codes come out in
 * Tiqiaa's native byte format, so they are sent verbatim (no re-encoding needed) — the
 * known-good path.
 */
public final class LocalIrdbSource {

    private final LocalIrDb db;

    public LocalIrdbSource(Context ctx) {
        this.db = LocalIrDb.get(ctx);
    }

    public LocalIrDb raw() { return db; }

    /** ZaZa's hardcoded appliance types (id -> label), as used by the real app. */
    public static final class Cat {
        public final int id; public final String label; public final String category;
        public Cat(int id, String label, String category) { this.id = id; this.label = label; this.category = category; }
    }

    public static final Cat[] CATS = new Cat[] {
            new Cat(1, "טלוויזיה", "tv"),
            new Cat(2, "מזגן", "ac"),
            new Cat(3, "מאוורר", "fan"),
            new Cat(4, "מקרן", "other"),
            new Cat(5, "ממיר", "stb"),
            new Cat(6, "DVD / וידאו / AV", "other"),
            new Cat(7, "מצלמה", "other"),
            new Cat(8, "מתג IR", "other"),
            new Cat(9, "מגבר", "other"),
            new Cat(10, "IPTV", "stb"),
            new Cat(11, "OTT Box", "stb"),
            new Cat(12, "דוד מים", "other"),
            new Cat(13, "מטהר אוויר", "other"),
    };

    /** Remotes for a brand, optionally filtered to a machine type (-1 = any). */
    public List<LocalIrDb.DbRemote> remotes(int machineType, long brandId) {
        List<LocalIrDb.DbRemote> out = new ArrayList<>();
        for (LocalIrDb.DbRemote r : db.remotesForBrand(brandId)) {
            if (machineType < 0 || r.type() == machineType || r.type() == -1) out.add(r);
        }
        return out;
    }

    public List<LocalIrDb.MachineTypeInfo> types() {
        List<LocalIrDb.MachineTypeInfo> out = new ArrayList<>();
        for (LocalIrDb.MachineTypeInfo t : db.machineTypes()) out.add(t);
        return out;
    }

    public List<LocalIrDb.DbBrand> brands(int machineType) {
        List<LocalIrDb.DbBrand> out = new ArrayList<>();
        for (LocalIrDb.DbBrand b : db.brands(machineType)) out.add(b);
        return out;
    }

    public List<LocalIrDb.DbRemote> remotesForBrand(long brandId) {
        List<LocalIrDb.DbRemote> out = new ArrayList<>();
        for (LocalIrDb.DbRemote r : db.remotesForBrand(brandId)) out.add(r);
        return out;
    }

    public LocalIrDb.DbRemote fetch(String id, long brandId) {
        return db.remote(id, brandId);
    }

    /** Convert an embedded-DB remote into a saveable button remote. */
    public SavedRemote toSavedRemote(LocalIrDb.DbRemote r, String category, String name) {
        SavedRemote out = new SavedRemote();
        out.source = "local";
        out.category = category;
        out.name = name;
        if (r.keys() != null) {
            for (LocalIrDb.DbIrKey k : r.keys()) {
                if (k == null || k.infrareds() == null || k.infrareds().length == 0) continue;
                LocalIrDb.DbInfrared inf = k.infrareds()[0];
                if (inf == null || inf.data() == null) continue;
                Code code = Code.nativeCode(inf.freq(), inf.data());
                out.keys.add(new IrKey(keyLabel(k), k.type(), code));
            }
        }
        return out;
    }

    private static String keyLabel(LocalIrDb.DbIrKey k) {
        if (k.name() != null && !k.name().isEmpty()) return k.name();
        return KeyNames.of(k.type());
    }
}
