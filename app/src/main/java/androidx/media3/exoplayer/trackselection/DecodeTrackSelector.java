package androidx.media3.exoplayer.trackselection;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.TrackGroupArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 具备「解码偏好」与「第二字幕轨」能力的轨道选择器。
 *
 * <p>第二字幕：渲染器工厂会注册两个 {@link androidx.media3.exoplayer.text.TextRenderer}，
 * 第一个输出到播放器自身的字幕通道（主字幕），第二个输出到
 * {@link androidx.media3.exoplayer.text.SecondaryTextOutput}（副字幕）。
 * 本选择器负责把主字幕固定在第一个文字渲染器上，并把副字幕分配给第二个文字渲染器。
 */
public class DecodeTrackSelector extends DefaultTrackSelector {

    public static final int DECODE_SOFT = 0;
    public static final int DECODE_HARD = 1;

    private int audioDecode;
    private int videoDecode;

    @Nullable
    private volatile TrackSelectionOverride secondaryOverride;
    private volatile boolean secondaryAutoSelectionEnabled;
    private volatile boolean secondarySuppressed;

    @Nullable
    private Boolean baseTunneling;

    public DecodeTrackSelector(Context context) {
        super(context);
        this.audioDecode = DECODE_HARD;
        this.videoDecode = DECODE_HARD;
    }

    public int getAudioDecode() {
        return audioDecode;
    }

    public int getVideoDecode() {
        return videoDecode;
    }

    /**
     * 设置音/视频的软硬解偏好。软解时关闭隧道播放（隧道播放强制要求硬解解码器）。
     */
    public void setRendererDecodePreferences(int audioDecode, int videoDecode) {
        if (this.audioDecode == audioDecode && this.videoDecode == videoDecode) return;
        this.audioDecode = audioDecode;
        this.videoDecode = videoDecode;
        if (baseTunneling == null) baseTunneling = getParameters().tunnelingEnabled;
        boolean tunneling = baseTunneling && videoDecode != DECODE_SOFT;
        if (getParameters().tunnelingEnabled != tunneling) setParameters(buildUponParameters().setTunnelingEnabled(tunneling).build());
    }

    @Nullable
    public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() {
        return secondaryOverride;
    }

    public boolean isSecondaryTextTrackAutoSelectionEnabled() {
        return secondaryAutoSelectionEnabled;
    }

    /**
     * 副字幕被抑制：请求了第二字幕，但当前没有可用的第二文字渲染器来承载它。
     */
    public boolean isSecondaryTextTrackSuppressed() {
        return secondarySuppressed;
    }

    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride override) {
        if (secondarySuppressed == (override == null) && Objects.equals(secondaryOverride, override)) return;
        secondaryOverride = override;
        secondarySuppressed = override == null;
        invalidate(getParameters());
    }

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
        if (secondaryAutoSelectionEnabled == enabled) return;
        secondaryAutoSelectionEnabled = enabled;
        invalidate(getParameters());
    }

    public void resetSecondaryTextTrackSelection() {
        if (secondaryOverride == null && !secondaryAutoSelectionEnabled && !secondarySuppressed) return;
        secondaryOverride = null;
        secondaryAutoSelectionEnabled = false;
        secondarySuppressed = false;
        invalidate(getParameters());
    }

    @Override
    protected void selectAllTracks(ExoTrackSelection.Definition[] definitions, MappingTrackSelector.MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports, int[] rendererMixedMimeTypeAdaptationSupports, Parameters params) throws ExoPlaybackException {
        super.selectAllTracks(definitions, mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports, params);
        applySecondaryTextTrack(definitions, mappedTrackInfo, rendererFormatSupports);
    }

    private void applySecondaryTextTrack(ExoTrackSelection.Definition[] definitions, MappingTrackSelector.MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports) {
        List<Integer> textRenderers = getTextRenderers(mappedTrackInfo);
        if (textRenderers.isEmpty()) {
            secondarySuppressed = false;
            return;
        }
        pinPrimaryTextTrack(definitions, textRenderers);
        TrackSelectionOverride override = secondaryOverride;
        boolean auto = secondaryAutoSelectionEnabled;
        if (override == null && !auto) {
            secondarySuppressed = false;
            return;
        }
        if (textRenderers.size() < 2) {
            secondarySuppressed = true;
            return;
        }
        int renderer = textRenderers.get(1);
        TrackGroup primary = definitions[textRenderers.get(0)] == null ? null : definitions[textRenderers.get(0)].group;
        ExoTrackSelection.Definition definition = createDefinition(override, mappedTrackInfo, rendererFormatSupports, renderer, primary);
        if (definition == null || definition.group.equals(primary)) {
            secondarySuppressed = true;
            return;
        }
        definitions[renderer] = definition;
        secondarySuppressed = false;
    }

    /**
     * 主字幕必须落在第一个文字渲染器上，否则主字幕会被送到副字幕通道。
     */
    private void pinPrimaryTextTrack(ExoTrackSelection.Definition[] definitions, List<Integer> textRenderers) {
        int primaryRenderer = textRenderers.get(0);
        if (definitions[primaryRenderer] != null) return;
        for (int i = 1; i < textRenderers.size(); i++) {
            int renderer = textRenderers.get(i);
            if (definitions[renderer] == null) continue;
            definitions[primaryRenderer] = definitions[renderer];
            definitions[renderer] = null;
            return;
        }
    }

    @Nullable
    private ExoTrackSelection.Definition createDefinition(@Nullable TrackSelectionOverride override, MappingTrackSelector.MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports, int renderer, @Nullable TrackGroup primary) {
        TrackGroupArray groups = mappedTrackInfo.getTrackGroups(renderer);
        if (override != null) return createDefinition(groups, override.mediaTrackGroup, toArray(override.trackIndices), renderer, rendererFormatSupports);
        return createAutoDefinition(groups, primary, renderer, rendererFormatSupports);
    }

    @Nullable
    private ExoTrackSelection.Definition createDefinition(TrackGroupArray groups, TrackGroup group, int[] tracks, int renderer, int[][][] rendererFormatSupports) {
        int groupIndex = groups.indexOf(group);
        if (groupIndex == C.INDEX_UNSET || tracks.length == 0) return null;
        int[] supported = getSupportedTracks(rendererFormatSupports, renderer, groupIndex, tracks);
        if (supported.length == 0) return null;
        return new ExoTrackSelection.Definition(groups.get(groupIndex), supported);
    }

    @Nullable
    private ExoTrackSelection.Definition createAutoDefinition(TrackGroupArray groups, @Nullable TrackGroup primary, int renderer, int[][][] rendererFormatSupports) {
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            TrackGroup group = groups.get(groupIndex);
            if (group.equals(primary)) continue;
            int[] tracks = getSupportedTracks(rendererFormatSupports, renderer, groupIndex, null);
            if (tracks.length == 0) continue;
            return new ExoTrackSelection.Definition(group, new int[]{tracks[0]});
        }
        return null;
    }

    private int[] getSupportedTracks(int[][][] rendererFormatSupports, int renderer, int groupIndex, @Nullable int[] candidates) {
        if (rendererFormatSupports == null || renderer >= rendererFormatSupports.length) return candidates == null ? new int[0] : candidates;
        int[][] groupSupports = rendererFormatSupports[renderer];
        if (groupIndex >= groupSupports.length) return new int[0];
        int[] supports = groupSupports[groupIndex];
        List<Integer> result = new ArrayList<>(supports.length);
        if (candidates == null) {
            for (int track = 0; track < supports.length; track++) if (isSupported(supports[track])) result.add(track);
        } else {
            for (int track : candidates) if (track >= 0 && track < supports.length && isSupported(supports[track])) result.add(track);
        }
        return toArray(result);
    }

    private static boolean isSupported(int capabilities) {
        return RendererCapabilities.isFormatSupported(capabilities, false);
    }

    private static int[] toArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static List<Integer> getTextRenderers(MappingTrackSelector.MappedTrackInfo mappedTrackInfo) {
        List<Integer> renderers = new ArrayList<>(2);
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_TEXT) renderers.add(i);
        }
        return renderers;
    }
}
