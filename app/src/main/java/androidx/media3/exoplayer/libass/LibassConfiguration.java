package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;

/**
 * libass 渲染配置。
 */
public final class LibassConfiguration {

    @Nullable
    private final String fontConfig;
    @Nullable
    private final String fontsDirectory;
    @Nullable
    private final String defaultFontFamily;
    private final int maximumRenderPixels;
    private final int maximumGlyphCount;
    private final int maximumBitmapCacheSizeMb;

    private LibassConfiguration(Builder builder) {
        this.fontConfig = builder.fontConfig;
        this.fontsDirectory = builder.fontsDirectory;
        this.defaultFontFamily = builder.defaultFontFamily;
        this.maximumRenderPixels = builder.maximumRenderPixels;
        this.maximumGlyphCount = builder.maximumGlyphCount;
        this.maximumBitmapCacheSizeMb = builder.maximumBitmapCacheSizeMb;
    }

    @Nullable
    public String getFontConfig() {
        return fontConfig;
    }

    @Nullable
    public String getFontsDirectory() {
        return fontsDirectory;
    }

    @Nullable
    public String getDefaultFontFamily() {
        return defaultFontFamily;
    }

    public int getMaximumRenderPixels() {
        return maximumRenderPixels;
    }

    public int getMaximumGlyphCount() {
        return maximumGlyphCount;
    }

    public int getMaximumBitmapCacheSizeMb() {
        return maximumBitmapCacheSizeMb;
    }

    public static final class Builder {

        private String fontConfig;
        private String fontsDirectory;
        private String defaultFontFamily;
        private int maximumRenderPixels;
        private int maximumGlyphCount;
        private int maximumBitmapCacheSizeMb;

        public Builder setFontConfig(String v) {
            this.fontConfig = v;
            return this;
        }

        public Builder setFontsDirectory(String v) {
            this.fontsDirectory = v;
            return this;
        }

        public Builder setDefaultFontFamily(String v) {
            this.defaultFontFamily = v;
            return this;
        }

        public Builder setMaximumRenderPixels(int v) {
            this.maximumRenderPixels = v;
            return this;
        }

        public Builder setMaximumGlyphCount(int v) {
            this.maximumGlyphCount = v;
            return this;
        }

        public Builder setMaximumBitmapCacheSizeMb(int v) {
            this.maximumBitmapCacheSizeMb = v;
            return this;
        }

        public LibassConfiguration build() {
            return new LibassConfiguration(this);
        }
    }
}
