package com.offlineremote.app.sources;

import android.content.Context;
import android.content.res.AssetManager;

import com.offlineremote.app.ir.BroadlinkCodec;
import com.offlineremote.app.ir.IrSignal;
import com.offlineremote.app.ir.ProntoCodec;
import com.offlineremote.app.model.Code;

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
 * Bundled open database (SmartIR / Home Assistant). Provides offline air-conditioner and
 * TV codes with no network. AC codes are Broadlink/Pronto encoded; we decode them to raw
 * microsecond signals ({@link Code.Kind#SIGNAL}).
 *
 * Assets:
 *   assets/smartir/index.json            - metadata for every device
 *   assets/smartir/climate/<file>.json   - AC devices
 *   assets/smartir/media/<file>.json     - TV devices
 */
public final class SmartIrSource {

    public static final class Entry {
        public String file;
        public String manufacturer;
        public List<String> models = new ArrayList<>();
        public String controller;
        public String encoding;
        public String kind;    // "ac" or "tv"
        public Integer minT, maxT;
        public List<String> modes = new ArrayList<>();
        public List<String> fans = new ArrayList<>();
        public String label() {
            String m = models.isEmpty() ? "" : (" " + models.get(0));
            return manufacturer + m;
        }
    }

    /** A parsed AC device, ready to generate codes per state. */
    public static final class AcSpec {
        public Entry entry;
        public JSONObject commands;
        public int minT = 16, maxT = 30;
        public List<String> modes = new ArrayList<>();
        public List<String> fans = new ArrayList<>();
        public String encoding = "Base64";
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final AssetManager assets;
    private List<Entry> cache;

    public SmartIrSource(Context ctx) {
        this.assets = ctx.getApplicationContext().getAssets();
    }

    public synchronized List<Entry> index() {
        if (cache != null) return cache;
        cache = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(readAsset("smartir/index.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.file = o.optString("file");
                e.manufacturer = o.optString("manufacturer");
                e.controller = o.optString("controller");
                e.encoding = o.optString("encoding");
                e.kind = o.optString("kind");
                if (o.has("minT") && !o.isNull("minT")) e.minT = o.optInt("minT");
                if (o.has("maxT") && !o.isNull("maxT")) e.maxT = o.optInt("maxT");
                e.models = strList(o.optJSONArray("models"));
                e.modes = strList(o.optJSONArray("modes"));
                e.fans = strList(o.optJSONArray("fan"));
                cache.add(e);
            }
        } catch (Exception ignore) {}
        return cache;
    }

    public List<String> manufacturers(String kind) {
        Set<String> set = new LinkedHashSet<>();
        for (Entry e : index()) if (kind.equals(e.kind) && e.manufacturer != null && !e.manufacturer.isEmpty()) set.add(e.manufacturer);
        List<String> out = new ArrayList<>(set);
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public List<Entry> models(String kind, String manufacturer) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : index()) if (kind.equals(e.kind) && manufacturer.equals(e.manufacturer)) out.add(e);
        return out;
    }

    public AcSpec loadAc(Entry e) {
        try {
            JSONObject o = new JSONObject(readAsset("smartir/climate/" + e.file));
            AcSpec s = new AcSpec();
            s.entry = e;
            s.commands = o.optJSONObject("commands");
            s.minT = o.optInt("minTemperature", e.minT != null ? e.minT : 16);
            s.maxT = o.optInt("maxTemperature", e.maxT != null ? e.maxT : 30);
            s.encoding = o.optString("commandsEncoding", "Base64");
            s.modes = strList(o.optJSONArray("operationModes"));
            s.fans = strList(o.optJSONArray("fanModes"));
            return s;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Resolve the AC code string for a given state, tolerating the two common layouts. */
    public Code acCode(AcSpec s, String mode, String fan, int temp) {
        if (s == null || s.commands == null) return null;
        try {
            if ("off".equalsIgnoreCase(mode) && s.commands.has("off")) {
                return decode(s.encoding, s.commands.getString("off"));
            }
            Object modeObj = s.commands.opt(mode);
            if (!(modeObj instanceof JSONObject)) return null;
            JSONObject mo = (JSONObject) modeObj;
            // layout A: mode -> fan -> temp
            if (fan != null && mo.opt(fan) instanceof JSONObject) {
                JSONObject fo = mo.getJSONObject(fan);
                String code = fo.optString(String.valueOf(temp), null);
                if (code != null) return decode(s.encoding, code);
            }
            // layout B: mode -> temp  (no fan dimension)
            String code = mo.optString(String.valueOf(temp), null);
            if (code != null) return decode(s.encoding, code);
        } catch (Exception ignore) {}
        return null;
    }

    public Code acOff(AcSpec s) {
        if (s == null || s.commands == null) return null;
        try {
            if (s.commands.has("off")) return decode(s.encoding, s.commands.getString("off"));
        } catch (Exception ignore) {}
        return null;
    }

    /** TV / media device: flat map of command name -> code. */
    public List<com.offlineremote.app.model.IrKey> tvKeys(Entry e) {
        List<com.offlineremote.app.model.IrKey> keys = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(readAsset("smartir/media/" + e.file));
            String enc = o.optString("commandsEncoding", "Base64");
            JSONObject cmds = o.optJSONObject("commands");
            if (cmds != null) {
                java.util.Iterator<String> it = cmds.keys();
                while (it.hasNext()) {
                    String name = it.next();
                    String code = cmds.optString(name, null);
                    if (code == null) continue;
                    Code c = decode(enc, code);
                    if (c != null) keys.add(new com.offlineremote.app.model.IrKey(name, 0, c));
                }
            }
        } catch (Exception ignore) {}
        return keys;
    }

    private Code decode(String encoding, String code) {
        IrSignal sig;
        if (encoding != null && encoding.toLowerCase().contains("pronto")) {
            sig = ProntoCodec.parse(code);
        } else if (encoding != null && encoding.equalsIgnoreCase("Hex")) {
            sig = BroadlinkCodec.decodeHex(code);
        } else {
            sig = BroadlinkCodec.decodeBase64(code);
        }
        if (sig == null || !sig.isValid()) return null;
        return Code.signal(sig.frequencyHz, sig.micros);
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

    private static List<String> strList(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }
}
