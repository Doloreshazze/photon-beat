package com.playeverywhere.photonbeat;

/** Deterministic audio-envelope regressions, runnable without Android or dependencies. */
public final class RhythmTrackerTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static RhythmTracker song(int bpm, boolean ghostNotes, boolean skipKick) {
        RhythmTracker tracker = new RhythmTracker();
        double period = 60000.0 / bpm;
        int detected = 0;
        for (int t = 0; t < 600 + 20 * period; t += 6) {
            double position = t - 600;
            int index = (int)Math.floor(position / period);
            double age = position - index * period;
            double kick = position >= 0 && !(skipKick && index == 10) ? Math.exp(-age / 65) : 0;
            double ghost = ghostNotes && position >= 0 && age >= 70 ? .25 * Math.exp(-(age - 70) / 20) : 0;
            double hatAge = (position % (period / 2) + period / 2) % (period / 2);
            float b = .001f + .04f * (float)(kick + ghost);
            float m = .001f + .012f * (float)(kick * .5 + Math.max(0, Math.sin(t * .013)));
            float h = .001f + .012f * (float)Math.exp(-hatAge / 12);
            if (tracker.update(b, m, h, 1000 + t)) {
                detected++;
                check(age <= 12, "Late or offbeat onset at " + bpm + " BPM: " + age + " ms");
            }
            check(tracker.mouthOpen >= 0 && tracker.mouthOpen <= 1, "Invalid mouth envelope");
        }
        int expected = skipKick ? 19 : 20;
        check(detected == expected, bpm + " BPM: expected " + expected + " kicks, got " + detected);
        check(Math.abs(tracker.bpm - bpm) < 2, "Tempo mismatch: " + tracker.bpm + " vs " + bpm);
        return tracker;
    }
    public static void main(String[] args) {
        for (int bpm : new int[]{60, 90, 120, 150, 180, 200}) song(bpm, true, false);
        RhythmTracker tracker = song(120, false, true);
        int before = tracker.beatCount;
        for (int t = 13000; t < 15000; t += 6) tracker.update(0, 0, 0, t);
        check(tracker.beatCount == before, "Silence generated beats");
        check(tracker.lastBeat == -1 && tracker.validIntervals == 0, "Silence kept stale beat lock");
        check(tracker.mouthOpen < .001f, "Mouth stayed open in silence");
        RhythmTracker sustained = new RhythmTracker();
        for (int t = 1000; t < 5000; t += 6) sustained.update(.02f, .02f, .005f, t);
        check(sustained.beatCount <= 1, "Sustained tone generated repeated beats");
        RhythmTracker treble = new RhythmTracker();
        for (int t = 1000; t < 5000; t += 6) treble.update(.001f, .002f, t % 250 < 12 ? .08f : .001f, t);
        check(treble.beatCount == 0, "Hi-hats moved the kick clock");
        System.out.println("PASS: " + checks + " checks; 60–200 BPM, ghost notes, missing kick, silence, sustained tone, hi-hats, lip envelope");
    }
}
