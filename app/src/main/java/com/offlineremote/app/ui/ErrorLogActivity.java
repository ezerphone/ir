package com.offlineremote.app.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.offlineremote.app.util.ErrorLog;

/** Minimal, read-only view of the quiet error log. */
public class ErrorLogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("לוג שגיאות");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, pad);

        final TextView body = new TextView(this);
        body.setTextSize(12);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        String log = ErrorLog.read(this);
        body.setText(log.isEmpty() ? "אין רשומות עדיין." : log);

        ScrollView sv = new ScrollView(this);
        sv.addView(body);

        Button clear = new Button(this);
        clear.setText("נקה לוג");
        clear.setOnClickListener(v -> { ErrorLog.clear(this); body.setText("אין רשומות עדיין."); });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(title);
        root.addView(sv, lp);
        root.addView(clear);
        root.setGravity(Gravity.TOP);
        setContentView(root);
    }
}
