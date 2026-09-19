package androidx.media3.exoplayer.libass;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.text.SecondaryTextOutput;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * 副字幕控制器：在播放器与 {@link DecodeTrackSelector} 之间管理第二字幕轨的选择状态。
 */
public final class LibassSubtitleController {

    private final LibassPlaybackSession session;
    private final ExoPlayer player;

    @Nullable
    private final SecondaryTextOutput secondaryTextOutput;

    private boolean closed;

    public LibassSubtitleController(ExoPlayer player, LibassPlaybackSession session, Object trackSelectorFactory, Object secondaryTextOutput) {
        this.player = player;
        this.session = session;
        this.secondaryTextOutput = SecondaryTextOutput.cast(secondaryTextOutput);
    }

    public LibassPlaybackSession session() {
        return session;
    }

    @Nullable
    public SecondaryTextOutput secondaryTextOutput() {
        return secondaryTextOutput;
    }

    public void close() {
        closed = true;
        if (secondaryTextOutput != null) secondaryTextOutput.reset();
    }

    public boolean isClosed() {
        return closed;
    }

    @Nullable
    public TrackSelectionOverride getPrimaryTextTrackSelectionOverride() {
        return getTextTrackSelectionOverride(0);
    }

    @Nullable
    public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector != null && selector.getSecondaryTextTrackSelectionOverride() != null) return selector.getSecondaryTextTrackSelectionOverride();
        return getTextTrackSelectionOverride(1);
    }

    public List<TrackSelectionOverride> getSecondaryTextTrackSelectionOverrides() {
        TrackSelectionOverride primary = getPrimaryTextTrackSelectionOverride();
        List<TrackSelectionOverride> overrides = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            if (primary != null && group.getMediaTrackGroup().equals(primary.mediaTrackGroup)) continue;
            overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), getAllTrackIndices(group)));
        }
        return List.copyOf(overrides);
    }

    public boolean isSecondaryTextTrackSuppressed() {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        return selector != null && selector.isSecondaryTextTrackSuppressed();
    }

    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride selection) {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector == null) return;
        selector.setSecondaryTextTrackSelectionOverride(selection);
    }

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector == null) return;
        selector.setSecondaryTextTrackAutoSelectionEnabled(enabled);
    }

    public void resetSecondaryTextTrackSelection() {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector == null) return;
        selector.resetSecondaryTextTrackSelection();
    }

    @Nullable
    private DecodeTrackSelector getDecodeTrackSelector() {
        TrackSelector trackSelector = player.getTrackSelector();
        return androidx.media3.exoplayer.trackselection.SecondaryTextTrackSelector.cast(trackSelector);
    }

    @Nullable
    private TrackSelectionOverride getTextTrackSelectionOverride(int order) {
        Tracks tracks = player.getCurrentTracks();
        int found = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            List<Integer> indices = getSelectedTrackIndices(group);
            if (indices.isEmpty()) continue;
            if (found++ == order) return new TrackSelectionOverride(group.getMediaTrackGroup(), indices);
        }
        return null;
    }

    @NonNull
    private static List<Integer> getSelectedTrackIndices(Tracks.Group group) {
        List<Integer> indices = new ArrayList<>(group.length);
        for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i)) indices.add(i);
        return indices;
    }

    @NonNull
    private static List<Integer> getAllTrackIndices(Tracks.Group group) {
        List<Integer> indices = new ArrayList<>(group.length);
        for (int i = 0; i < group.length; i++) indices.add(i);
        return indices;
    }
}
