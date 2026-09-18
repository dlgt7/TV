package androidx.media3.mpvplayer.audio;

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
        for (float s : samples) sum += s;
        return sum / samples.length;
    }

    public static float[][] createFrontCenterGainMix(int channelCount, float gain) {
        float[][] mix = new float[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) mix[i][i] = 1f;
        if (channelCount > 2) mix[2][2] = gain;
        return mix;
    }

    public static float[][] createStereoMix(int channelCount, boolean reverse) {
        float[][] mix = new float[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) mix[i][i] = 1f;
        if (channelCount >= 2) {
            if (reverse) {
                mix[0][0] = 0f; mix[0][1] = 1f;
                mix[1][1] = 0f; mix[1][0] = 1f;
            }
        }
        return mix;
    }

    public static float[][] createMonoMix(int channelCount) {
        float[][] mix = new float[channelCount][channelCount];
        float v = 1f / channelCount;
        for (int i = 0; i < channelCount; i++) {
            mix[0][i] = v;
            mix[1][i] = v;
        }
        return mix;
    }

    public static float[][] createFrontBalanceMix(int channelCount, float balance) {
        float[][] mix = new float[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) mix[i][i] = 1f;
        if (channelCount >= 2) {
            mix[0][0] = balance >= 0 ? 1f - balance : 1f;
            mix[1][1] = balance <= 0 ? 1f + balance : 1f;
        }
        return mix;
    }

    public static float[][] compose(float[][] first, float[][] second) {
        if (first == null) return second;
        if (second == null) return first;
        int rows = first.length;
        int cols = second[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                for (int k = 0; k < first[0].length; k++) {
                    result[i][j] += first[i][k] * second[k][j];
                }
            }
        }
        return result;
    }
}