package com.offlineremote.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A remote the user has saved. Three shapes:
 *   - button remote (TV/STB/learned): {@link #keys} holds labelled codes.
 *   - SmartIR air conditioner: {@link #acFile} names the bundled asset; codes are
 *     generated per state (mode/temp/fan) on demand by SmartIrSource.
 *   - local-DB remote: keys are materialised from the embedded DB when saved.
 */
public final class SavedRemote {
    public String id;
    public String name;
    public String category;   // "ac", "tv", "stb", "fan", "other"
    public String source;     // "local", "smartir", "learned", "cloud"
    public String acFile;     // SmartIR asset filename (AC only), else null
    public String brand;      // manufacturer shown under the name on the home card
    public List<IrKey> keys = new ArrayList<>();
    public long createdAt;

    public SavedRemote() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isAc() { return "ac".equals(category); }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("category", category);
        o.put("source", source);
        if (acFile != null) o.put("acFile", acFile);
        if (brand != null) o.put("brand", brand);
        o.put("createdAt", createdAt);
        JSONArray a = new JSONArray();
        for (IrKey k : keys) a.put(k.toJson());
        o.put("keys", a);
        return o;
    }

    public static SavedRemote fromJson(JSONObject o) throws JSONException {
        SavedRemote r = new SavedRemote();
        r.id = o.optString("id", r.id);
        r.name = o.optString("name", "");
        r.category = o.optString("category", "other");
        r.source = o.optString("source", "learned");
        r.acFile = o.has("acFile") ? o.optString("acFile") : null;
        r.brand = o.has("brand") ? o.optString("brand") : null;
        r.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        r.keys = new ArrayList<>();
        JSONArray a = o.optJSONArray("keys");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                r.keys.add(IrKey.fromJson(a.getJSONObject(i)));
            }
        }
        return r;
    }
}
