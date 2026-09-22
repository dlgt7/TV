package com.fongmi.android.tv.player.effect.audio;

import android.os.Build;

import androidx.media3.exoplayer.ExoPlayer;

public final class ExoAudioEffectController {

    private final AudioEqualizerController equalizer;
    private final AudioEffectProcessor processor;

    public ExoAudioEffectController() {
        this.equalizer = new AudioEqualizerController();
        this.processor = new AudioEffectProcessor();
    }

    public AudioEffectProcessor getProcessor() {
        return processor;
    }

    public boolean apply(ExoPlayer player, AudioEffectConfig config) {
        if (config.hasEffect()) return applyEffect(player, config);
        release();
        return true;
    }

    private boolean applyEffect(ExoPlayer player, AudioEffectConfig config) {
        boolean softwareEqualizer = config.hasBands() && Build.VERSION.SDK_INT < Build.VERSION_CODES.P;
        if (config.hasBands() && !softwareEqualizer) {
            // DynamicsProcessing is optional in practice: some API 28+ vendor implementations
            // reject valid configurations. Fall back to the bundled biquad EQ instead of disabling
            // every processor effect.
            softwareEqualizer = !equalizer.apply(player, config);
        } else {
            equalizer.release();
        }
        processor.setConfig(config, softwareEqualizer);
        return true;
    }

    public void resetToDisabled() {
        release();
    }

    public void release() {
        equalizer.release();
        processor.resetSettings();
    }
}
