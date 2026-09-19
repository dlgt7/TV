package androidx.media3.exoplayer.trackselection;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 第二字幕轨道选择器：在解码选择器之上叠加「副字幕」能力。
 */
public final class SecondaryTextTrackSelector {

    /**
     * 把任意轨道选择器转为 {@link DecodeTrackSelector}，无法转换时返回 null（调用方降级为单字幕）。
     */
    @Nullable
    public static DecodeTrackSelector cast(@Nullable TrackSelector trackSelector) {
        return trackSelector instanceof DecodeTrackSelector selector ? selector : null;
    }

    public static final class Factory implements TrackSelector.Factory {

        private final TrackSelector.Factory delegate;

        public Factory(TrackSelector.Factory delegate) {
            this.delegate = delegate;
        }

        @NonNull
        @Override
        public TrackSelector createTrackSelector(@NonNull Context context) {
            TrackSelector trackSelector = delegate.createTrackSelector(context);
            return trackSelector instanceof DecodeTrackSelector ? trackSelector : new DecodeTrackSelector(context);
        }
    }

    private SecondaryTextTrackSelector() {
    }
}
