package com.playeverywhere.photonbeat;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.Log;
import java.io.InputStream;

public class CaptureService extends Service {
    private static final int RATE = 44100, BLOCK = 256;
    static final LipTimeline lips = new LipTimeline();
    public static volatile boolean running;
    public static volatile float bass, mid, high;

    public static final class Spectrum {
        public final float bass, mid, high, bpm;
        public final long sequence, capturedAt, lastBeat;
        public final int beatCount, validIntervals;
        Spectrum(float b, float m, float h, long s, long at, RhythmTracker rhythm) {
            bass = b; mid = m; high = h; sequence = s; capturedAt = at;
            bpm = rhythm.bpm; lastBeat = rhythm.lastBeat;
            beatCount = rhythm.beatCount; validIntervals = rhythm.validIntervals;
        }
    }
    public static volatile Spectrum spectrum = new Spectrum(0, 0, 0, 0, 0, new RhythmTracker());
    private AudioRecord record;
    private MediaProjection projection;
    private MediaProjection.Callback callback;
    private Thread worker;
    private volatile boolean recording;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || recording) return START_NOT_STICKY;
        boolean mic = intent.getBooleanExtra("mic", false);
        try {
            NotificationManager notifications = getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel("capture", "Audio capture", 2));
            Notification note = new Notification.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Photon Beat").build();
            startForeground(1, note, mic ? (Build.VERSION.SDK_INT >= 30 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0)
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            AudioPlaybackCaptureConfiguration config = null;
            if (!mic) {
                projection = getSystemService(MediaProjectionManager.class)
                        .getMediaProjection(intent.getIntExtra("result", 0), intent.getParcelableExtra("data"));
                callback = new MediaProjection.Callback() { @Override public void onStop() { stopSelf(); } };
                projection.registerCallback(callback, new Handler(getMainLooper()));
                config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
            }
            if (!mic) {
                try { record = startRecorder(config, AudioFormat.CHANNEL_IN_STEREO); }
                catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException unsupported) {
                    Log.i("PhotonBeat", "Stereo capture unavailable; using mono");
                }
            }
            if (record == null) record = startRecorder(config, AudioFormat.CHANNEL_IN_MONO);
            LipSync sync;
            try (InputStream shapes = getAssets().open("visemes.bin");
                 InputStream voice = getAssets().open("voice-activity.bin");
                 InputStream singing = getAssets().open("sung-vowels.bin")) {
                sync = new LipSync(RATE, record.getChannelCount() == 2,
                        new VisemeModel(shapes), new VoiceActivityModel(voice), new SungVowels(singing), lips);
            }
            lips.clear();
            recording = running = true;
            AudioRecord session = record;
            worker = new Thread(() -> capture(session, sync), "PhotonBeat capture");
            worker.start();
        } catch (Exception e) {
            Log.e("PhotonBeat", "Could not start audio capture", e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private static AudioRecord startRecorder(AudioPlaybackCaptureConfiguration config, int mask) {
        int channels = mask == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        int min = AudioRecord.getMinBufferSize(RATE, mask, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalArgumentException("Unsupported channel configuration");
        AudioFormat format = new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(mask).build();
        AudioRecord.Builder builder = new AudioRecord.Builder().setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(4096 * channels, min));
        if (config == null) builder.setAudioSource(MediaRecorder.AudioSource.MIC);
        else builder.setAudioPlaybackCaptureConfig(config);
        AudioRecord result = builder.build();
        try {
            result.startRecording();
            if (result.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("AudioRecord did not start");
            return result;
        } catch (RuntimeException e) {
            result.release();
            throw e;
        }
    }

    private void capture(AudioRecord session, LipSync sync) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        final int n = 1024, channels = session.getChannelCount();
        final AudioFft fft = new AudioFft(n);
        final RhythmTracker rhythm = new RhythmTracker();
        final short[] block = new short[BLOCK * channels];
        final float[] pcm = new float[n], window = new float[n], frame = new float[n];
        final AudioTimestamp timestamp = new AudioTimestamp();
        for (int j = 0; j < n; j++) window[j] = (float)(.5 - .5 * Math.cos(2 * Math.PI * j / (n - 1)));
        long sequence = spectrum.sequence, framesRead = 0, origin = 0, nextClockCheck = 0;
        int filled = 0;
        try {
            while (recording) {
                int samples = session.read(block, 0, block.length, AudioRecord.READ_BLOCKING);
                if (samples < 0 || !recording) break;
                int count = samples / channels;
                if (count == 0) continue;
                framesRead += count;
                long arrival = SystemClock.elapsedRealtimeNanos();
                long elapsed = framesRead * 1_000_000_000L / RATE;
                if (origin == 0) origin = arrival - elapsed;
                if (framesRead >= nextClockCheck) {
                    nextClockCheck = framesRead + RATE / 4;
                    if (session.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS
                            && timestamp.framePosition > 0) {
                        long measured = timestamp.nanoTime - timestamp.framePosition * 1_000_000_000L / RATE;
                        if (Math.abs(measured + elapsed - arrival) < 500_000_000L)
                            origin += Math.max(-2_000_000L, Math.min(2_000_000L, measured - origin));
                    }
                }
                sync.accept(block, samples, Math.min(arrival, origin + elapsed), arrival);
                System.arraycopy(pcm, count, pcm, 0, n - count);
                for (int j = 0; j < count; j++) {
                    int k = channels * j;
                    pcm[n - count + j] = channels == 2 ? (block[k] + block[k + 1]) / 65536f : block[k] / 32768f;
                }
                filled = Math.min(n, filled + count);
                if (filled < n) continue;
                for (int j = 0; j < n; j++) frame[j] = pcm[j] * window[j];
                fft.analyze(frame);
                float lo = 0, mi = 0, hi = 0;
                for (int j = 1; j < n / 2; j++) {
                    if (j * RATE < 250 * n) lo += fft.power[j];
                    else if (j * RATE < 2500 * n) mi += fft.power[j];
                    else hi += fft.power[j];
                }
                bass = (float)Math.sqrt(lo) / n; mid = (float)Math.sqrt(mi) / n; high = (float)Math.sqrt(hi) / n;
                long now = arrival / 1_000_000L;
                rhythm.update(bass, mid, high, now);
                spectrum = new Spectrum(bass, mid, high, ++sequence, now, rhythm);
            }
        } catch (RuntimeException e) { if (recording) Log.e("PhotonBeat", "Audio read failed", e); }
        finally { if (recording) stopSelf(); }
    }

    @Override public void onDestroy() {
        running = recording = false;
        if (record != null) {
            try { record.stop(); } catch (IllegalStateException ignored) { }
            try { if (worker != null) worker.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            record.release();
        }
        if (projection != null) {
            if (callback != null) projection.unregisterCallback(callback);
            projection.stop();
        }
        lips.clear();
        bass = mid = high = 0;
        spectrum = new Spectrum(0, 0, 0, spectrum.sequence + 1, SystemClock.elapsedRealtime(), new RhythmTracker());
        super.onDestroy();
    }
}
