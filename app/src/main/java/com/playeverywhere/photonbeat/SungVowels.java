package com.playeverywhere.photonbeat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Five sung vowels and an accompaniment class, learned from labelled singer recordings and mixes. */
final class SungVowels {
    private static final int[] BORDERS = {2, 10, 22, 40, 70, 120, 220};
    private final float[] features = new float[48], hidden = new float[64], hidden2 = new float[48];
    private final float[][] weights = new float[3][], biases = new float[3][];
    final float[] probabilities = new float[6];

    SungVowels(InputStream stream) throws IOException {
        DataInputStream data = new DataInputStream(stream);
        if (data.readInt() != 0x50425331) throw new IOException("Invalid sung vowel model");
        int[] sizes = {48, 64, 48, 6};
        for (int i = 0; i < 3; i++) {
            weights[i] = new float[sizes[i] * sizes[i + 1]];
            biases[i] = new float[sizes[i + 1]];
            for (int k = 0; k < weights[i].length; k++) weights[i][k] = data.readFloat();
            for (int k = 0; k < biases[i].length; k++) biases[i][k] = data.readFloat();
        }
    }

    int classify(float[] power, float[] mel, float total, float peakShare) {
        for (int m = 0; m < 40; m++)
            features[m] = (float)((Math.log10(Math.max(1e-7, mel[m] / total)) + 3) * .5);
        for (int b = 0; b < 6; b++) {
            float sum = 1e-12f, peak = 0;
            for (int k = BORDERS[b]; k < BORDERS[b + 1]; k++) {
                sum += power[k]; peak = Math.max(peak, power[k]);
            }
            features[40 + b] = peak / sum;
        }
        features[46] = peakShare;
        float low = 0;
        for (int i = 6; i < 43; i++) low += power[i];
        features[47] = low / total;
        dense(features, hidden, 0);
        dense(hidden, hidden2, 1);
        dense(hidden2, probabilities, 2);
        int best = 0;
        for (int i = 1; i < 5; i++) if (probabilities[i] > probabilities[best]) best = i;
        float largest = Math.max(probabilities[best], probabilities[5]), sum = 0;
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = (float)Math.exp(probabilities[i] - largest);
            sum += probabilities[i];
        }
        for (int i = 0; i < probabilities.length; i++) probabilities[i] /= sum;
        return best;
    }

    private void dense(float[] input, float[] output, int layer) {
        System.arraycopy(biases[layer], 0, output, 0, output.length);
        float[] w = weights[layer];
        int index = 0;
        for (float v : input)
            for (int k = 0; k < output.length; k++) output[k] += v * w[index++];
        if (layer < 2) for (int i = 0; i < output.length; i++) output[i] = Math.max(0, output[i]);
    }
}
