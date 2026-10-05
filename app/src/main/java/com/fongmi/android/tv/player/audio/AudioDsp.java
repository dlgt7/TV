package com.fongmi.android.tv.player.audio;

import java.util.Arrays;

/** PCM-only DSP. One instance per AudioSink; never shared with the ASR input. */
public final class AudioDsp {
    public static final int[] FREQUENCIES = {80, 250, 1000, 4000, 10000};
    private final int channels;
    private final double[][] coefficients = new double[5][5];
    private final double[][] z1;
    private final double[][] z2;
    private final float centerGain;
    private final boolean normalize;
    private final double rmsAlpha;
    private final double release;
    private double power;
    private double gain = 1;

    public AudioDsp(int sampleRate, int channels, float[] bands, float centerDb, boolean normalize) {
        if (sampleRate <= 0 || channels <= 0 || channels > 32 || bands == null || bands.length != 5) throw new IllegalArgumentException("Invalid PCM configuration");
        this.channels = channels;
        this.centerGain = (float) Math.pow(10, finiteDb(centerDb) / 20);
        this.normalize = normalize;
        rmsAlpha = Math.exp(-1.0 / (Math.max(1, sampleRate) * 0.15));
        release = Math.exp(-1.0 / (Math.max(1, sampleRate) * 0.5));
        z1 = new double[channels][5];
        z2 = new double[channels][5];
        for (int b = 0; b < 5; b++) {
            double db = finiteDb(bands[b]);
            double a = Math.pow(10, db / 40);
            double w = 2 * Math.PI * Math.min(FREQUENCIES[b], sampleRate * .45) / sampleRate;
            double alpha = Math.sin(w) / 2;
            double denominator = 1 + alpha / a;
            coefficients[b] = new double[]{(1 + alpha * a) / denominator, -2 * Math.cos(w) / denominator,
                    (1 - alpha * a) / denominator, -2 * Math.cos(w) / denominator, (1 - alpha / a) / denominator};
        }
    }

    private static double finiteDb(float value) { return Float.isFinite(value) ? Math.max(-12, Math.min(12, value)) : 0; }

    public void process(float[] frame) {
        double peak = 0;
        double energy = 0;
        for (int c = 0; c < channels; c++) {
            double sample = Float.isFinite(frame[c]) ? frame[c] : 0;
            if (channels >= 3 && c == 2) sample *= centerGain;
            for (int b = 0; b < 5; b++) {
                double[] a = coefficients[b];
                double output = a[0] * sample + z1[c][b];
                z1[c][b] = a[1] * sample - a[3] * output + z2[c][b];
                z2[c][b] = a[2] * sample - a[4] * output;
                sample = output;
            }
            frame[c] = (float) sample;
            energy += sample * sample;
            peak = Math.max(peak, Math.abs(sample));
        }
        power = rmsAlpha * power + (1 - rmsAlpha) * energy / channels;
        // Do not boost silence/background hiss. Adapt slowly, limit transients immediately.
        double target = normalize && power > .0001 ? Math.min(4, .18 / Math.sqrt(power)) : 1;
        if (peak > 0) target = Math.min(target, .97 / peak);
        gain = target < gain ? target : release * gain + (1 - release) * target;
        for (int c = 0; c < channels; c++) frame[c] = (float) Math.max(-.97, Math.min(.97, frame[c] * gain));
    }

    public void reset() {
        for (double[] state : z1) Arrays.fill(state, 0);
        for (double[] state : z2) Arrays.fill(state, 0);
        power = 0;
        gain = 1;
    }
}
