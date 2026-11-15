package com.offlineremote.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.icontrol.dev.IControlIRData;
import com.offlineremote.app.App;
import com.offlineremote.app.R;
import com.offlineremote.app.usb.DongleManager;
import com.tiqiaa.local.LocalIrDb;

/**
 * On-device diagnostics: native-lib load, embedded DB contents, dongle connection, and a
 * learn test. Everything is wrapped in try/catch so failures are reported, not swallowed.
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private TextView out;
    private final StringBuilder sb = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_diag);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        out = findViewById(R.id.out);
        findViewById(R.id.btnRun).setOnClickListener(v -> run());
        findViewById(R.id.btnLearn).setOnClickListener(v -> learnTest());
        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("diag", sb.toString()));
            Toast.makeText(this, "הועתק", Toast.LENGTH_SHORT).show();
        });
        run();
    }

    private void line(String s) {
        sb.append(s).append('\n');
        runOnUiThread(() -> out.setText(sb.toString()));
    }

    private void run() {
        sb.setLength(0);
        App.get().io().execute(() -> {
            line("=== DEVICE ===");
            line("model: " + Build.MANUFACTURER + " " + Build.MODEL);
            line("android: API " + Build.VERSION.SDK_INT);
            line("abis: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS));

            line("\n=== NATIVE LIBS ===");
            loadTest("tiqiaautil");
            loadTest("tiqiaadev");
            loadTest("localirdb");
            line("LocalIrDb.loadError: " + LocalIrDb.loadError);

            line("\n=== LOCAL DB (ZaZa) ===");
            try {
                LocalIrDb db = App.get().localDb().raw();
                line("irdbOpen: " + db.lastOpen);
                line("getAllMachineTypeInfo: " + db.machineTypes().length);
                String[] names = {"TV","AC","Fan","Projector","STB","DVD/AV","Camera",
                        "IrSwitch","Amp","IPTV","OTT","WaterHeater","AirCleaner"};
                for (int t = 1; t <= 13; t++) {
                    int bc;
                    try { bc = db.brands(t).length; } catch (Throwable e) { bc = -1; }
                    int mask;
                    try { mask = db.acModeMask(t); } catch (Throwable e) { mask = -1; }
                    line("  type " + t + " (" + names[t - 1] + "): brands=" + bc + " acMask=" + mask);
                }
            } catch (Throwable t) {
                line("DB ERROR: " + t);
            }

            line("\n=== DONGLE ===");
            try {
                DongleManager d = App.get().dongle();
                UsbDevice dev = d.findDevice();
                if (dev == null) {
                    line("findDevice: null (לא מחובר / לא מזוהה)");
                    line("hint: ודא שהדונגל מחובר ושאישרת גישת USB");
                } else {
                    line(String.format("device: vid=0x%04X pid=0x%04X name=%s",
                            dev.getVendorId(), dev.getProductId(), dev.getDeviceName()));
                    line("interfaceCount: " + dev.getInterfaceCount());
                    if (dev.getInterfaceCount() > 0) {
                        UsbInterface in0 = dev.getInterface(0);
                        line("iface0 endpoints: " + in0.getEndpointCount());
                        for (int i = 0; i < in0.getEndpointCount(); i++) {
                            UsbEndpoint e = in0.getEndpoint(i);
                            line(String.format("  ep%d dir=0x%02X type=%d addr=0x%02X",
                                    i, e.getDirection(), e.getType(), e.getAddress()));
                        }
                    }
                    line("hasPermission: " + d.hasPermission(dev));
                    if (!d.hasPermission(dev)) {
                        line("-> requesting permission...");
                        d.requestPermission(dev);
                    } else {
                        boolean ok = d.connect(dev);
                        line("connect(): " + ok + "  isConnected=" + d.isConnected());
                    }
                }
            } catch (Throwable t) {
                line("DONGLE ERROR: " + t);
            }
            line("\n--- done ---");
        });
    }

    private void loadTest(String name) {
        try {
            System.loadLibrary(name);
            line("  " + name + ": OK");
        } catch (Throwable t) {
            line("  " + name + ": FAIL " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    private void learnTest() {
        App.get().io().execute(() -> {
            line("\n=== LEARN TEST (10s) ===");
            try {
                DongleManager d = App.get().dongle();
                if (!d.isConnected()) {
                    UsbDevice dev = d.findDevice();
                    if (dev != null && d.hasPermission(dev)) d.connect(dev);
                }
                if (!d.isConnected()) { line("not connected — cannot learn"); return; }
                line("point remote at dongle, press a button NOW...");
                IControlIRData got = d.learn(10000);
                if (got == null) {
                    line("learn: null (לא נקלט אות)");
                } else {
                    byte[] data = got.a();
                    line("learn OK: type=" + got.f() + " freq=" + got.b()
                            + " dataLen=" + (data == null ? 0 : data.length));
                }
            } catch (Throwable t) {
                line("LEARN ERROR: " + t);
            }
        });
    }
}
