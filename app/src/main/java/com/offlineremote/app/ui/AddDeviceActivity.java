package com.offlineremote.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.IrKey;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.sources.LocalIrdbSource;
import com.offlineremote.app.sources.SmartIrSource;
import com.tiqiaa.local.LocalIrDb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Guided add-device flow: category -> brand/manufacturer -> model, driven by a back stack. */
public class AddDeviceActivity extends AppCompatActivity {

    private interface Work<T> { T run(); }

    private MaterialToolbar toolbar;
    private RecyclerView list;
    private ProgressBar progress;
    private SimpleAdapter adapter;
    private final Deque<Runnable> stack = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        toolbar = findViewById(R.id.toolbar);
        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter();
        list.setAdapter(adapter);
        toolbar.setNavigationOnClickListener(v -> back());
        push(this::rootScreen);
    }

    @Override public void onBackPressed() { back(); }

    private void back() {
        if (stack.size() > 1) { stack.pop(); stack.peek().run(); }
        else finish();
    }

    private void push(Runnable screen) { stack.push(screen); screen.run(); }

    private void setRows(String title, List<SimpleAdapter.Row> rows) {
        toolbar.setTitle(title);
        adapter.submit(rows);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /** Run heavy work off the UI thread, then build rows on the UI thread. */
    private void loadRows(String title, Work<List<SimpleAdapter.Row>> work) {
        progress.setVisibility(View.VISIBLE);
        toolbar.setTitle(title);
        App.get().io().execute(() -> {
            final List<SimpleAdapter.Row> rows = work.run();
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                adapter.submit(rows == null ? new ArrayList<>() : rows);
            });
        });
    }

    // ---------------- screens ----------------

    private void rootScreen() {
        List<SimpleAdapter.Row> rows = new ArrayList<>();
        rows.add(new SimpleAdapter.Row(getString(R.string.category_ac),
                getString(R.string.source_smartir), () -> push(this::acManufacturers)));
        rows.add(new SimpleAdapter.Row(getString(R.string.category_tv),
                getString(R.string.source_smartir), () -> push(this::tvManufacturers)));
        rows.add(new SimpleAdapter.Row(getString(R.string.source_local),
                "TV / ממיר / ...", () -> push(this::localTypes)));
        setRows(getString(R.string.pick_category), rows);
    }

    private void acManufacturers() {
        loadRows(getString(R.string.pick_manufacturer), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String m : App.get().smartIr().manufacturers("ac")) {
                final String man = m;
                rows.add(new SimpleAdapter.Row(man, null, () -> push(() -> acModels(man))));
            }
            return rows;
        });
    }

    private void acModels(String manufacturer) {
        loadRows(getString(R.string.pick_model), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (SmartIrSource.Entry e : App.get().smartIr().models("ac", manufacturer)) {
                final SmartIrSource.Entry entry = e;
                String sub = e.models.isEmpty() ? e.file : join(e.models);
                rows.add(new SimpleAdapter.Row(entry.manufacturer, sub, () -> saveAc(entry)));
            }
            return rows;
        });
    }

    private void tvManufacturers() {
        loadRows(getString(R.string.pick_manufacturer), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String m : App.get().smartIr().manufacturers("tv")) {
                final String man = m;
                rows.add(new SimpleAdapter.Row(man, null, () -> push(() -> tvModels(man))));
            }
            return rows;
        });
    }

    private void tvModels(String manufacturer) {
        loadRows(getString(R.string.pick_model), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (SmartIrSource.Entry e : App.get().smartIr().models("tv", manufacturer)) {
                final SmartIrSource.Entry entry = e;
                String sub = e.models.isEmpty() ? e.file : join(e.models);
                rows.add(new SimpleAdapter.Row(entry.manufacturer, sub, () -> saveTv(entry)));
            }
            return rows;
        });
    }

    private void localTypes() {
        loadRows(getString(R.string.pick_category), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (LocalIrDb.MachineTypeInfo t : App.get().localDb().types()) {
                final LocalIrDb.MachineTypeInfo type = t;
                rows.add(new SimpleAdapter.Row(type.display(), "type " + type.type(),
                        () -> push(() -> localBrands(type))));
            }
            return rows;
        });
    }

    private void localBrands(LocalIrDb.MachineTypeInfo type) {
        loadRows(getString(R.string.pick_brand), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (LocalIrDb.DbBrand brand : App.get().localDb().brands(type.type())) {
                final LocalIrDb.DbBrand b = brand;
                rows.add(new SimpleAdapter.Row(b.display(), null,
                        () -> push(() -> localRemotes(type, b))));
            }
            return rows;
        });
    }

    private void localRemotes(LocalIrDb.MachineTypeInfo type, LocalIrDb.DbBrand brand) {
        loadRows(getString(R.string.pick_remote), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            List<LocalIrDb.DbRemote> remotes = App.get().localDb().remotesForBrand(brand.id());
            int i = 1;
            for (LocalIrDb.DbRemote r : remotes) {
                final LocalIrDb.DbRemote rem = r;
                String model = (r.model() == null || r.model().isEmpty()) ? ("#" + i) : r.model();
                final String name = brand.display() + " " + model;
                rows.add(new SimpleAdapter.Row(name, r.id(),
                        () -> saveLocal(type, brand, rem, name)));
                i++;
            }
            return rows;
        });
    }

    // ---------------- save actions ----------------

    private void saveAc(SmartIrSource.Entry e) {
        SavedRemote r = new SavedRemote();
        r.source = "smartir";
        r.category = "ac";
        r.acFile = e.file;
        r.name = e.label();
        App.get().store().add(r);
        open(r);
    }

    private void saveTv(SmartIrSource.Entry e) {
        progress.setVisibility(View.VISIBLE);
        App.get().io().execute(() -> {
            List<IrKey> keys = App.get().smartIr().tvKeys(e);
            SavedRemote r = new SavedRemote();
            r.source = "smartir";
            r.category = "tv";
            r.name = e.label();
            r.keys = keys;
            App.get().store().add(r);
            runOnUiThread(() -> { progress.setVisibility(View.GONE); open(r); });
        });
    }

    private void saveLocal(LocalIrDb.MachineTypeInfo type, LocalIrDb.DbBrand brand,
                           LocalIrDb.DbRemote remote, String name) {
        progress.setVisibility(View.VISIBLE);
        App.get().io().execute(() -> {
            LocalIrDb.DbRemote full = App.get().localDb().fetch(remote.id(), brand.id());
            if (full == null) full = remote;
            SavedRemote r = App.get().localDb().toSavedRemote(full, catFor(type), name);
            App.get().store().add(r);
            runOnUiThread(() -> { progress.setVisibility(View.GONE); open(r); });
        });
    }

    private static String catFor(LocalIrDb.MachineTypeInfo t) {
        String s = (t.display() + " " + t.en()).toLowerCase();
        if (s.contains("tv") || s.contains("电视")) return "tv";
        if (s.contains("air") || s.contains("空调") || s.contains("ac")) return "ac";
        if (s.contains("box") || s.contains("盒")) return "stb";
        if (s.contains("fan") || s.contains("风")) return "fan";
        return "other";
    }

    private void open(SavedRemote r) {
        Intent i = new Intent(this, RemoteActivity.class);
        i.putExtra(RemoteActivity.EXTRA_ID, r.id);
        startActivity(i);
        finish();
    }
}
