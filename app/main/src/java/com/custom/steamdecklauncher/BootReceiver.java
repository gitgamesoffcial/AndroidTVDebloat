package com.custom.steamdeckphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;

public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "launcher_prefs";
    private static final String KEY_BOOT_TARGET = "boot_target";
    private static final String HOME_TARGET = "HOME";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String bootTarget = prefs.getString(KEY_BOOT_TARGET, HOME_TARGET);

        if (HOME_TARGET.equals(bootTarget)) {
            launchHome(context);
            return;
        }

        try {
            Intent tvIntent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(bootTarget));
            tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(tvIntent);
        } catch (Exception e) {
            launchHome(context);
        }
    }

    private void launchHome(Context context) {
        Intent homeIntent = new Intent(context, MainActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(homeIntent);
    }
}
