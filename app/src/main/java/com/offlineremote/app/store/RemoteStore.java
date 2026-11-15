package com.offlineremote.app.store;

import android.content.Context;

import com.offlineremote.app.model.SavedRemote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists saved remotes as JSON in the app's private storage, and implements the
 * export / import ("backup all my codes") feature over arbitrary streams (SAF Uris).
 *
 * This is also where cache-on-use lands: whenever a code is fetched online or learned,
 * the resulting SavedRemote is added here, so the offline library grows with real use.
 */
public final class RemoteStore {

    private static final String FILE = "remotes.json";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Context ctx;
    private final List<SavedRemote> remotes = new ArrayList<>();
    private boolean loaded = false;

    public RemoteStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public synchronized List<SavedRemote> all() {
        ensureLoaded();
        return new ArrayList<>(remotes);
    }

    public synchronized SavedRemote get(String id) {
        ensureLoaded();
        for (SavedRemote r : remotes) if (r.id.equals(id)) return r;
        return null;
    }

    public synchronized void add(SavedRemote r) {
        ensureLoaded();
        remotes.add(r);
        save();
    }

    public synchronized void remove(String id) {
        ensureLoaded();
        for (int i = 0; i < remotes.size(); i++) {
            if (remotes.get(i).id.equals(id)) { remotes.remove(i); break; }
        }
        save();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return;
        try {
            byte[] data = readAll(new java.io.FileInputStream(f));
            parseInto(new String(data, UTF8), remotes);
        } catch (Exception ignore) {}
    }

    private void save() {
        try {
            byte[] bytes = serialize(remotes).getBytes(UTF8);
            File f = new File(ctx.getFilesDir(), FILE);
            try (FileOutputStream os = new FileOutputStream(f)) {
                os.write(bytes);
            }
        } catch (Exception ignore) {}
    }

    // ---------- export / import ----------

    public synchronized void exportTo(OutputStream os) throws Exception {
        ensureLoaded();
        os.write(serialize(remotes).getBytes(UTF8));
        os.flush();
    }

    /** Import remotes from a backup stream, skipping ids that already exist. Returns count added. */
    public synchronized int importFrom(InputStream is) throws Exception {
        ensureLoaded();
        List<SavedRemote> incoming = new ArrayList<>();
        parseInto(new String(readAll(is), UTF8), incoming);
        int added = 0;
        for (SavedRemote r : incoming) {
            if (get(r.id) == null) { remotes.add(r); added++; }
        }
        if (added > 0) save();
        return added;
    }

    private static String serialize(List<SavedRemote> list) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "offline-ir-remote/1");
        JSONArray a = new JSONArray();
        for (SavedRemote r : list) a.put(r.toJson());
        root.put("remotes", a);
        return root.toString(2);
    }

    private static void parseInto(String json, List<SavedRemote> out) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray a = root.optJSONArray("remotes");
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            out.add(SavedRemote.fromJson(a.getJSONObject(i)));
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }
}
