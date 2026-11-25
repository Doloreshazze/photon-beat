package com.playeverywhere.photonbeat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** CPU implementation of the fused PulseVAD 2.1k network. All scratch storage is session-local. */
final class VoiceActivityModel {
    static final int WINDOW = 3200;
    private static final int FRAMES = 21, BANDS = 64;
    private static final int[][] SHAPES = {
        {64,12,1}, {12,12,11}, {12,8,1}, {8,8,1}, {8,8,1}, {8,8,17}, {8,8,1},
        {8,8,17}, {8,8,1}, {8,8,1}, {8,8,29}, {8,8,1}, {8,8,1}, {8,2,1}
    };
    private final Layer[] layers = new Layer[SHAPES.length];
    private final float[][] filters = new float[BANDS][];
    private final int[] firstBin = new int[BANDS];
    private final float[] hann = new float[400], samples = new float[WINDOW], fftFrame = new float[512];
    private final AudioFft fft = new AudioFft(512);
    private final float[] input = new float[BANDS * FRAMES], a = new float[12 * FRAMES],
            b = new float[12 * FRAMES], skip = new float[8 * FRAMES];

    VoiceActivityModel(InputStream source) throws IOException {
        DataInputStream data = new DataInputStream(source);
        if (data.readInt() != 0x50425631) throw new IOException("Invalid voice model");
        for (int i = 0; i < BANDS; i++) {
            firstBin[i] = data.readUnsignedShort();
            filters[i] = new float[data.readUnsignedShort()];
            if (firstBin[i] + filters[i].length > fft.power.length) throw new IOException("Invalid mel bank");
            for (int j = 0; j < filters[i].length; j++) filters[i][j] = data.readFloat();
        }
        for (int i = 0; i < layers.length; i++) layers[i] = new Layer(data, SHAPES[i]);
        if (data.read() != -1) throw new IOException("Unexpected voice model size");
        for (int i = 0; i < hann.length; i++) hann[i] = (float)(.5 - .5 * Math.cos(2 * Math.PI * i / hann.length));
    }

    /** ring[next] is the oldest sample of the trailing 200 ms. */
    float probability(float[] ring, int next) {
        float previous = 0;
        double sum = 0, squares = 0;
        for (int i = 0; i < WINDOW; i++) {
            float value = ring[next];
            if (++next == WINDOW) next = 0;
            float y = value - .97f * previous;
            previous = value;
            samples[i] = y;
            sum += y;
            squares += y * y;
        }
        float mean = (float)(sum / WINDOW);
        float inverseStd = (float)(1 / (Math.sqrt(Math.max(0, squares / WINDOW - mean * mean)) + 1e-5));
        for (int i = 0; i < WINDOW; i++) samples[i] = (samples[i] - mean) * inverseStd;
        for (int frame = 0; frame < FRAMES; frame++) {
            int start = frame * 160 - 200;
            for (int j = 0; j < 400; j++) {
                int index = start + j;
                if (index < 0) index = -index;
                if (index >= WINDOW) index = 2 * WINDOW - 2 - index;
                fftFrame[56 + j] = samples[index] * hann[j];
            }
            fft.analyze(fftFrame);
            for (int band = 0; band < BANDS; band++) {
                float energy = 0;
                float[] filter = filters[band];
                for (int k = 0; k < filter.length; k++) energy += filter[k] * fft.power[firstBin[band] + k];
                input[band * FRAMES + frame] = (float)Math.log(energy + 1e-5);
            }
        }
        for (int band = 0; band < BANDS; band++) {
            int base = band * FRAMES;
            float avg = 0, variance = 0;
            for (int t = 0; t < FRAMES; t++) avg += input[base + t];
            avg /= FRAMES;
            for (int t = 0; t < FRAMES; t++) {
                float d = input[base + t] - avg;
                variance += d * d;
            }
            float scale = 1f / ((float)Math.sqrt(variance / FRAMES) + 1e-5f);
            for (int t = 0; t < FRAMES; t++) input[base + t] = (input[base + t] - avg) * scale;
        }
        layers[0].point(input, a, true);
        layers[1].depth(a, b, 1);
        layers[2].point(b, a, true);
        layers[3].point(a, b, true);
        layers[4].point(b, a, true);
        layers[9].point(a, skip, false);
        layers[5].depth(a, b, 1);
        layers[6].point(b, a, true);
        layers[7].depth(a, b, 1);
        layers[8].point(b, a, false);
        for (int i = 0; i < skip.length; i++) a[i] = Math.max(0, a[i] + skip[i]);
        layers[10].depth(a, b, 2);
        layers[11].point(b, a, true);
        layers[12].point(a, b, true);
        Layer head = layers[13];
        float logOdds = head.bias[1] - head.bias[0];
        for (int channel = 0; channel < head.in; channel++) {
            float pooled = 0;
            for (int t = 0; t < FRAMES; t++) pooled += b[channel * FRAMES + t];
            logOdds += (head.weights[head.in + channel] - head.weights[channel]) * pooled / FRAMES;
        }
        return (float)(1 / (1 + Math.exp(-logOdds)));
    }

    private static final class Layer {
        final int in, out, kernel;
        final float[] weights, bias;

        Layer(DataInputStream data, int[] shape) throws IOException {
            in = shape[0]; out = shape[1]; kernel = shape[2];
            weights = new float[out * (kernel == 1 ? in : kernel)];
            bias = new float[out];
            for (int i = 0; i < weights.length; i++) weights[i] = data.readFloat();
            for (int i = 0; i < out; i++) bias[i] = data.readFloat();
        }

        void point(float[] src, float[] dst, boolean relu) {
            for (int ch = 0; ch < out; ch++) {
                int base = ch * FRAMES;
                Arrays.fill(dst, base, base + FRAMES, bias[ch]);
                for (int j = 0; j < in; j++) {
                    float w = weights[ch * in + j];
                    for (int t = 0; t < FRAMES; t++) dst[base + t] += w * src[j * FRAMES + t];
                }
                if (relu) for (int t = 0; t < FRAMES; t++) dst[base + t] = Math.max(0, dst[base + t]);
            }
        }

        void depth(float[] src, float[] dst, int dilation) {
            for (int ch = 0; ch < out; ch++) {
                int base = ch * FRAMES;
                Arrays.fill(dst, base, base + FRAMES, bias[ch]);
                for (int k = 0; k < kernel; k++) {
                    int offset = (k - kernel / 2) * dilation;
                    float w = weights[ch * kernel + k];
                    int end = Math.min(FRAMES, FRAMES - offset);
                    for (int t = Math.max(0, -offset); t < end; t++)
                        dst[base + t] += w * src[base + t + offset];
                }
            }
        }
    }
}
