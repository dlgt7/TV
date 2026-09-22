package com.fongmi.android.tv.player.effect.video;

import androidx.media3.mpvplayer.MpvPlayer;

import java.util.Locale;

/** Maps the portable profile to libmpv's public GPU equalizer properties. */
public final class MpvVideoEffectController {

    public boolean apply(MpvPlayer player, VideoEffectProfile profile) {
        // mpv exposes five public video-equalizer properties. Fold shadow lift and temperature
        // into those controls; sharpness is handled separately by the public vf/libavfilter path.
        float brightness = profile.getBrightness() + profile.getShadowLift() * 0.30f;
        float contrast = profile.getContrast() - profile.getShadowLift() * 0.15f;
        float hue = profile.getHue() + profile.getTemperature() * 0.12f;
        boolean equalizer = player.setVideoEqualizer(
                convert(brightness, 0.0f),
                convert(contrast, 1.0f),
                convert(profile.getSaturation(), 1.0f),
                toGamma(profile.getGamma()),
                Math.clamp(hue / 1.8f, -100.0f, 100.0f));
        boolean detail = player.setVideoFilter(createDetailFilter(profile));
        return equalizer && detail;
    }

    public boolean clear(MpvPlayer player) {
        boolean equalizer = player.setVideoEqualizer(0, 0, 0, 0, 0);
        boolean detail = player.setVideoFilter("");
        return equalizer && detail;
    }

    private String createDetailFilter(VideoEffectProfile profile) {
        if (profile.getSharpness() <= 0.0f) return "";
        float amount = Math.clamp(profile.getSharpness() * 1.5f, 0.0f, 1.2f);
        return String.format(Locale.US, "lavfi=[unsharp=5:5:%.3f:5:5:0]", amount);
    }

    private float toGamma(float gamma) {
        return Math.clamp((float) (Math.log(gamma) * 100.0 / Math.log(8.0)), -100.0f, 100.0f);
    }

    private float convert(float value, float neutral) {
        return Math.clamp((value - neutral) * 100.0f, -100.0f, 100.0f);
    }
}
