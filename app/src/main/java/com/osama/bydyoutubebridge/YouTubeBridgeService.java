package com.osama.bydyoutubebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YouTubeBridgeService extends NotificationListenerService {
    private static final String TAG = "BYD-YouTubeBridge";
    private static final String CHANNEL_ID = "bridge_status";
    private static final int NOTIFICATION_ID = 1407;

    private static final Set<String> SUPPORTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.youtube.premium",
            "com.android.youtube.music.premium",
            "com.byd.youtube"
    ));

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaSessionManager sessionManager;
    private MediaController sourceController;
    private MediaSession bridgeSession;
    private BridgeState lastState = BridgeState.empty();
    private AmbientLightBridge ambientBridge;
    private AudioSpectrumAnalyzer spectrumAnalyzer;
    private boolean ambientRunning;
    private String ambientStatus = "Not started";
    private int[] ambientBands = new int[16];

    private final MediaSessionManager.OnActiveSessionsChangedListener activeSessionsListener =
            controllers -> selectSourceController(controllers);

    private final MediaController.Callback sourceCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publishFromSource();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publishFromSource();
        }

        @Override
        public void onSessionDestroyed() {
            sourceController = null;
            refreshSessions();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification("Waiting for YouTube"));
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        createBridgeSession();
        ambientBridge = new AmbientLightBridge(this);
        spectrumAnalyzer = new AudioSpectrumAnalyzer(this, new AudioSpectrumAnalyzer.Listener() {
            @Override public void onBands(int[] bands) {
                ambientBands = bands;
                boolean sent = ambientBridge.send(bands);
                ambientStatus = ambientBridge.getStatus();
                if (!sent) Log.w(TAG, "Ambient frame not sent: " + ambientStatus);
                publishFromSource();
            }
            @Override public void onStatus(String status) {
                ambientStatus = status;
                publishFromSource();
            }
        });
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        ComponentName listenerComponent = new ComponentName(this, YouTubeBridgeService.class);
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    activeSessionsListener, listenerComponent, mainHandler);
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to register active-session listener", e);
        }
        refreshSessions();
    }

    @Override
    public void onListenerDisconnected() {
        detachSource();
        publish(BridgeState.empty());
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        detachSource();
        if (spectrumAnalyzer != null) spectrumAnalyzer.stop();
        if (sessionManager != null) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener);
            } catch (RuntimeException ignored) {
            }
        }
        if (bridgeSession != null) {
            bridgeSession.release();
            bridgeSession = null;
        }
        super.onDestroy();
    }

    private void refreshSessions() {
        ComponentName listenerComponent = new ComponentName(this, YouTubeBridgeService.class);
        try {
            List<MediaController> controllers = sessionManager.getActiveSessions(listenerComponent);
            selectSourceController(controllers);
        } catch (SecurityException e) {
            Log.e(TAG, "Notification-listener access is not enabled", e);
            publish(BridgeState.empty());
        }
    }

    private void selectSourceController(List<MediaController> controllers) {
        MediaController selected = null;
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (SUPPORTED_PACKAGES.contains(controller.getPackageName())) {
                    selected = controller;
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                        break;
                    }
                }
            }
        }

        if (sameSession(sourceController, selected)) {
            publishFromSource();
            return;
        }

        detachSource();
        sourceController = selected;
        if (sourceController != null) {
            sourceController.registerCallback(sourceCallback, mainHandler);
        }
        publishFromSource();
    }

    private boolean sameSession(MediaController a, MediaController b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getSessionToken().equals(b.getSessionToken());
    }

    private void detachSource() {
        if (sourceController != null) {
            try {
                sourceController.unregisterCallback(sourceCallback);
            } catch (RuntimeException ignored) {
            }
            sourceController = null;
        }
    }

    private void createBridgeSession() {
        bridgeSession = new MediaSession(this, "BYD YouTube Bridge");
        bridgeSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        bridgeSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { if (sourceController != null) sourceController.getTransportControls().play(); }
            @Override public void onPause() { if (sourceController != null) sourceController.getTransportControls().pause(); }
            @Override public void onSkipToNext() { if (sourceController != null) sourceController.getTransportControls().skipToNext(); }
            @Override public void onSkipToPrevious() { if (sourceController != null) sourceController.getTransportControls().skipToPrevious(); }
            @Override public void onSeekTo(long pos) { if (sourceController != null) sourceController.getTransportControls().seekTo(pos); }
        }, mainHandler);
        bridgeSession.setActive(true);
    }

    private void publishFromSource() {
        if (sourceController == null) {
            publish(new BridgeState(false, "", "", "", false, 0, 0,
                    ambientRunning, ambientStatus, ambientBands));
            return;
        }

        MediaMetadata metadata = sourceController.getMetadata();
        PlaybackState playback = sourceController.getPlaybackState();

        String title = metadataText(metadata,
                MediaMetadata.METADATA_KEY_TITLE,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = metadataText(metadata,
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = playback == null ? 0 : playback.getPosition();
        boolean playing = playback != null && playback.getState() == PlaybackState.STATE_PLAYING;

        BridgeState state = new BridgeState(true, sourceController.getPackageName(),
                title, artist, playing, position, duration,
                ambientRunning, ambientStatus, ambientBands);
        mirrorToBridgeSession(metadata, playback, state);
        publish(state);
    }

    private String metadataText(MediaMetadata metadata, String... keys) {
        if (metadata == null) return "";
        for (String key : keys) {
            CharSequence value = metadata.getText(key);
            if (value != null && value.length() > 0) return value.toString();
        }
        if (metadata.getDescription() != null) {
            CharSequence value = metadata.getDescription().getTitle();
            if (value != null) return value.toString();
        }
        return "";
    }

    private void mirrorToBridgeSession(MediaMetadata sourceMetadata,
                                       PlaybackState sourcePlayback,
                                       BridgeState state) {
        if (bridgeSession == null) return;

        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs);
        if (sourceMetadata != null && sourceMetadata.getDescription() != null &&
                sourceMetadata.getDescription().getIconBitmap() != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART,
                    sourceMetadata.getDescription().getIconBitmap());
        }
        bridgeSession.setMetadata(metadata.build());

        long actions = PlaybackState.ACTION_PLAY |
                PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE |
                PlaybackState.ACTION_SEEK_TO |
                PlaybackState.ACTION_SKIP_TO_NEXT |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        int playbackState = state.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        bridgeSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(playbackState, state.positionMs, state.playing ? 1f : 0f)
                .build());
    }

    private void publish(BridgeState state) {
        lastState = state;
        Intent update = new Intent(BridgeState.ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtras(state.toBundle());
        sendBroadcast(update);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String text = state.connected
                ? (state.playing ? "Playing: " : "Paused: ") + state.title
                : "Waiting for YouTube";
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (BridgeState.ACTION_AMBIENT_START.equals(action)) {
                ambientRunning = spectrumAnalyzer != null && spectrumAnalyzer.start();
                ambientStatus = ambientRunning ? "Ambient analyzer active" : ambientStatus;
                publishFromSource();
            } else if (BridgeState.ACTION_AMBIENT_STOP.equals(action)) {
                ambientRunning = false;
                if (spectrumAnalyzer != null) spectrumAnalyzer.stop();
                publishFromSource();
            } else if (BridgeState.ACTION_AMBIENT_TEST.equals(action)) {
                int[] test = {2,2,3,5,5,3,5,8,8,3,13,10,4,1,1,1};
                ambientBands = test;
                boolean ok = ambientBridge != null && ambientBridge.send(test);
                ambientStatus = ambientBridge == null ? "Bridge unavailable" : ambientBridge.getStatus();
                if (!ok) Log.w(TAG, "Ambient test failed: " + ambientStatus);
                publishFromSource();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("BYD YouTube Bridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channel_description));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

}
