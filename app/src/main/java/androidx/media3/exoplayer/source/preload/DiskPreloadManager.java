package androidx.media3.exoplayer.source.preload;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.PriorityDataSourceFactory;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 磁盘预加载：把指定媒体后续的一段数据提前写入 {@link Cache}，播放时直接命中缓存。
 *
 * <p>按 {@link Options#getMaxThreads()} 把目标区间切片，每个切片由一个 {@link CacheWriter} 并行下载。
 */
public final class DiskPreloadManager {

    /** 未知码率时的保守估计：约 4 Mbps。 */
    private static final long DEFAULT_BYTES_PER_MS = 512L;
    private static final long MIN_SEGMENT_BYTES = 256L * 1024L;

    private final Cache cache;
    private final DataSource.Factory dataSourceFactory;
    private final RenderersFactory renderersFactory;
    @Nullable
    private final PriorityTaskManager priorityTaskManager;

    private final List<CacheWriter> writers;

    @Nullable
    private ExecutorService executor;

    private boolean released;

    private DiskPreloadManager(Builder builder) {
        this.cache = builder.cache;
        this.dataSourceFactory = builder.dataSourceFactory;
        this.renderersFactory = builder.renderersFactory;
        this.priorityTaskManager = builder.priorityTaskManager;
        this.writers = new CopyOnWriteArrayList<>();
    }

    public RenderersFactory getRenderersFactory() {
        return renderersFactory;
    }

    public void start(ExoPlayer player, MediaItem mediaItem, Options options) {
        stop();
        if (released || mediaItem == null || options == null) return;
        Uri uri = getUri(mediaItem);
        if (uri == null) return;
        String key = getCacheKey(uri);
        long contentLength = getContentLength(key);
        long start = getStartPosition(key);
        long length = getTargetLength(player, options.getDurationMs(), contentLength, start);
        if (length <= 0) return;
        int threads = Math.max(1, options.getMaxThreads());
        ExecutorService service = Executors.newFixedThreadPool(threads);
        executor = service;
        long segment = Math.max(MIN_SEGMENT_BYTES, (length + threads - 1) / threads);
        for (int i = 0; i < threads; i++) {
            long position = start + i * segment;
            long remaining = start + length - position;
            if (remaining <= 0) break;
            if (!submit(service, uri, position, Math.min(segment, remaining))) break;
        }
    }

    public void stop() {
        for (CacheWriter writer : writers) writer.cancel();
        writers.clear();
        ExecutorService service = executor;
        executor = null;
        if (service != null) service.shutdownNow();
    }

    public void release() {
        stop();
        released = true;
    }

    private boolean submit(ExecutorService service, Uri uri, long position, long length) {
        DataSpec dataSpec = new DataSpec.Builder().setUri(uri).setPosition(position).setLength(length).build();
        CacheWriter writer = new CacheWriter(createCacheDataSource(), dataSpec, new byte[CacheWriter.DEFAULT_BUFFER_SIZE_BYTES], (requested, cached, newCached) -> {
        });
        writers.add(writer);
        try {
            service.execute(() -> {
                try {
                    writer.cache();
                } catch (Throwable ignored) {
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private CacheDataSource createCacheDataSource() {
        DataSource.Factory upstream = dataSourceFactory;
        if (priorityTaskManager != null) upstream = new PriorityDataSourceFactory(upstream, priorityTaskManager, C.PRIORITY_DOWNLOAD);
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource();
    }

    private long getContentLength(String key) {
        try {
            return ContentMetadata.getContentLength(cache.getContentMetadata(key));
        } catch (Throwable e) {
            return C.LENGTH_UNSET;
        }
    }

    private long getStartPosition(String key) {
        try {
            NavigableSet<CacheSpan> spans = cache.getCachedSpans(key);
            if (spans == null || spans.isEmpty()) return 0;
            CacheSpan first = spans.first();
            return first.position <= 0 ? first.position + first.length : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    private long getTargetLength(ExoPlayer player, long durationMs, long contentLength, long start) {
        if (durationMs <= 0) durationMs = 10_000L;
        long remaining = contentLength == C.LENGTH_UNSET ? Long.MAX_VALUE : contentLength - start;
        if (remaining <= 0) return 0;
        long duration = player == null ? C.TIME_UNSET : player.getDuration();
        if (contentLength != C.LENGTH_UNSET && duration > 0) {
            long bytes = (long) ((double) contentLength * durationMs / duration);
            return Math.min(bytes, remaining);
        }
        return Math.min(durationMs * DEFAULT_BYTES_PER_MS, remaining);
    }

    @Nullable
    private static Uri getUri(MediaItem mediaItem) {
        if (mediaItem.localConfiguration == null) return null;
        return mediaItem.localConfiguration.uri;
    }

    private static String getCacheKey(Uri uri) {
        return CacheKeyFactory.DEFAULT.buildCacheKey(new DataSpec.Builder().setUri(uri).build());
    }

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

        public long getDurationMs() {
            return durationMs;
        }

        public int getMaxThreads() {
            return maxThreads;
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
                return new Options(durationMs, Math.max(1, maxThreads));
            }
        }
    }
}
