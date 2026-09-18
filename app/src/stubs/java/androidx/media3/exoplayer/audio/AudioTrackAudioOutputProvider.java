package androidx.media3.exoplayer.audio;

import androidx.annotation.Nullable;

public final class AudioTrackAudioOutputProvider {

    public static final class Builder {
        @Nullable
        private Object audioProcessor;

        public Builder(@Nullable Object audioProcessor) { this.audioProcessor = audioProcessor; }
        public AudioTrackAudioOutputProvider build() { return new AudioTrackAudioOutputProvider(); }
    }

    private AudioTrackAudioOutputProvider() {}
}