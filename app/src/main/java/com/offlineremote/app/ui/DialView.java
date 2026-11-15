package com.offlineremote.app.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.offlineremote.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The ZaZa temperature dial: a soft disc with temperatures around the top arc and a
 * raised knob in the middle. Drag/tap on the ring picks a temperature (sent on release);
 * tapping the knob re-sends the current state. Drawn in a 240-unit box like the mockup.
 */
public class DialView extends View {

    public interface Listener {
        void onTemp(int temp, boolean commit);
        void onKnob();
    }

    private int minT = 16, maxT = 30, temp = 24;
    private Listener listener;
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean dragging, knobDown;
    private float lastW = -1;

    public DialView(Context c) { this(c, null); }

    public DialView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // for the blurred knob shadow
        label.setColor(Ui.color(c, R.color.tempLabel));
        label.setTypeface(Ui.font(c, false));
        label.setTextAlign(Paint.Align.CENTER);
        labelSel.setColor(Ui.color(c, R.color.ink));
        labelSel.setTypeface(Ui.font(c, true));
        labelSel.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0x10000000);
        shadow.setColor(0x2E465A78);
    }

    public void setListener(Listener l) { listener = l; }

    public void setRange(int min, int max) {
        minT = min; maxT = Math.max(min + 1, max);
        temp = Math.max(minT, Math.min(maxT, temp));
        invalidate();
    }

    public void setTemp(int t) { temp = Math.max(minT, Math.min(maxT, t)); invalidate(); }

    public int getTemp() { return temp; }

    @Override
    protected void onMeasure(int w, int h) {
        int def = Ui.dp(getContext(), 240);
        int size = Math.min(resolveSize(def, w), resolveSize(def, h));
        setMeasuredDimension(size, size);
    }

    private float u() { return getWidth() / 240f; }

    private float angleOf(int t) { return -115f + (t - minT) * (230f / (maxT - minT)); }

    private void prepare() {
        if (lastW == getWidth()) return;
        lastW = getWidth();
        float u = u(), cx = getWidth() / 2f, cy = getHeight() / 2f;
        disc.setShader(new RadialGradient(cx, cy - 16.3f * u, 156 * u, 0xFFFFFFFF, 0xFFE4E9EF, Shader.TileMode.CLAMP));
        knob.setShader(new RadialGradient(cx, cy - 13 * u, 86 * u, 0xFFFFFFFF, 0xFFDFE4EA, Shader.TileMode.CLAMP));
        shadow.setMaskFilter(new BlurMaskFilter(Math.max(1, 9 * u), BlurMaskFilter.Blur.NORMAL));
        ring.setStrokeWidth(Math.max(1, u));
        label.setTextSize(15 * u);
        labelSel.setTextSize(15 * u);
    }

    @Override
    protected void onDraw(Canvas c) {
        prepare();
        float u = u(), cx = getWidth() / 2f, cy = getHeight() / 2f;
        c.drawCircle(cx, cy, 102 * u, disc);
        c.drawCircle(cx, cy, 102 * u, ring);
        c.drawCircle(cx, cy + 6 * u, 54 * u, shadow);
        c.drawCircle(cx, cy, 54 * u, knob);

        List<Integer> labels = new ArrayList<>();
        for (int t = minT; t <= maxT; t += 2) labels.add(t);
        if (!labels.contains(temp)) labels.add(temp);
        float r = 100 * u;
        for (int t : labels) {
            double a = Math.toRadians(angleOf(t));
            float x = cx + (float) (r * Math.sin(a));
            float y = cy - (float) (r * Math.cos(a));
            Paint p = (t == temp) ? labelSel : label;
            float base = y - (p.ascent() + p.descent()) / 2f;
            c.drawText(t + "°", x, base, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float u = u(), cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dx = e.getX() - cx, dy = e.getY() - cy;
        float dist = (float) Math.hypot(dx, dy);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (dist <= 54 * u) { knobDown = true; return true; }
                if (dist <= 122 * u) { dragging = true; pick(dx, dy); getParent().requestDisallowInterceptTouchEvent(true); return true; }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging) pick(dx, dy);
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging && listener != null) listener.onTemp(temp, true);
                else if (knobDown && dist <= 54 * u && listener != null) { performClick(); listener.onKnob(); }
                dragging = knobDown = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = knobDown = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void pick(float dx, float dy) {
        double a = Math.toDegrees(Math.atan2(dx, -dy));      // 0 = top, clockwise
        if (a > 115 && a <= 180) a = 115;
        if (a < -115 && a >= -180) a = -115;
        int t = Math.round(minT + (float) ((a + 115) / 230.0) * (maxT - minT));
        t = Math.max(minT, Math.min(maxT, t));
        if (t != temp) {
            temp = t;
            invalidate();
            if (listener != null) listener.onTemp(temp, false);
        }
    }
}
