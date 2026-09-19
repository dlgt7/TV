package androidx.media3.mpvplayer;

import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.text.SecondaryTextOutput;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;
import androidx.media3.exoplayer.trackselection.SecondaryTextTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.mpvplayer.audio.MpvAudioFilter;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放器外壳：以 ExoPlayer 为内核，对外提供 mpv 风格的接口。
 *
 * <p>画面调节会转成 media3 的 {@link Effect}（亮度 / 对比度 / 色相 / 饱和度），
 * 第二字幕由额外的 {@link TextRenderer} 输出到 {@link SecondaryTextOutput}。
 */
public class MpvPlayer extends ForwardingPlayer {

    public static final int VIDEO_EFFECTS_SUPPORTED = 0;
    public static final int VIDEO_EFFECTS_UNSUPPORTED_DIRECT_DOLBY_VISION_OUTPUT = 1;
    public static final int AUDIO_EFFECTS_SUPPORTED = 0;
    public static final int AUDIO_EFFECTS_UNSUPPORTED_PASSTHROUGH = 1;

    private final SecondaryTextOutput secondaryTextOutput;
    private final AnalyticsListener analyticsListener;
    private final MpvPlayerConfig config;
    private final ExoPlayer player;

    private MpvVideoEqualizer equalizer;
    private MpvAudioFilter audioFilter;
    private MpvSubtitleOptions subtitleOptions;
    private PlayerView playerView;
    private Runnable audioOutputListener;
    private int decode;

    public static boolean isAvailable() {
        return true;
    }

    public MpvPlayer(Builder builder) {
        super(createPlayer(builder));
        this.player = (ExoPlayer) getWrappedPlayer();
        this.secondaryTextOutput = builder.secondaryTextOutput;
        this.config = builder.config;
        this.equalizer = MpvVideoEqualizer.DEFAULT;
        this.audioFilter = MpvAudioFilter.EMPTY;
        this.decode = builder.decode;
        this.analyticsListener = new ComponentListener();
        this.player.addAnalyticsListener(analyticsListener);
    }

    private static ExoPlayer createPlayer(Builder builder) {
        DecodeTrackSelector trackSelector = new DecodeTrackSelector(builder.context);
        trackSelector.setRendererDecodePreferences(builder.decode, builder.decode);
        return new ExoPlayer.Builder(builder.context).setTrackSelector(trackSelector).setRenderersFactory(new MpvRenderersFactory(builder.context, builder.secondaryTextOutput)).setAudioAttributes(AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true).build();
    }

    @Nullable
    public MpvPlayerConfig getConfig() {
        return config;
    }

    public SecondaryTextOutput getSecondaryTextOutput() {
        return secondaryTextOutput;
    }

    public void setAudioOutputListener(@Nullable Runnable listener) {
        this.audioOutputListener = listener;
    }

    public int getAudioChannelCount() {
        Format format = player.getAudioFormat();
        return format == null ? Format.NO_VALUE : format.channelCount;
    }

    public boolean addSubtitle(Object subtitleConfig) {
        if (!(subtitleConfig instanceof MediaItem.SubtitleConfiguration configuration)) return false;
        MediaItem current = getCurrentMediaItem();
        if (current == null || current.localConfiguration == null) return false;
        List<MediaItem.SubtitleConfiguration> configurations = new ArrayList<>(current.localConfiguration.subtitleConfigurations);
        for (MediaItem.SubtitleConfiguration existing : configurations) {
            if (existing.id != null && existing.id.equals(configuration.id)) return false;
        }
        configurations.add(configuration);
        long position = Math.max(0, getCurrentPosition());
        setMediaItem(current.buildUpon().setSubtitleConfigurations(configurations).build(), position);
        prepare();
        return true;
    }

    public void setDecode(int decode) {
        this.decode = decode;
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector != null) selector.setRendererDecodePreferences(decode, decode);
    }

    public int getDecode() {
        return decode;
    }

    public void setVideoEqualizer(@Nullable MpvVideoEqualizer equalizer) {
        this.equalizer = equalizer == null ? MpvVideoEqualizer.DEFAULT : equalizer;
        applyVideoEffects();
    }

    public MpvVideoEqualizer getVideoEqualizer() {
        return equalizer;
    }

    public void setSubtitleOptions(@Nullable MpvSubtitleOptions options) {
        this.subtitleOptions = options;
        applySubtitleOptions();
    }

    @Nullable
    public MpvSubtitleOptions getSubtitleOptions() {
        return subtitleOptions;
    }

    public boolean setAudioFilter(@Nullable MpvAudioFilter filter) {
        this.audioFilter = filter == null ? MpvAudioFilter.EMPTY : filter;
        return true;
    }

    public MpvAudioFilter getAudioFilter() {
        return audioFilter;
    }

    public int getVideoEffectsSupport() {
        return VIDEO_EFFECTS_SUPPORTED;
    }

    public int getAudioEffectsSupport() {
        return AUDIO_EFFECTS_SUPPORTED;
    }

    public boolean isVideoSharpnessSupported() {
        return false;
    }

    /**
     * 绑定播放视图后，字幕位置 / 缩放 / 样式才能真正作用到字幕视图上。
     */
    public void bindPlayerView(@Nullable PlayerView playerView) {
        this.playerView = playerView;
        applySubtitleOptions();
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
            TrackGroup trackGroup = group.getMediaTrackGroup();
            if (primary != null && trackGroup.equals(primary.mediaTrackGroup)) continue;
            List<Integer> indices = new ArrayList<>(group.length);
            for (int i = 0; i < group.length; i++) indices.add(i);
            overrides.add(new TrackSelectionOverride(trackGroup, indices));
        }
        return List.copyOf(overrides);
    }

    public boolean isSecondaryTextTrackSuppressed() {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        return selector != null && selector.isSecondaryTextTrackSuppressed();
    }

    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride selection) {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector != null) selector.setSecondaryTextTrackSelectionOverride(selection);
    }

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector != null) selector.setSecondaryTextTrackAutoSelectionEnabled(enabled);
    }

    public void resetSecondaryTextTrackSelection() {
        DecodeTrackSelector selector = getDecodeTrackSelector();
        if (selector != null) selector.resetSecondaryTextTrackSelection();
    }

    @Override
    public void release() {
        player.removeAnalyticsListener(analyticsListener);
        audioOutputListener = null;
        playerView = null;
        super.release();
    }

    @Nullable
    private DecodeTrackSelector getDecodeTrackSelector() {
        TrackSelector trackSelector = player.getTrackSelector();
        return SecondaryTextTrackSelector.cast(trackSelector);
    }

    @Nullable
    private TrackSelectionOverride getTextTrackSelectionOverride(int order) {
        int found = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            List<Integer> indices = new ArrayList<>(group.length);
            for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i)) indices.add(i);
            if (indices.isEmpty()) continue;
            if (found++ == order) return new TrackSelectionOverride(group.getMediaTrackGroup(), indices);
        }
        return null;
    }

    private void applyVideoEffects() {
        List<Effect> effects = new ArrayList<>();
        if (!equalizer.isDefault()) {
            if (equalizer.getBrightness() != 0f) effects.add(new Brightness(equalizer.getBrightnessUnit()));
            if (equalizer.getContrast() != 0f) effects.add(new Contrast(equalizer.getContrastUnit()));
            if (equalizer.getSaturation() != 0f || equalizer.getHue() != 0f) {
                effects.add(new HslAdjustment.Builder().adjustSaturation(equalizer.getSaturationUnit()).adjustHue(equalizer.getHue()).build());
            }
        }
        try {
            player.setVideoEffects(effects);
        } catch (Throwable ignored) {
        }
    }

    private void applySubtitleOptions() {
        PlayerView view = playerView;
        if (view == null || subtitleOptions == null) return;
        SubtitleView subtitleView = view.getSubtitleView();
        if (subtitleView == null) return;
        if (subtitleOptions.hasScale()) subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleOptions.getScale(), false);
        if (subtitleOptions.hasPosition()) {
            float padding = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION + (float) ((100.0 - subtitleOptions.getPosition()) / 100.0);
            subtitleView.setBottomPaddingFraction(Math.min(1.0f, Math.max(0.0f, padding)));
        }
        CaptionStyleCompat style = createCaptionStyle(view.getContext());
        if (style != null) {
            subtitleView.setStyle(style);
            subtitleView.setApplyEmbeddedStyles(false);
        } else {
            subtitleView.setApplyEmbeddedStyles(true);
        }
    }

    @Nullable
    private CaptionStyleCompat createCaptionStyle(Context context) {
        if (subtitleOptions == null) return null;
        if (subtitleOptions.hasCustomStyle()) {
            return new CaptionStyleCompat(subtitleOptions.getTextColor(), subtitleOptions.getBackgroundColor(), Color.TRANSPARENT, subtitleOptions.getEdgeType(), subtitleOptions.getEdgeColor(), null);
        }
        if (subtitleOptions.hasSystemCaptionStyle()) {
            CaptioningManager manager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
            if (manager != null) {
                CaptionStyleCompat style = CaptionStyleCompat.createFromCaptionStyle(manager.getUserStyle());
                return new CaptionStyleCompat(style.foregroundColor, style.backgroundColor, Color.TRANSPARENT, style.edgeType, style.edgeColor, null);
            }
        }
        return null;
    }

    private void notifyAudioOutputChanged() {
        if (audioOutputListener != null) audioOutputListener.run();
    }

    private final class ComponentListener implements AnalyticsListener {

        @Override
        public void onAudioTrackInitialized(@NonNull EventTime eventTime, @NonNull AudioSink.AudioTrackConfig audioTrackConfig) {
            notifyAudioOutputChanged();
        }

        @Override
        public void onAudioTrackReleased(@NonNull EventTime eventTime, @NonNull AudioSink.AudioTrackConfig audioTrackConfig) {
            notifyAudioOutputChanged();
        }
    }

    /**
     * 在标准渲染器之外追加一个文字渲染器，用于承载第二字幕。
     */
    private static final class MpvRenderersFactory extends DefaultRenderersFactory {

        @Nullable
        private final SecondaryTextOutput secondaryTextOutput;

        private MpvRenderersFactory(Context context, @Nullable SecondaryTextOutput secondaryTextOutput) {
            super(context);
            this.secondaryTextOutput = secondaryTextOutput;
            setEnableDecoderFallback(true);
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
        }

        @Override
        protected void buildTextRenderers(@NonNull Context context, @NonNull TextOutput output, @NonNull Looper outputLooper, int extensionRendererMode, @NonNull ArrayList<Renderer> out) {
            super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out);
            if (secondaryTextOutput != null) out.add(new TextRenderer(secondaryTextOutput, outputLooper));
        }
    }

    public static final class Builder {

        private final Context context;
        private final SecondaryTextOutput secondaryTextOutput = new SecondaryTextOutput();

        private MpvPlayerConfig config;
        private int decode = DecodeTrackSelector.DECODE_HARD;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setDecode(int decode) {
            this.decode = decode;
            return this;
        }

        public Builder setConfig(MpvPlayerConfig config) {
            this.config = config;
            return this;
        }

        public MpvPlayer build() {
            return new MpvPlayer(this);
        }
    }
}
