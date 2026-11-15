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
    public static final String EXTRA_PREVIEW = "preview";

    private SavedRemote remote;
    private FrameLayout content;
    private TextView note;
    private MaterialToolbar toolbar;
    private boolean preview;
    private com.offlineremote.app.model.PreviewSession session;
    private android.view.GestureDetector gesture;

    // AC state
    private SmartIrSource.AcSpec acSpec;
    private String acMode, acFan;
    private int acTemp;
    private TextView tempText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_remote);
        toolbar = findViewById(R.id.toolbar);
        content = findViewById(R.id.content);
        note = findViewById(R.id.note);
        toolbar.setNavigationOnClickListener(v -> finish());

        preview = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);

        // swipe between sibling remotes (preview only)
        gesture = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                if (!preview || e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > 120 && Math.abs(vx) > 200) {
                    if (dx < 0) goTo(session.index + 1); else goTo(session.index - 1);
                    return true;
                }
                return false;
            }
        });

        if (preview) {
            session = App.get().preview;
            if (session == null) { finish(); return; }
            View bar = findViewById(R.id.previewBar);
            bar.setVisibility(View.VISIBLE);
            findViewById(R.id.btnSavePreview).setOnClickListener(v -> savePreview());
            loadCurrent();
        } else {
            String id = getIntent().getStringExtra(EXTRA_ID);
            remote = App.get().store().get(id);
            if (remote == null) { finish(); return; }
            showRemote(remote);
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (gesture != null) gesture.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void goTo(int idx) {
        if (session == null || idx < 0 || idx >= session.size() || idx == session.index) return;
        session.index = idx;
        loadCurrent();
    }

    private void loadCurrent() {
        toolbar.setTitle("טוען...");
        App.get().io().execute(() -> {
            final SavedRemote r = session.current();
            runOnUiThread(() -> showRemote(r));
        });
    }

    private void savePreview() {
        if (remote == null) return;
        App.get().store().add(remote);
        App.get().preview = null;
        Toast.makeText(this, "נשמר: " + remote.name, Toast.LENGTH_SHORT).show();
        android.content.Intent i = new android.content.Intent(this, MainActivity.class);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void showRemote(SavedRemote r) {
        remote = r;
        content.removeAllViews();
        String title = r.name;
        if (preview && session != null && session.size() > 1)
            title = r.name + "  (" + (session.index + 1) + "/" + session.size() + ")";
        toolbar.setTitle(title);

        if (r.isAc() && "smartir".equals(r.source)) {
            note.setText("SmartIR • אם לא עובד, נסה שלט אחר (החלקה) או למידה");
            buildAc();
        } else {
            note.setText("");
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
        sendBtn.setText("שלח מצב");

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
