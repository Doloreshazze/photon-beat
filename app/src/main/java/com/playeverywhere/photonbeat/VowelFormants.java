package com.playeverywhere.photonbeat;

/** Refines voiced vowels using the first two resonances of a short LPC spectral envelope. */
final class VowelFormants {
    private static final int ORDER = 18, BINS = 257, POINTS = 115;
    private static final double[] F1 = {740, 490, 310, 470, 325};
    private static final double[] F2 = {1330, 1920, 2240, 860, 760};
    private final float[][] lagCos = new float[ORDER + 1][BINS];
    private final float[][] cos = new float[POINTS][ORDER + 1], sin = new float[POINTS][ORDER + 1];
    private final double[] r = new double[ORDER + 1], a = new double[ORDER + 1], old = new double[ORDER + 1];
    private final double[] response = new double[POINTS];
    float confidence;

    VowelFormants() {
        for (int l = 0; l <= ORDER; l++) {
            for (int k = 0; k < BINS; k++) lagCos[l][k] = (float)Math.cos(2 * Math.PI * k * l / 512);
            for (int i = 0; i < POINTS; i++) {
                double angle = 2 * Math.PI * (150 + 25 * i) * l / 16000;
                cos[i][l] = (float)Math.cos(angle); sin[i][l] = (float)Math.sin(angle);
            }
        }
    }

    int vowel(float[] power) {
        confidence = 0;
        for (int l = 0; l <= ORDER; l++) {
            double sum = .5 * (power[0] + lagCos[l][256] * power[256]);
            for (int i = 1; i < 256; i++) sum += lagCos[l][i] * power[i];
            r[l] = sum;
        }
        double error = r[0] * 1.0001;
        if (error < 1e-9) return -1;
        a[0] = 1;
        for (int i = 1; i <= ORDER; i++) {
            double next = r[i];
            for (int k = 1; k < i; k++) next += a[k] * r[i-k];
            double reflection = -next / error;
            if (Math.abs(reflection) >= 1) return -1;
            System.arraycopy(a, 0, old, 0, i);
            for (int k = 1; k < i; k++) a[k] = old[k] + reflection * old[i-k];
            a[i] = reflection;
            error *= 1 - reflection * reflection;
        }
        for (int i = 0; i < POINTS; i++) {
            double re = 1, im = 0;
            for (int k = 1; k <= ORDER; k++) { re += a[k] * cos[i][k]; im += a[k] * sin[i][k]; }
            response[i] = re * re + im * im;
        }
        int first = 0, second = 0;
        for (int i = 1; i < POINTS - 1; i++) {
            if (response[i] >= response[i-1] || response[i] >= response[i+1]) continue;
            int hz = 150 + 25 * i;
            if (first == 0 && hz >= 225 && hz <= 1080) first = hz;
            else if (first != 0 && hz >= Math.max(600, first + 230)) { second = hz; break; }
        }
        if (second == 0) return -1;
        int best = 0;
        double minimum = Double.MAX_VALUE, nextBest = Double.MAX_VALUE;
        for (int v = 0; v < 5; v++) {
            double x = Math.log(first / F1[v]), y = Math.log(second / F2[v]);
            double scale = Math.max(-.14, Math.min(.23, .5 * (x + y)));
            double d = 16 * (x - scale) * (x - scale) + 23 * (y - scale) * (y - scale) + 5 * scale * scale;
            if (d < minimum) { nextBest = minimum; minimum = d; best = v; }
            else nextBest = Math.min(nextBest, d);
        }
        confidence = (float)(Math.exp(-minimum) * Math.min(1, (nextBest - minimum) * 1.8));
        return best;
    }
}
