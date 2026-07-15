# BMIB v0.3 — BYD Music Integration Bridge

Experimental Android project for a BYD DiLink 5 head unit.

## Current scope

- Reads active MediaSession metadata from:
  - `com.android.youtube.premium`
  - `com.android.youtube.music.premium`
  - `com.byd.youtube`
- Mirrors title, artist, duration and playback state into a bridge MediaSession.
- Provides transport controls for YouTube.
- Includes a separate foreground ambient-light probe that:
  - Reflects into `android.hardware.bydauto.audio.BYDAutoAudioDevice`.
  - Tries `setAmbientLightFreq(...)` with 16 frequency bands.
  - Can analyze cabin audio through the microphone and emit approximately five frames per second.

## Important limitation

This is an experimental probe. BYD may protect the ambient-light API with a signature/system permission. Metadata mirroring alone cannot make the lights react to YouTube audio.

## Build with GitHub Actions

The workflow is in `.github/workflows/build.yml`. Push the extracted project contents to the root of a GitHub repository. The produced artifact is named `BMIB-v0.3-debug` and contains `app-debug.apk`.

## First vehicle test

1. Install the APK.
2. Open it and enable Notification Access.
3. Play a YouTube video and verify the title and state appear.
4. Press **Test BYD lights once**.
5. Record the exact Ambient API status shown in the app.
6. If the one-shot test succeeds, grant microphone permission and test live synchronization.

Do not replace or uninstall the factory MediaCenter application.
