package com.offlineremote.app.ui;

import com.offlineremote.app.R;
import com.offlineremote.app.model.IrKey;

import java.util.Locale;

/**
 * What a button is (power, volume up, a colour…), worked out from the type chosen when
 * learning, or from the library button name. Drives where a key sits in the remote
 * layout, its icon, its Hebrew label and its colour.
 */
public final class KeyRoles {

    public static final String POWER = "POWER", ON = "ON", OFF = "OFF", HOME = "HOME", MUTE = "MUTE",
            UP = "UP", DOWN = "DOWN", LEFT = "LEFT", RIGHT = "RIGHT", OK = "OK",
            VOL_UP = "VOL_UP", VOL_DN = "VOL_DN", CH_UP = "CH_UP", CH_DN = "CH_DN",
            MENU = "MENU", SOURCE = "SOURCE", BACK = "BACK", NUM = "NUM", COLOR = "COLOR",
            BRIGHT_UP = "BRIGHT_UP", BRIGHT_DN = "BRIGHT_DN", TEMP_UP = "TEMP_UP", TEMP_DN = "TEMP_DN",
            MODE = "MODE", FAN = "FAN", GENERIC = "GENERIC";

    private KeyRoles() {}

    /** Role of a key: the learned type if set, else inferred from its label. */
    public static String of(IrKey k, boolean ledContext) {
        if (k.role != null && !k.role.isEmpty()) return k.role;
        return byName(k.label, ledContext);
    }

    static String norm(String label) {
        if (label == null) return "";
        String n = label.toLowerCase(Locale.ROOT).trim();
        n = n.replaceAll("\\s\\d+$", "");          // "red 2" (dedup suffix) -> "red"
        return n.replaceAll("[_\\-\\s./]+", "").replace("−", "");
    }

    private static boolean has(String n, String... parts) {
        for (String p : parts) if (n.contains(p)) return true;
        return false;
    }

    private static boolean isUp(String n) { return has(n, "up", "+", "plus", "inc", "next", "higher", "faster"); }
    private static boolean isDown(String n) { return has(n, "dn", "down", "minus", "dec", "prev", "lower", "slower"); }

    public static String byName(String label, boolean led) {
        String raw = label == null ? "" : label.toLowerCase(Locale.ROOT).trim();
        String n = norm(label);
        if (n.isEmpty()) return GENERIC;

        if (n.equals("on") || n.equals("poweron") || n.equals("turnon") || n.equals("lighton") || n.equals("ledon")) return ON;
        if (n.equals("off") || n.equals("poweroff") || n.equals("turnoff") || n.equals("lightoff") || n.equals("ledoff")) return OFF;
        if (has(n, "power", "pwr", "standby") || n.equals("onoff")) return POWER;

        if (n.startsWith("vol") || has(n, "volume")) {
            if (has(n, "mute")) return MUTE;
            if (isUp(n) || raw.endsWith("+")) return VOL_UP;
            if (isDown(n) || raw.endsWith("-")) return VOL_DN;
        }
        if (n.startsWith("ch") || n.startsWith("channel") || n.startsWith("prog") || n.equals("p+") || n.equals("p-")) {
            if (!n.startsWith("chlist") && !n.startsWith("chan list")) {
                if (isUp(n) || raw.endsWith("+")) return CH_UP;
                if (isDown(n) || raw.endsWith("-")) return CH_DN;
            }
        }
        if (has(n, "mute")) return MUTE;

        if (has(n, "bright") || has(n, "dim") || n.equals("darker") || n.equals("lighter")) {
            if (has(n, "dim", "darker") || isDown(n) || raw.endsWith("-")) return BRIGHT_DN;
            return BRIGHT_UP;
        }
        if (has(n, "temp")) {
            if (isUp(n) || raw.endsWith("+")) return TEMP_UP;
            if (isDown(n) || raw.endsWith("-")) return TEMP_DN;
        }

        if (has(n, "light") && !has(n, "lightblue", "lightgreen", "lighton", "lightoff")) {
            if (has(n, "more") || isUp(n) || raw.endsWith("+")) return BRIGHT_UP;
            if (has(n, "less") || isDown(n) || raw.endsWith("-")) return BRIGHT_DN;
        }
        if (led) {
            if (n.equals("up") || n.equals("plus") || n.equals("+")) return BRIGHT_UP;
            if (n.equals("down") || n.equals("minus") || n.equals("-")) return BRIGHT_DN;
        } else {
            if (n.equals("up") || n.equals("arrowup") || n.equals("cursorup") || n.equals("navup") || n.equals("dirup")) return UP;
            if (n.equals("down") || n.equals("arrowdown") || n.equals("cursordown") || n.equals("navdown") || n.equals("dirdown")) return DOWN;
            if (n.equals("left") || n.equals("arrowleft") || n.equals("cursorleft") || n.equals("navleft")) return LEFT;
            if (n.equals("right") || n.equals("arrowright") || n.equals("cursorright") || n.equals("navright")) return RIGHT;
            if (n.equals("ok") || n.equals("enter") || n.equals("select") || n.equals("confirm")) return OK;
        }

        if (n.equals("home") || n.equals("smarthub")) return HOME;
        if (n.equals("menu") || n.equals("setup") || n.equals("settings") || n.equals("tools") || n.equals("options")) return MENU;
        if (has(n, "source", "input") || n.equals("av") || n.equals("tvav")) return SOURCE;
        if (n.equals("back") || n.equals("return") || n.equals("exit")) return BACK;

        if (n.matches("^(num|digit|key|number|btn)?[0-9]$")) return NUM;
        if (n.equals("mode") || n.equals("fanmode")) return MODE;
        if (n.equals("fan") || n.equals("speed") || n.equals("fanspeed")) return FAN;

        if (colorFor(label) != 0) return COLOR;
        return GENERIC;
    }

    /** Digit for NUM keys, -1 if none. */
    public static int digit(IrKey k) {
        String s = k.label == null ? "" : k.label;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') return c - '0';
        }
        return -1;
    }

    /** Swatch colour for a colour-named button, else 0. */
    public static int colorFor(String label) {
        String n = norm(label);
        switch (n) {
            case "r": case "red": case "r1": case "אדום": return 0xFFEF4444;
            case "r2": return 0xFFFF5A1F;
            case "r3": return 0xFFFF8A00;
            case "r4": return 0xFFFFC21A;
            case "g": case "green": case "g1": case "ירוק": return 0xFF22C55E;
            case "g2": return 0xFF34D399;
            case "g3": return 0xFF14B8A6;
            case "g4": return 0xFF0EA5A0;
            case "b": case "blue": case "b1": case "כחול": return 0xFF3B82F6;
            case "b2": return 0xFF6366F1;
            case "b3": return 0xFF8B5CF6;
            case "b4": return 0xFFEC4899;
            case "w": case "white": case "לבן": return 0xFFFFFFFF;
            case "warm": case "warmwhite": return 0xFFFDE2B4;
            case "coolwhite": case "cool": return 0xFFE0F2FE;
        }
        if (n.isEmpty()) return 0;
        if (has(n, "lightblue", "skyblue", "azure", "תכלת")) return 0xFF7DD3FC;
        if (has(n, "lightgreen", "lime")) return 0xFFA3E635;
        if (has(n, "darkblue", "navy")) return 0xFF1E40AF;
        if (has(n, "turquoise", "teal", "cyan", "aqua")) return 0xFF14B8A6;
        if (has(n, "ocean")) return 0xFF0369A1;
        if (has(n, "orange", "כתום")) return 0xFFF97316;
        if (has(n, "yellow", "צהוב", "gold")) return 0xFFEAB308;
        if (has(n, "purple", "violet", "סגול", "magenta")) return 0xFF8B5CF6;
        if (has(n, "pink", "ורוד", "rose")) return 0xFFEC4899;
        if (n.equals("redkey") || n.startsWith("red")) return 0xFFEF4444;
        if (n.startsWith("green")) return 0xFF22C55E;
        if (n.startsWith("blue")) return 0xFF3B82F6;
        if (n.startsWith("white")) return 0xFFFFFFFF;
        return 0;
    }

    /** Icon for a role, 0 if the key should show its text label. */
    public static int iconFor(String role) {
        switch (role) {
            case POWER: case ON: case OFF: return R.drawable.ic_power;
            case HOME: return R.drawable.ic_home;
            case MUTE: return R.drawable.ic_mute;
            case UP: return R.drawable.ic_up;
            case DOWN: return R.drawable.ic_dn;
            case LEFT: return R.drawable.ic_lf;
            case RIGHT: return R.drawable.ic_rt;
            case VOL_UP: case VOL_DN: return R.drawable.ic_vol;
            case CH_UP: return R.drawable.ic_up;
            case CH_DN: return R.drawable.ic_dn;
            case MENU: return R.drawable.ic_menu;
            case SOURCE: return R.drawable.ic_input;
            case BACK: return R.drawable.ic_back;
            case NUM: return R.drawable.ic_num;
            case BRIGHT_UP: return R.drawable.ic_sun;
            case BRIGHT_DN: return R.drawable.ic_moon;
            case TEMP_UP: return R.drawable.ic_plus;
            case TEMP_DN: return R.drawable.ic_minus;
            case MODE: return R.drawable.ic_snow;
            case FAN: return R.drawable.ic_fanbars;
            default: return 0;
        }
    }

    /** Hebrew text for a key label (common remote words), else the label cleaned up. */
    public static String hebrew(String label) {
        if (label == null) return "";
        String n = norm(label);
        switch (n) {
            case "info": case "display": return "מידע";
            case "guide": case "epg": return "מדריך";
            case "exit": return "יציאה";
            case "back": case "return": return "חזור";
            case "play": return "נגן";
            case "pause": return "השהה";
            case "playpause": return "נגן/השהה";
            case "stop": return "עצור";
            case "rec": case "record": return "הקלטה";
            case "rewind": case "rew": return "אחורה";
            case "fastforward": case "fastfwd": case "forward": case "ff": case "fwd": return "קדימה";
            case "prech": case "lastch": case "chprev": case "recall": return "ערוץ קודם";
            case "minus": case "dash": return "—";
            case "morelight": case "lightup": return "בהירות +";
            case "lesslight": case "lightdn": case "lightdown": return "בהירות −";
            case "emanual": return "מדריך";
            case "psize": return "גודל תמונה";
            case "next": return "הבא";
            case "prev": case "previous": return "הקודם";
            case "sleep": return "שינה";
            case "timer": return "טיימר";
            case "swing": case "osc": case "oscillate": case "oscillation": case "rotate": case "rotation": return "נדנוד";
            case "turbo": return "טורבו";
            case "auto": return "אוטו'";
            case "flash": return "הבהוב";
            case "fade": return "דעיכה";
            case "strobe": return "סטרובו";
            case "smooth": return "רך";
            case "jump": return "קפיצה";
            case "music": return "מוזיקה";
            case "speed": return "מהירות";
            case "quick": case "faster": return "מהיר";
            case "slow": case "slower": return "איטי";
            case "subtitle": case "subtitles": case "cc": return "כתוביות";
            case "audio": case "sound": return "שמע";
            case "zoom": return "זום";
            case "aspect": return "יחס תצוגה";
            case "menu": return "תפריט";
            case "source": case "input": return "מקור";
            case "home": return "בית";
            case "mute": return "השתקה";
            case "power": return "הפעלה";
            case "on": case "poweron": return "הדלק";
            case "off": case "poweroff": return "כבה";
            case "mode": return "מצב";
            case "fan": case "fanspeed": return "מאוורר";
            case "light": case "lamp": return "תאורה";
            case "night": return "לילה";
            case "eject": return "הוצא";
            case "search": return "חיפוש";
            case "list": case "chlist": return "רשימה";
            case "fav": case "favorite": return "מועדפים";
            case "text": case "teletext": return "טלטקסט";
            case "picture": return "תמונה";
            case "settings": case "setup": return "הגדרות";
            case "tools": case "options": return "אפשרויות";
            case "bluetooth": case "bt": return "בלוטות'";
            case "bassup": return "באס +";
            case "bassdn": case "bassdown": return "באס −";
            case "red": return "אדום";
            case "green": return "ירוק";
            case "blue": return "כחול";
            case "yellow": return "צהוב";
            case "white": return "לבן";
            case "orange": return "כתום";
            case "purple": case "violet": return "סגול";
            case "pink": return "ורוד";
        }
        return label.replace('_', ' ').trim();
    }

    /** Short Hebrew name for a role (used for learned keys / rockers). */
    public static String roleName(String role) {
        switch (role) {
            case POWER: return "הפעלה";
            case ON: return "הדלק";
            case OFF: return "כבה";
            case HOME: return "בית";
            case MUTE: return "השתקה";
            case UP: return "למעלה";
            case DOWN: return "למטה";
            case LEFT: return "שמאלה";
            case RIGHT: return "ימינה";
            case OK: return "OK";
            case VOL_UP: return "עוצמה +";
            case VOL_DN: return "עוצמה −";
            case CH_UP: return "ערוץ +";
            case CH_DN: return "ערוץ −";
            case MENU: return "תפריט";
            case SOURCE: return "מקור";
            case BACK: return "חזור";
            case NUM: return "מספר";
            case COLOR: return "צבע";
            case BRIGHT_UP: return "בהירות +";
            case BRIGHT_DN: return "בהירות −";
            case TEMP_UP: return "טמפ' +";
            case TEMP_DN: return "טמפ' −";
            case MODE: return "מצב";
            case FAN: return "מאוורר";
            default: return "כללי";
        }
    }
}
