package com.offlineremote.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.icontrol.dev.IControlIRData;
import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.SavedRemote;

import java.util.ArrayList;
import java.util.List;

/**
 * Learn codes from a physical remote via the dongle's study mode. This is the guaranteed
 * offline path for any device (including ACs whose model isn't in the bundled DBs): point
 * the original remote at the dongle, press each button, and its exact native code is saved.
 */
public class LearnActivity extends AppCompatActivity {

    private EditText nameInput, keyLabel;
    private Spinner categorySpinner;
    private SimpleAdapter adapter;
    private android.widget.TextView status;
    private final List<IrKey> learned = new ArrayList<>();

    private static final String[] CAT_VALUES = {"ac", "tv", "stb", "fan", "other"};
    private static final String[] CAT_LABELS = {"מזגן", "טלוויזיה", "ממיר", "מאוורר", "אחר"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_learn);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        nameInput = findViewById(R.id.nameInput);
        keyLabel = findViewById(R.id.keyLabel);
        categorySpinner = findViewById(R.id.categorySpinner);
        status = findViewById(R.id.learnStatus);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CAT_LABELS);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(catAdapter);

        RecyclerView keys = findViewById(R.id.keys);
        keys.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter();
        keys.setAdapter(adapter);

        Button btnLearnKey = findViewById(R.id.btnLearnKey);
        Button btnSave = findViewById(R.id.btnSave);
        btnLearnKey.setOnClickListener(v -> learnOne());
        btnSave.setOnClickListener(v -> save());
    }

    /** Same open sequence the Diagnostics screen uses. */
    private boolean ensureRawOpen() {
        com.offlineremote.app.usb.RawDongle raw = App.get().raw();
        if (raw.isOpen()) return true;
        try { App.get().dongle().disconnect(); } catch (Throwable ignore) {}
        return raw.open();
    }

    private void setStatus(String s) { runOnUiThread(() -> status.setText(s)); }

    private void learnOne() {
        final String label = keyLabel.getText().toString().trim();
        if (TextUtils.isEmpty(label)) {
            Toast.makeText(this, "שם כפתור?", Toast.LENGTH_SHORT).show();
            return;
        }
        setStatus("⏺ מכוון את השלט המקורי לדונגל ולוחץ כפתור...");
        App.get().io().execute(() -> {
            if (!ensureRawOpen()) {
                com.offlineremote.app.util.ErrorLog.add(this, "LEARN open-failed (permission/attach)");
                setStatus("✗ הדונגל לא נפתח (הרשאה? מחובר?)");
                return;
            }
            java.util.List<String> lg = new java.util.ArrayList<>();
            byte[] frame = App.get().raw().learnLogged(12000, lg);
            // quiet log to file only — not shown here
            StringBuilder detail = new StringBuilder();
            for (String s : lg) detail.append(s).append(" | ");
            com.offlineremote.app.util.ErrorLog.add(this,
                    (frame == null ? "LEARN FAIL \"" : "LEARN OK \"") + label + "\"  " + detail);
            runOnUiThread(() -> {
                if (frame == null) {
                    status.setText("✗ לא נקלט אות — נסה שוב, כוון את השלט ישירות לדונגל");
                    return;
                }
                Code c = Code.nativeCode(38000, frame); // native learned frame
                learned.add(new IrKey(label, 0, c));
                keyLabel.setText("");
                status.setText("✓ נקלט \"" + label + "\"");
                refreshKeys();
            });
        });
    }

    private void refreshKeys() {
        List<SimpleAdapter.Row> rows = new ArrayList<>();
        for (IrKey k : learned) {
            rows.add(new SimpleAdapter.Row(k.label, "✓", null));
        }
        adapter.submit(rows);
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "שם השלט?", Toast.LENGTH_SHORT).show();
            return;
        }
        if (learned.isEmpty()) {
            Toast.makeText(this, "למד לפחות כפתור אחד", Toast.LENGTH_SHORT).show();
            return;
        }
        SavedRemote r = new SavedRemote();
        r.source = "learned";
        r.category = CAT_VALUES[categorySpinner.getSelectedItemPosition()];
        r.name = name;
        r.keys = new ArrayList<>(learned);
        App.get().store().add(r);
        Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show();
        finish();
    }
}
