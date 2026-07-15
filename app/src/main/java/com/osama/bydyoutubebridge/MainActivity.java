package com.osama.bydyoutubebridge;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView status, pkg, title, artist, playback, timing, ambient, bands;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BridgeState.ACTION_UPDATE.equals(intent.getAction())) render(BridgeState.fromBundle(intent.getExtras()));
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestPermissionsIfNeeded();
        render(BridgeState.empty());
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BridgeState.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(updateReceiver, filter);
        status.setText(isListenerEnabled() ? "Notification access enabled" : "Enable notification access");
    }

    @Override protected void onPause() {
        try { unregisterReceiver(updateReceiver); } catch (IllegalArgumentException ignored) {}
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(16,20,24));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28),dp(24),dp(28),dp(24)); scroll.addView(root);
        TextView h = text("BYD YouTube Bridge",28,Color.WHITE); h.setTypeface(null, android.graphics.Typeface.BOLD); root.addView(h);
        TextView sub = text("Version 0.3 — YouTube session + direct BYD ambient probe",15,Color.rgb(183,195,204)); sub.setPadding(0,dp(4),0,dp(20)); root.addView(sub);
        status=valueRow(root,"Bridge status"); pkg=valueRow(root,"Source package"); title=valueRow(root,"Title"); artist=valueRow(root,"Artist / channel"); playback=valueRow(root,"Playback"); timing=valueRow(root,"Position / duration"); ambient=valueRow(root,"Ambient API"); bands=valueRow(root,"16 frequency bands");
        root.addView(button("Open notification access", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(button("Open YouTube", v -> openPackage("com.android.youtube.premium")));
        root.addView(button("Test BYD lights once", v -> sendServiceAction(BridgeState.ACTION_AMBIENT_TEST)));
        root.addView(button("Start microphone light sync", v -> { requestPermissionsIfNeeded(); sendServiceAction(BridgeState.ACTION_AMBIENT_START); }));
        root.addView(button("Stop light sync", v -> sendServiceAction(BridgeState.ACTION_AMBIENT_STOP)));
        TextView note=text("The test sends the same 16-value format observed from BYD Music. Microphone mode analyzes sound from the cabin and sends a new 16-band frame about five times per second. It is an experimental direct-light path and may be blocked by BYD signature permissions.",14,Color.rgb(183,195,204)); note.setPadding(0,dp(20),0,0); root.addView(note);
        return scroll;
    }

    private void sendServiceAction(String action) {
        Intent i = new Intent(this, AmbientControlService.class); i.setAction(action); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private TextView valueRow(LinearLayout root,String label){ TextView l=text(label,13,Color.rgb(183,195,204)); l.setPadding(0,dp(8),0,dp(2)); root.addView(l); TextView v=text("—",18,Color.WHITE); v.setPadding(0,0,0,dp(7)); root.addView(v); return v; }
    private Button button(String label, View.OnClickListener listener){ Button b=new Button(this); b.setText(label); b.setTextSize(16); b.setAllCaps(false); b.setOnClickListener(listener); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54)); lp.setMargins(0,dp(10),0,0); b.setLayoutParams(lp); return b; }
    private TextView text(String value,int sp,int color){ TextView tv=new TextView(this); tv.setText(value); tv.setTextSize(sp); tv.setTextColor(color); return tv; }
    private void render(BridgeState s){ status.setText(s.connected?"YouTube connected":"Waiting for YouTube"); pkg.setText(empty(s.packageName)); title.setText(empty(s.title)); artist.setText(empty(s.artist)); playback.setText(s.playing?"PLAYING":"PAUSED / IDLE"); timing.setText(format(s.positionMs)+" / "+format(s.durationMs)); ambient.setText((s.ambientRunning?"RUNNING — ":"")+empty(s.ambientStatus)); bands.setText(Arrays.toString(s.bands)); }
    private String empty(String s){ return s==null||s.isEmpty()?"—":s; }
    private String format(long ms){ long sec=Math.max(0,ms/1000); return String.format(Locale.US,"%d:%02d",sec/60,sec%60); }
    private void openPackage(String name){ Intent i=getPackageManager().getLaunchIntentForPackage(name); if(i!=null) startActivity(i); }
    private boolean isListenerEnabled(){ String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners"); return enabled!=null&&enabled.contains(getPackageName()); }
    private void requestPermissionsIfNeeded(){ if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.RECORD_AUDIO},42); else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42); }
    private int dp(int value){ return Math.round(value*getResources().getDisplayMetrics().density); }
}
