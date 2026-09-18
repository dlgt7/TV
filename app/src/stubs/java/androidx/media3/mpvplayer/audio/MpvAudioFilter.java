package androidx.media3.mpvplayer.audio;

public final class MpvAudioFilter {

    public static final MpvAudioFilter EMPTY = new MpvAudioFilter();

    private MpvAudioFilter() {}

    public static final class Builder {
        public Builder addRuntimeChannelMix(String id, float[][] mix) { return this; }
        public Builder addLoudnessNormalization(String id, double target, double maxGain, double maxScale) { return this; }
        public Builder addCompressor(String id, float threshold, float ratio, float makeup, float mix) { return this; }
        public Builder addVolume(String id, double gain) { return this; }
        public Builder addEqualizer(String id, double frequency, double gain) { return this; }
        public Builder addLimiter(String id, double threshold) { return this; }
        public MpvAudioFilter build() { return new MpvAudioFilter(); }
    }
}