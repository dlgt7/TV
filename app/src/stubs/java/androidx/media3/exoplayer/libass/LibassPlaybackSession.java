package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;

public final class LibassPlaybackSession {

    public LibassPlaybackSession(LibassConfiguration configuration, boolean enabled) {}

    public void setPreloadMediaItem(@Nullable MediaItem mediaItem) {}

    public void close() {}

    public boolean isAvailable() { return false; }

    public Renderer createClockRenderer() { return null; }

    public void setBottomPositionFraction(float fraction) {}

    public void setSecondaryBottomPositionFraction(float fraction) {}

    public void setFontScale(float scale, boolean includeBitmaps) {}

    public MediaComponents createMediaComponents(MediaItem mediaItem, ExtractorsFactory extractorsFactory) {
        return new MediaComponents(extractorsFactory, null);
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
}