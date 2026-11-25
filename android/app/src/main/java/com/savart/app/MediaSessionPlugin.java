package com.savart.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.InputStream;
import java.net.URL;

@CapacitorPlugin(name = "MediaSession")
public class MediaSessionPlugin extends Plugin {

    private static final String CHANNEL_ID = "savart_media";
    private static final int    NOTIF_ID   = 42;

    private MediaSessionCompat  mediaSession;
    private NotificationManager notifManager;
    private AudioManager        audioManager;
    private AudioFocusRequest   audioFocusRequest; // API 26+
    private boolean             hadFocusBeforeDuck = false;

    // Pauses playback when Bluetooth / headphones disconnect
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                notifyListeners("audioBecomingNoisy", new JSObject());
            }
        }
    };

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void load() {
        notifManager = (NotificationManager)
                getActivity().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        audioManager = (AudioManager)
                getActivity().getSystemService(android.content.Context.AUDIO_SERVICE);
        createChannel();
        initMediaSession();
        requestAudioFocus();

        // Register noisy receiver dynamically (required by Android — cannot be in manifest)
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(noisyReceiver, filter);
        }
    }

    @Override
    protected void handleOnDestroy() {
        try { getContext().unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
        abandonAudioFocus();
        if (mediaSession != null) mediaSession.release();
    }

    // ── Plugin methods called from JS ──────────────────────────────────────

    /** updateMetadata({ title, artist, album, artworkUrl }) */
    @PluginMethod
    public void updateMetadata(PluginCall call) {
        String title      = call.getString("title",      "");
        String artist     = call.getString("artist",     "");
        String album      = call.getString("album",      "");
        String artworkUrl = call.getString("artworkUrl", null);

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  album);

        final String url = artworkUrl;
        new Thread(() -> {
            Bitmap bmp = null;
            if (url != null && !url.isEmpty()) {
                try {
                    InputStream is = new URL(url).openStream();
                    bmp = BitmapFactory.decodeStream(is);
                } catch (Exception ignored) {}
            }
            final Bitmap artwork = bmp;
            getActivity().runOnUiThread(() -> {
                if (artwork != null) {
                    meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
                }
                mediaSession.setMetadata(meta.build());
                updateNotification(title, artist, artwork);
            });
        }).start();

        call.resolve();
    }

    /** setPlaybackState({ state: 'playing' | 'paused' | 'none' }) */
    @PluginMethod
    public void setPlaybackState(PluginCall call) {
        String state = call.getString("state", "none");

        int pbState;
        switch (state) {
            case "playing": pbState = PlaybackStateCompat.STATE_PLAYING; break;
            case "paused":  pbState = PlaybackStateCompat.STATE_PAUSED;  break;
            default:        pbState = PlaybackStateCompat.STATE_NONE;    break;
        }

        long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;

        PlaybackStateCompat pb = new PlaybackStateCompat.Builder()
                .setState(pbState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(actions)
                .build();
        mediaSession.setPlaybackState(pb);
        mediaSession.setActive(pbState == PlaybackStateCompat.STATE_PLAYING);

        // Refresh notification icon (play ↔ pause)
        if (mediaSession.getController() != null &&
                mediaSession.getController().getMetadata() != null) {
            String title  = mediaSession.getController().getMetadata()
                    .getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artist = mediaSession.getController().getMetadata()
                    .getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            Bitmap art = mediaSession.getController().getMetadata()
                    .getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            updateNotification(title, artist, art);
        }

        call.resolve();
    }

    // ── Audio Focus ────────────────────────────────────────────────────────

    private final AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> {
        switch (focusChange) {

            case AudioManager.AUDIOFOCUS_GAIN:
                // Notification finished / call ended → restore full volume
                JSObject gainData = new JSObject();
                gainData.put("volume", 1.0);
                notifyListeners("audioFocusGain", gainData);
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Short notification sound → duck to 20% instead of pausing
                JSObject duckData = new JSObject();
                duckData.put("volume", 0.2);
                notifyListeners("audioFocusDuck", duckData);
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Phone call / navigation → pause briefly, will resume on GAIN
                notifyListeners("audioFocusLossTransient", new JSObject());
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Another music app took over → stop
                notifyListeners("audioFocusLoss", new JSObject());
                break;
        }
    };

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)   // we handle ducking ourselves
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(focusListener);
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(getContext(), "SavartSession");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()           { notifyJS("play");     }
            @Override public void onPause()          { notifyJS("pause");    }
            @Override public void onSkipToNext()     { notifyJS("next");     }
            @Override public void onSkipToPrevious() { notifyJS("previous"); }
            @Override public void onSeekTo(long pos) {
                JSObject data = new JSObject();
                data.put("seekTime", pos / 1000.0);
                notifyListeners("seekto", data);
            }
        });

        PlaybackStateCompat idle = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build();
        mediaSession.setPlaybackState(idle);
    }

    private void notifyJS(String event) {
        notifyListeners(event, new JSObject());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Savart Reproducción",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Controles de reproducción");
            ch.setShowBadge(false);
            notifManager.createNotificationChannel(ch);
        }
    }

    private void updateNotification(String title, String artist, Bitmap artwork) {
        if (mediaSession == null) return;

        boolean isPlaying = false;
        if (mediaSession.getController() != null &&
                mediaSession.getController().getPlaybackState() != null) {
            isPlaying = mediaSession.getController().getPlaybackState().getState()
                    == PlaybackStateCompat.STATE_PLAYING;
        }

        Intent intent = new Intent(getContext(), MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(getContext(), 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setContentTitle(title  != null ? title  : "Savart")
                .setContentText(artist != null ? artist : "")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .addAction(R.drawable.ic_skip_previous, "Anterior",  buildActionIntent(0))
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                           isPlaying ? "Pausar" : "Reproducir",      buildActionIntent(1))
                .addAction(R.drawable.ic_skip_next,     "Siguiente", buildActionIntent(2))
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        if (artwork != null) builder.setLargeIcon(artwork);

        notifManager.notify(NOTIF_ID, builder.build());
    }

    private PendingIntent buildActionIntent(int action) {
        Intent i = new Intent(getContext(), MediaButtonReceiver.class);
        i.putExtra("action", action);
        return PendingIntent.getBroadcast(getContext(), action, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
