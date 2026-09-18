package androidx.media3.mpvplayer;

import android.content.Context;
import java.io.File;

public final class MpvPlayerConfig {

    public static final class Builder {
        public Builder addConfigDirectory(File dir) { return this; }
        public Builder addAndroidDefaults(MpvAndroidOptions options) { return this; }
        public Builder addTlsCaFileFromAsset(Context context, String assetName, File target) { return this; }
        public Builder addAndroidSubtitleOptions(Context context, MpvSubtitleOptions options) { return this; }
        public Builder addDiskCacheOptions(File cacheDir, int timeSeconds) { return this; }
        public MpvPlayerConfig build() { return new MpvPlayerConfig(); }
    }
}