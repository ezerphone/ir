package com.offlineremote.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.ir.IrSignal;
import com.offlineremote.app.model.Code;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.sources.SmartIrSource;

import java.util.ArrayList;
import java.util.List;

public class RemoteActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "remote_id";

    private SavedRemote remote;
    private FrameLayout content;
    private TextView note;

    // AC state
    private SmartIrSource.AcSpec acSpec;
    private String acMode, acFan;
    private int acTemp;
    private TextView tempText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_remote);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        content = findViewById(R.id.content);
        note = findViewById(R.id.note);

        String id = getIntent().getStringExtra(EXTRA_ID);
        remote = App.get().store().get(id);
        if (remote == null) { finish(); return; }

        toolbar.setTitle(remote.name);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (remote.isAc() && "smartir".equals(remote.source)) {
            note.setText("SmartIR • אם השליחה לא עובדת, השתמש בלמידה משלט פיזי");
            buildAc();
        } else {
            note.setText("local".equals(remote.source) ? "ZaZa" : "");
            buildButtons();
        }
    }

    // ---------------- AC ----------------

    private void buildAc() {
        View v = getLayoutInflater().inflate(R.layout.view_ac, content, false);
        content.addView(v);
        tempText = v.findViewById(R.id.tempText);
        final Spinner modeSpinner = v.findViewById(R.id.modeSpinner);
        final Spinner fanSpinner = v.findViewById(R.id.fanSpinner);
        Button tempMinus = v.findViewById(R.id.tempMinus);
        Button tempPlus = v.findViewById(R.id.tempPlus);
        Button powerBtn = v.findViewById(R.id.powerBtn);
        Button sendBtn = v.findViewById(R.id.sendBtn);
        sendBtn.setText(R.string.save);

        App.get().io().execute(() -> {
            SmartIrSource.Entry e = new SmartIrSource.Entry();
            e.file = remote.acFile;
            final SmartIrSource.AcSpec spec = App.get().smartIr().loadAc(e);
            runOnUiThread(() -> {
                acSpec = spec;
                if (spec == null) { Toast.makeText(this, "load failed", Toast.LENGTH_SHORT).show(); return; }
                acTemp = Math.max(spec.minT, Math.min(spec.maxT, (spec.minT + spec.maxT) / 2));
                tempText.setText(acTemp + "°");

                List<String> modes = spec.modes.isEmpty() ? listOf("cool", "heat") : spec.modes;
                List<String> fans = spec.fans.isEmpty() ? listOf("auto") : spec.fans;
                acMode = modes.get(0);
                acFan = fans.get(0);
                modeSpinner.setAdapter(spinner(modes));
                fanSpinner.setAdapter(spinner(fans));

                final boolean[] modeInit = {true};
                final boolean[] fanInit = {true};
                modeSpinner.setOnItemSelectedListener(new SimpleSel(pos -> {
                    acMode = modes.get(pos);
                    if (modeInit[0]) { modeInit[0] = false; return; } // ignore initial selection
                    sendState();
                }));
                fanSpinner.setOnItemSelectedListener(new SimpleSel(pos -> {
                    acFan = fans.get(pos);
                    if (fanInit[0]) { fanInit[0] = false; return; }
                    sendState();
                }));
            });
        });

        tempMinus.setOnClickListener(x -> { if (acSpec != null && acTemp > acSpec.minT) { acTemp--; tempText.setText(acTemp + "°"); sendState(); } });
        tempPlus.setOnClickListener(x -> { if (acSpec != null && acTemp < acSpec.maxT) { acTemp++; tempText.setText(acTemp + "°"); sendState(); } });
        powerBtn.setOnClickListener(x -> sendOff());
        sendBtn.setOnClickListener(x -> sendState());
    }

    private void sendState() {
        if (acSpec == null) return;
        final String mode = acMode, fan = acFan;
        final int temp = acTemp;
        App.get().io().execute(() -> {
            Code c = App.get().smartIr().acCode(acSpec, mode, fan, temp);
            sendCodeResult(c);
        });
    }

    private void sendOff() {
        if (acSpec == null) return;
        App.get().io().execute(() -> sendCodeResult(App.get().smartIr().acOff(acSpec)));
    }

    // ---------------- buttons ----------------

    private void buildButtons() {
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        SimpleAdapter adapter = new SimpleAdapter();
        rv.setAdapter(adapter);
        content.addView(rv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        List<SimpleAdapter.Row> rows = new ArrayList<>();
        for (IrKey k : remote.keys) {
            final Code c = k.code;
            rows.add(new SimpleAdapter.Row(k.label == null ? "" : k.label, null,
                    () -> App.get().io().execute(() -> sendCodeResult(c))));
        }
        adapter.submit(rows);
        if (remote.keys.isEmpty()) {
            Toast.makeText(this, "no keys", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- send ----------------

    private void sendCodeResult(Code c) {
        com.offlineremote.app.usb.RawDongle raw = App.get().raw();
        if (!raw.isOpen()) raw.open();
        boolean ok = false;
        if (c != null && raw.isOpen()) {
            byte[] nativeFrame = (c.kind == Code.Kind.NATIVE) ? c.nativeBytes : null;
            ok = raw.transmit(c.freq, nativeFrame, c.micros);
        }
        final boolean result = ok;
        final boolean connected = raw.isOpen();
        runOnUiThread(() -> {
            String msg = !connected ? getString(R.string.dongle_missing)
                    : (result ? getString(R.string.sent) : getString(R.string.send_failed));
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    // ---------------- helpers ----------------

    private ArrayAdapter<String> spinner(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private static List<String> listOf(String... s) {
        List<String> l = new ArrayList<>();
        for (String x : s) l.add(x);
        return l;
    }

    /** Minimal Spinner selection listener. */
    private static final class SimpleSel implements android.widget.AdapterView.OnItemSelectedListener {
        interface Sel { void at(int pos); }
        private final Sel sel;
        SimpleSel(Sel sel) { this.sel = sel; }
        @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { sel.at(pos); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
    }
}
