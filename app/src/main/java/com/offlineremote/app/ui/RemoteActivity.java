package com.offlineremote.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.PreviewSession;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.sources.SmartIrSource;
import com.offlineremote.app.usb.RawDongle;
import com.offlineremote.app.util.ErrorLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A remote, ZaZa style. In matching mode (adding from the library) it shows the blue
 * banner, the "(i/n)" counter and the הקודם / עבד! / הבא bar; otherwise it is a saved remote.
 * Layouts: AC dial (SmartIR), LED colour panel, and a TV-style layout for everything else.
 */
public class RemoteActivity extends BaseActivity {

    public static final String EXTRA_ID = "remote_id";
    public static final String EXTRA_PREVIEW = "preview";

    private SavedRemote remote;
    private FrameLayout content;
    private TopBar bar;
    private boolean preview;
    private PreviewSession session;
    private android.view.GestureDetector gesture;

    // AC state
    private SmartIrSource.AcSpec acSpec;
    private String acMode, acFan;
    private int acTemp;
    private boolean acOn;
    private DialView dial;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_remote);
        content = findViewById(R.id.content);
        bar = topBar();
        preview = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);

        if (preview) {
            session = App.get().preview;
            if (session == null) { finish(); return; }
            if ("ac".equals(session.category)) { bar.rotate(true); bar.back(false); }
            else { bar.back(true); bar.rotate(false); }

            View banner = findViewById(R.id.banner);
            banner.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.bannerText)).setText("כוון את הטלפון אל " + session.target);
            findViewById(R.id.bannerClose).setOnClickListener(v -> banner.setVisibility(View.GONE));

            findViewById(R.id.matchbar).setVisibility(View.VISIBLE);
            findViewById(R.id.btnPrev).setOnClickListener(v -> goTo(session.index - 1));
            findViewById(R.id.btnNext).setOnClickListener(v -> goTo(session.index + 1));
            findViewById(R.id.btnWorked).setOnClickListener(v -> saveWorked());

            gesture = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                    if (Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > 140 && Math.abs(vx) > 250) {
                        goTo(session.index + (dx < 0 ? 1 : -1));
                        return true;
                    }
                    return false;
                }
            });
            loadCurrent();
        } else {
            bar.back(true);
            bar.rotate(false);
            remote = App.get().store().get(getIntent().getStringExtra(EXTRA_ID));
            if (remote == null) { finish(); return; }
            showRemote(remote);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gesture != null) gesture.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // ---------------- matching ----------------

    private void goTo(int idx) {
        if (session == null || idx < 0 || idx >= session.size() || idx == session.index) return;
        session.index = idx;
        loadCurrent();
    }

    private void loadCurrent() {
        bar.title("טוען...");
        App.get().io().execute(() -> {
            final SavedRemote r = session.current();
            runOnUiThread(() -> showRemote(r));
        });
    }

    private void updateMatchBar() {
        findViewById(R.id.btnPrev).setAlpha(session.hasPrev() ? 1f : 0.45f);
        findViewById(R.id.btnNext).setAlpha(session.hasNext() ? 1f : 0.45f);
    }

    private void saveWorked() {
        if (remote == null) return;
        remote.name = Icons.noun(remote.category, session.categoryHe);
        remote.brand = session.brand;
        App.get().store().add(remote);
        App.get().preview = null;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(MainActivity.EXTRA_SAVED, remote.name);
        startActivity(i);
        finish();
    }

    private void showRemote(SavedRemote r) {
        remote = r;
        content.removeAllViews();
        if (preview) {
            bar.title("התאמה · " + session.brand + " " + session.categoryHe
                    + " (" + (session.index + 1) + "/" + session.size() + ")");
            updateMatchBar();
        } else {
            bar.title(r.name + (r.brand != null && !r.brand.isEmpty() ? " · " + r.brand : ""));
        }
        if (r.isAc() && "smartir".equals(r.source)) buildAc();
        else if ("led".equals(r.category)) buildLed(r.keys);
        else buildTv(r.keys);
    }

    // ---------------- sending ----------------

    private void send(View v, Code c) {
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (c == null) return;
        App.get().io().execute(() -> {
            RawDongle raw = App.get().raw();
            if (!raw.isOpen()) raw.open();
            boolean open = raw.isOpen(), ok = false;
            if (open) {
                ok = raw.transmit(c.freq, c.kind == Code.Kind.NATIVE ? c.nativeBytes : null, c.micros);
            }
            if (!open) ErrorLog.add(this, "SEND dongle not open");
            else if (!ok) ErrorLog.add(this, "SEND failed");
            final boolean fOpen = open, fOk = ok;
            runOnUiThread(() -> {
                if (!fOpen) toast(getString(R.string.dongle_missing));
                else if (!fOk) toast(getString(R.string.send_failed));
            });
        });
    }

    private ScrollView area(LinearLayout column, int padTop) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundResource(R.drawable.bg_area);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 16);
        column.setPadding(p, Ui.dp(this, padTop), p, p);
        sv.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return sv;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private LinearLayout.LayoutParams fullWidth(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }

    // ---------------- AC (SmartIR) ----------------

    private static String modeHe(String m) {
        if (m == null) return "";
        switch (m) {
            case "cool": return "קירור";
            case "heat": return "חימום";
            case "dry": return "ייבוש";
            case "fan_only": case "fan": return "אוורור";
            case "auto": case "heat_cool": return "אוטומטי";
            default: return m;
        }
    }

    private static String fanHe(String f) {
        if (f == null) return "";
        switch (f) {
            case "auto": return "מאוורר: אוטו'";
            case "low": case "lowest": return "מאוורר: נמוך";
            case "mid": case "medium": case "middle": return "מאוורר: בינוני";
            case "high": case "highest": return "מאוורר: גבוה";
            case "turbo": case "powerful": return "מאוורר: טורבו";
            case "quiet": case "silent": return "מאוורר: שקט";
            default: return "מאוורר: " + f;
        }
    }

    private void buildAc() {
        LinearLayout col = new LinearLayout(this);
        area(col, 6);
        int side = Ui.dp(this, 2);
        col.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 14));

        // MODE (right) ... power (left)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout modeKey = Ui.key(this, 56, 0, "MODE", 0);
        ((TextView) modeKey.getChildAt(0)).setTextSize(10);
        ((TextView) modeKey.getChildAt(0)).setLetterSpacing(0.05f);
        FrameLayout powerKey = Ui.key(this, 56, R.drawable.ic_power, null, Ui.color(this, R.color.power));
        top.addView(modeKey);
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(powerKey);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 4);
        col.addView(top, tlp);

        dial = new DialView(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int ds = Math.min(Ui.dp(this, 360), Math.round(screenW * 0.8f));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ds, ds);
        dlp.topMargin = side;
        col.addView(dial, dlp);

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(keys, fullWidth(8));

        App.get().io().execute(() -> {
            SmartIrSource.Entry e = new SmartIrSource.Entry();
            e.file = remote.acFile;
            final SmartIrSource.AcSpec spec = App.get().smartIr().loadAc(e);
            runOnUiThread(() -> {
                acSpec = spec;
                if (spec == null) { toast("טעינת השלט נכשלה"); return; }
                List<String> modes = spec.modes.isEmpty() ? java.util.Arrays.asList("cool", "heat") : spec.modes;
                List<String> fans = spec.fans.isEmpty() ? java.util.Collections.singletonList("auto") : spec.fans;
                acMode = modes.contains("cool") ? "cool" : modes.get(0);
                acFan = fans.contains("auto") ? "auto" : fans.get(0);
                acTemp = (24 >= spec.minT && 24 <= spec.maxT) ? 24 : (spec.minT + spec.maxT) / 2;
                dial.setRange(spec.minT, spec.maxT);
                dial.setTemp(acTemp);

                modeKey.setOnClickListener(v -> {
                    acMode = modes.get((modes.indexOf(acMode) + 1) % modes.size());
                    toast(modeHe(acMode));
                    sendState(v);
                });
                powerKey.setOnClickListener(v -> {
                    if (acOn) { acOn = false; toast("כיבוי"); send(v, App.get().smartIr().acOff(acSpec)); }
                    else sendState(v);
                });
                dial.setListener(new DialView.Listener() {
                    @Override public void onTemp(int t, boolean commit) { acTemp = t; if (commit) sendState(dial); }
                    @Override public void onKnob() { sendState(dial); }
                });

                // only the keys that have codes: fan speed, cool, heat
                List<View> row = new ArrayList<>();
                if (fans.size() > 1) {
                    FrameLayout k = Ui.key(this, 48, R.drawable.ic_fanbars, null, Ui.color(this, R.color.icon));
                    k.setOnClickListener(v -> {
                        acFan = fans.get((fans.indexOf(acFan) + 1) % fans.size());
                        toast(fanHe(acFan));
                        sendState(v);
                    });
                    row.add(k);
                }
                if (modes.contains("cool")) {
                    FrameLayout k = Ui.key(this, 48, R.drawable.ic_snow, null, Ui.color(this, R.color.icon));
                    k.setOnClickListener(v -> { acMode = "cool"; toast(modeHe(acMode)); sendState(v); });
                    row.add(k);
                }
                if (modes.contains("heat")) {
                    FrameLayout k = Ui.key(this, 48, R.drawable.ic_sun, null, Ui.color(this, R.color.icon));
                    k.setOnClickListener(v -> { acMode = "heat"; toast(modeHe(acMode)); sendState(v); });
                    row.add(k);
                }
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) keys.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                    keys.addView(row.get(i));
                }
                if (row.size() == 1) keys.setGravity(Gravity.CENTER);
            });
        });
    }

    private void sendState(View v) {
        if (acSpec == null) return;
        acOn = true;
        final String mode = acMode, fan = acFan;
        final int temp = acTemp;
        App.get().io().execute(() -> {
            Code c = App.get().smartIr().acCode(acSpec, mode, fan, temp);
            runOnUiThread(() -> {
                if (c == null) toast("אין קוד למצב הזה");
                else send(v, c);
            });
        });
    }

    // ---------------- TV-style layout (TV and every other device) ----------------

    private void buildTv(List<IrKey> keys) {
        LinearLayout col = new LinearLayout(this);
        area(col, 16);
        if (keys == null || keys.isEmpty()) {
            col.addView(Ui.text(this, "אין כפתורים בשלט הזה", 14, Ui.color(this, R.color.sub), false), topMargin(40));
            return;
        }
        Map<String, IrKey> one = new LinkedHashMap<>();
        List<IrKey> nums = new ArrayList<>(), colors = new ArrayList<>(), rest = new ArrayList<>();
        for (IrKey k : keys) {
            String role = KeyRoles.of(k, false);
            if (KeyRoles.NUM.equals(role)) nums.add(k);
            else if (KeyRoles.COLOR.equals(role)) colors.add(k);
            else if (KeyRoles.GENERIC.equals(role)) rest.add(k);
            else if (one.containsKey(role)) {
                // prefer the key literally named for the slot ("Menu" over "Tools")
                if (KeyRoles.isCanonical(role, k.label) && !KeyRoles.isCanonical(role, one.get(role).label)) {
                    rest.add(one.get(role));
                    one.put(role, k);
                } else rest.add(k);
            }
            else one.put(role, k);
        }
        Set<String> used = new HashSet<>();
        int icon = Ui.color(this, R.color.icon);

        // 1. power / home / mute
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER);
        String powerRole = one.containsKey(KeyRoles.POWER) ? KeyRoles.POWER : (one.containsKey(KeyRoles.ON) ? KeyRoles.ON : null);
        for (String role : new String[]{powerRole, KeyRoles.HOME, KeyRoles.MUTE}) {
            if (role == null || !one.containsKey(role)) continue;
            IrKey k = one.get(role);
            boolean pw = KeyRoles.POWER.equals(role) || KeyRoles.ON.equals(role);
            FrameLayout v = Ui.key(this, 56, KeyRoles.iconFor(role), null, pw ? Ui.color(this, R.color.power) : icon);
            v.setOnClickListener(x -> send(x, k.code));
            Ui.addGap(r1, v, 26);
            used.add(role);
        }
        if (r1.getChildCount() > 0) col.addView(r1, fullWidth(0));

        // 2. D-pad
        boolean anyArrow = false;
        for (String r : new String[]{KeyRoles.UP, KeyRoles.DOWN, KeyRoles.LEFT, KeyRoles.RIGHT, KeyRoles.OK})
            if (one.containsKey(r)) anyArrow = true;
        if (anyArrow) {
            FrameLayout pad = new FrameLayout(this);
            pad.setBackgroundResource(R.drawable.bg_dpad);
            int ps = Ui.dp(this, 188);
            col.addView(pad, sized(ps, ps, col.getChildCount() > 0 ? 13 : 0));
            addArrow(pad, one.get(KeyRoles.UP), R.drawable.ic_up, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            addArrow(pad, one.get(KeyRoles.DOWN), R.drawable.ic_dn, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            addArrow(pad, one.get(KeyRoles.LEFT), R.drawable.ic_lf, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addArrow(pad, one.get(KeyRoles.RIGHT), R.drawable.ic_rt, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            IrKey ok = one.get(KeyRoles.OK);
            if (ok != null) {
                TextView o = Ui.text(this, "OK", 15, 0xFFFFFFFF, true);
                o.setGravity(Gravity.CENTER);
                o.setBackgroundResource(R.drawable.bg_ok);
                o.setElevation(Ui.dp(this, 4));
                o.setOnClickListener(x -> send(x, ok.code));
                pad.addView(o, new FrameLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72), Gravity.CENTER));
            }
            used.add(KeyRoles.UP); used.add(KeyRoles.DOWN); used.add(KeyRoles.LEFT); used.add(KeyRoles.RIGHT); used.add(KeyRoles.OK);
        }

        // 3. rockers
        List<View> rockers = new ArrayList<>();
        addRocker(rockers, one, used, KeyRoles.VOL_DN, KeyRoles.VOL_UP, "עוצמה");
        addRocker(rockers, one, used, KeyRoles.CH_DN, KeyRoles.CH_UP, "ערוץ");
        addRocker(rockers, one, used, KeyRoles.TEMP_DN, KeyRoles.TEMP_UP, "טמפ'");
        addRocker(rockers, one, used, KeyRoles.BRIGHT_DN, KeyRoles.BRIGHT_UP, "בהירות");
        for (int i = 0; i < rockers.size(); i += 2) {
            LinearLayout rr = new LinearLayout(this);
            rr.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, rockers.size()); j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (j > i) lp.setMarginStart(Ui.dp(this, 14));
                rr.addView(rockers.get(j), lp);
            }
            if (rockers.size() - i == 1) {
                rr.addView(new View(this), 0, new LinearLayout.LayoutParams(0, 1, 0.5f));
                rr.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 0.5f));
            }
            col.addView(rr, fullWidth(col.getChildCount() > 0 ? 13 : 0));
        }

        // 4. number pad / menu / source / back
        LinearLayout r4 = new LinearLayout(this);
        r4.setOrientation(LinearLayout.HORIZONTAL);
        r4.setGravity(Gravity.CENTER);
        if (!nums.isEmpty()) {
            FrameLayout v = Ui.key(this, 48, R.drawable.ic_num, null, icon);
            v.setOnClickListener(x -> showNumPad(nums));
            Ui.addGap(r4, v, 26);
        }
        for (String role : new String[]{KeyRoles.MENU, KeyRoles.SOURCE, KeyRoles.BACK}) {
            IrKey k = one.get(role);
            if (k == null) continue;
            FrameLayout v = Ui.key(this, 48, 0, KeyRoles.roleName(role), 0);
            v.setOnClickListener(x -> send(x, k.code));
            Ui.addGap(r4, v, 26);
            used.add(role);
        }
        if (r4.getChildCount() > 0) col.addView(r4, fullWidth(col.getChildCount() > 0 ? 13 : 0));

        // 5. coloured keys (e.g. teletext red/green/yellow/blue)
        if (colors.size() == 1) { rest.add(colors.get(0)); colors.clear(); }
        if (!colors.isEmpty()) {
            LinearLayout r5 = new LinearLayout(this);
            r5.setOrientation(LinearLayout.HORIZONTAL);
            r5.setGravity(Gravity.CENTER);
            for (IrKey k : colors) {
                View s = swatch(k, 40, true);
                Ui.addGap(r5, s, 14);
            }
            col.addView(r5, fullWidth(13));
        }

        // 6. everything else as round keys, 4 per row
        for (Map.Entry<String, IrKey> e : one.entrySet()) if (!used.contains(e.getKey())) rest.add(e.getValue());
        addKeyGrid(col, rest, 4, false);
    }

    private LinearLayout.LayoutParams sized(int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }

    private void addArrow(FrameLayout pad, IrKey k, int icon, int gravity) {
        if (k == null) return;
        ImageView a = new ImageView(this);
        a.setImageResource(icon);
        Ui.tint(a, Ui.color(this, R.color.icon));
        int p = Ui.dp(this, 14);
        a.setPadding(p, p, p, p);
        a.setBackgroundResource(R.drawable.bg_icon_btn);
        a.setOnClickListener(x -> send(x, k.code));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), gravity);
        int m = Ui.dp(this, 2);
        lp.setMargins(m, m, m, m);
        pad.addView(a, lp);
    }

    private void addRocker(List<View> out, Map<String, IrKey> one, Set<String> used, String dnRole, String upRole, String label) {
        IrKey dn = one.get(dnRole), up = one.get(upRole);
        if (dn == null && up == null) return;
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.bg_rocker);
        r.setElevation(Ui.dp(this, 2));
        r.addView(rockerHalf("−", dn), new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        TextView mid = Ui.text(this, label, 11, Ui.color(this, R.color.sub), false);
        r.addView(mid);
        r.addView(rockerHalf("+", up), new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        used.add(dnRole);
        used.add(upRole);
        out.add(r);
    }

    private TextView rockerHalf(String sign, IrKey k) {
        TextView t = Ui.text(this, sign, 20, Ui.color(this, R.color.icon), false);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_rocker_half);
        if (k == null) t.setAlpha(0.3f);
        else t.setOnClickListener(x -> send(x, k.code));
        return t;
    }

    private View swatch(IrKey k, int sizeDp, boolean circle) {
        int col = k.color != 0 ? k.color : KeyRoles.colorFor(k.label);
        if (col == 0) col = 0xFFCBD5E1;
        View s = new View(this);
        GradientDrawable g = new GradientDrawable();
        if (circle) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(Ui.dp(this, 9));
        g.setColor(col);
        g.setStroke(Ui.dp(this, 1), 0x0F000000);
        s.setBackground(g);
        s.setElevation(Ui.dp(this, 1));
        s.setOnClickListener(x -> send(x, k.code));
        s.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        return s;
    }

    /** Round keys in rows; icon when the role has one, else the Hebrew label. */
    private void addKeyGrid(LinearLayout col, List<IrKey> keys, int perRow, boolean led) {
        int icon = Ui.color(this, R.color.icon);
        for (int i = 0; i < keys.size(); i += perRow) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < perRow; j++) {
                FrameLayout cell = new FrameLayout(this);
                if (i + j < keys.size()) {
                    IrKey k = keys.get(i + j);
                    String role = KeyRoles.of(k, led);
                    String raw = k.label == null ? "" : k.label.replace('_', ' ').trim();
                    String label = KeyRoles.hebrew(k.label);
                    boolean translated = !label.equalsIgnoreCase(raw);
                    int ic = (!KeyRoles.GENERIC.equals(role) && !translated) ? KeyRoles.iconFor(role) : 0;
                    if (KeyRoles.NUM.equals(role)) { ic = 0; label = String.valueOf(Math.max(0, KeyRoles.digit(k))); }
                    FrameLayout v = Ui.key(this, 56, ic, label,
                            KeyRoles.POWER.equals(role) ? Ui.color(this, R.color.power) : icon);
                    v.setOnClickListener(x -> send(x, k.code));
                    cell.addView(v, new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.CENTER));
                }
                r.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));
            }
            col.addView(r, fullWidth(i == 0 ? 14 : 4));
        }
    }

    private void showNumPad(List<IrKey> nums) {
        IrKey[] byDigit = new IrKey[10];
        for (IrKey k : nums) { int d = KeyRoles.digit(k); if (d >= 0 && d <= 9 && byDigit[d] == null) byDigit[d] = k; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        int p = Ui.dp(this, 16);
        box.setPadding(p, p, p, p);
        int[][] grid = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {-1, 0, -1}};
        for (int[] row : grid) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int d : row) {
                FrameLayout cell = new FrameLayout(this);
                if (d >= 0 && byDigit[d] != null) {
                    IrKey k = byDigit[d];
                    FrameLayout v = Ui.key(this, 56, 0, String.valueOf(d), 0);
                    TextView t = (TextView) v.getChildAt(0);
                    t.setTextSize(18);
                    t.setTextColor(Ui.color(this, R.color.ink));
                    v.setOnClickListener(x -> send(x, k.code));
                    cell.addView(v, new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.CENTER));
                }
                r.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1f));
            }
            box.addView(r);
        }
        box.setBackgroundColor(Ui.color(this, R.color.bg));
        AlertDialog d = new AlertDialog.Builder(this).setView(box).create();
        d.show();
        rotateDialog(d);
    }

    // ---------------- LED ----------------

    private void buildLed(List<IrKey> keys) {
        LinearLayout col = new LinearLayout(this);
        area(col, 16);
        if (keys == null || keys.isEmpty()) {
            col.addView(Ui.text(this, "אין כפתורים בשלט הזה", 14, Ui.color(this, R.color.sub), false), topMargin(40));
            return;
        }
        IrKey on = null, off = null, power = null, bUp = null, bDn = null;
        List<IrKey> colors = new ArrayList<>(), rest = new ArrayList<>();
        for (IrKey k : keys) {
            String role = KeyRoles.of(k, true);
            if (KeyRoles.ON.equals(role) && on == null) on = k;
            else if (KeyRoles.OFF.equals(role) && off == null) off = k;
            else if (KeyRoles.POWER.equals(role) && power == null) power = k;
            else if (KeyRoles.COLOR.equals(role)) colors.add(k);
            else if (KeyRoles.BRIGHT_UP.equals(role) && bUp == null) bUp = k;
            else if (KeyRoles.BRIGHT_DN.equals(role) && bDn == null) bDn = k;
            else rest.add(k);
        }
        if (on == null && power != null) { on = power; power = null; }
        shadeColors(colors);
        if (power != null) rest.add(0, power);

        // on / off
        LinearLayout oo = new LinearLayout(this);
        oo.setOrientation(LinearLayout.HORIZONTAL);
        if (on != null) oo.addView(wideKey(R.drawable.ic_power, off == null ? "הפעלה/כיבוי" : "הדלק", Ui.color(this, R.color.ink), on),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (off != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (oo.getChildCount() > 0) lp.setMarginStart(Ui.dp(this, 10));
            oo.addView(wideKey(R.drawable.ic_power, "כבה", Ui.color(this, R.color.sub), off), lp);
        }
        if (oo.getChildCount() > 0) col.addView(oo, fullWidth(0));

        // colour wheel + swatches
        List<IrKey> hued = new ArrayList<>();
        for (IrKey k : colors) {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(k.color != 0 ? k.color : KeyRoles.colorFor(k.label), hsv);
            if (hsv[1] >= 0.2f) hued.add(k);
        }
        if (hued.size() >= 2) {
            ColorWheelView w = new ColorWheelView(this);
            w.setListener(argb -> {
                IrKey best = null;
                float bd = Float.MAX_VALUE;
                for (IrKey k : hued) {
                    int kc = k.color != 0 ? k.color : KeyRoles.colorFor(k.label);
                    float d = ColorWheelView.hueDistance(argb, kc);
                    if (d < bd) { bd = d; best = k; }
                }
                if (best != null) send(w, best.code);
            });
            col.addView(w, topMargin(oo.getChildCount() > 0 ? 7 : 0));
        }
        if (!colors.isEmpty()) {
            int avail = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32);
            int gap = Ui.dp(this, 8);
            int cell = (avail - gap * 5) / 6;
            for (int i = 0; i < colors.size(); i += 6) {
                LinearLayout r = new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                for (int j = i; j < Math.min(i + 6, colors.size()); j++) {
                    View s = swatch(colors.get(j), 10, false);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cell, cell);
                    if (j > i) lp.setMarginStart(gap);
                    r.addView(s, lp);
                }
                col.addView(r, fullWidth(i == 0 ? 12 : 8));
            }
        }

        // brightness + effects, 3 per row
        List<View> wide = new ArrayList<>();
        if (bUp != null) wide.add(wideKey(R.drawable.ic_sun, "בהירות +", Ui.color(this, R.color.ink), bUp));
        if (bDn != null) wide.add(wideKey(R.drawable.ic_moon, "בהירות −", Ui.color(this, R.color.ink), bDn));
        for (IrKey k : rest) {
            String role = KeyRoles.of(k, true);
            String label = KeyRoles.GENERIC.equals(role) ? KeyRoles.hebrew(k.label) : KeyRoles.roleName(role);
            if (KeyRoles.NUM.equals(role)) label = k.label;
            wide.add(wideKey(0, label, 0, k));
        }
        for (int i = 0; i < wide.size(); i += 3) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + 3; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (j > i) lp.setMarginStart(Ui.dp(this, 10));
                if (j < wide.size()) r.addView(wide.get(j), lp);
                else r.addView(new View(this), lp);
            }
            col.addView(r, fullWidth(i == 0 ? 12 : 10));
        }
    }

    /** Library colour rows (Red1..Red5, Green_0..4, Blue...) get the shades of a 24-key remote. */
    private static void shadeColors(List<IrKey> colors) {
        int[][] shades = {
                {0xFFEF4444, 0xFFFF5A1F, 0xFFFF8A00, 0xFFFFB000, 0xFFEAB308},   // red -> yellow
                {0xFF22C55E, 0xFF34D399, 0xFF10B981, 0xFF14B8A6, 0xFF06B6D4},   // green -> cyan
                {0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFEC4899}};  // blue -> pink
        int[] base = {0xFFEF4444, 0xFF22C55E, 0xFF3B82F6};
        int[] count = new int[3];
        for (IrKey k : colors) {
            if (k.color != 0) continue;
            int c = KeyRoles.colorFor(k.label);
            for (int b = 0; b < 3; b++) {
                if (c == base[b]) { k.color = shades[b][Math.min(count[b], 4)]; count[b]++; break; }
            }
        }
    }

    /** White rounded key with an optional icon and a label (LED on/off, brightness, effects). */
    private View wideKey(int iconRes, String label, int iconColor, IrKey k) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundResource(R.drawable.bg_btn_white);
        v.setElevation(Ui.dp(this, 2));
        int p = Ui.dp(this, 11);
        v.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        if (iconRes != 0) {
            ImageView iv = Ui.icon(this, iconRes, 17, iconColor);
            v.addView(iv);
        }
        TextView t = Ui.text(this, label, iconRes != 0 ? 13 : 12, Ui.color(this, R.color.ink), iconRes != 0);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (iconRes != 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Ui.dp(this, 7));
            v.addView(t, lp);
        } else v.addView(t);
        v.setOnClickListener(x -> send(x, k.code));
        return v;
    }
}
