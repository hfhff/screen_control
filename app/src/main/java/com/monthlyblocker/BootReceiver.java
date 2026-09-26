package com.monthlyblocker;

import android.content.*;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean enabled = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
                .getBoolean(BlockerService.PREF_RUNNING, false);
        if (!enabled || !Settings.canDrawOverlays(context)) return;
        context.startForegroundService(new Intent(context, BlockerService.class));
    }
}
