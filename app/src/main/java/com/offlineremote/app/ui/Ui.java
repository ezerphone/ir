package com.offlineremote.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;

import com.offlineremote.app.R;

/** Small view helpers shared by the screens. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static int color(Context c, int res) { return ContextCompat.getColor(c, res); }

    public static Typeface font(Context c, boolean bold) {
        Typeface t = ResourcesCompat.getFont(c, bold ? R.font.assistant_bold : R.font.assistant_regular);
        return t != null ? t : (bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    public static void tint(ImageView v, int argb) {
        ImageViewCompat.setImageTintList(v, ColorStateList.valueOf(argb));
    }

    public static ImageView icon(Context c, int res, int sizeDp, int argb) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        tint(v, argb);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    public static TextView text(Context c, String s, float sp, int argb, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(argb);
        t.setTypeface(font(c, bold));
        t.setIncludeFontPadding(false);
        return t;
    }

    /**
     * White circular key (the ZaZa look): icon if iconRes != 0, else a small text label.
     */
    public static FrameLayout key(Context c, int sizeDp, int iconRes, String label, int iconColor) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundResource(R.drawable.bg_key);
        f.setElevation(dp(c, 2));
        f.setClickable(true);
        f.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        f.setLayoutParams(lp);
        if (iconRes != 0) {
            int is = sizeDp >= 54 ? 24 : 20;
            ImageView iv = new ImageView(c);
            iv.setImageResource(iconRes);
            tint(iv, iconColor);
            f.addView(iv, new FrameLayout.LayoutParams(dp(c, is), dp(c, is), Gravity.CENTER));
        } else {
            TextView t = text(c, label == null ? "" : label, label != null && label.length() > 6 ? 9.5f : 10.5f,
                    color(c, R.color.sub), true);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            int p = dp(c, 4);
            t.setPadding(p, 0, p, 0);
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        return f;
    }

    public static LinearLayout row(Context c, int gapDp) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setTag(gapDp);
        return r;
    }

    /** Add child to a row with a horizontal gap before it (except the first). */
    public static void addGap(LinearLayout row, View child, int gapDp) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
        if (lp == null) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (row.getChildCount() > 0) lp.setMarginStart(dp(row.getContext(), gapDp));
        row.addView(child, lp);
    }
}
