package com.offlineremote.app.ui;

import com.offlineremote.app.R;

/** Category icons and Hebrew names, matching the mockup's line-icon set. */
public final class Icons {
    private Icons() {}

    public static int forCategory(String cat) {
        if (cat == null) return R.drawable.ic_remote;
        switch (cat) {
            case "ac": return R.drawable.ic_snow;
            case "tv": return R.drawable.ic_tv;
            case "led": return R.drawable.ic_bulb;
            case "speaker": return R.drawable.ic_spk;
            case "soundbar": return R.drawable.ic_soundbar;
            case "receiver": return R.drawable.ic_recv;
            case "projector": return R.drawable.ic_proj;
            case "fan": return R.drawable.ic_propeller;
            case "cable": return R.drawable.ic_box;
            case "streaming": return R.drawable.ic_play;
            case "bluray": case "dvd": case "cd": return R.drawable.ic_disc;
            case "heater": case "fireplace": return R.drawable.ic_fire;
            case "airpurifier": return R.drawable.ic_air;
            case "humidifier": return R.drawable.ic_drop;
            case "vacuum": return R.drawable.ic_robot;
            case "camera": return R.drawable.ic_camera;
            case "car": return R.drawable.ic_car;
            case "vcr": return R.drawable.ic_tape;
            case "monitor": return R.drawable.ic_monitor;
            case "console": return R.drawable.ic_gamepad;
            case "converter": return R.drawable.ic_plug;
            case "toy": return R.drawable.ic_star;
            case "misc": return R.drawable.ic_grid;
            default: return R.drawable.ic_remote;
        }
    }

    /** "the X" form used in the banner: "כוון את הטלפון אל המזגן". */
    public static String target(String cat) {
        if (cat == null) return "המכשיר";
        switch (cat) {
            case "ac": return "המזגן";
            case "tv": return "הטלוויזיה";
            case "led": return "המנורה";
            case "speaker": return "הרמקול";
            case "soundbar": return "הסאונדבר";
            case "receiver": return "הרסיבר";
            case "projector": return "המקרן";
            case "fan": return "המאוורר";
            case "cable": return "הממיר";
            case "heater": return "התנור";
            default: return "המכשיר";
        }
    }

    /** Short category noun used as the default remote name ("מזגן", "טלוויזיה"...). */
    public static String noun(String cat, String fallback) {
        if (cat == null) return fallback;
        switch (cat) {
            case "ac": return "מזגן";
            case "tv": return "טלוויזיה";
            case "led": return "מנורה";
            case "speaker": return "רמקול";
            case "soundbar": return "סאונדבר";
            case "receiver": return "רסיבר";
            case "projector": return "מקרן";
            case "fan": return "מאוורר";
            case "cable": return "ממיר";
            case "streaming": return "סטרימר";
            case "heater": return "תנור";
            default: return fallback;
        }
    }
}
