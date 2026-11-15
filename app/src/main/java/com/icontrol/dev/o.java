package com.icontrol.dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/** registerReceiver helper (stands in for ZaZa's com.icontrol.dev.o). */
public final class o {
    private o() {}

    public static Intent m(Context c, BroadcastReceiver r, IntentFilter f, boolean exported) {
        if (Build.VERSION.SDK_INT >= 33) {
            return c.registerReceiver(r, f, exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED);
        }
        return c.registerReceiver(r, f);
    }
}
