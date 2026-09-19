package androidx.media3.mpvplayer.video;

/**
 * 画面调节参数，取值区间与 mpv 的 brightness / contrast / saturation / gamma / hue 一致（-100 ~ 100）。
 */
public final class MpvVideoEqualizer {

    public static final MpvVideoEqualizer DEFAULT = new MpvVideoEqualizer(0f, 0f, 0f, 0f, 0f, 0f);

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

    public float getBrightness() {
        return brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getGamma() {
        return gamma;
    }

    public float getHue() {
        return hue;
    }

    public float getSharpness() {
        return sharpness;
    }

    /**
     * media3 的 {@link androidx.media3.effect.Brightness} 等效果使用 [-1, 1] 区间。
     */
    public float getBrightnessUnit() {
        return brightness / 100.0f;
    }

    public float getContrastUnit() {
        return contrast / 100.0f;
    }

    public float getSaturationUnit() {
        return saturation / 100.0f;
    }

    public boolean isDefault() {
        return brightness == 0f && contrast == 0f && saturation == 0f && gamma == 0f && hue == 0f && sharpness == 0f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MpvVideoEqualizer other)) return false;
        return Float.compare(brightness, other.brightness) == 0 && Float.compare(contrast, other.contrast) == 0 && Float.compare(saturation, other.saturation) == 0 && Float.compare(gamma, other.gamma) == 0 && Float.compare(hue, other.hue) == 0 && Float.compare(sharpness, other.sharpness) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(brightness);
        result = 31 * result + Float.hashCode(contrast);
        result = 31 * result + Float.hashCode(saturation);
        result = 31 * result + Float.hashCode(gamma);
        result = 31 * result + Float.hashCode(hue);
        result = 31 * result + Float.hashCode(sharpness);
        return result;
    }

    @Override
    public String toString() {
        return "MpvVideoEqualizer{brightness=" + brightness + ", contrast=" + contrast + ", saturation=" + saturation + ", gamma=" + gamma + ", hue=" + hue + ", sharpness=" + sharpness + "}";
    }
}
