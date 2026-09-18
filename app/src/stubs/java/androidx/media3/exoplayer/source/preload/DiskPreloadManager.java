package androidx.media3.exoplayer.source.preload;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import androidx.annotation.Nullable;

public final class DiskPreloadManager {

    private DiskPreloadManager(Builder builder) {}

    public void start(ExoPlayer player, MediaItem mediaItem, Options options) {}

    public void release() {}

    public static final class Builder {

        private final Cache cache;
        private final DataSource.Factory dataSourceFactory;
        private final RenderersFactory renderersFactory;
        @Nullable
        private PriorityTaskManager priorityTaskManager;

        public Builder(Cache cache, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
            this.cache = cache;
            this.dataSourceFactory = dataSourceFactory;
            this.renderersFactory = renderersFactory;
        }

        public Builder setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
            this.priorityTaskManager = priorityTaskManager;
            return this;
        }

        public DiskPreloadManager build() {
            return new DiskPreloadManager(this);
        }
    }

    public static final class Options {

        private final long durationMs;
        private final int maxThreads;

        private Options(long durationMs, int maxThreads) {
            this.durationMs = durationMs;
            this.maxThreads = maxThreads;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private long durationMs;
            private int maxThreads;

            public Builder setDurationMs(long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Builder setMaxThreads(int maxThreads) {
                this.maxThreads = maxThreads;
                return this;
            }

            public Options build() {
                return new Options(durationMs, maxThreads);
            }
        }
    }
}