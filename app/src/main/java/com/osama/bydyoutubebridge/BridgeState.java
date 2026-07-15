package com.osama.bydyoutubebridge;

import android.os.Bundle;

public final class BridgeState {
    public static final String ACTION_UPDATE = "com.osama.bydyoutubebridge.UPDATE";
    public static final String ACTION_AMBIENT_START = "com.osama.bydyoutubebridge.AMBIENT_START";
    public static final String ACTION_AMBIENT_STOP = "com.osama.bydyoutubebridge.AMBIENT_STOP";
    public static final String ACTION_AMBIENT_TEST = "com.osama.bydyoutubebridge.AMBIENT_TEST";
    public static final String EXTRA_CONNECTED = "connected";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_AMBIENT_RUNNING = "ambient_running";
    public static final String EXTRA_AMBIENT_STATUS = "ambient_status";
    public static final String EXTRA_BANDS = "bands";

    public final boolean connected;
    public final String packageName;
    public final String title;
    public final String artist;
    public final boolean playing;
    public final long positionMs;
    public final long durationMs;
    public final boolean ambientRunning;
    public final String ambientStatus;
    public final int[] bands;

    public BridgeState(boolean connected, String packageName, String title, String artist,
                       boolean playing, long positionMs, long durationMs,
                       boolean ambientRunning, String ambientStatus, int[] bands) {
        this.connected = connected;
        this.packageName = safe(packageName);
        this.title = safe(title);
        this.artist = safe(artist);
        this.playing = playing;
        this.positionMs = Math.max(0, positionMs);
        this.durationMs = Math.max(0, durationMs);
        this.ambientRunning = ambientRunning;
        this.ambientStatus = safe(ambientStatus);
        this.bands = bands == null ? new int[16] : bands.clone();
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putBoolean(EXTRA_CONNECTED, connected);
        b.putString(EXTRA_PACKAGE, packageName);
        b.putString(EXTRA_TITLE, title);
        b.putString(EXTRA_ARTIST, artist);
        b.putBoolean(EXTRA_PLAYING, playing);
        b.putLong(EXTRA_POSITION, positionMs);
        b.putLong(EXTRA_DURATION, durationMs);
        b.putBoolean(EXTRA_AMBIENT_RUNNING, ambientRunning);
        b.putString(EXTRA_AMBIENT_STATUS, ambientStatus);
        b.putIntArray(EXTRA_BANDS, bands);
        return b;
    }

    public static BridgeState fromBundle(Bundle b) {
        if (b == null) return empty();
        return new BridgeState(
                b.getBoolean(EXTRA_CONNECTED, false), b.getString(EXTRA_PACKAGE, ""),
                b.getString(EXTRA_TITLE, ""), b.getString(EXTRA_ARTIST, ""),
                b.getBoolean(EXTRA_PLAYING, false), b.getLong(EXTRA_POSITION, 0),
                b.getLong(EXTRA_DURATION, 0), b.getBoolean(EXTRA_AMBIENT_RUNNING, false),
                b.getString(EXTRA_AMBIENT_STATUS, ""), b.getIntArray(EXTRA_BANDS));
    }

    public static BridgeState empty() {
        return new BridgeState(false, "", "", "", false, 0, 0,
                false, "Not started", new int[16]);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
