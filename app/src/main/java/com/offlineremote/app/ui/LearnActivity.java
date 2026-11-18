package com.offlineremote.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.usb.RawDongle;
import com.offlineremote.app.util.ErrorLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Learn from a physical remote: pick the button type (its icon follows), press למד, press
 * the button on the original remote. Uses the same capture path as before (learnLogged).
 */
public class LearnActivity extends BaseActivity {

    private static final class Type {
        final String role, label; final int icon;
        Type(String role, String label, int icon) { this.role = role; this.label = label; this.icon = icon; }
    }

    // first eight exactly as in the mockup, the rest below them
    private static final Type[] TYPES = {
            new Type(KeyRoles.POWER, "הפעלה", R.drawable.ic_power),
            new Type(KeyRoles.MODE, "מצב", R.drawable.ic_snow),
            new Type(KeyRoles.TEMP_UP, "טמפ' +", R.drawable.ic_plus),
            new Type(KeyRoles.VOL_UP, "עוצמה +", R.drawable.ic_vol),
            new Type(KeyRoles.CH_UP, "ערוץ +", R.drawable.ic_up),
            new Type(KeyRoles.NUM, "מספר", R.drawable.ic_num),
            new Type(KeyRoles.MUTE, "השתקה", R.drawable.ic_mute),
            new Type(KeyRoles.COLOR, "צבע", 0),
            new Type(KeyRoles.TEMP_DN, "טמפ' −", R.drawable.ic_minus),
            new Type(KeyRoles.VOL_DN, "עוצמה −", R.drawable.ic_vol),
            new Type(KeyRoles.CH_DN, "ערוץ −", R.drawable.ic_dn),
            new Type(KeyRoles.FAN, "מאוורר", R.drawable.ic_fanbars),
            new Type(KeyRoles.UP, "למעלה", R.drawable.ic_up),
            new Type(KeyRoles.DOWN, "למטה", R.drawable.ic_dn),
            new Type(KeyRoles.LEFT, "שמאלה", R.drawable.ic_lf),
            new Type(KeyRoles.RIGHT, "ימינה", R.drawable.ic_rt),
            new Type(KeyRoles.OK, "OK", R.drawable.ic_check),
            new Type(KeyRoles.MENU, "תפריט", R.drawable.ic_menu),
            new Type(KeyRoles.SOURCE, "מקור", R.drawable.ic_input),
            new Type(KeyRoles.BACK, "חזור", R.drawable.ic_back),
            new Type(KeyRoles.HOME, "בית", R.drawable.ic_home),
            new Type(KeyRoles.BRIGHT_UP, "בהירות +", R.drawable.ic_sun),
            new Type(KeyRoles.BRIGHT_DN, "בהירות −", R.drawable.ic_moon),
            new Type(KeyRoles.GENERIC, "כללי", R.drawable.ic_edit),
    };

    private static final int[] COLORS = {0xFFEF4444, 0xFFF97316, 0xFFEAB308, 0xFF22C55E, 0xFF14B8A6, 0xFF3B82F6,
            0xFF8B5CF6, 0xFFEC4899, 0xFFFFFFFF, 0xFFFDE68A, 0xFFA7F3D0, 0xFFBFDBFE};
    private static final String[] COLOR_NAMES = {"אדום", "כתום", "צהוב", "ירוק", "טורקיז", "כחול",
            "סגול", "ורוד", "לבן", "לבן חם", "ירוק בהיר", "תכלת"};

    private EditText nameInput;
    private TextView recText, status;
    private View recBtn;
    private ImageView saveIcon;
    private final List<FrameLayout> tiles = new ArrayList<>();
    private final List<IrKey> learned = new ArrayList<>();
    private int selected = 0;
    private String pendingLabel = TYPES[0].label;
    private int pendingColor = 0;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_learn);
        TopBar bar = topBar().title("למידה · בחר סוג כפתור");
        bar.addStart(R.drawable.ic_chr, v -> leave());
        bar.rotate(false);
        saveIcon = bar.addEnd(R.drawable.ic_check, v -> save());
        Ui.tint(saveIcon, Ui.color(this, R.color.blue));
        saveIcon.setVisibility(View.GONE);

        nameInput = findViewById(R.id.remoteName);
        recText = findViewById(R.id.recText);
        status = findViewById(R.id.learnStatus);
        recBtn = findViewById(R.id.btnRec);
        recBtn.setOnClickListener(v -> learnOne());
        buildTypes();
        select(0);
    }

    @Override
    public void onBackPressed() { leave(); }

    private void leave() {
        if (learned.isEmpty()) { finish(); return; }
        AlertDialog d = new AlertDialog.Builder(this).setMessage("לצאת בלי לשמור את הכפתורים שנלמדו?")
                .setPositiveButton("צא", (x, w) -> finish())
                .setNegativeButton("הישאר", null).create();
        d.show();
        rotateDialog(d);
    }

    // ---------------- type grid ----------------

    private void buildTypes() {
        LinearLayout grid = findViewById(R.id.typesGrid);
        int gap = Ui.dp(this, 9);
        for (int i = 0; i < TYPES.length; i += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + 4; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (j > i) lp.setMarginStart(gap);
                if (j < TYPES.length) {
                    FrameLayout t = tile(TYPES[j], j);
                    tiles.add(t);
                    row.addView(t, lp);
                } else row.addView(new View(this), lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = gap;
            grid.addView(row, rlp);
        }
    }

    private FrameLayout tile(Type t, int index) {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundResource(R.drawable.bg_type);
        f.setElevation(Ui.dp(this, 2));
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 10);
        in.setPadding(Ui.dp(this, 4), p, Ui.dp(this, 4), p);
        View icon;
        if (t.icon != 0) {
            ImageView iv = Ui.icon(this, t.icon, 22, Ui.color(this, R.color.icon));
            iv.setTag("icon");
            icon = iv;
        } else {
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            g.setColors(new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF0099FF, 0xFFFF00FF, 0xFFFF0000});
            sw.setBackground(g);
            sw.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            icon = sw;
        }
        in.addView(icon);
        TextView label = Ui.text(this, t.label, 10, Ui.color(this, R.color.ink), true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 5);
        in.addView(label, llp);
        f.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = Ui.text(this, "", 9, 0xFFFFFFFF, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_badge);
        badge.setVisibility(View.GONE);
        badge.setTag("badge");
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16), Gravity.TOP | Gravity.START);
        blp.setMargins(Ui.dp(this, 4), Ui.dp(this, 4), 0, 0);
        f.addView(badge, blp);

        f.setOnClickListener(v -> pick(index));
        return f;
    }

    private void pick(int index) {
        if (busy) return;
        Type t = TYPES[index];
        if (KeyRoles.NUM.equals(t.role)) askNumber(index);
        else if (KeyRoles.COLOR.equals(t.role)) askColor(index);
        else if (KeyRoles.GENERIC.equals(t.role)) askName(index);
        else { pendingLabel = t.label; pendingColor = 0; select(index); }
    }

    private void select(int index) {
        selected = index;
        for (int i = 0; i < tiles.size(); i++) {
            FrameLayout f = tiles.get(i);
            boolean sel = i == index;
            f.setBackgroundResource(sel ? R.drawable.bg_type_sel : R.drawable.bg_type);
            ImageView iv = f.findViewWithTag("icon");
            if (iv != null) Ui.tint(iv, Ui.color(this, sel ? R.color.blue : R.color.icon));
        }
        recText.setText("למד \"" + shown(TYPES[index].role, pendingLabel) + "\"");
        status.setText("");
    }

    private void refreshBadges() {
        for (int i = 0; i < tiles.size(); i++) {
            int n = 0;
            for (IrKey k : learned) if (TYPES[i].role.equals(k.role)) n++;
            TextView badge = tiles.get(i).findViewWithTag("badge");
            if (n == 0) badge.setVisibility(View.GONE);
            else { badge.setVisibility(View.VISIBLE); badge.setText(n == 1 ? "✓" : String.valueOf(n)); }
        }
        saveIcon.setVisibility(learned.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------- pickers for number / colour / custom name ----------------

    private void askNumber(int index) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        int p = Ui.dp(this, 14);
        box.setPadding(p, p, p, p);
        final AlertDialog[] dlg = new AlertDialog[1];
        int[][] grid = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {-1, 0, -1}};
        for (int[] row : grid) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int d : row) {
                FrameLayout cell = new FrameLayout(this);
                if (d >= 0) {
                    FrameLayout k = Ui.key(this, 54, 0, String.valueOf(d), 0);
                    TextView tv = (TextView) k.getChildAt(0);
                    tv.setTextSize(18);
                    tv.setTextColor(Ui.color(this, R.color.ink));
                    final int digit = d;
                    k.setOnClickListener(v -> {
                        pendingLabel = String.valueOf(digit);
                        pendingColor = 0;
                        select(index);
                        dlg[0].dismiss();
                    });
                    cell.addView(k, new FrameLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54), Gravity.CENTER));
                }
                r.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));
            }
            box.addView(r);
        }
        box.setBackgroundColor(Ui.color(this, R.color.bg));
        dlg[0] = new AlertDialog.Builder(this).setTitle("איזה מספר?").setView(box).create();
        dlg[0].show();
        rotateDialog(dlg[0]);
    }

    private void askColor(int index) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        box.setPadding(p, p, p, p);
        final AlertDialog[] dlg = new AlertDialog[1];
        for (int i = 0; i < COLORS.length; i += 4) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + 4; j++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                View sw = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(COLORS[j]);
                g.setStroke(Ui.dp(this, 1), 0x22000000);
                sw.setBackground(g);
                sw.setElevation(Ui.dp(this, 1));
                cell.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
                TextView name = Ui.text(this, COLOR_NAMES[j], 11, Ui.color(this, R.color.ink), false);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nlp.topMargin = Ui.dp(this, 4);
                cell.addView(name, nlp);
                final int c = COLORS[j];
                final String n = COLOR_NAMES[j];
                cell.setOnClickListener(v -> {
                    pendingLabel = n;
                    pendingColor = c;
                    select(index);
                    dlg[0].dismiss();
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                clp.bottomMargin = Ui.dp(this, 12);
                r.addView(cell, clp);
            }
            box.addView(r);
        }
        box.setBackgroundColor(Ui.color(this, R.color.bg));
        dlg[0] = new AlertDialog.Builder(this).setTitle("איזה צבע?").setView(box).create();
        dlg[0].show();
        rotateDialog(dlg[0]);
    }

    private void askName(int index) {
        EditText in = new EditText(this);
        in.setHint("שם הכפתור");
        int p = Ui.dp(this, 20);
        in.setPadding(p, p, p, p);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("כפתור כללי").setView(in)
                .setPositiveButton("אישור", (x, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty()) return;
                    pendingLabel = n;
                    pendingColor = 0;
                    select(index);
                })
                .setNegativeButton("ביטול", null).create();
        d.show();
        rotateDialog(d);
    }

    // ---------------- learning ----------------

    private boolean ensureRawOpen() {
        RawDongle raw = App.get().raw();
        if (raw.isOpen()) return true;
        try { App.get().dongle().disconnect(); } catch (Throwable ignore) {}
        return raw.open();
    }

    /** What the user sees for a pending label ("מספר 3" for digits). */
    private String shown(String role, String label) {
        return KeyRoles.NUM.equals(role) ? "מספר " + label : label;
    }

    private void setStatus(String s, int colorRes) {
        status.setText(s);
        status.setTextColor(Ui.color(this, colorRes));
    }

    private void learnOne() {
        if (busy) return;
        final Type t = TYPES[selected];
        final String label = pendingLabel;
        final int color = pendingColor;
        busy = true;
        recBtn.setAlpha(0.6f);
        recText.setText("לחץ על הכפתור בשלט המקורי…");
        setStatus("כוון את השלט המקורי אל הדונגל", R.color.sub);
        App.get().io().execute(() -> {
            byte[] frame = null;
            if (ensureRawOpen()) {
                List<String> lg = new ArrayList<>();
                frame = App.get().raw().learnLogged(12000, lg);
                StringBuilder detail = new StringBuilder();
                for (String s : lg) detail.append(s).append(" | ");
                ErrorLog.add(this, (frame == null ? "LEARN FAIL \"" : "LEARN OK \"") + label + "\"  " + detail);
            } else {
                ErrorLog.add(this, "LEARN open-failed (permission/attach)");
            }
            final byte[] f = frame;
            runOnUiThread(() -> {
                busy = false;
                recBtn.setAlpha(1f);
                recText.setText("למד \"" + shown(t.role, label) + "\"");
                if (f == null) {
                    setStatus(App.get().raw().isOpen()
                            ? "✗ לא נקלט — כוון את השלט ישירות לדונגל ונסה שוב"
                            : "✗ הדונגל לא מחובר", R.color.failText);
                    return;
                }
                // carrier guessed from the timing pattern (Sony 40k, Philips 36k, NEC 38k...)
                IrKey k = new IrKey(label, 0, Code.nativeCode(com.offlineremote.app.ir.IrGen.detectCarrier(f), f));
                k.role = t.role;
                k.color = color;
                for (int i = 0; i < learned.size(); i++) {
                    IrKey o = learned.get(i);
                    if (TextUtils.equals(o.role, k.role) && TextUtils.equals(o.label, k.label)) { learned.remove(i); break; }
                }
                learned.add(k);
                refreshBadges();
                setStatus("✓ נקלט — הכפתור יופיע עם אייקון " + shown(t.role, label), R.color.okText);
            });
        });
    }

    private void save() {
        if (learned.isEmpty()) return;
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) name = "שלט שנלמד";
        boolean led = false;
        for (IrKey k : learned)
            if (KeyRoles.COLOR.equals(k.role) || KeyRoles.BRIGHT_UP.equals(k.role) || KeyRoles.BRIGHT_DN.equals(k.role)) led = true;
        SavedRemote r = new SavedRemote();
        r.source = "learned";
        r.category = led ? "led" : "learned";
        r.name = name;
        r.keys = new ArrayList<>(learned);
        App.get().store().add(r);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(MainActivity.EXTRA_SAVED, name);
        startActivity(i);
        finish();
    }
}
