package androidx.media3.exoplayer.trackselection;

public final class SecondaryTextTrackSelector {

    public static final class Factory implements TrackSelector.Factory {
        public Factory(TrackSelector.Factory decodeTrackSelectorFactory) {}

        @Override
        public TrackSelector createTrackSelector(android.content.Context context) {
            return new DecodeTrackSelector(context);
        }
    }
}