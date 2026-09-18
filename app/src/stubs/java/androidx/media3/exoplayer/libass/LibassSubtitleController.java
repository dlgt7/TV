package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

public final class LibassSubtitleController {

    public LibassSubtitleController(ExoPlayer player, LibassPlaybackSession session, Object trackSelectorFactory, Object secondaryTextOutput) {}

    public void close() {}

    @Nullable
    public TrackSelectionOverride getPrimaryTextTrackSelectionOverride() { return null; }

    @Nullable
    public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() { return null; }

    public List<TrackSelectionOverride> getSecondaryTextTrackSelectionOverrides() { return List.of(); }

    public boolean isSecondaryTextTrackSuppressed() { return false; }

    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride selection) {}

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {}
}