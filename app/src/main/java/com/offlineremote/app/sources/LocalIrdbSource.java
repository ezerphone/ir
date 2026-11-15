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
