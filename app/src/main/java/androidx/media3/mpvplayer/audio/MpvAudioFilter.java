package androidx.media3.mpvplayer.audio;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * mpv 音频滤镜链：按顺序保存各个滤镜节点，交给 mpv 的 af 命令串接。
 *
 * <p>节点本身只是描述，{@link androidx.media3.mpvplayer.MpvPlayer#setAudioFilter(MpvAudioFilter)}
 * 会把它下发给播放器。
 */
public final class MpvAudioFilter {

    public static final MpvAudioFilter EMPTY = new MpvAudioFilter(List.of());

    private final List<Node> nodes;

    private MpvAudioFilter(List<Node> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    @NonNull
    public List<Node> getNodes() {
        return nodes;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    @Override
    public String toString() {
        return "MpvAudioFilter" + nodes;
    }

    public record Node(String id, Kind kind, float[] params) {

        @NonNull
        @Override
        public String toString() {
            return id + ":" + kind;
        }
    }

    public enum Kind {
        CHANNEL_MIX,
        LOUDNESS,
        COMPRESSOR,
        VOLUME,
        EQUALIZER,
        LIMITER
    }

    public static final class Builder {

        private final List<Node> nodes = new ArrayList<>();

        public Builder addRuntimeChannelMix(String id, float[][] mix) {
            nodes.add(new Node(id, Kind.CHANNEL_MIX, flatten(mix)));
            return this;
        }

        public Builder addLoudnessNormalization(String id, double target, double maxGain, double maxScale) {
            nodes.add(new Node(id, Kind.LOUDNESS, new float[]{(float) target, (float) maxGain, (float) maxScale}));
            return this;
        }

        public Builder addCompressor(String id, float threshold, float ratio, float makeup, float mix) {
            nodes.add(new Node(id, Kind.COMPRESSOR, new float[]{threshold, ratio, makeup, mix}));
            return this;
        }

        public Builder addVolume(String id, double gain) {
            nodes.add(new Node(id, Kind.VOLUME, new float[]{(float) gain}));
            return this;
        }

        public Builder addEqualizer(String id, double frequency, double gain) {
            nodes.add(new Node(id, Kind.EQUALIZER, new float[]{(float) frequency, (float) gain}));
            return this;
        }

        public Builder addLimiter(String id, double threshold) {
            nodes.add(new Node(id, Kind.LIMITER, new float[]{(float) threshold}));
            return this;
        }

        public MpvAudioFilter build() {
            return nodes.isEmpty() ? EMPTY : new MpvAudioFilter(nodes);
        }

        private static float[] flatten(float[][] matrix) {
            if (matrix == null || matrix.length == 0) return new float[0];
            List<Float> values = new ArrayList<>();
            for (float[] row : matrix) {
                if (row == null) continue;
                for (float value : row) values.add(value);
            }
            float[] result = new float[values.size()];
            for (int i = 0; i < result.length; i++) result[i] = values.get(i);
            return result;
        }
    }
}
