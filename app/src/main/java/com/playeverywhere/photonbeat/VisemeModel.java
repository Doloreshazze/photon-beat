package com.playeverywhere.photonbeat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** HeadAudio's 16 kHz acoustic feature space and Gaussian phoneme prototypes. See asset license. */
final class VisemeModel {
    static final int SIZE = 512, SHAPES = 15, REST = 14;
    private static final int BANDS = 40, FEATURES = 12, RECORD_BYTES = 368;
    private final int[] edges = new int[BANDS + 2], shape;
    private final float[][] mean, precision;
    private final float[][] dct = new float[FEATURES][BANDS];
    private final float[] mel = new float[BANDS], feature = new float[FEATURES], diff = new float[FEATURES];
    final float[] distances = new float[SHAPES];
    final float[] melPower = new float[BANDS];

    VisemeModel(InputStream source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        for (int n; (n = source.read(chunk)) != -1;) bytes.write(chunk, 0, n);
        ByteBuffer records = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        int n = records.remaining() / RECORD_BYTES;
        if (n == 0 || records.remaining() % RECORD_BYTES != 0) throw new IOException("Invalid viseme model");
        shape = new int[n];
        mean = new float[n][FEATURES];
        precision = new float[n][FEATURES * (FEATURES + 1) / 2];
        for (int i = 0; i < n; i++) {
            records.position(i * RECORD_BYTES + 7);
            shape[i] = records.get() & 255;
            if (shape[i] >= SHAPES) throw new IOException("Invalid viseme index");
            for (int j = 0; j < FEATURES; j++) mean[i][j] = records.getFloat();
            for (int j = 0; j < precision[i].length; j++) precision[i][j] = records.getFloat();
        }
        double low = Math.log1p(30 / 700.0), high = Math.log1p(7800 / 700.0);
        for (int b = 0; b < edges.length; b++)
            edges[b] = (int)(SIZE / 16000.0 * 700 * Math.expm1(low + (high - low) * b / (BANDS + 1)));
        for (int k = 0; k < FEATURES; k++) {
            double scale = Math.sqrt(2.0 / BANDS) * (1 + 11 * Math.sin(Math.PI * (k + 1) / 22));
            for (int b = 0; b < BANDS; b++)
                dct[k][b] = (float)(scale * Math.cos(Math.PI * (k + 1) * (b + .5) / BANDS));
        }
    }

    // Input is a Hamming-window power spectrum after pre-emphasis, scaled by 1/N.
    int classify(float[] power) {
        for (int b = 0; b < BANDS; b++) {
            int left = edges[b], center = edges[b + 1], right = edges[b + 2];
            double sum = 0;
            for (int k = left; k < center; k++) sum += power[k] * (k - left) / (center - left);
            for (int k = center; k < right; k++) sum += power[k] * (right - k) / (right - center);
            melPower[b] = (float)sum;
            mel[b] = (float)Math.log10(sum + 1e-10);
        }
        for (int f = 0; f < FEATURES; f++) {
            double sum = 0;
            for (int b = 0; b < BANDS; b++) sum += dct[f][b] * mel[b];
            feature[f] = (float)Math.tanh(sum);
        }
        Arrays.fill(distances, Float.POSITIVE_INFINITY);
        for (int i = 0; i < shape.length; i++) {
            for (int j = 0; j < FEATURES; j++) diff[j] = feature[j] - mean[i][j];
            float distance = 0;
            int index = 0;
            for (int j = 0; j < FEATURES; j++) {
                float cross = 0;
                for (int k = 0; k < j; k++) cross += precision[i][index++] * diff[k];
                distance += diff[j] * (2 * cross + precision[i][index++] * diff[j]);
            }
            int v = shape[i];
            distances[v] = Math.min(distances[v], Math.max(0, distance));
        }
        int best = 0;
        for (int v = 1; v < SHAPES; v++) if (distances[v] < distances[best]) best = v;
        return best;
    }
}
