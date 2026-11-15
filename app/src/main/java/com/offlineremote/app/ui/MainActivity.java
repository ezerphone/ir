package com.offlineremote.app.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.model.SavedRemote;
import com.offlineremote.app.usb.DongleManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private View statusDot;
    private TextView statusText;
    private Button btnConnect;
    private RecyclerView list;
    private TextView empty;
    private RemoteListAdapter adapter;

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DongleManager.ACTION_USB_PERMISSION.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted) tryConnect();
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
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        btnConnect = findViewById(R.id.btnConnect);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);

        list.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new RemoteListAdapter(r -> {
            Intent i = new Intent(this, RemoteActivity.class);
            i.putExtra(RemoteActivity.EXTRA_ID, r.id);
            startActivity(i);
        }, r -> confirmDelete(r));
        list.setAdapter(adapter);

        btnConnect.setOnClickListener(v -> tryConnect());
        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddDeviceActivity.class)));
        findViewById(R.id.btnLearn).setOnClickListener(v ->
                startActivity(new Intent(this, LearnActivity.class)));

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), this::doExport);
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::doImport);
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

    private UsbDevice findDongle() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (um == null) return null;
        java.util.HashMap<String, UsbDevice> list = um.getDeviceList();
        if (list != null) for (UsbDevice d : list.values()) if (DongleManager.matches(d)) return d;
        return null;
    }

    private void tryConnect() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice dev = findDongle();
        if (dev == null || um == null) { refreshStatus(); return; }
        if (!um.hasPermission(dev)) {
            statusText.setText(R.string.dongle_no_permission);
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 23) flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 0,
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
        boolean connected = App.get().raw().isOpen();
        UsbDevice dev = findDongle();
        if (connected) {
            statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
            statusText.setText(R.string.dongle_connected);
            btnConnect.setVisibility(View.GONE);
        } else if (dev == null) {
            statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.danger));
            statusText.setText(R.string.dongle_missing);
            btnConnect.setVisibility(View.GONE);
        } else {
            statusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.danger));
            statusText.setText(R.string.dongle_no_permission);
            btnConnect.setVisibility(View.VISIBLE);
        }
    }

    private void confirmDelete(SavedRemote r) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(r.name)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    App.get().store().remove(r.id);
                    refreshList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_diag) {
            startActivity(new Intent(this, DiagnosticsActivity.class));
            return true;
        } else if (id == R.id.action_export) {
            exportLauncher.launch("offline-ir-remotes.json");
            return true;
        } else if (id == R.id.action_import) {
            importLauncher.launch(new String[]{"application/json", "*/*"});
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doExport(Uri uri) {
        if (uri == null) return;
        App.get().io().execute(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                App.get().store().exportTo(os);
                toast(getString(R.string.export));
            } catch (Exception e) {
                toast("export failed: " + e.getMessage());
            }
        });
    }

    private void doImport(Uri uri) {
        if (uri == null) return;
        App.get().io().execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                int n = App.get().store().importFrom(is);
                toast("+" + n);
                runOnUiThread(this::refreshList);
            } catch (Exception e) {
                toast("import failed: " + e.getMessage());
            }
        });
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}
