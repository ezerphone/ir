package com.offlineremote.app.ui;

import android.app.Activity;
import android.content.Intent;

import com.offlineremote.app.App;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.sources.FlipperLibrary;
import com.offlineremote.app.sources.SmartIrSource;

import java.util.List;

/**
 * Sample data + screen launcher used only when the app is started with an explicit
 * "demo" extra (from adb on the CI emulator) to screenshot each screen for review.
 * Normal use never passes this extra.
 */
final class DemoMode {
    static final String EXTRA = "demo";

    private DemoMode() {}

    static boolean handle(Activity a, Intent intent) {
        String demo = intent == null ? null : intent.getStringExtra(EXTRA);
        if (demo == null) return false;
        intent.removeExtra(EXTRA);
        switch (demo) {
            case "rot": a.getSharedPreferences("ui", Activity.MODE_PRIVATE).edit().putBoolean("rot180", true).commit(); return false;
            case "unrot": a.getSharedPreferences("ui", Activity.MODE_PRIVATE).edit().putBoolean("rot180", false).commit(); return false;
            case "seed": seed(); return false;
            case "learn": a.startActivity(new Intent(a, LearnActivity.class)); return true;
            case "add": a.startActivity(new Intent(a, AddDeviceActivity.class)); return true;
            case "more": case "brands": case "match_ac": case "match_led": case "match_tv":
                a.startActivity(new Intent(a, AddDeviceActivity.class).putExtra(EXTRA, demo)); return true;
            case "tv": case "led": case "ac": case "learned": {
                seed();
                for (SavedRemote r : App.get().store().all()) {
                    if (demo.equals(r.category) || ("learned".equals(demo) && "learned".equals(r.source))) {
                        a.startActivity(new Intent(a, RemoteActivity.class).putExtra(RemoteActivity.EXTRA_ID, r.id));
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    /** Fill the home screen like the mockup (מזגן סלון / טלוויזיה / מנורת לד / רמקול). */
    static synchronized void seed() {
        if (!App.get().store().all().isEmpty()) return;
        SmartIrSource s = App.get().smartIr();
        List<SmartIrSource.Entry> el = s.models("ac", "Electra");
        if (!el.isEmpty()) {
            SavedRemote r = new SavedRemote();
            r.source = "smartir"; r.category = "ac"; r.name = "מזגן סלון"; r.brand = "אלקטרה"; r.acFile = el.get(0).file;
            App.get().store().add(r);
        }
        addLib("tv", "Samsung", "Samsung AA59-00443A", "טלוויזיה", "סמסונג");
        addLib("led", "Gako", "RGB CONTROLLER 5050SMD", "מנורת לד", "RGB");
        addLib("speaker", "JBL", null, "רמקול", "JBL");
        SavedRemote l = new SavedRemote();
        l.source = "learned"; l.category = "learned"; l.name = "מזגן חדר שינה";
        String[][] roles = {{KeyRoles.POWER, "הפעלה"}, {KeyRoles.MODE, "מצב"}, {KeyRoles.TEMP_UP, "טמפ' +"},
                {KeyRoles.TEMP_DN, "טמפ' −"}, {KeyRoles.FAN, "מאוורר"}, {KeyRoles.GENERIC, "טורבו"}};
        com.offlineremote.app.model.Code dummy = com.offlineremote.app.ir.IrGen.toCode("NEC", "0000", "0000");
        for (String[] rr : roles) { IrKey k = new IrKey(rr[1], 0, dummy); k.role = rr[0]; l.keys.add(k); }
        App.get().store().add(l);
    }

    private static void addLib(String slug, String brand, String model, String name, String brandHe) {
        FlipperLibrary lib = App.get().lib();
        List<FlipperLibrary.Dev> devs = lib.models(slug, brand);
        if (devs.isEmpty()) return;
        FlipperLibrary.Dev pick = devs.get(0);
        if (model != null) for (FlipperLibrary.Dev d : devs) if (d.model.equals(model)) pick = d;
        SavedRemote r = new SavedRemote();
        r.source = "lib"; r.category = slug; r.name = name; r.brand = brandHe;
        r.keys = lib.keys(pick);
        App.get().store().add(r);
    }
}
