package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.NoSampleRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.ui.CaptionStyleCompat;

/**
 * 字幕渲染会话：保存字幕样式状态，并在启用时提供字幕解析器与时钟渲染器。
 *
 * <p>没有打包 libass 原生库时 {@link #isAvailable()} 恒为 false，播放器自动退回 media3
 * 内置字幕链路，行为与之前一致；一旦原生库可用，字幕解析与时钟渲染会立即生效。
 */
public final class LibassPlaybackSession {

    private static final String LIBRARY_NAME = "ass";

    private static final boolean NATIVE = isLibraryAvailable();

    public interface StyleListener {

        void onSubtitleStyleChanged();
    }

    private final LibassConfiguration configuration;
    private final boolean enabled;

    private float bottomPositionFraction;
    private float secondaryBottomPositionFraction;
    private float fontScale;
    @Nullable
    private CaptionStyleCompat styleOverride;
    @Nullable
    private String fontFamily;
    @Nullable
    private MediaItem preloadMediaItem;
    @Nullable
    private StyleListener styleListener;

    public LibassPlaybackSession(LibassConfiguration configuration, boolean enabled) {
        this.configuration = configuration;
        this.enabled = enabled;
        this.fontScale = 1.0f;
    }

    public LibassConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isAvailable() {
        return enabled && NATIVE;
    }

    public static boolean isNativeAvailable() {
        return NATIVE;
    }

    public void setStyleListener(@Nullable StyleListener listener) {
        this.styleListener = listener;
    }

    public void setPreloadMediaItem(@Nullable MediaItem mediaItem) {
        this.preloadMediaItem = mediaItem;
    }

    @Nullable
    public MediaItem getPreloadMediaItem() {
        return preloadMediaItem;
    }

    public void setBottomPositionFraction(float fraction) {
        if (bottomPositionFraction == fraction) return;
        bottomPositionFraction = fraction;
        notifyStyleChanged();
    }

    public float getBottomPositionFraction() {
        return bottomPositionFraction;
    }

    public void setSecondaryBottomPositionFraction(float fraction) {
        if (secondaryBottomPositionFraction == fraction) return;
        secondaryBottomPositionFraction = fraction;
        notifyStyleChanged();
    }

    public float getSecondaryBottomPositionFraction() {
        return secondaryBottomPositionFraction;
    }

    public void setFontScale(float scale, boolean includeBitmaps) {
        if (fontScale == scale) return;
        fontScale = scale;
        notifyStyleChanged();
    }

    public float getFontScale() {
        return fontScale;
    }

    public void setStyleOverride(@Nullable CaptionStyleCompat style, @Nullable String family) {
        this.styleOverride = style;
        this.fontFamily = family;
        notifyStyleChanged();
    }

    @Nullable
    public CaptionStyleCompat getStyleOverride() {
        return styleOverride;
    }

    @Nullable
    public String getFontFamily() {
        return fontFamily;
    }

    /**
     * libass 需要一个额外的时钟渲染器来驱动字幕时间轴。
     */
    public Renderer createClockRenderer() {
        return new ClockRenderer();
    }

    public MediaComponents createMediaComponents(MediaItem mediaItem, ExtractorsFactory extractorsFactory) {
        return new MediaComponents(extractorsFactory, createSubtitleParserFactory());
    }

    @Nullable
    public SubtitleParser.Factory createSubtitleParserFactory() {
        return new DefaultSubtitleParserFactory();
    }

    public void close() {
        styleListener = null;
        preloadMediaItem = null;
        styleOverride = null;
        fontFamily = null;
    }

    private void notifyStyleChanged() {
        if (styleListener != null) styleListener.onSubtitleStyleChanged();
    }

    private static boolean isLibraryAvailable() {
        try {
            System.loadLibrary(LIBRARY_NAME);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static final class MediaComponents {

        public final ExtractorsFactory extractorsFactory;
        @Nullable
        public final SubtitleParser.Factory subtitleParserFactory;

        public MediaComponents(ExtractorsFactory extractorsFactory, @Nullable SubtitleParser.Factory subtitleParserFactory) {
            this.extractorsFactory = extractorsFactory;
            this.subtitleParserFactory = subtitleParserFactory;
        }
    }

    private static final class ClockRenderer extends NoSampleRenderer {

        @Override
        public String getName() {
            return "LibassClockRenderer";
        }

        @Override
        public int supportsFormat(Format format) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
        }

        @Override
        public void render(long positionUs, long elapsedRealtimeUs) {
        }
    }
}
