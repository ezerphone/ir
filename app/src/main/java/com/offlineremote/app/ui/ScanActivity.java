package com.offlineremote.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.PreviewSession;
import com.offlineremote.app.usb.RawDongle;
import com.offlineremote.app.util.ErrorLog;

import java.util.List;

/**
 * Auto-scan for a device without a known brand/remote: sends one power code at a time
 * (manually or every 2 seconds). "עבד!" opens the remotes that use that code for matching.
 */
public class ScanActivity extends BaseActivity {

    public static final String EXTRA_KIND = "kind";   // "ac" or "audio"
    private static final long STEP_MS = 2200;

    private String kind;
    private List<ScanPool.Cand> pool;
    private int index;
    private boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TopBar bar;
    private TextView info, autoText;
    private ImageView autoIcon;
    private View autoBtn;

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (!running || pool == null) return;
            if (index >= pool.size() - 1) { stopAuto(); toast("הסריקה הסתיימה"); return; }
            index++;
            showIndex();
            sendCurrent(null);
            handler.postDelayed(this, STEP_MS);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_remote);
        kind = getIntent().getStringExtra(EXTRA_KIND);
        if (kind == null) kind = "audio";
        bar = topBar();
        if ("ac".equals(kind)) { bar.rotate(true); bar.back(false); }
        else { bar.back(true); bar.rotate(false); }
        bar.title("חיפוש אוטומטי · " + noun());

        View banner = findViewById(R.id.banner);
        banner.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.bannerText)).setText("כוון את הטלפון אל " + target() + " ולחץ הפעלה");
        findViewById(R.id.bannerClose).setOnClickListener(v -> banner.setVisibility(View.GONE));

        findViewById(R.id.matchbar).setVisibility(View.VISIBLE);
        findViewById(R.id.btnPrev).setOnClickListener(v -> move(-1));
        findViewById(R.id.btnNext).setOnClickListener(v -> move(1));
        findViewById(R.id.btnWorked).setOnClickListener(v -> worked());

        buildContent();
        App.get().io().execute(() -> {
            final List<ScanPool.Cand> p = "ac".equals(kind) ? ScanPool.ac()
                    : "led".equals(kind) ? ScanPool.led() : ScanPool.audio();
            runOnUiThread(() -> { pool = p; index = 0; showContentReady(); showIndex(); });
        });
    }

    private String noun() { return "ac".equals(kind) ? "מזגן" : "led".equals(kind) ? "מנורה" : "רמקול / סאונד בר"; }
    private String target() { return "ac".equals(kind) ? "המזגן" : "led".equals(kind) ? "המנורה" : "הרמקול / הסאונד בר"; }

    // ---------------- layout ----------------

    private LinearLayout col;
    private View loading, ready;

    private void buildContent() {
        FrameLayout content = findViewById(R.id.content);
        content.setBackgroundResource(R.drawable.bg_area);

        loading = progressView();
        content.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 20);
        col.setPadding(p, p, p, p);
        ready = col;
        col.setVisibility(View.GONE);
        content.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView hint = Ui.text(this, "led".equals(kind)
                ? "כבה את המנורה, ואז לחץ על ההפעלה. המנורה נדלקה? לחץ עבד!"
                : "לחץ על ההפעלה. המכשיר נדלק, כבה או צפצף? לחץ עבד!", 13,
                Ui.color(this, R.color.sub), false);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, wide(0));

        FrameLayout power = Ui.key(this, 120, R.drawable.ic_power, null, Ui.color(this, R.color.power));
        power.setElevation(Ui.dp(this, 4));
        ImageView pi = (ImageView) power.getChildAt(0);
        pi.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46), Gravity.CENTER));
        power.setOnClickListener(v -> { stopAuto(); sendCurrent(v); });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 120));
        plp.topMargin = Ui.dp(this, 28);
        col.addView(power, plp);

        info = Ui.text(this, "", 12, Ui.color(this, R.color.sub), false);
        info.setGravity(Gravity.CENTER);
        info.setMaxLines(2);
        col.addView(info, wide(22));

        LinearLayout ab = new LinearLayout(this);
        ab.setOrientation(LinearLayout.HORIZONTAL);
        ab.setGravity(Gravity.CENTER);
        ab.setBackgroundResource(R.drawable.bg_btn_white);
        ab.setElevation(Ui.dp(this, 2));
        int ph = Ui.dp(this, 22), pv = Ui.dp(this, 12);
        ab.setPadding(ph, pv, ph, pv);
        autoIcon = Ui.icon(this, R.drawable.ic_playpause, 18, Ui.color(this, R.color.blue));
        ab.addView(autoIcon);
        autoText = Ui.text(this, "סריקה רציפה", 14, Ui.color(this, R.color.ink), true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(Ui.dp(this, 9));
        ab.addView(autoText, tlp);
        ab.setOnClickListener(v -> { if (running) stopAuto(); else startAuto(); });
        autoBtn = ab;
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 22);
        col.addView(ab, alp);

        TextView autoHint = Ui.text(this, "סריקה רציפה שולחת קוד כל 2 שניות — לחץ עבד! כשהמכשיר מגיב",
                11, Ui.color(this, R.color.sub), false);
        autoHint.setGravity(Gravity.CENTER);
        col.addView(autoHint, wide(10));
    }

    private View progressView() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.addView(new ProgressBar(this));
        TextView t = Ui.text(this, "מכין את הקודים…", 13, Ui.color(this, R.color.sub), false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        l.addView(t, lp);
        return l;
    }

    private LinearLayout.LayoutParams wide(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }

    private void showContentReady() {
        loading.setVisibility(View.GONE);
        ready.setVisibility(View.VISIBLE);
        if (pool.isEmpty()) {
            info.setText("לא נמצאו קודים לסריקה");
            autoBtn.setEnabled(false);
        }
    }

    private void showIndex() {
        if (pool == null || pool.isEmpty()) return;
        bar.title("חיפוש אוטומטי · " + noun() + " (" + (index + 1) + "/" + pool.size() + ")");
        ScanPool.Cand c = pool.get(index);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, c.brands.size()); i++) { if (i > 0) b.append(", "); b.append(c.brands.get(i)); }
        if (c.brands.size() > 3) b.append(" ועוד");
        info.setText("קוד " + (index + 1) + " מתוך " + pool.size() + " · " + c.builders.size()
                + (c.builders.size() == 1 ? " שלט" : " שלטים") + " (" + b + ")");
        findViewById(R.id.btnPrev).setAlpha(index > 0 ? 1f : 0.45f);
        findViewById(R.id.btnNext).setAlpha(index < pool.size() - 1 ? 1f : 0.45f);
    }

    // ---------------- actions ----------------

    private void move(int d) {
        if (pool == null) return;
        stopAuto();
        int n = index + d;
        if (n < 0 || n >= pool.size()) return;
        index = n;
        showIndex();
        sendCurrent(null);
    }

    private void startAuto() {
        if (pool == null || pool.isEmpty()) return;
        running = true;
        autoText.setText("עצור סריקה");
        sendCurrent(null);
        handler.postDelayed(step, STEP_MS);
    }

    private void stopAuto() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(step);
        autoText.setText("סריקה רציפה");
    }

    private void worked() {
        if (pool == null || pool.isEmpty()) return;
        if (running) {
            // the device may have reacted to the previous code: let the user confirm first
            stopAuto();
            toast("נעצר. לחץ הפעלה לוודא — אם לא, חזור עם הקודם");
            return;
        }
        ScanPool.Cand c = pool.get(index);
        PreviewSession s = new PreviewSession(c.builders, 0);
        s.brand = c.brands.isEmpty() ? "" : c.brands.get(0);
        s.category = c.slug;
        s.categoryHe = Icons.noun(c.slug, "מכשיר");
        s.target = Icons.target(c.slug);
        App.get().preview = s;
        startActivity(new Intent(this, RemoteActivity.class).putExtra(RemoteActivity.EXTRA_PREVIEW, true));
    }

    private void sendCurrent(View v) {
        if (pool == null || pool.isEmpty()) return;
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        final Code c = pool.get(index).code;
        App.get().io().execute(() -> {
            RawDongle raw = App.get().raw();
            if (!raw.isOpen()) raw.open();
            boolean open = raw.isOpen(), ok = open && raw.transmit(c.freq,
                    c.kind == Code.Kind.NATIVE ? c.nativeBytes : null, c.micros);
            if (!open) ErrorLog.add(this, "SCAN dongle not open");
            else if (!ok) ErrorLog.add(this, "SCAN send failed");
            final boolean fOpen = open, fOk = ok;
            runOnUiThread(() -> {
                if (!fOpen) { stopAuto(); toast(getString(R.string.dongle_missing)); }
                else if (!fOk) toast(getString(R.string.send_failed));
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAuto();
    }
}
