package com.offlineremote.app.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.usb.DongleManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Home: dongle status, the user's remotes as cards, add / learn. */
public class MainActivity extends BaseActivity {

    public static final String EXTRA_SAVED = "saved_name";

    private View statusDot;
    private TextView statusText;
    private RemoteListAdapter adapter;
    private TextView empty;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DongleManager.ACTION_USB_PERMISSION.equals(action)) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) tryConnect();
                else refreshStatus();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                tryConnect();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                App.get().raw().close();
                refreshStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        TopBar bar = topBar().title("השלטים שלי");
        bar.rotate(true);
        bar.addEnd(R.drawable.ic_plus, v -> startActivity(new Intent(this, AddDeviceActivity.class)));
        bar.addEnd(R.drawable.ic_more, v -> showMenu());

        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        empty = findViewById(R.id.empty);
        findViewById(R.id.statusLine).setOnClickListener(v -> tryConnect());

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new RemoteListAdapter(r -> {
            Intent i = new Intent(this, RemoteActivity.class);
            i.putExtra(RemoteActivity.EXTRA_ID, r.id);
            startActivity(i);
        }, this::cardMenu);
        list.setAdapter(adapter);

        findViewById(R.id.btnAdd).setOnClickListener(v -> startActivity(new Intent(this, AddDeviceActivity.class)));
        findViewById(R.id.btnLearn).setOnClickListener(v -> startActivity(new Intent(this, LearnActivity.class)));

        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::doExport);
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::doImport);
        showSaved(getIntent());
        DemoMode.handle(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showSaved(intent);
        DemoMode.handle(this, intent);
        applyRotation(false);
        refreshList();
    }

    private void showSaved(Intent i) {
        String name = i == null ? null : i.getStringExtra(EXTRA_SAVED);
        if (name != null) {
            i.removeExtra(EXTRA_SAVED);
            getWindow().getDecorView().post(() -> toast("נשמר: " + name));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(DongleManager.ACTION_USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        ContextCompat.registerReceiver(this, usbReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshList();
        tryConnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(usbReceiver); } catch (Exception ignore) {}
    }

    private void refreshList() {
        List<SavedRemote> all = App.get().store().all();
        adapter.submit(all);
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------------- dongle ----------------

    private UsbDevice findDongle() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (um == null) return null;
        for (UsbDevice d : um.getDeviceList().values()) if (DongleManager.matches(d)) return d;
        return null;
    }

    private void tryConnect() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice dev = findDongle();
        if (dev == null || um == null) { refreshStatus(); return; }
        if (!um.hasPermission(dev)) {
            setStatus(false, getString(R.string.dongle_no_permission));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                    new Intent(DongleManager.ACTION_USB_PERMISSION).setPackage(getPackageName()), flags);
            um.requestPermission(dev, pi);
            return;
        }
        App.get().io().execute(() -> {
            App.get().raw().open();
            runOnUiThread(this::refreshStatus);
        });
    }

    private void refreshStatus() {
        if (App.get().raw().isOpen()) setStatus(true, getString(R.string.dongle_connected));
        else if (findDongle() == null) setStatus(false, getString(R.string.dongle_missing));
        else setStatus(false, getString(R.string.dongle_no_permission) + " — לחץ לחיבור");
    }

    private void setStatus(boolean ok, String text) {
        statusDot.setBackgroundResource(ok ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
        statusText.setText(text);
    }

    // ---------------- menus ----------------

    private void showMenu() {
        String[] items = {"לוג שגיאות", "ייצוא קודים", "ייבוא קודים"};
        AlertDialog d = new AlertDialog.Builder(this).setItems(items, (dlg, which) -> {
            if (which == 0) startActivity(new Intent(this, ErrorLogActivity.class));
            else if (which == 1) exportLauncher.launch("offline-ir-remotes.json");
            else importLauncher.launch(new String[]{"application/json", "*/*"});
        }).create();
        d.show();
        rotateDialog(d);
    }

    private void cardMenu(SavedRemote r) {
        String[] items = {"שנה שם", "מחק"};
        AlertDialog d = new AlertDialog.Builder(this).setTitle(r.name).setItems(items, (dlg, which) -> {
            if (which == 0) rename(r);
            else confirmDelete(r);
        }).create();
        d.show();
        rotateDialog(d);
    }

    private void rename(SavedRemote r) {
        EditText in = new EditText(this);
        in.setText(r.name);
        in.setSelection(in.getText().length());
        int p = Ui.dp(this, 20);
        in.setPadding(p, p, p, p);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("שם השלט").setView(in)
                .setPositiveButton("שמור", (dlg, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) { r.name = n; App.get().store().update(r); refreshList(); }
                })
                .setNegativeButton("ביטול", null).create();
        d.show();
        rotateDialog(d);
    }

    private void confirmDelete(SavedRemote r) {
        AlertDialog d = new AlertDialog.Builder(this).setMessage("למחוק את \"" + r.name + "\"?")
                .setPositiveButton("מחק", (dlg, w) -> { App.get().store().remove(r.id); refreshList(); })
                .setNegativeButton("ביטול", null).create();
        d.show();
        rotateDialog(d);
    }

    // ---------------- export / import ----------------

    private void doExport(Uri uri) {
        if (uri == null) return;
        App.get().io().execute(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                App.get().store().exportTo(os);
                runOnUiThread(() -> toast("הקודים יוצאו"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("הייצוא נכשל"));
            }
        });
    }

    private void doImport(Uri uri) {
        if (uri == null) return;
        App.get().io().execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                int n = App.get().store().importFrom(is);
                runOnUiThread(() -> { toast("יובאו " + n + " שלטים"); refreshList(); });
            } catch (Exception e) {
                runOnUiThread(() -> toast("הייבוא נכשל"));
            }
        });
    }
}
