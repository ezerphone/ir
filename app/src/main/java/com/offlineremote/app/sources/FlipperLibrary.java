package com.offlineremote.app.sources;

import android.content.Context;
import android.content.res.AssetManager;

import com.offlineremote.app.ir.IrGen;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All non-AC device remotes converted from Flipper-IRDB, stored compactly as
 * protocol+address+command (expanded to timings by IrGen) or raw timings.
 *
 *   assets/lib/index.json          - [{slug, he, icon, count}]
 *   assets/lib/<slug>/index.json   - [{id, brand, model, keys}]
 *   assets/lib/<slug>/<id>.json    - {brand, model, buttons:[{n,p,a,c} | {n,f,m}]}
 */
public final class FlipperLibrary {

    public static final class Cat {
        public final String slug, he, icon; public final int count;
        Cat(String slug, String he, String icon, int count) { this.slug = slug; this.he = he; this.icon = icon; this.count = count; }
    }

    public static final class Dev {
        public final String slug, id, brand, model; public final int keys;
        Dev(String slug, String id, String brand, String model, int keys) {
            this.slug = slug; this.id = id; this.brand = brand; this.model = model; this.keys = keys;
        }
        public String label() { return model; }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final AssetManager assets;
    private List<Cat> cats;
    private final Map<String, List<Dev>> devCache = new HashMap<>();

    public FlipperLibrary(Context ctx) { this.assets = ctx.getApplicationContext().getAssets(); }

    public synchronized List<Cat> categories() {
        if (cats != null) return cats;
        List<Cat> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(readAsset("lib/index.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Cat(o.getString("slug"), o.getString("he"), o.optString("icon", ""), o.optInt("count")));
            }
        } catch (Throwable ignore) {}
        cats = out;
        return out;
    }

    private synchronized List<Dev> devices(String slug) {
        List<Dev> cached = devCache.get(slug);
        if (cached != null) return cached;
        List<Dev> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(readAsset("lib/" + slug + "/index.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Dev(slug, o.getString("id"), o.getString("brand"), o.getString("model"), o.optInt("keys")));
            }
        } catch (Throwable ignore) {}
        devCache.put(slug, out);
        return out;
    }

    /** Every device in a category. */
    public List<Dev> all(String slug) { return new ArrayList<>(devices(slug)); }

    public List<String> brands(String slug) {
        Set<String> s = new LinkedHashSet<>();
        for (Dev d : devices(slug)) s.add(d.brand);
        List<String> out = new ArrayList<>(s);
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public List<Dev> models(String slug, String brand) {
        List<Dev> out = new ArrayList<>();
        for (Dev d : devices(slug)) if (d.brand.equals(brand)) out.add(d);
        return out;
    }

    /** Load and expand all buttons for a device into sendable keys. */
    public List<IrKey> keys(Dev dev) {
        List<IrKey> keys = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(readAsset("lib/" + dev.slug + "/" + dev.id + ".json"));
            JSONArray btns = o.getJSONArray("buttons");
            for (int i = 0; i < btns.length(); i++) {
                JSONObject b = btns.getJSONObject(i);
                String name = b.getString("n");
                Code c;
                if (b.has("p")) {
                    c = IrGen.toCode(b.getString("p"), b.getString("a"), b.getString("c"));
                } else {
                    JSONArray m = b.getJSONArray("m");
                    int[] micros = new int[m.length()];
                    for (int j = 0; j < micros.length; j++) micros[j] = m.getInt(j);
                    c = Code.signal(b.optInt("f", 38000), micros);
                }
                if (c != null) keys.add(new IrKey(name, 0, c));
            }
        } catch (Throwable ignore) {}
        return keys;
    }

    private String readAsset(String path) throws Exception {
        InputStream is = assets.open(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), UTF8);
    }
}
