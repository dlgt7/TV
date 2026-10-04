package com.fongmi.android.tv.player.track;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import com.fongmi.android.tv.player.util.PlayerHelper;

import java.util.Arrays;

/** Keeps primary selection parameters intact while selecting a second text SampleStream. */
public final class DualSubtitleTrackSelector extends TrackSelector {
    private final DecodeTrackSelector primary;
    private final int secondaryRendererCount;
    private volatile String secondary;

    public DualSubtitleTrackSelector(DecodeTrackSelector primary, int secondaryRendererCount) {
        this.primary = primary;
        this.secondaryRendererCount = secondaryRendererCount;
    }

    public void setSecondary(String format) { secondary = format; invalidate(null); }
    public String getSecondary() { return secondary; }
    public void setDecode(int mode) { primary.setRendererDecodePreferences(mode, mode); }

    @Override public void init(InvalidationListener listener, BandwidthMeter meter) {
        super.init(listener, meter);
        primary.init(listener, meter);
    }
    @Override public void release() { primary.release(); super.release(); }
    @Override public TrackSelectionParameters getParameters() { return primary.getParameters(); }
    @Override public boolean isSetParametersSupported() { return true; }
    @Override public void setParameters(TrackSelectionParameters parameters) { primary.setParameters(parameters); }
    @Override public void onParametersActivated(TrackSelectionParameters parameters) { primary.onParametersActivated(parameters); }
    @Override public void onSelectionActivated(Object info) { primary.onSelectionActivated(info); }
    @Override public void setAudioAttributes(AudioAttributes attributes) { primary.setAudioAttributes(attributes); }
    @Override public RendererCapabilities.Listener getRendererCapabilitiesListener() { return primary.getRendererCapabilitiesListener(); }

    @Override public TrackSelectorResult selectTracks(RendererCapabilities[] capabilities, TrackGroupArray groups,
            MediaSource.MediaPeriodId period, Timeline timeline) throws ExoPlaybackException {
        int primaryCount = capabilities.length - secondaryRendererCount;
        TrackSelectorResult selected = primary.selectTracks(Arrays.copyOf(capabilities, primaryCount), groups, period, timeline);
        RendererConfiguration[] configurations = Arrays.copyOf(selected.rendererConfigurations, capabilities.length);
        ExoTrackSelection[] selections = Arrays.copyOf(selected.selections, capabilities.length);
        String wanted = secondary;
        if (wanted != null) {
            outer: for (int g = 0; g < groups.length; g++) {
                TrackGroup group = groups.get(g);
                if (group.type != C.TRACK_TYPE_TEXT) continue;
                for (int t = 0; t < group.length; t++) {
                    if (!wanted.equals(PlayerHelper.describeFormat(group.getFormat(t))) || isPrimary(selected, group, t)) continue;
                    for (int r = primaryCount; r < capabilities.length; r++) {
                        if (RendererCapabilities.getFormatSupport(capabilities[r].supportsFormat(group.getFormat(t))) != C.FORMAT_HANDLED) continue;
                        configurations[r] = RendererConfiguration.DEFAULT;
                        selections[r] = new FixedTrackSelection(group, t);
                        break outer;
                    }
                }
            }
        }
        // Public Tracks describes primary selection; the secondary picker has its own state.
        return new TrackSelectorResult(configurations, selections, selected.tracks, selected.info);
    }

    private static boolean isPrimary(TrackSelectorResult result, TrackGroup group, int track) {
        for (ExoTrackSelection selection : result.selections) {
            if (selection != null && selection.getTrackGroup().equals(group) && selection.indexOf(track) >= 0) return true;
        }
        return false;
    }
}
