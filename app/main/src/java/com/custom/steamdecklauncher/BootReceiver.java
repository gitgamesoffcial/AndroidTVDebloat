package com.custom.steamdecklauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
            String bootTarget = prefs.getString("boot_target", "HOME");

            if (!"HOME".equals(bootTarget)) {
                try {
                    Intent tvIntent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(bootTarget));
                    tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(tvIntent);
                } catch (Exception e) {
                    launchHome(context);
                }
            } else {
                launchHome(context);
            }
        }
    }

    private void launchHome(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}
