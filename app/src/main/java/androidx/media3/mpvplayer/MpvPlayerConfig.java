package androidx.media3.mpvplayer;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * mpv 播放器配置。
 */
public final class MpvPlayerConfig {

    @Nullable
    private final File configDirectory;
    @Nullable
    private final MpvAndroidOptions androidOptions;
    @Nullable
    private final MpvSubtitleOptions subtitleOptions;
    @Nullable
    private final File tlsCaFile;
    @Nullable
    private final File diskCacheDirectory;

    private final int diskCacheTimeSeconds;

    private MpvPlayerConfig(Builder builder) {
        this.configDirectory = builder.configDirectory;
        this.androidOptions = builder.androidOptions;
        this.subtitleOptions = builder.subtitleOptions;
        this.tlsCaFile = builder.tlsCaFile;
        this.diskCacheDirectory = builder.diskCacheDirectory;
        this.diskCacheTimeSeconds = builder.diskCacheTimeSeconds;
    }

    @Nullable
    public File getConfigDirectory() {
        return configDirectory;
    }

    @Nullable
    public MpvAndroidOptions getAndroidOptions() {
        return androidOptions;
    }

    @Nullable
    public MpvSubtitleOptions getSubtitleOptions() {
        return subtitleOptions;
    }

    @Nullable
    public File getTlsCaFile() {
        return tlsCaFile;
    }

    @Nullable
    public File getDiskCacheDirectory() {
        return diskCacheDirectory;
    }

    public int getDiskCacheTimeSeconds() {
        return diskCacheTimeSeconds;
    }

    public boolean hasDiskCache() {
        return diskCacheDirectory != null && diskCacheTimeSeconds > 0;
    }

    public static final class Builder {

        private File configDirectory;
        private MpvAndroidOptions androidOptions;
        private MpvSubtitleOptions subtitleOptions;
        private File tlsCaFile;
        private File diskCacheDirectory;
        private int diskCacheTimeSeconds;

        public Builder addConfigDirectory(File dir) {
            this.configDirectory = dir;
            return this;
        }

        public Builder addAndroidDefaults(MpvAndroidOptions options) {
            this.androidOptions = options;
            return this;
        }

        /**
         * 把 assets 里的 CA 证书释放到私有目录，供 mpv 的 tls-ca-file 使用。
         */
        public Builder addTlsCaFileFromAsset(Context context, String assetName, File target) {
            File file = copyAsset(context, assetName, target);
            this.tlsCaFile = file == null ? target : file;
            return this;
        }

        public Builder addAndroidSubtitleOptions(Context context, MpvSubtitleOptions options) {
            this.subtitleOptions = options;
            return this;
        }

        public Builder addDiskCacheOptions(File cacheDir, int timeSeconds) {
            this.diskCacheDirectory = cacheDir;
            this.diskCacheTimeSeconds = timeSeconds;
            return this;
        }

        public MpvPlayerConfig build() {
            return new MpvPlayerConfig(this);
        }

        @Nullable
        private static File copyAsset(Context context, String assetName, @Nullable File target) {
            if (context == null || target == null) return null;
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) return null;
                try (InputStream input = context.getAssets().open(assetName); OutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                return target;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
    }
}
