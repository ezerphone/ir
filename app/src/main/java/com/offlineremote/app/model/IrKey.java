package com.offlineremote.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One labelled button on a remote and the code it sends. */
public final class IrKey {
    public String label;
    public int func;      // optional Tiqiaa function id (for local-DB keys)
    public Code code;
    public String role;   // button type chosen when learning (KeyRoles.*), null = infer from label
    public int color;     // ARGB colour for COLOR keys, 0 = none

    public IrKey() {}

    public IrKey(String label, int func, Code code) {
        this.label = label;
        this.func = func;
        this.code = code;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("label", label);
        o.put("func", func);
        if (code != null) o.put("code", code.toJson());
        if (role != null) o.put("role", role);
        if (color != 0) o.put("color", color);
        return o;
    }

    public static IrKey fromJson(JSONObject o) throws JSONException {
        IrKey k = new IrKey();
        k.label = o.optString("label", "");
        k.func = o.optInt("func", 0);
        k.role = o.has("role") ? o.optString("role") : null;
        k.color = o.optInt("color", 0);
        JSONObject c = o.optJSONObject("code");
        if (c != null) k.code = Code.fromJson(c);
        return k;
    }
}
