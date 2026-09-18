package androidx.media3.mpvplayer;

public final class MpvSubtitleOptions {

    public static final class Builder {
        public Builder setPosition(double position) { return this; }
        public Builder setScale(float scale) { return this; }
        public Builder setSecondarySubtitlePosition(float position) { return this; }
        public Builder setSecondaryAssStyleOverride(boolean override) { return this; }
        public Builder setFontFamily(String fontFamily) { return this; }
        public Builder setFontsDirectory(String directory) { return this; }
        public Builder setCustomStyle(int textColor, int backgroundColor, int edgeType, int edgeColor, float edgeWidth, float shadow) { return this; }
        public Builder setSystemCaptionStyle() { return this; }
        public MpvSubtitleOptions build() { return new MpvSubtitleOptions(); }
    }
}