package com.playeverywhere.photonbeat;

/** Streaming 16 kHz analysis. Visibility follows phrases; articulation follows 10 ms audio hops. */
final class LipSync {
    private static final int RATE = 16000, HOP = 160, N = VisemeModel.SIZE;
    private static final int TAPS = 32, PHASES = 64;
    // Jaw, rounding, lateral spread, lower-lip bite, tongue height, tooth exposure.
    private static final float[][] RIG = {
        {.96f,.05f,.30f,0,.07f,.36f}, {.53f,.02f,.72f,0,.20f,.72f}, {.24f,0,1,0,.28f,.87f},
        {.68f,.85f,.03f,0,.02f,.13f}, {.28f,1,0,0,0,.03f}, {0,.10f,.28f,0,0,0},
        {.11f,.02f,.72f,0,.18f,.98f}, {.21f,.05f,.40f,0,.95f,.73f}, {.18f,0,.48f,0,.88f,.68f},
        {.09f,.03f,.46f,1,0,.94f}, {.36f,.08f,.38f,0,.30f,.50f}, {.15f,.04f,.38f,0,.82f,.56f},
        {.30f,.30f,.29f,0,.69f,.32f}, {.21f,.46f,.23f,0,.28f,.88f}, {0,0,.20f,0,0,0}
    };
    private final int inputRate;
    private final boolean stereo;
    private final VisemeModel visemes;
    private final VoiceActivityModel activity;
    private final SungVowels singing;
    private final VowelFormants formants = new VowelFormants();
    private final LipTimeline timeline;
    private final float[] center = new float[VoiceActivityModel.WINDOW], side = new float[N + 1];
    private final float[] filterMid = new float[TAPS], filterSide = new float[TAPS];
    private final float[][] filters = new float[PHASES][TAPS];
    private final float[] window = new float[N], frame = new float[N], power = new float[N / 2 + 1];
    private final AudioFft fft = new AudioFft(N);
    private final MouthPose pose = new MouthPose();
    private final float[] target = new float[6];
    private int phase, writeFilter, writeCenter, writeSide, filled, hop, frames, attack;
    private int sinceVoice = 1000, sinceAnchor = 1000, lastVowel = -1, resonanceCandidate = -1, stableResonance;
    private float voiceProbability, peak = .015f, syllableLevel, phraseEvidence;

    LipSync(int inputRate, boolean stereo, VisemeModel visemes, VoiceActivityModel activity,
            SungVowels singing, LipTimeline timeline) {
        this.inputRate = inputRate;
        this.stereo = stereo;
        this.visemes = visemes;
        this.activity = activity;
        this.singing = singing;
        this.timeline = timeline;
        for (int i = 0; i < N; i++) window[i] = (float)(.54 - .46 * Math.cos(2 * Math.PI * i / (N - 1)));
        // Windowed sinc suppresses treble that would alias into the speech bands when downsampling.
        double cutoff = .48 * RATE / inputRate;
        for (int p = 0; p < PHASES; p++) {
            double sum = 0;
            for (int i = 0; i < TAPS; i++) {
                double x = i - (TAPS - 1) / 2.0 - (double)p / PHASES;
                double sinc = Math.abs(x) < 1e-8 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * x) / (Math.PI * x);
                filters[p][i] = (float)(sinc * (.54 - .46 * Math.cos(2 * Math.PI * i / (TAPS - 1))));
                sum += filters[p][i];
            }
            for (int i = 0; i < TAPS; i++) filters[p][i] /= sum;
        }
    }

    /** atEnd is the capture-clock time just past the last input frame. */
    void accept(short[] pcm, int samples, long atEnd) {
        accept(pcm, samples, atEnd, atEnd);
    }

    void accept(short[] pcm, int samples, long atEnd, long arrival) {
        int channels = stereo ? 2 : 1, count = samples / channels;
        for (int i = 0; i < count; i++) {
            float left = pcm[i * channels] / 32768f;
            float right = stereo ? pcm[i * channels + 1] / 32768f : left;
            float mid = .5f * (left + right), sides = .5f * (left - right);
            if (inputRate != RATE) {
                writeFilter = (writeFilter - 1) & (TAPS - 1);
                filterMid[writeFilter] = mid;
                filterSide[writeFilter] = sides;
                phase += RATE;
                if (phase < inputRate) continue;
                phase -= inputRate;
                float[] kernel = filters[phase * PHASES / RATE];
                mid = sides = 0;
                for (int k = 0; k < TAPS; k++) {
                    int idx = (writeFilter + k) & (TAPS - 1);
                    mid += filterMid[idx] * kernel[k];
                    if (stereo) sides += filterSide[idx] * kernel[k];
                }
            }
            center[writeCenter] = mid;
            side[writeSide] = sides;
            if (++writeCenter == center.length) writeCenter = 0;
            if (++writeSide == side.length) writeSide = 0;
            filled = Math.min(N + 1, filled + 1);
            if (++hop == HOP) {
                hop = 0;
                if (filled >= N) {
                    analyze();
                    long filterNs = inputRate == RATE ? 0 : (TAPS - 1L) * 500_000_000L / inputRate;
                    long centerNs = atEnd - (count - i - 1L) * 1_000_000_000L / inputRate - 16_000_000L - filterNs;
                    timeline.publish(centerNs, arrival, pose);
                }
            }
        }
    }

    private float spectrum(float[] ring, int next) {
        int index = (next + ring.length - N - 1) % ring.length;
        float prev = ring[index], recentEnergy = 0;
        for (int i = 0; i < N; i++) {
            if (++index == ring.length) index = 0;
            float x = ring[index];
            frame[i] = (x - .97f * prev) * window[i];
            prev = x;
            if (i >= N - HOP) recentEnergy += x * x;
        }
        fft.analyze(frame);
        return (float)Math.sqrt(recentEnergy / HOP);
    }

    private void analyze() {
        float level = spectrum(center, writeCenter);
        System.arraycopy(fft.power, 0, power, 0, power.length);
        if (stereo) {
            spectrum(side, writeSide);
            for (int i = 0; i < power.length; i++) {
                // Retain panned singers; attenuate side-dominant accompaniment in each frequency bin.
                float gain = power[i] / (power[i] + 1.6f * fft.power[i] + 1e-10f);
                power[i] *= .26f + .74f * gain;
            }
        }
        float all = 1e-12f, presence = 0, lower = 0, strongest = 0;
        for (int i = 0; i < power.length; i++) {
            power[i] /= N;
            all += power[i];
            if (i >= 6 && i <= 144) presence += power[i];
            if (i >= 6 && i <= 38) lower += power[i];
            if (i >= 2) strongest = Math.max(strongest, power[i] + power[i-1] + power[i-2]);
        }
        boolean audible = level > .00048f && all > .0000012f;
        if (frames++ % 4 == 0) voiceProbability = audible ? activity.probability(center, writeCenter) : 0;
        int best = audible ? visemes.classify(power) : VisemeModel.REST;
        float share = presence / all, concentration = strongest / all;
        int sung = audible ? singing.classify(power, visemes.melPower, all, concentration) : 0;
        float sungConfidence = audible ? singing.probabilities[sung] : 0;
        float foreground = audible ? 1 - singing.probabilities[5] : 0;
        boolean rich = audible && share > .20f && lower / all > .018f && concentration < .92f;
        boolean vowel = best < 5;
        int resonant = rich && (vowel || foreground > .48f) ? formants.vowel(power) : -1;
        boolean agreedVowel = resonant == sung && formants.confidence > .48f;
        boolean sungAnchor = sungConfidence > .68f && foreground > .86f && share > .35f
                && (agreedVowel || (voiceProbability > .45f && vowel));
        boolean speechAnchor = voiceProbability >= .67f && (foreground > .60f || voiceProbability > .94f)
                && best != VisemeModel.REST;
        boolean softVoice = rich && ((foreground > .68f && voiceProbability > .28f && (vowel || sungConfidence > .52f))
                || (agreedVowel && sungConfidence > .64f && voiceProbability > .16f));
        phraseEvidence += ((softVoice ? 1 : 0) - phraseEvidence) * .105f;
        boolean anchored = rich && (sungAnchor || speechAnchor || (softVoice && phraseEvidence > .64f));
        sinceAnchor = anchored ? 0 : Math.min(1000, sinceAnchor + 1);
        boolean continues = sinceVoice < 58 && sinceAnchor < 160 && rich
                && ((foreground > .53f && voiceProbability > .35f) || (sungConfidence > .58f && agreedVowel));
        // A sung vowel may outlast the 200 ms detector window with almost no spectral change.
        boolean sustained = sinceVoice < 15 && lastVowel == sung && rich && foreground > .68f
                && share > .45f && (vowel || sungConfidence > .63f);
        if (anchored) {
            attack = Math.min(5, attack + 2);
            if (vowel || sungAnchor) lastVowel = sung;
        } else attack = Math.max(0, attack - 1);
        if ((anchored && attack >= 4) || ((sustained || continues) && pose.visible > .8f)) sinceVoice = 0;
        else sinceVoice = Math.min(1000, sinceVoice + 1);
        if (sinceVoice > 65) lastVowel = -1;
        int hold = audible ? 64 : 45;
        float visibility = sinceVoice <= hold ? 1 : Math.max(0, (hold + 13 - sinceVoice) / 13f);
        pose.visible += (visibility - pose.visible) * (visibility > pose.visible ? .72f : .5f);
        if (pose.visible < .005f) pose.visible = 0;

        peak = Math.max(level, peak * .993f);
        syllableLevel += (level - syllableLevel) * .15f;
        if (!audible || level < Math.max(.0005f, peak * .035f) || (sinceVoice > 22 && !softVoice)
                || (!rich && voiceProbability < .24f && sinceVoice > 6)) best = VisemeModel.REST;
        float sungMix = Math.max(0, Math.min(1, (sungConfidence - .42f) * 2.6f));
        if (!vowel) sungMix *= (best == 5 || best == 9 || best == 6 || best == 13) ? .25f : .66f;
        if (best == VisemeModel.REST) sungMix = 0;
        if (resonant >= 0 && formants.confidence > .58f) {
            stableResonance = resonant == resonanceCandidate ? Math.min(8, stableResonance + 1) : 1;
            resonanceCandidate = resonant;
        } else stableResonance = 0;
        // The speech prototypes can alternate between a stop and a vowel on a stationary sung note.
        // Keep the vowel only while its two independent estimates agree and there is no acoustic closure.
        if (stableResonance >= 3 && resonant == sung && sungConfidence > .85f
                && sinceVoice < 15 && level > Math.max(.0008f, syllableLevel * .62f)) {
            best = sung;
            sungMix = 1;
            vowel = true;
        }
        float resonanceMix = resonant < 0 || best == VisemeModel.REST ? 0 : formants.confidence
                * (vowel ? .76f : (best == 5 || best == 9 || best == 6 || best == 13) ? .18f : .58f);
        if (stableResonance >= 4 && best != VisemeModel.REST && best != 5 && best != 9
                && (sungConfidence > .45f || vowel) && concentration < .87f)
            resonanceMix = Math.max(resonanceMix, .9f * formants.confidence);
        articulate(best, level, sung, sungMix, resonant, resonanceMix);
    }

    private void articulate(int best, float level, int sung, float sungMix, int resonant, float resonanceMix) {
        float[] primary = RIG[best];
        int runner = -1;
        if (best != 5 && best != VisemeModel.REST) {
            for (int v = 0; v < VisemeModel.REST; v++)
                if (v != best && (runner < 0 || visemes.distances[v] < visemes.distances[runner])) runner = v;
        }
        float mix = runner < 0 ? 0 : .22f * (float)Math.exp(-Math.max(0,
                visemes.distances[runner] - visemes.distances[best]) / 2.5f);
        for (int i = 0; i < target.length; i++) {
            target[i] = runner < 0 ? primary[i] : primary[i] + mix * (RIG[runner][i] - primary[i]);
            target[i] += sungMix * (RIG[sung][i] - target[i]);
            if (resonant >= 0) target[i] += resonanceMix * (RIG[resonant][i] - target[i]);
        }
        target[0] *= .78f + .22f * Math.min(1, level / Math.max(.002f, peak * .5f));
        pose.jaw += (target[0] - pose.jaw) * (best == 5 || best == VisemeModel.REST ? .78f : .58f);
        pose.round += (target[1] - pose.round) * .54f;
        pose.wide += (target[2] - pose.wide) * .56f;
        pose.bite += (target[3] - pose.bite) * .65f;
        pose.tongue += (target[4] - pose.tongue) * .57f;
        pose.teeth += (target[5] - pose.teeth) * .63f;
    }
}
