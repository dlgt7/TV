package androidx.media3.exoplayer.libass;

public final class LibassConfiguration {

    public static final class Builder {
        private String fontConfig;
        private String fontsDirectory;
        private String defaultFontFamily;
        private int maximumRenderPixels;
        private int maximumGlyphCount;
        private int maximumBitmapCacheSizeMb;

        public Builder setFontConfig(String v) { this.fontConfig = v; return this; }
        public Builder setFontsDirectory(String v) { this.fontsDirectory = v; return this; }
        public Builder setDefaultFontFamily(String v) { this.defaultFontFamily = v; return this; }
        public Builder setMaximumRenderPixels(int v) { this.maximumRenderPixels = v; return this; }
        public Builder setMaximumGlyphCount(int v) { this.maximumGlyphCount = v; return this; }
        public Builder setMaximumBitmapCacheSizeMb(int v) { this.maximumBitmapCacheSizeMb = v; return this; }

        public LibassConfiguration build() { return new LibassConfiguration(); }
    }
}