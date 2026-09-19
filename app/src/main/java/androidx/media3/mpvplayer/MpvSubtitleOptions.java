package androidx.media3.mpvplayer;

import androidx.annotation.Nullable;

/**
 * 字幕渲染选项。未显式设置的项保持为默认值（{@link Double#NaN} / {@link Float#NaN}）。
 */
public final class MpvSubtitleOptions {

    public static final MpvSubtitleOptions EMPTY = new Builder().build();

    private final double position;
    private final float scale;
    private final float secondaryPosition;
    private final boolean secondaryAssStyleOverride;
    @Nullable
    private final String fontFamily;
    @Nullable
    private final String fontsDirectory;
    private final boolean customStyle;
    private final boolean systemCaptionStyle;
    private final int textColor;
    private final int backgroundColor;
    private final int edgeType;
    private final int edgeColor;
    private final float edgeWidth;
    private final float shadow;

    private MpvSubtitleOptions(Builder builder) {
        this.position = builder.position;
        this.scale = builder.scale;
        this.secondaryPosition = builder.secondaryPosition;
        this.secondaryAssStyleOverride = builder.secondaryAssStyleOverride;
        this.fontFamily = builder.fontFamily;
        this.fontsDirectory = builder.fontsDirectory;
        this.customStyle = builder.customStyle;
        this.systemCaptionStyle = builder.systemCaptionStyle;
        this.textColor = builder.textColor;
        this.backgroundColor = builder.backgroundColor;
        this.edgeType = builder.edgeType;
        this.edgeColor = builder.edgeColor;
        this.edgeWidth = builder.edgeWidth;
        this.shadow = builder.shadow;
    }

    public boolean hasPosition() {
        return !Double.isNaN(position);
    }

    /**
     * mpv 的 sub-pos：100 表示贴底，0 表示贴顶。
     */
    public double getPosition() {
        return position;
    }

    public boolean hasScale() {
        return !Float.isNaN(scale);
    }

    public float getScale() {
        return scale;
    }

    public boolean hasSecondaryPosition() {
        return !Float.isNaN(secondaryPosition);
    }

    public float getSecondaryPosition() {
        return secondaryPosition;
    }

    public boolean hasSecondaryAssStyleOverride() {
        return secondaryAssStyleOverride;
    }

    @Nullable
    public String getFontFamily() {
        return fontFamily;
    }

    @Nullable
    public String getFontsDirectory() {
        return fontsDirectory;
    }

    public boolean hasCustomStyle() {
        return customStyle;
    }

    public boolean hasSystemCaptionStyle() {
        return systemCaptionStyle;
    }

    public int getTextColor() {
        return textColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getEdgeType() {
        return edgeType;
    }

    public int getEdgeColor() {
        return edgeColor;
    }

    public float getEdgeWidth() {
        return edgeWidth;
    }

    public float getShadow() {
        return shadow;
    }

    public static final class Builder {

        private double position = Double.NaN;
        private float scale = Float.NaN;
        private float secondaryPosition = Float.NaN;
        private boolean secondaryAssStyleOverride;
        private String fontFamily;
        private String fontsDirectory;
        private boolean customStyle;
        private boolean systemCaptionStyle;
        private int textColor;
        private int backgroundColor;
        private int edgeType;
        private int edgeColor;
        private float edgeWidth;
        private float shadow;

        public Builder setPosition(double position) {
            this.position = position;
            return this;
        }

        public Builder setScale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder setSecondarySubtitlePosition(float position) {
            this.secondaryPosition = position;
            return this;
        }

        public Builder setSecondaryAssStyleOverride(boolean override) {
            this.secondaryAssStyleOverride = override;
            return this;
        }

        public Builder setFontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
            return this;
        }

        public Builder setFontsDirectory(String directory) {
            this.fontsDirectory = directory;
            return this;
        }

        public Builder setCustomStyle(int textColor, int backgroundColor, int edgeType, int edgeColor, float edgeWidth, float shadow) {
            this.customStyle = true;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.edgeType = edgeType;
            this.edgeColor = edgeColor;
            this.edgeWidth = edgeWidth;
            this.shadow = shadow;
            return this;
        }

        public Builder setSystemCaptionStyle() {
            this.systemCaptionStyle = true;
            return this;
        }

        public MpvSubtitleOptions build() {
            return new MpvSubtitleOptions(this);
        }
    }
}
