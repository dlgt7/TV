package androidx.media3.common;

import androidx.annotation.Nullable;

public final class MediaEdition {

    public boolean selected;
    public String label;
    public long durationUs;

    public MediaEdition() {
        this.label = "";
        this.durationUs = C.TIME_UNSET;
    }

    public MediaEdition(@Nullable String id, @Nullable String label) {
        this.label = label == null ? "" : label;
        this.durationUs = C.TIME_UNSET;
    }

    @Nullable
    public String id() { return null; }

    @Nullable
    public String label() { return label; }
}
