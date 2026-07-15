package com.osama.bydyoutubebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/** Independent foreground service for the direct BYD ambient-light experiment. */
public final class AmbientControlService extends Service {
    private static final String TAG = "BYD-AmbientService";
    private static final String CHANNEL_ID = "ambient_probe";
    private static final int NOTIFICATION_ID = 1408;

    private AmbientLightBridge bridge;
    private AudioSpectrumAnalyzer analyzer;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        bridge = new AmbientLightBridge(this);
        analyzer = new AudioSpectrumAnalyzer(this, new AudioSpectrumAnalyzer.Listener() {
            @Override public void onBands(int[] bands) {
                boolean ok = bridge.send(bands);
                broadcastStatus(ok, bridge.getStatus(), bands);
            }
            @Override public void onStatus(String status) {
                broadcastStatus(false, status, null);
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (BridgeState.ACTION_AMBIENT_START.equals(action)) {
            startForeground(NOTIFICATION_ID, notification("Listening for music"));
            boolean started = analyzer.start();
            broadcastStatus(started, started ? "Ambient analyzer active" : "Ambient analyzer failed", null);
        } else if (BridgeState.ACTION_AMBIENT_STOP.equals(action)) {
            analyzer.stop();
            stopForeground(true);
            stopSelf();
        } else if (BridgeState.ACTION_AMBIENT_TEST.equals(action)) {
            startForeground(NOTIFICATION_ID, notification("Testing BYD ambient API"));
            int[] test = {2,2,3,5,5,3,5,8,8,3,13,10,4,1,1,1};
            boolean ok = bridge.send(test);
            broadcastStatus(ok, bridge.getStatus(), test);
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (analyzer != null && analyzer.isRunning()) analyzer.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void broadcastStatus(boolean running, String status, int[] bands) {
        Intent i = new Intent(BridgeState.ACTION_UPDATE);
        i.setPackage(getPackageName());
        BridgeState state = new BridgeState(false, "", "", "", false, 0, 0,
                running, status == null ? "" : status, bands == null ? new int[16] : bands);
        i.putExtras(state.toBundle());
        sendBroadcast(i);
        Log.i(TAG, status);
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("BMIB ambient probe")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID,
                    "BMIB ambient probe", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }
}
