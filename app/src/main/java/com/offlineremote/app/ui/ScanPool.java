package com.offlineremote.app.ui;

import com.offlineremote.app.App;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.PreviewSession;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.sources.FlipperLibrary;
import com.offlineremote.app.sources.SmartIrSource;
import com.offlineremote.app.util.HebrewNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Candidate power codes for the auto-scan. Every distinct code appears once; the remotes
 * that share it are kept so "עבד!" can open them for matching. Most common codes first.
 */
final class ScanPool {

    static final class Cand {
        Code code;
        String slug;
        final List<String> brands = new ArrayList<>();
        final List<PreviewSession.Builder> builders = new ArrayList<>();
    }

    private static List<Cand> acCache, audioCache;

    private ScanPool() {}

    private static String id(Code c) {
        if (c.kind == Code.Kind.NATIVE) return "n" + Arrays.toString(c.nativeBytes);
        return c.freq + ":" + Arrays.toString(c.micros);
    }

    // brands sold in Israel first, then everything else
    private static final List<String> AC_FIRST = Arrays.asList("electra", "tadiran", "tornado", "gree", "midea",
            "elco", "amcor", "family", "haier", "hisense", "samsung", "lg", "toshiba", "fujitsu", "mitsubishi",
            "mitsubishi electric", "mitsubishi heavy", "panasonic", "daikin", "sharp", "tcl", "aux", "carrier");

    static synchronized List<Cand> ac() {
        if (acCache != null) return acCache;
        SmartIrSource s = App.get().smartIr();
        List<String> mans = new ArrayList<>(s.manufacturers("ac"));
        Collections.sort(mans, (a, b) -> {
            int ia = AC_FIRST.indexOf(a.toLowerCase()), ib = AC_FIRST.indexOf(b.toLowerCase());
            if (ia < 0) ia = 1000;
            if (ib < 0) ib = 1000;
            return ia != ib ? Integer.compare(ia, ib) : a.compareToIgnoreCase(b);
        });
        Map<String, Cand> map = new LinkedHashMap<>();
        for (String man : mans) {
            for (SmartIrSource.Entry e : s.models("ac", man)) {
                SmartIrSource.AcSpec spec = s.loadAc(e);
                if (spec == null) continue;
                String mode = spec.modes.contains("cool") ? "cool" : (spec.modes.isEmpty() ? "cool" : spec.modes.get(0));
                String fan = spec.fans.contains("auto") ? "auto" : (spec.fans.isEmpty() ? "auto" : spec.fans.get(0));
                int temp = (24 >= spec.minT && 24 <= spec.maxT) ? 24 : (spec.minT + spec.maxT) / 2;
                Code c = s.acCode(spec, mode, fan, temp);
                if (c == null) continue;
                String key = id(c);
                Cand cand = map.get(key);
                if (cand == null) { cand = new Cand(); cand.code = c; cand.slug = "ac"; map.put(key, cand); }
                final String file = e.file, brandHe = HebrewNames.only(man);
                if (!cand.brands.contains(brandHe)) cand.brands.add(brandHe);
                cand.builders.add(() -> {
                    SavedRemote r = new SavedRemote();
                    r.source = "smartir"; r.category = "ac"; r.name = "מזגן";
                    r.acFile = file; r.brand = brandHe;
                    return r;
                });
            }
        }
        acCache = new ArrayList<>(map.values());   // keeps the Israeli-first brand order
        return acCache;
    }

    static synchronized List<Cand> audio() {
        if (audioCache != null) return audioCache;
        FlipperLibrary lib = App.get().lib();
        Map<String, Cand> map = new LinkedHashMap<>();
        for (String slug : new String[]{"soundbar", "speaker"}) {
            for (FlipperLibrary.Dev d : lib.all(slug)) {
                Code power = null;
                for (IrKey k : lib.keys(d)) {
                    String role = KeyRoles.byName(k.label, false);
                    if (KeyRoles.POWER.equals(role)) { power = k.code; break; }
                    if (KeyRoles.ON.equals(role) && power == null) power = k.code;
                }
                if (power == null) continue;
                String key = id(power);
                Cand cand = map.get(key);
                if (cand == null) { cand = new Cand(); cand.code = power; cand.slug = slug; map.put(key, cand); }
                final FlipperLibrary.Dev dev = d;
                final String brandHe = HebrewNames.only(d.brand);
                if (!cand.brands.contains(brandHe)) cand.brands.add(brandHe);
                cand.builders.add(() -> {
                    SavedRemote r = new SavedRemote();
                    r.source = "lib"; r.category = dev.slug; r.name = Icons.noun(dev.slug, "רמקול");
                    r.brand = brandHe;
                    r.keys = App.get().lib().keys(dev);
                    return r;
                });
            }
        }
        List<Cand> out = new ArrayList<>(map.values());
        // codes shared by many remotes (typical factory codes) are tried first
        Collections.sort(out, (a, b) -> Integer.compare(b.builders.size(), a.builders.size()));
        audioCache = out;
        return out;
    }
}
