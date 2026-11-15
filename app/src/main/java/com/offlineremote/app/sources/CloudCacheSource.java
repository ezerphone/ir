package com.offlineremote.app.sources;

import android.content.Context;

import com.offlineremote.app.model.SavedRemote;

/**
 * SCAFFOLD — cache-on-use from ZaZa's cloud.
 *
 * Design: when a device is not found in the embedded DB or SmartIR, fetch it ONCE from
 * ZaZa's online API, convert the returned remote to a {@link SavedRemote} with native-format
 * codes, and persist it via RemoteStore so it is available offline forever after (and shows
 * up in export backups). This downloads only what the user actually uses — not the whole
 * proprietary database.
 *
 * NOT YET IMPLEMENTED: ZaZa's cloud endpoints and request signing need to be extracted from
 * the decompiled network layer (com.tiqiaa.network.*). The embedded DB + SmartIR + learn
 * cover the offline goal without this; wire it in when online top-up is desired.
 */
public final class CloudCacheSource {

    public interface Callback {
        void onResult(SavedRemote remote);   // remote == null => not found / offline
        void onError(String message);
    }

    private final Context ctx;

    public CloudCacheSource(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean isAvailable() {
        return false; // enable once the ZaZa cloud API is wired up
    }

    /** Asynchronously fetch a remote online. Currently reports "unavailable". */
    public void fetch(int machineType, String brand, String model, Callback cb) {
        if (cb != null) cb.onError("Online top-up not configured yet");
    }
}
