package com.offlineremote.app.model;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A single sendable IR code. Either:
 *   NATIVE  - bytes already in Tiqiaa's internal format (local DB / learned), sent verbatim.
 *   SIGNAL  - raw microsecond on/off timings (foreign DBs), re-encoded before sending.
 */
public final class Code {
    public enum Kind { NATIVE, SIGNAL }

    public final Kind kind;
    public final int freq;
    public final byte[] nativeBytes;  // NATIVE only
    public final int[] micros;        // SIGNAL only

    private Code(Kind kind, int freq, byte[] nativeBytes, int[] micros) {
        this.kind = kind;
        this.freq = freq <= 0 ? 38000 : freq;
        this.nativeBytes = nativeBytes;
        this.micros = micros;
    }

    public static Code nativeCode(int freq, byte[] bytes) {
        return new Code(Kind.NATIVE, freq, bytes, null);
    }

    public static Code signal(int freq, int[] micros) {
        return new Code(Kind.SIGNAL, freq, null, micros);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("kind", kind.name());
        o.put("freq", freq);
        if (kind == Kind.NATIVE && nativeBytes != null) {
            o.put("bytes", Base64.encodeToString(nativeBytes, Base64.NO_WRAP));
        } else if (kind == Kind.SIGNAL && micros != null) {
            JSONArray a = new JSONArray();
            for (int v : micros) a.put(v);
            o.put("micros", a);
        }
        return o;
    }

    public static Code fromJson(JSONObject o) throws JSONException {
        Kind k = Kind.valueOf(o.optString("kind", "NATIVE"));
        int freq = o.optInt("freq", 38000);
        if (k == Kind.NATIVE) {
            byte[] b = Base64.decode(o.optString("bytes", ""), Base64.NO_WRAP);
            return nativeCode(freq, b);
        } else {
            JSONArray a = o.optJSONArray("micros");
            int[] m = new int[a == null ? 0 : a.length()];
            for (int i = 0; i < m.length; i++) m[i] = a.getInt(i);
            return signal(freq, m);
        }
    }
}
