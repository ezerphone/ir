package com.offlineremote.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Surface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.offlineremote.app.R;

/**
 * Common base: right-to-left layout and the remembered 180° rotation (so the phone can be
 * held "upside down" with the dongle at the bottom pointing forward). The whole display is
 * flipped (status bar, navigation, dialogs). Phones that refuse upside-down mode fall back
 * to flipping just the app's content.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String PREFS = "ui", KEY_ROT = "rot180", KEY_FALLBACK = "rotFallback";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        requestOrientation();
    }

    private static boolean fallback(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_FALLBACK, false);
    }

    private void requestOrientation() {
        boolean full = isRotated(this) && !fallback(this);
        setRequestedOrientation(full ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyRotation(false);
    }

    public static boolean isRotated(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ROT, false);
    }

    public void toggleRotation() {
        boolean r = !isRotated(this);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ROT, r).apply();
        applyRotation(true);
    }

    @SuppressWarnings("deprecation")
    protected void applyRotation(boolean animate) {
        requestOrientation();
        View c = findViewById(android.R.id.content);
        if (c == null) return;
        boolean rotated = isRotated(this);
        // app-only rotation is used only on phones that refuse the upside-down display
        float target = (rotated && fallback(this)) ? 180f : 0f;
        if (animate && fallback(this)) c.animate().rotation(target).setDuration(260).start();
        else c.setRotation(target);
        if (rotated && !fallback(this)) {
            c.postDelayed(() -> {
                if (isFinishing() || !isRotated(this) || fallback(this)) return;
                int r = getWindowManager().getDefaultDisplay().getRotation();
                if (r != Surface.ROTATION_180) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_FALLBACK, true).apply();
                    applyRotation(true);
                    toast("הטלפון לא מאפשר סיבוב מלא — מסובבים את האפליקציה");
                }
            }, 1200);
        }
    }

    /** Call after dialog.show(): with app-only rotation the dialog is flipped to match. */
    public void rotateDialog(Dialog d) {
        if (d == null || d.getWindow() == null) return;
        View decor = d.getWindow().getDecorView();
        decor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        View v = d.getWindow().findViewById(android.R.id.content);
        if (v != null) v.setRotation(isRotated(this) && fallback(this) ? 180f : 0f);
    }

    /** Short message shown inside the (possibly rotated) screen. */
    public void toast(String msg) {
        View root = findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;
        final FrameLayout content = (FrameLayout) root;
        final TextView t = Ui.text(this, msg, 14, 0xFFFFFFFF, true);
        t.setBackgroundResource(R.drawable.bg_toast);
        int ph = Ui.dp(this, 18), pv = Ui.dp(this, 10);
        t.setPadding(ph, pv, ph, pv);
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(this, 96);
        t.setAlpha(0f);
        t.setElevation(Ui.dp(this, 6));
        content.addView(t, lp);
        t.animate().alpha(1f).setDuration(150).withEndAction(() ->
                t.animate().alpha(0f).setStartDelay(1500).setDuration(250)
                        .withEndAction(() -> content.removeView(t)).start()).start();
    }

    protected TopBar topBar() { return new TopBar(this, findViewById(R.id.topbar)); }
}
