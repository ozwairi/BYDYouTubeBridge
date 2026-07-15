package com.osama.bydyoutubebridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures cabin audio and produces 16 values in BYD's observed 1..15 range. */
public final class AudioSpectrumAnalyzer {
    public interface Listener {
        void onBands(int[] bands);
        void onStatus(String status);
    }

    private static final String TAG = "BYD-Spectrum";
    private static final int SAMPLE_RATE = 16000;
    private static final int WINDOW = 1024;
    private static final double[] CENTERS = {
            80, 120, 180, 250, 350, 500, 700, 950,
            1300, 1800, 2400, 3200, 4300, 5600, 7000, 7800
    };

    private final Context context;
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private AudioRecord recorder;

    public AudioSpectrumAnalyzer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized boolean start() {
        if (running.get()) return true;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Microphone permission required");
            return false;
        }
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            listener.onStatus("AudioRecord buffer unavailable: " + min);
            return false;
        }
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, WINDOW * 4));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                listener.onStatus("AudioRecord initialization failed");
                recorder.release();
                recorder = null;
                return false;
            }
            recorder.startRecording();
            running.set(true);
            worker = new Thread(this::loop, "BYD-AmbientAnalyzer");
            worker.start();
            listener.onStatus("Microphone spectrum running");
            return true;
        } catch (RuntimeException e) {
            listener.onStatus(e.getClass().getSimpleName() + ": " + e.getMessage());
            Log.e(TAG, "Unable to start capture", e);
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (recorder != null) {
            try { recorder.stop(); } catch (RuntimeException ignored) {}
            recorder.release();
            recorder = null;
        }
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        listener.onStatus("Ambient analyzer stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] buffer = new short[WINDOW];
        int[] smoothed = new int[16];
        Arrays.fill(smoothed, 1);
        long lastPublish = 0;
        while (running.get()) {
            AudioRecord local = recorder;
            if (local == null) break;
            int read = local.read(buffer, 0, buffer.length);
            if (read <= 0) continue;
            long now = System.currentTimeMillis();
            if (now - lastPublish < 170) continue;
            int[] raw = calculateBands(buffer, read);
            for (int i = 0; i < 16; i++) {
                smoothed[i] = Math.max(1, Math.min(15,
                        Math.round(smoothed[i] * 0.45f + raw[i] * 0.55f)));
            }
            listener.onBands(Arrays.copyOf(smoothed, smoothed.length));
            lastPublish = now;
        }
    }

    private int[] calculateBands(short[] samples, int count) {
        int usable = Math.min(count, WINDOW);
        double rms = 0;
        for (int i = 0; i < usable; i++) {
            double s = samples[i] / 32768.0;
            rms += s * s;
        }
        rms = Math.sqrt(rms / Math.max(1, usable));
        int[] values = new int[16];
        for (int band = 0; band < 16; band++) {
            double frequency = CENTERS[band];
            double real = 0;
            double imag = 0;
            for (int i = 0; i < usable; i++) {
                double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / Math.max(1, usable - 1));
                double sample = (samples[i] / 32768.0) * window;
                double angle = 2.0 * Math.PI * frequency * i / SAMPLE_RATE;
                real += sample * Math.cos(angle);
                imag -= sample * Math.sin(angle);
            }
            double magnitude = Math.sqrt(real * real + imag * imag) / Math.max(1, usable);
            double normalized = Math.log10(1.0 + magnitude * 140.0 + rms * 8.0);
            values[band] = Math.max(1, Math.min(15, (int) Math.round(1 + normalized * 10.5)));
        }
        return values;
    }
}
