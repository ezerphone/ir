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
    private android.widget.EditText search;
    private List<SimpleAdapter.Row> fullRows = new ArrayList<>();
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
        search = findViewById(R.id.search);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { applyFilter(s.toString()); }
        });
        toolbar.setNavigationOnClickListener(v -> back());
        push(this::rootScreen);
    }

    private void present(String title, List<SimpleAdapter.Row> rows) {
        toolbar.setTitle(title);
        fullRows = (rows == null) ? new ArrayList<>() : rows;
        search.setText("");
        search.setVisibility(fullRows.size() > 6 ? View.VISIBLE : View.GONE);
        adapter.submit(fullRows);
    }

    private void applyFilter(String q) {
        if (q == null) q = "";
        q = q.trim().toLowerCase();
        if (q.isEmpty()) { adapter.submit(fullRows); return; }
        List<SimpleAdapter.Row> out = new ArrayList<>();
        for (SimpleAdapter.Row r : fullRows) {
            String hay = ((r.title == null ? "" : r.title) + " " + (r.subtitle == null ? "" : r.subtitle)).toLowerCase();
            if (hay.contains(q)) out.add(r);
        }
        adapter.submit(out);
    }

    private void openPreview(List<com.offlineremote.app.model.PreviewSession.Builder> builders, int index) {
        App.get().preview = new com.offlineremote.app.model.PreviewSession(builders, index);
        Intent i = new Intent(this, RemoteActivity.class);
        i.putExtra(RemoteActivity.EXTRA_PREVIEW, true);
        startActivity(i);
    }

    @Override public void onBackPressed() { back(); }

    private void back() {
        if (stack.size() > 1) { stack.pop(); stack.peek().run(); }
        else finish();
    }

    private void push(Runnable screen) { stack.push(screen); screen.run(); }

    private void setRows(String title, List<SimpleAdapter.Row> rows) {
        present(title, rows);
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
                present(title, rows);
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
        rows.add(new SimpleAdapter.Row("מנורה צבעונית (LED)",
                "מאגר Flipper-IRDB · 168 שלטים", () -> push(this::ledBrands)));
        setRows(getString(R.string.pick_category), rows);
    }

    private static String modelLabel(SmartIrSource.Entry e) {
        return e.models.isEmpty() ? e.file : join(e.models);
    }

    private void acManufacturers() {
        loadRows(getString(R.string.pick_manufacturer), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String m : App.get().smartIr().manufacturers("ac")) {
                final String man = m;
                rows.add(new SimpleAdapter.Row(com.offlineremote.app.util.HebrewNames.display(m),
                        null, () -> push(() -> acModels(man))));
            }
            return rows;
        });
    }

    private void acModels(String manufacturer) {
        loadRows(com.offlineremote.app.util.HebrewNames.only(manufacturer), () -> {
            final List<SmartIrSource.Entry> entries = App.get().smartIr().models("ac", manufacturer);
            final List<com.offlineremote.app.model.PreviewSession.Builder> builders = new ArrayList<>();
            for (SmartIrSource.Entry e : entries) {
                final SmartIrSource.Entry entry = e;
                builders.add(() -> {
                    SavedRemote r = new SavedRemote();
                    r.source = "smartir"; r.category = "ac";
                    r.name = com.offlineremote.app.util.HebrewNames.only(entry.manufacturer) + " · " + modelLabel(entry);
                    r.acFile = entry.file;
                    return r;
                });
            }
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                final int idx = i;
                rows.add(new SimpleAdapter.Row(modelLabel(entries.get(i)), entries.get(i).file,
                        () -> openPreview(builders, idx)));
            }
            return rows;
        });
    }

    private void tvManufacturers() {
        loadRows(getString(R.string.pick_manufacturer), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String m : App.get().smartIr().manufacturers("tv")) {
                final String man = m;
                rows.add(new SimpleAdapter.Row(com.offlineremote.app.util.HebrewNames.display(m),
                        null, () -> push(() -> tvModels(man))));
            }
            return rows;
        });
    }

    private void tvModels(String manufacturer) {
        loadRows(com.offlineremote.app.util.HebrewNames.only(manufacturer), () -> {
            final List<SmartIrSource.Entry> entries = App.get().smartIr().models("tv", manufacturer);
            final List<com.offlineremote.app.model.PreviewSession.Builder> builders = new ArrayList<>();
            for (SmartIrSource.Entry e : entries) {
                final SmartIrSource.Entry entry = e;
                builders.add(() -> {
                    SavedRemote r = new SavedRemote();
                    r.source = "smartir"; r.category = "tv";
                    r.name = com.offlineremote.app.util.HebrewNames.only(entry.manufacturer) + " · " + modelLabel(entry);
                    r.keys = App.get().smartIr().tvKeys(entry);
                    return r;
                });
            }
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                final int idx = i;
                rows.add(new SimpleAdapter.Row(modelLabel(entries.get(i)), entries.get(i).file,
                        () -> openPreview(builders, idx)));
            }
            return rows;
        });
    }

    private void localTypes() {
        List<SimpleAdapter.Row> rows = new ArrayList<>();
        for (com.offlineremote.app.sources.LocalIrdbSource.Cat c : com.offlineremote.app.sources.LocalIrdbSource.CATS) {
            final com.offlineremote.app.sources.LocalIrdbSource.Cat cat = c;
            rows.add(new SimpleAdapter.Row(cat.label, null, () -> push(() -> localBrands(cat))));
        }
        setRows(getString(R.string.pick_category), rows);
    }

    private void localBrands(com.offlineremote.app.sources.LocalIrdbSource.Cat cat) {
        loadRows(getString(R.string.pick_brand), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (LocalIrDb.DbBrand brand : App.get().localDb().brands(cat.id)) {
                final LocalIrDb.DbBrand b = brand;
                rows.add(new SimpleAdapter.Row(b.display(), null,
                        () -> push(() -> localRemotes(cat, b))));
            }
            return rows;
        });
    }

    private void localRemotes(com.offlineremote.app.sources.LocalIrdbSource.Cat cat, LocalIrDb.DbBrand brand) {
        loadRows(getString(R.string.pick_remote), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            List<LocalIrDb.DbRemote> remotes = App.get().localDb().remotes(cat.id, brand.id());
            int i = 1;
            for (LocalIrDb.DbRemote r : remotes) {
                final LocalIrDb.DbRemote rem = r;
                String model = (r.model() == null || r.model().isEmpty()) ? ("#" + i) : r.model();
                final String name = brand.display() + " " + model;
                rows.add(new SimpleAdapter.Row(name, r.id(),
                        () -> saveLocal(cat, brand, rem, name)));
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

    private void ledBrands() {
        loadRows(getString(R.string.pick_manufacturer), () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String b : App.get().led().brands()) {
                final String brand = b;
                rows.add(new SimpleAdapter.Row(brand, null, () -> push(() -> ledModels(brand))));
            }
            return rows;
        });
    }

    private void ledModels(String brand) {
        loadRows(brand, () -> {
            final List<com.offlineremote.app.sources.LedLibrarySource.Dev> devs = App.get().led().models(brand);
            final List<com.offlineremote.app.model.PreviewSession.Builder> builders = new ArrayList<>();
            for (com.offlineremote.app.sources.LedLibrarySource.Dev d : devs) {
                final com.offlineremote.app.sources.LedLibrarySource.Dev dev = d;
                builders.add(() -> {
                    SavedRemote r = new SavedRemote();
                    r.source = "led"; r.category = "led";
                    r.name = dev.model;
                    r.keys = App.get().led().keys(dev);
                    return r;
                });
            }
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (int i = 0; i < devs.size(); i++) {
                final int idx = i;
                rows.add(new SimpleAdapter.Row(devs.get(i).model, devs.get(i).keys + " כפתורים",
                        () -> openPreview(builders, idx)));
            }
            return rows;
        });
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

    private void saveLocal(com.offlineremote.app.sources.LocalIrdbSource.Cat cat, LocalIrDb.DbBrand brand,
                           LocalIrDb.DbRemote remote, String name) {
        progress.setVisibility(View.VISIBLE);
        App.get().io().execute(() -> {
            LocalIrDb.DbRemote full = App.get().localDb().fetch(remote.id(), brand.id());
            if (full == null) full = remote;
            SavedRemote r = App.get().localDb().toSavedRemote(full, cat.category, name);
            App.get().store().add(r);
            runOnUiThread(() -> { progress.setVisibility(View.GONE); open(r); });
        });
    }

    private void open(SavedRemote r) {
        Intent i = new Intent(this, RemoteActivity.class);
        i.putExtra(RemoteActivity.EXTRA_ID, r.id);
        startActivity(i);
        finish();
    }
}
