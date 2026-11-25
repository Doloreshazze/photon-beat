package com.playeverywhere.photonbeat;

import java.util.Arrays;

/** Audio-thread onset detector. Its clock is independent of drawing and effect speed. */
final class RhythmTracker {
    private final float[] intervals = new float[7], sorted = new float[7];
    private int intervalCount, intervalIndex;
    private long previousTime = -1, quietSince = -1;
    private float previousBass, previousMid, previousHigh;
    private float bassMean = .004f, fluxMean = .001f;
    long lastBeat = -1;
    int beatCount, validIntervals;
    float bpm = 120;

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float approach(float old, float target, float dt, float seconds) {
        return old + (target - old) * (1 - (float)Math.exp(-dt / seconds));
    }

    boolean update(float bass, float mid, float high, long now) {
        float dt = previousTime < 0 ? .006f : clamp((now - previousTime) / 1000f, .001f, .1f);
        previousTime = now;
        float flux = Math.max(0, bass - previousBass)
                + .25f * Math.max(0, mid - previousMid) + .08f * Math.max(0, high - previousHigh);
        float period = 60000f / bpm;
        long gap = lastBeat < 0 ? Long.MAX_VALUE : now - lastBeat;
        float refractory = validIntervals >= 3 ? clamp(period * .62f, 180, 520) : 210;
        boolean beat = gap >= refractory && bass > Math.max(.0035f, bassMean * 1.15f)
                && bass - previousBass > Math.max(.00045f, bassMean * .045f)
                && flux > Math.max(.0007f, fluxMean * 1.65f);
        if (beat) {
            if (lastBeat >= 0 && gap <= 2200) {
                float interval = gap;
                // A missed kick must not suddenly halve the dance tempo.
                if (validIntervals >= 3) {
                    int multiple = Math.max(1, Math.round(interval / period));
                    if (multiple <= 3 && Math.abs(interval / multiple - period) < period * .2f)
                        interval /= multiple;
                }
                if (interval >= 300 && interval <= 1000) {
                    intervals[intervalIndex++ % intervals.length] = interval;
                    intervalCount = Math.min(intervalCount + 1, intervals.length);
                    System.arraycopy(intervals, 0, sorted, 0, intervalCount);
                    Arrays.sort(sorted, 0, intervalCount);
                    bpm = 60000f / sorted[intervalCount / 2];
                    validIntervals++;
                }
            } else if (lastBeat >= 0) {
                intervalCount = intervalIndex = validIntervals = 0;
                bpm = 120;
            }
            lastBeat = now;
            beatCount++;
        }
        bassMean = approach(bassMean, bass, dt, .65f);
        fluxMean = approach(fluxMean, flux, dt, .45f);
        if (bass + mid + high < .0009f) {
            if (quietSince < 0) quietSince = now;
            if (now - quietSince > 750) {
                lastBeat = -1;
                intervalCount = intervalIndex = validIntervals = 0;
                bpm = 120;
            }
        } else quietSince = -1;
        previousBass = bass; previousMid = mid; previousHigh = high;
        return beat;
    }
}
