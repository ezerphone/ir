package com.offlineremote.app.sources;

import android.content.Context;
import android.content.res.AssetManager;

import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RGB / colour LED lamp remotes, converted offline from Flipper-IRDB (LED_Lighting).
 * NEC / NECext codes were expanded to microsecond timings at build time, so each
 * button is a plain raw signal the dongle can transmit directly.
 *
 *   assets/led/index.json      - [{id, brand, model, keys}]
 *   assets/led/<id>.json       - {brand, model, buttons:[{n,f,m:[...]}]}
 */
public final class LedLibrarySource {

    public static final class Dev {
        public final String id, brand, model;
        public final int keys;
        Dev(String id, String brand, String model, int keys) {
            this.id = id; this.brand = brand; this.model = model; this.keys = keys;
        }
        public String label() { return model; }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final AssetManager assets;
    private List<Dev> index;

    public LedLibrarySource(Context ctx) {
        this.assets = ctx.getApplicationContext().getAssets();
    }

    private synchronized List<Dev> index() {
        if (index != null) return index;
        List<Dev> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(readAsset("led/index.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Dev(o.getString("id"), o.getString("brand"),
                        o.getString("model"), o.optInt("keys")));
            }
        } catch (Throwable ignore) {}
        index = out;
        return out;
    }

    public List<String> brands() {
        Set<String> s = new LinkedHashSet<>();
        for (Dev d : index()) s.add(d.brand);
        List<String> out = new ArrayList<>(s);
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public List<Dev> models(String brand) {
        List<Dev> out = new ArrayList<>();
        for (Dev d : index()) if (d.brand.equals(brand)) out.add(d);
        return out;
    }

    /** Load the full button list for a device as sendable keys. */
    public List<IrKey> keys(Dev dev) {
        List<IrKey> keys = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(readAsset("led/" + dev.id + ".json"));
            JSONArray btns = o.getJSONArray("buttons");
            for (int i = 0; i < btns.length(); i++) {
                JSONObject b = btns.getJSONObject(i);
                String name = b.getString("n");
                int freq = b.optInt("f", 38000);
                JSONArray m = b.getJSONArray("m");
                int[] micros = new int[m.length()];
                for (int j = 0; j < micros.length; j++) micros[j] = m.getInt(j);
                keys.add(new IrKey(name, 0, Code.signal(freq, micros)));
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
