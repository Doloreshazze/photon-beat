package com.playeverywhere.photonbeat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises the same streaming path used by AudioRecord, including fractional resampling. */
public final class LipSyncTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private static LipSync create(Path assets, int rate, boolean stereo, LipTimeline timeline) throws Exception {
        try (InputStream shapes = Files.newInputStream(assets.resolve("visemes.bin"));
             InputStream speech = Files.newInputStream(assets.resolve("voice-activity.bin"));
             InputStream vowels = Files.newInputStream(assets.resolve("sung-vowels.bin"))) {
            return new LipSync(rate, stereo, new VisemeModel(shapes), new VoiceActivityModel(speech),
                    new SungVowels(vowels), timeline);
        }
    }

    private static double vowel(double time, double[] formants) {
        double f0 = 145, result = 0;
        for (int harmonic = 1; harmonic <= 38; harmonic++) {
            double hz = f0 * harmonic, gain = 1.0 / harmonic;
            for (double f : formants) {
                double ratio = hz / f;
                double damping = 110 * hz / (f * f);
                gain /= Math.hypot(1 - ratio * ratio, damping);
            }
            result += gain * Math.sin(2 * Math.PI * hz * time);
        }
        return result * .052;
    }

    private static void phrase(Path assets, int rate, boolean stereo) throws Exception {
        LipTimeline timeline = new LipTimeline();
        LipSync sync = create(assets, rate, stereo, timeline);
        MouthPose pose = new MouthPose();
        int channels = stereo ? 2 : 1;
        short[] block = new short[317 * channels];
        double[] aa = {720, 1270, 2650}, oo = {430, 800, 2700};
        double first = -1, minAlpha = 1, pauseJaw = 1, last = 0, maxBeat = 0;
        double aaJaw = 0, ooJaw = 0, aaRound = 0, ooRound = 0;
        int countAa = 0, countOo = 0, frame = 0;
        for (int pos = 0; pos < rate * 4;) {
            int count = Math.min(87 + (frame++ * 53 % 230), rate * 4 - pos);
            for (int i = 0; i < count; i++) {
                double t = (pos + i) / (double)rate;
                double x = 0;
                if (t >= .4 && t < 1.22) x = vowel(t, aa);
                if (t >= 1.34 && t < 2.05) x = vowel(t, oo);
                if (t >= 3) x = .25 * Math.sin(2 * Math.PI * 82 * t) * Math.exp(-12 * (t % .3));
                double side = stereo ? .02 * Math.sin(2 * Math.PI * 1760 * t) : 0;
                block[i * channels] = (short)(32767 * (x + side));
                if (stereo) block[i * channels + 1] = (short)(32767 * (x - side));
            }
            pos += count;
            long end = pos * 1_000_000_000L / rate;
            double t = end / 1e9;
            sync.accept(block, count * channels, end);
            timeline.sample(end, pose);
            if (t < .39) check(pose.visible == 0, "Visible in opening silence");
            if (t > .4 && first < 0 && pose.visible > .5) first = t - .4;
            if (t > .72 && t < 1.98) minAlpha = Math.min(minAlpha, pose.visible);
            if (t > 1.27 && t < 1.33) pauseJaw = Math.min(pauseJaw, pose.jaw);
            if (t > .75 && t < 1.19) { aaJaw += pose.jaw; aaRound += pose.round; countAa++; }
            if (t > 1.60 && t < 2) { ooJaw += pose.jaw; ooRound += pose.round; countOo++; }
            if (t > 2.75 && t < 2.99) last = Math.max(last, pose.visible);
            if (t > 3.1) maxBeat = Math.max(maxBeat, pose.visible);
        }
        aaJaw /= countAa; ooJaw /= countOo; aaRound /= countAa; ooRound /= countOo;
        System.out.printf(java.util.Locale.US,
                "%d Hz, %d channels: entry %.0f ms, phrase %.2f, pause jaw %.2f, A/O jaw %.2f/%.2f, rounding %.2f/%.2f%n",
                rate, channels, first * 1000, minAlpha, pauseJaw, aaJaw, ooJaw, aaRound, ooRound);
        check(first >= 0 && first <= .24, "Missed vocal entry");
        check(minAlpha > .80, "Lips disappeared during the phrase");
        check(pauseJaw < .05, "Mouth did not close between syllables");
        check(aaJaw > ooJaw + .05 && ooRound > aaRound + .12, "Vowels had indistinguishable shapes");
        check(last < .01 && maxBeat < .02, "Silence or a bass drum triggered the overlay");
    }

    private static void timeline() {
        LipTimeline t = new LipTimeline(); MouthPose a = new MouthPose(), b = new MouthPose();
        a.visible = 1;
        for (int i = 0; i < 100; i++) {
            a.jaw = i / 100f;
            t.publish(1_000_000_000L + i * 10_000_000L, 1_020_000_000L + i * 10_000_000L, a);
        }
        t.sample(1_715_000_000L, b);
        check(Math.abs(b.jaw - .715f) < .0001f, "Incorrect delayed interpolation after wraparound");
        t.sample(3_000_000_000L, b); check(b.visible == 0, "Frozen capture left lips onscreen");
        t.clear(); t.sample(1_715_000_000L, b); check(b.visible == 0, "Previous session leaked");
    }

    public static void main(String[] args) throws Exception {
        Path assets = java.nio.file.Paths.get(args[0]);
        timeline();
        phrase(assets, 16000, false);
        phrase(assets, 44100, true);
    }
}
