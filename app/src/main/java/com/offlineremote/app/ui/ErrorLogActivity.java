package com.offlineremote.app.ui;

import android.os.Bundle;
import android.widget.TextView;

import com.offlineremote.app.R;
import com.offlineremote.app.util.ErrorLog;

/** Quiet place to check whether captures and sends worked. */
public class ErrorLogActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_errorlog);
        TopBar bar = topBar().title("לוג שגיאות");
        bar.back(true);
        bar.rotate(false);
        TextView body = findViewById(R.id.logText);
        body.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
        String log = ErrorLog.read(this);
        body.setText(log.isEmpty() ? "אין רשומות עדיין." : log);
        findViewById(R.id.btnClear).setOnClickListener(v -> { ErrorLog.clear(this); body.setText("אין רשומות עדיין."); });
    }
}
