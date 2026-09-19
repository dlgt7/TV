package androidx.media3.common;

import androidx.annotation.Nullable;

/**
 * 版本（Edition）描述：同一部片源的不同剪辑版本，例如剧场版 / 导演剪辑版。
 */
public final class MediaEdition {

    public static final MediaEdition DEFAULT = new MediaEdition();

    public boolean selected;
    public String label;
    public long durationUs;

    @Nullable
    private final String id;

    public MediaEdition() {
        this(null, null, C.TIME_UNSET);
    }

    public MediaEdition(@Nullable String id, @Nullable String label) {
        this(id, label, C.TIME_UNSET);
    }

    public MediaEdition(@Nullable String id, @Nullable String label, long durationUs) {
        this.id = id;
        this.label = label == null ? "" : label;
        this.durationUs = durationUs;
    }

    public static MediaEdition create(@Nullable String id, @Nullable String label, long durationMs) {
        return new MediaEdition(id, label, durationMs == C.TIME_UNSET ? C.TIME_UNSET : C.msToUs(durationMs));
    }

    @Nullable
    public String id() {
        return id;
    }

    @Nullable
    public String label() {
        return label;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public long getDurationMs() {
        return durationUs == C.TIME_UNSET ? C.TIME_UNSET : C.usToMs(durationUs);
    }

    public boolean hasDuration() {
        return durationUs != C.TIME_UNSET;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaEdition other)) return false;
        if (durationUs != other.durationUs) return false;
        if (id == null ? other.id != null : !id.equals(other.id)) return false;
        return label.equals(other.label);
    }

    @Override
    public int hashCode() {
        int result = id == null ? 0 : id.hashCode();
        result = 31 * result + label.hashCode();
        result = 31 * result + Long.hashCode(durationUs);
        return result;
    }

    @Override
    public String toString() {
        return "MediaEdition{id=" + id + ", label=" + label + ", durationUs=" + durationUs + ", selected=" + selected + "}";
    }
}
