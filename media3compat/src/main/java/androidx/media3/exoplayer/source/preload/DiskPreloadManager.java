package androidx.media3.exoplayer.source.preload;

import android.content.Context;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Disk-backed Media3 preloader used in place of the private upstream implementation. */
public final class DiskPreloadManager {

    private final Context context;
    private final Cache cache;
    private final DataSource.Factory dataSourceFactory;
    private final RenderersFactory renderersFactory;

    private DefaultPreloadManager manager;
    private ExecutorService executor;

    private DiskPreloadManager(Builder builder) {
        this.context = builder.context.getApplicationContext();
        this.cache = builder.cache;
        this.dataSourceFactory = builder.dataSourceFactory;
        this.renderersFactory = builder.renderersFactory;
    }

    public void start(ExoPlayer player, MediaItem mediaItem, Options options) {
        release();
        int threads = Math.clamp(options.maxThreads, 1, 10);
        executor = Executors.newFixedThreadPool(threads);
        manager = new DefaultPreloadManager.Builder(context, ranking ->
                DefaultPreloadManager.PreloadStatus.specifiedRangeCached(options.durationMs))
                .setCache(cache)
                .setDataSourceFactory(dataSourceFactory)
                .setRenderersFactory(renderersFactory)
                .setCachingExecutor(executor)
                .build();
        manager.add(mediaItem, 0);
        manager.invalidate();
    }

    public void release() {
        if (manager != null) {
            manager.release();
            manager = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public static final class Builder {

        private final Context context;
        private final Cache cache;
        private final DataSource.Factory dataSourceFactory;
        private final RenderersFactory renderersFactory;
        @SuppressWarnings("unused")
        private PriorityTaskManager priorityTaskManager;

        public Builder(Context context, Cache cache, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
            this.context = context;
            this.cache = cache;
            this.dataSourceFactory = dataSourceFactory;
            this.renderersFactory = renderersFactory;
        }

        public Builder setPriorityTaskManager(PriorityTaskManager priorityTaskManager) {
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
            this.durationMs = Math.max(0, durationMs);
            this.maxThreads = maxThreads;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private long durationMs;
            private int maxThreads = 1;

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
