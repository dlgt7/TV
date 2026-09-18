package androidx.media3.mpvplayer.video;

public final class MpvVideoEqualizer {

    public static final MpvVideoEqualizer DEFAULT = new MpvVideoEqualizer(0f, 1f, 1f, 0f, 0f, 0f);

    private final float brightness;
    private final float contrast;
    private final float saturation;
    private final float gamma;
    private final float hue;
    private final float sharpness;

    private MpvVideoEqualizer(float brightness, float contrast, float saturation, float gamma, float hue, float sharpness) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.saturation = saturation;
        this.gamma = gamma;
        this.hue = hue;
        this.sharpness = sharpness;
    }

    public static MpvVideoEqualizer create(float brightness, float contrast, float saturation, float gamma, float hue, float sharpness) {
        return new MpvVideoEqualizer(brightness, contrast, saturation, gamma, hue, sharpness);
    }
}