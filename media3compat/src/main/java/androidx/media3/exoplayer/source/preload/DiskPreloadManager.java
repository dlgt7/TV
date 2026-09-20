package androidx.media3.exoplayer.source.preload;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.PriorityDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Disk-backed preloader implemented only with public Media3 APIs. */
public final class DiskPreloadManager {

    public interface Listener {
        default void onPrepared(MediaItem mediaItem) {
        }

        default void onProgress(MediaItem mediaItem, long contentLength, long bytesDownloaded, float percentageDownloaded) {
        }

        default void onCompleted(MediaItem mediaItem, long bytesDownloaded) {
        }

        default void onCancelled(MediaItem mediaItem) {
        }

        default void onError(MediaItem mediaItem, IOException error) {
        }
    }

    private final Context context;
    private final Cache cache;
    private final DataSource.Factory dataSourceFactory;
    private final RenderersFactory renderersFactory;
    @Nullable
    private final PriorityTaskManager priorityTaskManager;
    @Nullable
    private final Listener listener;

    private DefaultPreloadManager preloadManager;
    private ExecutorService executor;
    private MediaItem mediaItem;
    private boolean finished;
    private boolean priorityRegistered;
    private boolean released = true;

    private DiskPreloadManager(Builder builder) {
        this.context = builder.context.getApplicationContext();
        this.cache = builder.cache;
        this.dataSourceFactory = builder.dataSourceFactory;
        this.renderersFactory = builder.renderersFactory;
        this.priorityTaskManager = builder.priorityTaskManager;
        this.listener = builder.listener;
    }

    @SuppressWarnings("unused")
    public void start(ExoPlayer player, MediaItem mediaItem, Options options) {
        release();
        this.mediaItem = mediaItem;
        this.finished = false;
        this.released = false;
        this.executor = Executors.newFixedThreadPool(Math.clamp(options.maxThreads, 1, 10));

        DataSource.Factory upstreamFactory = dataSourceFactory;
        if (priorityTaskManager != null) {
            priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
            priorityRegistered = true;
            upstreamFactory = new PriorityDataSource.Factory(dataSourceFactory, priorityTaskManager, C.PRIORITY_DOWNLOAD);
        }

        DefaultPreloadManager.PreloadStatus target = DefaultPreloadManager.PreloadStatus.specifiedRangeCached(
                options.startPositionMs, options.durationMs);
        preloadManager = new DefaultPreloadManager.Builder(context, rankingData -> target)
                .setDataSourceFactory(upstreamFactory)
                .setRenderersFactory(renderersFactory)
                .setCache(cache)
                .setCachingExecutor(executor)
                .build();
        preloadManager.addListener(new ManagerListener());
        preloadManager.setCurrentPlayingIndex(-1);
        preloadManager.add(mediaItem, 0);
        if (listener != null) listener.onPrepared(mediaItem);
        preloadManager.invalidate();
    }

    /**
     * Cancels work while retaining downloaded spans in the caller-owned shared cache.
     * Eviction remains controlled by that cache's LRU policy.
     */
    public void release() {
        if (released) return;
        released = true;
        if (preloadManager != null) {
            preloadManager.release();
            preloadManager = null;
        }
        if (!finished && listener != null && mediaItem != null) listener.onCancelled(mediaItem);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        unregisterPriority();
        mediaItem = null;
        finished = false;
    }

    private void unregisterPriority() {
        if (priorityRegistered && priorityTaskManager != null) {
            priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
            priorityRegistered = false;
        }
    }

    private final class ManagerListener implements PreloadManagerListener {

        @Override
        public void onCompleted(MediaItem item) {
            if (released || finished || !item.equals(mediaItem)) return;
            finished = true;
            unregisterPriority();
            if (listener != null) listener.onCompleted(item, 0);
        }

        @Override
        public void onError(PreloadException exception) {
            MediaItem item = exception.mediaItem;
            if (released || finished || !item.equals(mediaItem)) return;
            finished = true;
            unregisterPriority();
            Throwable cause = exception.getCause();
            IOException error = cause instanceof IOException io ? io : new IOException(exception);
            if (listener != null) listener.onError(item, error);
        }
    }

    public static final class Builder {

        private final Context context;
        private final Cache cache;
        private final DataSource.Factory dataSourceFactory;
        private final RenderersFactory renderersFactory;
        @Nullable
        private PriorityTaskManager priorityTaskManager;
        @Nullable
        private Listener listener;

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

        public Builder setListener(@Nullable Listener listener) {
            this.listener = listener;
            return this;
        }

        public DiskPreloadManager build() {
            return new DiskPreloadManager(this);
        }
    }

    public static final class Options {

        private final long startPositionMs;
        private final long durationMs;
        private final int maxThreads;

        private Options(long startPositionMs, long durationMs, int maxThreads) {
            this.startPositionMs = Math.max(0, startPositionMs);
            this.durationMs = Math.max(0, durationMs);
            this.maxThreads = maxThreads;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private long startPositionMs;
            private long durationMs;
            private int maxThreads = 1;

            public Builder setStartPositionMs(long startPositionMs) {
                this.startPositionMs = startPositionMs;
                return this;
            }

            public Builder setDurationMs(long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Builder setMaxThreads(int maxThreads) {
                this.maxThreads = maxThreads;
                return this;
            }

            public Options build() {
                return new Options(startPositionMs, durationMs, maxThreads);
            }
        }
    }
}
