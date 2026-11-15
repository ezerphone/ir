package com.offlineremote.app.util;

import java.util.HashMap;
import java.util.Map;

/** Hebrew display names for brands commonly sold in Israel. Others fall back to the original. */
public final class HebrewNames {

    private static final Map<String, String> HE = new HashMap<>();
    static {
        put("Electra", "אלקטרה");
        put("Amcor", "אמקור");
        put("Tadiran", "תדיראן");
        put("Tornado", "טורנדו");
        put("Family", "פמילי");
        put("Elco", "אלקו");
        put("Gree", "גרי");
        put("Hisense", "היסנס");
        put("Samsung", "סמסונג");
        put("LG", "אל ג'י");
        put("Sharp", "שארפ");
        put("SHARP", "שארפ");
        put("Toshiba", "טושיבה");
        put("Midea", "מידאה");
        put("TCL", "טי.סי.אל");
        put("Haier", "האייר");
        put("Fujitsu", "פוג'יטסו");
        put("Fuji", "פוג'י");
        put("Panasonic", "פנסוניק");
        put("Daikin", "דايקין");
        put("Mitsubishi", "מיצובישי");
        put("Mitsubishi Electric", "מיצובישי אלקטריק");
        put("Mitsubishi Heavy", "מיצובישי הבי");
        put("Mitsubishi Heavy Industries", "מיצובישי הבי");
        put("Carrier", "קרייר");
        put("Bosch", "בוש");
        put("Whirlpool", "וירלפול");
        put("Electrolux", "אלקטרולוקס");
        put("Beko", "בקו");
        put("Hitachi", "היטאצ'י");
        put("Sony", "סוני");
        put("Philips", "פיליפס");
        put("Xiaomi", "שיאומי");
        put("Sanyo", "סניו");
        put("AUX", "אוקס");
        put("Unionaire", "יוניאייר");
        put("Hyundai", "יונדאי");
        put("Akai", "אקאי");
        put("JVC", "ג'יי.וי.סי");
        put("Pioneer", "פיוניר");
        put("Yamaha", "ימהה");
        put("JBL", "ג'יי.בי.אל");
        put("Blueridge", "בלורידג'");
        put("Zanussi", "זנוסי");
        put("Voltas", "וולטאס");
    }

    private HebrewNames() {}

    private static void put(String en, String he) { HE.put(en.trim().toLowerCase(), he); }

    /** Hebrew name if known (with the original in brackets), else the original. */
    public static String display(String brand) {
        if (brand == null) return "";
        String he = HE.get(brand.trim().toLowerCase());
        return he == null ? brand : he + " (" + brand.trim() + ")";
    }

    /** Hebrew name if known, else the original — no brackets (for titles/saved names). */
    public static String only(String brand) {
        if (brand == null) return "";
        String he = HE.get(brand.trim().toLowerCase());
        return he == null ? brand.trim() : he;
    }
}
