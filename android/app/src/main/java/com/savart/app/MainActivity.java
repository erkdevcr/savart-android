package com.savart.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaSessionPlugin.class);
        super.onCreate(savedInstanceState);
        // Prevent gray flash on unlock — set WebView background to app's dark color
        getBridge().getWebView().setBackgroundColor(Color.parseColor("#0C0C0F"));
        startMusicService();
    }

    @Override
    public void onResume() {
        super.onResume();
        startMusicService();
    }

    /**
     * Capacitor's BridgeActivity calls webView.pauseTimers() on onPause(), which
     * freezes all JavaScript execution — stopping audio playback when the screen
     * locks. We immediately resume timers so the HTMLAudioElement and any active
     * setInterval/setTimeout keep running in the background.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().resumeTimers();
        }
    }

    private void startMusicService() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onDestroy() {
        stopService(new Intent(this, MusicService.class));
        super.onDestroy();
    }
}
