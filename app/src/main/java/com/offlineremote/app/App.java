package com.offlineremote.app;

import android.app.Application;

import com.offlineremote.app.ir.TiqiaaTx;
import com.offlineremote.app.sources.LocalIrdbSource;
import com.offlineremote.app.sources.SmartIrSource;
import com.offlineremote.app.store.RemoteStore;
import com.offlineremote.app.usb.DongleManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Holds the shared, process-wide services. */
public final class App extends Application {

    private static App instance;

    private DongleManager dongle;
    private TiqiaaTx tx;
    private RemoteStore store;
    private SmartIrSource smartIr;
    private LocalIrdbSource localDb; // created lazily (loads native lib)
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public static App get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        dongle = new DongleManager(this);
        tx = new TiqiaaTx(this, dongle);
        store = new RemoteStore(this);
        smartIr = new SmartIrSource(this);
    }

    public DongleManager dongle() { return dongle; }
    public TiqiaaTx tx() { return tx; }
    public RemoteStore store() { return store; }
    public SmartIrSource smartIr() { return smartIr; }
    public ExecutorService io() { return io; }

    /** Lazy: touching this loads liblocalirdb.so. */
    public synchronized LocalIrdbSource localDb() {
        if (localDb == null) localDb = new LocalIrdbSource(this);
        return localDb;
    }
}
