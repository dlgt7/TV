package androidx.media3.common;

import androidx.annotation.Nullable;

/**
 * 章节描述：一个章节只包含标题与起始时间。
 */
public final class MediaChapter {

    public static final MediaChapter DEFAULT = new MediaChapter();

    public boolean selected;
    public String label;
    public long timeUs;

    public MediaChapter() {
        this(null, C.TIME_UNSET);
    }

    public MediaChapter(@Nullable String label, long timeUs) {
        this.label = label == null ? "" : label;
        this.timeUs = timeUs;
    }

    /**
     * 以毫秒创建章节，{@link C#TIME_UNSET} 表示无时间点。
     */
    public static MediaChapter create(@Nullable String label, long timeMs) {
        return new MediaChapter(label, timeMs == C.TIME_UNSET ? C.TIME_UNSET : C.msToUs(timeMs));
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public long getTimeUs() {
        return timeUs;
    }

    public long getTimeMs() {
        return timeUs == C.TIME_UNSET ? C.TIME_UNSET : C.usToMs(timeUs);
    }

    public boolean hasTime() {
        return timeUs != C.TIME_UNSET;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaChapter other)) return false;
        return timeUs == other.timeUs && label.equals(other.label);
    }

    @Override
    public int hashCode() {
        return 31 * label.hashCode() + Long.hashCode(timeUs);
    }

    @Override
    public String toString() {
        return "MediaChapter{label=" + label + ", timeUs=" + timeUs + ", selected=" + selected + "}";
    }
}
