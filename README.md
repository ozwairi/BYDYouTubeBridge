# BYD YouTube Bridge v0.1

First diagnostic build for BYD DiLink MediaCenter research.

## What v0.1 does

- Uses `NotificationListenerService` to access active media sessions.
- Detects:
  - `com.android.youtube.premium`
  - `com.android.youtube.music.premium`
  - `com.byd.youtube`
- Reads title, artist/channel, playback state, position, and duration.
- Mirrors the data into a bridge `MediaSession`.
- Forwards bridge Play/Pause/Next/Previous/Seek commands to YouTube.
- Shows live diagnostic data in the app.

## Important limitation

This build does **not** inject YouTube PCM/audio into `com.byd.mediacenter`. Therefore it is not expected to activate synchronized ambient lighting yet. It establishes the reliable YouTube-side foundation needed for the next build.

## Build

Use Android Studio or Gradle with Android SDK 35 and Java 17:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open the app and tap **Open notification access**, enable **BYD YouTube Bridge**, and start YouTube playback.
