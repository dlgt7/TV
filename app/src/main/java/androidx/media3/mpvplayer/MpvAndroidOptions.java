package androidx.media3.mpvplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.DolbyVisionOutputPolicy;

import java.io.File;

/**
 * mpv Android 平台相关选项。
 */
public final class MpvAndroidOptions {

    @Nullable
    private final File shaderCacheDirectory;
    private final boolean audioPassthroughEnabled;
    private final int dolbyVisionOutputPolicy;
    private final boolean vulkanEnabled;
    private final boolean gpuNextEnabled;

    private MpvAndroidOptions(Builder builder) {
        this.shaderCacheDirectory = builder.shaderCacheDirectory;
        this.audioPassthroughEnabled = builder.audioPassthroughEnabled;
        this.dolbyVisionOutputPolicy = builder.dolbyVisionOutputPolicy;
        this.vulkanEnabled = builder.vulkanEnabled;
        this.gpuNextEnabled = builder.gpuNextEnabled;
    }

    @Nullable
    public File getShaderCacheDirectory() {
        return shaderCacheDirectory;
    }

    public boolean isAudioPassthroughEnabled() {
        return audioPassthroughEnabled;
    }

    public @DolbyVisionOutputPolicy.Mode int getDolbyVisionOutputPolicy() {
        return dolbyVisionOutputPolicy;
    }

    public boolean isVulkanEnabled() {
        return vulkanEnabled;
    }

    public boolean isGpuNextEnabled() {
        return gpuNextEnabled;
    }

    public static final class Builder {

        private File shaderCacheDirectory;
        private boolean audioPassthroughEnabled;
        private int dolbyVisionOutputPolicy = DolbyVisionOutputPolicy.AUTO;
        private boolean vulkanEnabled;
        private boolean gpuNextEnabled;

        public Builder setShaderCacheDirectory(File dir) {
            this.shaderCacheDirectory = dir;
            return this;
        }

        public Builder setAudioPassthroughEnabled(boolean enabled) {
            this.audioPassthroughEnabled = enabled;
            return this;
        }

        public Builder setDolbyVisionOutputPolicy(int policy) {
            this.dolbyVisionOutputPolicy = DolbyVisionOutputPolicy.parse(policy);
            return this;
        }

        public Builder setVulkanEnabled(boolean enabled) {
            this.vulkanEnabled = enabled;
            return this;
        }

        public Builder setGpuNextEnabled(boolean enabled) {
            this.gpuNextEnabled = enabled;
            return this;
        }

        public MpvAndroidOptions build() {
            return new MpvAndroidOptions(this);
        }
    }
}
