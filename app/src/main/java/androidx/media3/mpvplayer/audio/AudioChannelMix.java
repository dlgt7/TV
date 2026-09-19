package androidx.media3.mpvplayer.audio;

/**
 * 声道混音矩阵工具：行为输出声道，列为输入声道。
 */
public final class AudioChannelMix {

    public static float mixStereoLeft(float[] samples) {
        return samples.length > 0 ? samples[0] : 0f;
    }

    public static float mixStereoRight(float[] samples) {
        return samples.length > 1 ? samples[1] : 0f;
    }

    public static float mixMono(float[] samples) {
        if (samples.length == 0) return 0f;
        float sum = 0f;
        for (float sample : samples) sum += sample;
        return sum / samples.length;
    }

    public static float[][] createIdentityMix(int channelCount) {
        return createIdentityMix(Math.max(0, channelCount));
    }

    public static float[][] createIdentityMix(int rows, int cols) {
        float[][] mix = new float[Math.max(0, rows)][Math.max(0, cols)];
        for (int i = 0; i < mix.length; i++) {
            for (int j = 0; j < mix[i].length; j++) mix[i][j] = i == j ? 1f : 0f;
        }
        return mix;
    }

    public static float[][] createFrontCenterGainMix(int channelCount, float gain) {
        float[][] mix = createIdentityMix(channelCount);
        if (mix.length > 2 && mix[2].length > 2) mix[2][2] = gain;
        return mix;
    }

    public static float[][] createStereoMix(int channelCount, boolean reverse) {
        float[][] mix = createIdentityMix(channelCount);
        if (mix.length < 2 || mix[0].length < 2) return mix;
        if (reverse) {
            mix[0][0] = 0f;
            mix[0][1] = 1f;
            mix[1][1] = 0f;
            mix[1][0] = 1f;
        }
        return mix;
    }

    public static float[][] createMonoMix(int channelCount) {
        float[][] mix = createIdentityMix(channelCount);
        if (mix.length == 0) return mix;
        float value = 1f / Math.max(1, channelCount);
        for (int channel = 0; channel < channelCount; channel++) {
            if (mix.length > 0 && channel < mix[0].length) mix[0][channel] = value;
            if (mix.length > 1 && channel < mix[1].length) mix[1][channel] = value;
        }
        return mix;
    }

    public static float[][] createFrontBalanceMix(int channelCount, float balance) {
        float[][] mix = createIdentityMix(channelCount);
        if (mix.length < 2 || mix[0].length < 2) return mix;
        mix[0][0] = balance >= 0 ? 1f - balance : 1f;
        mix[1][1] = balance <= 0 ? 1f + balance : 1f;
        return mix;
    }

    /**
     * 矩阵相乘：先应用 first，再应用 second。
     */
    public static float[][] compose(float[][] first, float[][] second) {
        if (first == null || first.length == 0) return second;
        if (second == null || second.length == 0) return first;
        int rows = first.length;
        int cols = second[0].length;
        int inner = Math.min(first[0].length, second.length);
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float sum = 0f;
                for (int k = 0; k < inner; k++) sum += first[i][k] * second[k][j];
                result[i][j] = sum;
            }
        }
        return result;
    }

    private AudioChannelMix() {
    }
}
