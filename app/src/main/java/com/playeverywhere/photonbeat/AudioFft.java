package com.playeverywhere.photonbeat;

/** Reused real-input FFT. Spectral power is unscaled, including DC and Nyquist. */
final class AudioFft {
    final int size;
    final float[] power;
    private final float[] real, imag, cos, sin;
    private final int[] reversal;

    AudioFft(int size) {
        this.size = size;
        power = new float[size / 2 + 1];
        real = new float[size];
        imag = new float[size];
        cos = new float[size / 2];
        sin = new float[size / 2];
        reversal = new int[size];
        int shift = 32 - Integer.numberOfTrailingZeros(size);
        for (int i = 0; i < size; i++) reversal[i] = Integer.reverse(i) >>> shift;
        for (int i = 0; i < size / 2; i++) {
            double angle = -2 * Math.PI * i / size;
            cos[i] = (float)Math.cos(angle);
            sin[i] = (float)Math.sin(angle);
        }
    }

    void analyze(float[] samples) {
        for (int i = 0; i < size; i++) {
            int j = reversal[i];
            real[j] = samples[i];
            imag[j] = 0;
        }
        for (int length = 2; length <= size; length *= 2) {
            int half = length / 2, step = size / length;
            for (int base = 0; base < size; base += length) {
                for (int j = 0; j < half; j++) {
                    int a = base + j, b = a + half, k = j * step;
                    float x = real[b] * cos[k] - imag[b] * sin[k];
                    float y = real[b] * sin[k] + imag[b] * cos[k];
                    real[b] = real[a] - x;
                    imag[b] = imag[a] - y;
                    real[a] += x;
                    imag[a] += y;
                }
            }
        }
        for (int i = 0; i < power.length; i++) power[i] = real[i] * real[i] + imag[i] * imag[i];
    }
}
