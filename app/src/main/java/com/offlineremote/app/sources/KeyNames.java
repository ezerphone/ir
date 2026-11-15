package com.offlineremote.app.sources;

/**
 * Human labels for Tiqiaa key-type ids. The exact id->name map lives in ZaZa's localized
 * resources (not reproduced here), so unknown ids fall back to a generic label. Users can
 * rename keys after import. Extend this map once the id scheme is confirmed on-device.
 */
public final class KeyNames {
    private KeyNames() {}

    public static String of(int type) {
        switch (type) {
            case 0:  return "Power";
            default: return "Key " + type;
        }
    }
}
