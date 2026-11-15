package com.offlineremote.app.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * Colour wheel from the mockup (white rim, colour disc in the middle). Tapping returns
 * the colour under the finger so the screen can send the closest colour button.
 */
public class ColorWheelView extends View {

    public interface Listener { void onColor(int argb); }

    // same stops as the mockup's conic-gradient, starting at 12 o'clock
    private static final int[] STOPS = {0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF40FF00, 0xFF00FFBF,
            0xFF00BFFF, 0xFF0040FF, 0xFF8000FF, 0xFFFF00BF, 0xFFFF0000};

    private Listener listener;
    private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wheel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lastW = -1;

    public ColorWheelView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        white.setColor(0xFFFFFFFF);
        shadow.setColor(0x263C5078);
    }

    public void setListener(Listener l) { listener = l; }

    @Override
    protected void onMeasure(int w, int h) {
        int s = Ui.dp(getContext(), 160);
        setMeasuredDimension(s, s);
    }

    private float u() { return getWidth() / 160f; }   // 150 wheel + 5 shadow margin each side

    @Override
    protected void onDraw(Canvas c) {
        float u = u(), cx = getWidth() / 2f, cy = getHeight() / 2f;
        if (lastW != getWidth()) {
            lastW = getWidth();
            SweepGradient sg = new SweepGradient(cx, cy, STOPS, null);
            Matrix m = new Matrix();
            m.setRotate(-90, cx, cy);
            sg.setLocalMatrix(m);
            wheel.setShader(sg);
            shadow.setMaskFilter(new BlurMaskFilter(Math.max(1, 4 * u), BlurMaskFilter.Blur.NORMAL));
        }
        c.drawCircle(cx, cy + 2 * u, 75 * u, shadow);
        c.drawCircle(cx, cy, 75 * u, white);
        c.drawCircle(cx, cy, 45 * u, wheel);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float dx = e.getX() - cx, dy = e.getY() - cy;
            if (Math.hypot(dx, dy) <= 75 * u() && listener != null) {
                double a = Math.toDegrees(Math.atan2(dx, -dy));
                if (a < 0) a += 360;
                listener.onColor(colorAt((float) a));
                performClick();
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private static int colorAt(float deg) {
        float pos = deg / 360f * (STOPS.length - 1);
        int i = Math.min(STOPS.length - 2, (int) pos);
        float f = pos - i;
        int a = STOPS[i], b = STOPS[i + 1];
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    /** Hue distance helper for picking the nearest colour key. */
    public static float hueDistance(int c1, int c2) {
        float[] h1 = new float[3], h2 = new float[3];
        Color.colorToHSV(c1, h1);
        Color.colorToHSV(c2, h2);
        if (h2[1] < 0.2f) return 1000f;             // whites/greys never match a hue tap
        float d = Math.abs(h1[0] - h2[0]);
        return Math.min(d, 360 - d);
    }
}
