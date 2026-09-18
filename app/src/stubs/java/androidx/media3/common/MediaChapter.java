package androidx.media3.common;

import androidx.annotation.Nullable;

public final class MediaChapter {

    public static final MediaChapter DEFAULT = new MediaChapter();

    public boolean selected;
    public String label;
    public long timeUs;

    public MediaChapter() {
        this.label = "";
        this.timeUs = C.TIME_UNSET;
    }

    public MediaChapter(@Nullable String label, long timeUs) {
        this.label = label == null ? "" : label;
        this.timeUs = timeUs;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public long getTimeMs() {
        return timeUs == C.TIME_UNSET ? C.TIME_UNSET : timeUs / 1000;
    }
}