package com.offlineremote.app.ui;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.offlineremote.app.R;

/** White top bar from the mockup: centred title, small grey line icons on either side. */
public final class TopBar {
    private final BaseActivity a;
    private final LinearLayout start, end;
    private final TextView title;

    TopBar(BaseActivity a, View root) {
        this.a = a;
        start = root.findViewById(R.id.tbStart);
        end = root.findViewById(R.id.tbEnd);
        title = root.findViewById(R.id.tbTitle);
    }

    public TopBar title(CharSequence t) { title.setText(t); return this; }

    private ImageView make(int icon, View.OnClickListener l) {
        ImageView v = new ImageView(a);
        v.setImageResource(icon);
        Ui.tint(v, Ui.color(a, R.color.sub));
        int p = Ui.dp(a, 9);
        v.setPadding(p, p, p, p);
        v.setBackgroundResource(R.drawable.bg_icon_btn);
        v.setOnClickListener(l);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38)));
        return v;
    }

    public ImageView addStart(int icon, View.OnClickListener l) { ImageView v = make(icon, l); start.addView(v); return v; }
    public ImageView addEnd(int icon, View.OnClickListener l) { ImageView v = make(icon, l); end.addView(v); return v; }

    /** Back chevron: at the start (right, pointing right) or at the end (left, pointing left). */
    public TopBar back(boolean atStart) {
        if (atStart) addStart(R.drawable.ic_chr, v -> a.finish());
        else addEnd(R.drawable.ic_chl, v -> a.finish());
        return this;
    }

    public TopBar rotate(boolean atStart) {
        if (atStart) addStart(R.drawable.ic_rotate, v -> a.toggleRotation());
        else addEnd(R.drawable.ic_rotate, v -> a.toggleRotation());
        return this;
    }
}
