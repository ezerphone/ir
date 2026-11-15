package com.offlineremote.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.PreviewSession;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.sources.FlipperLibrary;
import com.offlineremote.app.sources.SmartIrSource;
import com.offlineremote.app.util.HebrewNames;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Add a device: מזגן / טלוויזיה / מנורה / מכשירים נוספים -> brand -> model -> matching
 * screen (הקודם / עבד! / הבא). Nothing is saved until "עבד!".
 */
public class AddDeviceActivity extends BaseActivity {

    private interface Work<T> { T run(); }

    private TopBar bar;
    private ProgressBar progress;
    private SimpleAdapter adapter;
    private EditText search;
    private View searchBox;
    private List<SimpleAdapter.Row> fullRows = new ArrayList<>();
    private final Deque<Runnable> stack = new ArrayDeque<>();
    private int loadGen;   // ignores results of loads the user already navigated away from

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        bar = topBar();
        bar.addStart(R.drawable.ic_chr, v -> back());
        bar.rotate(false);
        progress = findViewById(R.id.progress);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleAdapter();
        list.setAdapter(adapter);
        searchBox = findViewById(R.id.searchBox);
        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(s.toString()); }
        });
        push(this::rootScreen);
        String demo = getIntent().getStringExtra(DemoMode.EXTRA);
        if ("more".equals(demo)) push(this::moreCategories);
        else if ("brands".equals(demo)) push(this::acBrands);
        else if ("match_ac".equals(demo)) App.get().io().execute(() -> {
            List<PreviewSession.Builder> bl = acBuilders("AUX");
            runOnUiThread(() -> { if (!bl.isEmpty()) openMatch(bl, 0, "AUX", "ac", "מזגן"); });
        });
        else if ("match_led".equals(demo) || "match_tv".equals(demo)) {
            final String slug = "match_led".equals(demo) ? "led" : "tv";
            final String brand = "led".equals(slug) ? "Gako" : "Samsung";
            App.get().io().execute(() -> {
                FlipperLibrary.Cat c = cat(slug);
                List<PreviewSession.Builder> bl = c == null ? new ArrayList<>() : libBuilders(c, brand);
                runOnUiThread(() -> { if (!bl.isEmpty()) openMatch(bl, 0, HebrewNames.only(brand), slug, Icons.noun(slug, c.he)); });
            });
        }
    }

    @Override
    public void onBackPressed() { back(); }

    private void back() {
        if (stack.size() > 1) { stack.pop(); stack.peek().run(); }
        else finish();
    }

    private void push(Runnable screen) { stack.push(screen); screen.run(); }

    private void present(String title, List<SimpleAdapter.Row> rows) {
        bar.title(title);
        fullRows = rows == null ? new ArrayList<>() : rows;
        search.setText("");
        searchBox.setVisibility(fullRows.size() > 6 ? View.VISIBLE : View.GONE);
        adapter.submit(fullRows);
    }

    private void applyFilter(String q) {
        q = q == null ? "" : q.trim().toLowerCase();
        if (q.isEmpty()) { adapter.submit(fullRows); return; }
        List<SimpleAdapter.Row> out = new ArrayList<>();
        for (SimpleAdapter.Row r : fullRows) {
            String hay = ((r.title == null ? "" : r.title) + " " + (r.subtitle == null ? "" : r.subtitle)).toLowerCase();
            if (hay.contains(q)) out.add(r);
        }
        adapter.submit(out);
    }

    private void loadRows(String title, Work<List<SimpleAdapter.Row>> work) {
        progress.setVisibility(View.VISIBLE);
        bar.title(title);
        adapter.submit(new ArrayList<>());
        final int gen = ++loadGen;
        App.get().io().execute(() -> {
            final List<SimpleAdapter.Row> rows = work.run();
            runOnUiThread(() -> {
                if (gen != loadGen || isFinishing()) return;
                progress.setVisibility(View.GONE);
                present(title, rows);
            });
        });
    }

    private static FlipperLibrary.Cat cat(String slug) {
        for (FlipperLibrary.Cat c : App.get().lib().categories()) if (c.slug.equals(slug)) return c;
        return null;
    }

    // ---------------- screens ----------------

    private void rootScreen() {
        loadRows("הוסף מכשיר", () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            rows.add(new SimpleAdapter.Row("מזגן",
                    "SmartIR · " + App.get().smartIr().manufacturers("ac").size() + " יצרנים",
                    () -> push(this::acBrands)).icon(R.drawable.ic_snow));
            FlipperLibrary.Cat tv = cat("tv"), led = cat("led");
            if (tv != null) rows.add(new SimpleAdapter.Row("טלוויזיה", tv.count + " דגמים",
                    () -> push(() -> libBrands(tv))).icon(R.drawable.ic_tv));
            if (led != null) rows.add(new SimpleAdapter.Row("מנורה (LED)", led.count + " שלטים",
                    () -> push(() -> libBrands(led))).icon(R.drawable.ic_bulb));
            int cats = 0, models = 0;
            for (FlipperLibrary.Cat c : App.get().lib().categories()) {
                if (c.slug.equals("tv") || c.slug.equals("led")) continue;
                cats++; models += c.count;
            }
            rows.add(new SimpleAdapter.Row("מכשירים נוספים", cats + " סוגים · " + models + " דגמים",
                    () -> push(this::moreCategories)).icon(R.drawable.ic_grid));
            return rows;
        });
    }

    private void moreCategories() {
        loadRows("מכשירים נוספים", () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (FlipperLibrary.Cat c : App.get().lib().categories()) {
                if (c.slug.equals("tv") || c.slug.equals("led")) continue;
                final FlipperLibrary.Cat cat = c;
                rows.add(new SimpleAdapter.Row(c.he, c.count + " דגמים", () -> push(() -> libBrands(cat)))
                        .icon(Icons.forCategory(c.slug)));
            }
            return rows;
        });
    }

    private static String sub(String brand) {
        String he = HebrewNames.only(brand);
        return he.equals(brand.trim()) ? null : brand.trim();
    }

    private void acBrands() {
        loadRows("בחר יצרן", () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String m : App.get().smartIr().manufacturers("ac")) {
                final String man = m;
                rows.add(new SimpleAdapter.Row(HebrewNames.only(m), sub(m), () -> push(() -> acModels(man))));
            }
            return rows;
        });
    }

    private static String modelLabel(SmartIrSource.Entry e) {
        if (e.models.isEmpty()) return e.file;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.models.size(); i++) { if (i > 0) sb.append(", "); sb.append(e.models.get(i)); }
        return sb.toString();
    }

    private List<PreviewSession.Builder> acBuilders(String manufacturer) {
        final List<PreviewSession.Builder> builders = new ArrayList<>();
        for (SmartIrSource.Entry e : App.get().smartIr().models("ac", manufacturer)) {
            final SmartIrSource.Entry entry = e;
            builders.add(() -> {
                SavedRemote r = new SavedRemote();
                r.source = "smartir"; r.category = "ac";
                r.name = "מזגן";
                r.acFile = entry.file;
                return r;
            });
        }
        return builders;
    }

    private List<PreviewSession.Builder> libBuilders(FlipperLibrary.Cat cat, String brand) {
        final List<PreviewSession.Builder> builders = new ArrayList<>();
        for (FlipperLibrary.Dev d : App.get().lib().models(cat.slug, brand)) {
            final FlipperLibrary.Dev dev = d;
            builders.add(() -> {
                SavedRemote r = new SavedRemote();
                r.source = "lib"; r.category = cat.slug;
                r.name = Icons.noun(cat.slug, cat.he);
                r.keys = App.get().lib().keys(dev);
                return r;
            });
        }
        return builders;
    }

    private void acModels(String manufacturer) {
        final String brandHe = HebrewNames.only(manufacturer);
        loadRows(brandHe, () -> {
            final List<SmartIrSource.Entry> entries = App.get().smartIr().models("ac", manufacturer);
            final List<PreviewSession.Builder> builders = acBuilders(manufacturer);
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                final int idx = i;
                rows.add(new SimpleAdapter.Row(modelLabel(entries.get(i)), null,
                        () -> openMatch(builders, idx, brandHe, "ac", "מזגן")));
            }
            return rows;
        });
    }

    private void libBrands(FlipperLibrary.Cat cat) {
        loadRows(cat.he, () -> {
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (String b : App.get().lib().brands(cat.slug)) {
                final String brand = b;
                rows.add(new SimpleAdapter.Row(HebrewNames.only(brand), sub(brand), () -> push(() -> libModels(cat, brand))));
            }
            return rows;
        });
    }

    private void libModels(FlipperLibrary.Cat cat, String brand) {
        final String brandHe = HebrewNames.only(brand);
        loadRows(brandHe, () -> {
            final List<FlipperLibrary.Dev> devs = App.get().lib().models(cat.slug, brand);
            final List<PreviewSession.Builder> builders = libBuilders(cat, brand);
            List<SimpleAdapter.Row> rows = new ArrayList<>();
            for (int i = 0; i < devs.size(); i++) {
                final int idx = i;
                rows.add(new SimpleAdapter.Row(devs.get(i).model, devs.get(i).keys + " כפתורים",
                        () -> openMatch(builders, idx, brandHe, cat.slug, Icons.noun(cat.slug, cat.he))));
            }
            return rows;
        });
    }

    private void openMatch(List<PreviewSession.Builder> builders, int index, String brandHe, String slug, String noun) {
        PreviewSession s = new PreviewSession(builders, index);
        s.brand = brandHe;
        s.category = slug;
        s.categoryHe = noun;
        s.target = Icons.target(slug);
        App.get().preview = s;
        Intent i = new Intent(this, RemoteActivity.class);
        i.putExtra(RemoteActivity.EXTRA_PREVIEW, true);
        startActivity(i);
    }
}
