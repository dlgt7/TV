package com.fongmi.android.tv.player.effect.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds a public libmpv/libavfilter graph without depending on the upstream private AAR. */
public final class MpvAudioEffectFilter {

    private MpvAudioEffectFilter() {
    }

    public static String create(AudioEffectConfig config, int channelCount) {
        if (config == null || !config.hasEffect()) return "";
        List<String> filters = new ArrayList<>();
        appendChannelFilter(filters, config, channelCount);
        if (config.isLoudnessEnabled()) filters.add("loudnorm=I=-18:LRA=11:TP=-1.5");
        if (config.getStability() > 0) {
            float amount = config.getStabilityAmount();
            filters.add(String.format(Locale.US,
                    "acompressor=threshold=%.3f:ratio=%.2f:attack=20:release=250:makeup=%.2f:mix=%.2f",
                    0.25f - 0.08f * amount, 1.4f + 2.6f * amount,
                    1.0f + 0.8f * amount, 0.35f + 0.65f * amount));
        }
        short[] levels = config.getLevels();
        int count = Math.min(levels.length, AudioEffectBands.STANDARD.getCount());
        for (int i = 0; i < count; i++) {
            if (levels[i] == 0) continue;
            float hz = AudioEffectBands.STANDARD.getCenterFrequency(i) / 1000.0f;
            filters.add(String.format(Locale.US,
                    "equalizer=f=%.1f:t=o:w=1:g=%.2f", hz, levels[i] / 100.0f));
        }
        int gain = config.getBoost() + config.getPreamp();
        if (gain != 0) filters.add(String.format(Locale.US, "volume=%.2fdB", gain / 100.0f));
        if (config.shouldLimitOutput(channelCount)) filters.add("alimiter=limit=0.98:attack=5:release=50");
        return filters.isEmpty() ? "" : "lavfi=[" + String.join(",", filters) + "]";
    }

    private static void appendChannelFilter(List<String> filters, AudioEffectConfig config, int channelCount) {
        int mode = AudioChannelMode.resolve(config.getChannelMode(), channelCount);
        int balance = config.getBalance();
        // Apply center gain while the original 5.1/7.1 layout still exists. Downmixing first and
        // then referencing c2 would make the lavfi graph invalid.
        if (config.hasCenterGain(channelCount)) {
            String layout = channelCount == 8 ? "7.1" : "5.1";
            StringBuilder pan = new StringBuilder("pan=").append(layout);
            for (int i = 0; i < channelCount; i++) {
                float gain = i == 2 ? config.getCenterGainFactor() : 1.0f;
                pan.append(String.format(Locale.US, "|c%d=%.3f*c%d", i, gain, i));
            }
            filters.add(pan.toString());
        }
        if (mode == AudioChannelMode.MONO) {
            if (channelCount > 2) filters.add(stereoDownmix(channelCount));
            filters.add("pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1");
        } else if (mode == AudioChannelMode.REVERSE) {
            if (channelCount > 2) filters.add(stereoDownmix(channelCount));
            filters.add("pan=stereo|c0=c1|c1=c0");
        } else if (mode == AudioChannelMode.STEREO && channelCount > 2) {
            filters.add(stereoDownmix(channelCount));
        }
        if (balance != 0 && mode != AudioChannelMode.MONO) {
            float left = balance > 0 ? 1.0f - balance / 100.0f : 1.0f;
            float right = balance < 0 ? 1.0f + balance / 100.0f : 1.0f;
            filters.add(String.format(Locale.US, "pan=stereo|c0=%.3f*c0|c1=%.3f*c1", left, right));
        }
    }

    private static String stereoDownmix(int channelCount) {
        StringBuilder left = new StringBuilder("c0");
        StringBuilder right = new StringBuilder("c1");
        if (channelCount > 2) { left.append("+0.707*c2"); right.append("+0.707*c2"); }
        if (channelCount > 3) { left.append("+0.5*c3"); right.append("+0.5*c3"); }
        if (channelCount > 4) left.append("+0.707*c4");
        if (channelCount > 5) right.append("+0.707*c5");
        if (channelCount > 6) left.append("+0.5*c6");
        if (channelCount > 7) right.append("+0.5*c7");
        return "pan=stereo|c0=" + left + "|c1=" + right;
    }
}
