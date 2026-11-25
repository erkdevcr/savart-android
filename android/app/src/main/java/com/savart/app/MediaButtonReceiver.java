package com.savart.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives PendingIntent actions from the MediaStyle notification buttons
 * and forwards them to the app via a local broadcast or direct activity call.
 * The actual JS dispatch happens via MediaSessionCompat.Callback in MediaSessionPlugin.
 */
public class MediaButtonReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int action = intent.getIntExtra("action", -1);
        // Bring app to foreground so the Capacitor bridge is active
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra("mediaAction", action);
        context.startActivity(launch);
    }
}
