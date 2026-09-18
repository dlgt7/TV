package androidx.media3.mpvplayer;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.mpvplayer.audio.MpvAudioFilter;
import androidx.media3.mpvplayer.video.MpvVideoEqualizer;

import java.util.List;

public class MpvPlayer extends ForwardingPlayer {

    public static final int VIDEO_EFFECTS_SUPPORTED = 0;
    public static final int VIDEO_EFFECTS_UNSUPPORTED_DIRECT_DOLBY_VISION_OUTPUT = 1;
    public static final int AUDIO_EFFECTS_SUPPORTED = 0;
    public static final int AUDIO_EFFECTS_UNSUPPORTED_PASSTHROUGH = 1;

    public static boolean isAvailable() { return false; }

    public MpvPlayer(Builder builder) {
        super(createDummy(builder.context));
    }

    private static Player createDummy(@Nullable Context context) {
        return new ExoPlayer.Builder(context).build();
    }

    public void setAudioOutputListener(@Nullable Runnable listener) {}
    public int getAudioChannelCount() { return 0; }
    public boolean addSubtitle(Object subtitleConfig) { return false; }
    public void setDecode(int decode) {}
    public void setVideoEqualizer(MpvVideoEqualizer equalizer) {}
    public void setSubtitleOptions(MpvSubtitleOptions options) {}
    public boolean setAudioFilter(MpvAudioFilter filter) { return false; }
    public int getVideoEffectsSupport() { return VIDEO_EFFECTS_SUPPORTED; }
    public int getAudioEffectsSupport() { return AUDIO_EFFECTS_SUPPORTED; }
    public boolean isVideoSharpnessSupported() { return false; }

    @Nullable
    public TrackSelectionOverride getPrimaryTextTrackSelectionOverride() { return null; }
    @Nullable
    public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() { return null; }
    public List<TrackSelectionOverride> getSecondaryTextTrackSelectionOverrides() { return List.of(); }
    public boolean isSecondaryTextTrackSuppressed() { return false; }
    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride selection) {}
    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {}
    public void resetSecondaryTextTrackSelection() {}

    public static final class Builder {
        private final Context context;
        private int decode;
        private MpvPlayerConfig config;

        public Builder(Context context) { this.context = context; }
        public Builder setDecode(int decode) { this.decode = decode; return this; }
        public Builder setConfig(MpvPlayerConfig config) { this.config = config; return this; }
        public MpvPlayer build() { return new MpvPlayer(this); }
    }
}