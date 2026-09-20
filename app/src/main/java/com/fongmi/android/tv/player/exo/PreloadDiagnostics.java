package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.CacheSpan;

import java.util.concurrent.atomic.AtomicLong;

/** Process-local preload counters and last-event snapshot for diagnostics. */
public final class PreloadDiagnostics {

    private static final String TAG = "VodPreload";
    private static final AtomicLong started = new AtomicLong();
    private static final AtomicLong completed = new AtomicLong();
    private static final AtomicLong cancelled = new AtomicLong();
    private static final AtomicLong failed = new AtomicLong();
    private static final AtomicLong skipped = new AtomicLong();
    private static volatile Snapshot latest = new Snapshot("idle", "", 0, 0, "", System.currentTimeMillis());

    private PreloadDiagnostics() {
    }

    public static void started(MediaItem item, long startPositionMs, long durationMs) {
        started.incrementAndGet();
        update("started", item, 0, startPositionMs, durationMs + "ms");
    }

    public static void progress(MediaItem item, long bytes, float percentage) {
        if (percentage < 0 || ((int) percentage) % 10 != 0) return;
        update("progress", item, bytes, 0, Math.round(percentage) + "%");
    }

    public static void completed(MediaItem item, long bytes) {
        completed.incrementAndGet();
        update("completed", item, bytes, 0, "");
    }

    public static void cancelled(@Nullable MediaItem item) {
        cancelled.incrementAndGet();
        update("cancelled", item, 0, 0, "");
    }

    public static void failed(MediaItem item, Throwable error) {
        failed.incrementAndGet();
        String detail = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        update("failed", item, 0, 0, detail);
        Log.w(TAG, detail, error);
    }

    public static void skipped(MediaItem item, String reason) {
        skipped.incrementAndGet();
        update("skipped", item, 0, 0, reason);
    }

    public static Snapshot latest() {
        return latest;
    }

    public static Counters counters() {
        return new Counters(started.get(), completed.get(), cancelled.get(), failed.get(), skipped.get());
    }

    /** Clears process-local counters. Used by the settings diagnostics view and debug harnesses. */
    public static void reset() {
        started.set(0);
        completed.set(0);
        cancelled.set(0);
        failed.set(0);
        skipped.set(0);
        latest = new Snapshot("idle", "", 0, 0, "", System.currentTimeMillis());
    }

    /** Human readable snapshot for the settings diagnostics dialog. */
    public static String summary() {
        Counters c = counters();
        Snapshot s = latest();
        return "started=" + c.started()
                + "\ncompleted=" + c.completed()
                + "\ncancelled=" + c.cancelled()
                + "\nfailed=" + c.failed()
                + "\nskipped=" + c.skipped()
                + "\n\nlast=" + s.state()
                + "\nsource=" + s.source()
                + "\nbytes=" + s.bytes()
                + "\ndetail=" + s.detail();
    }

    /**
     * Bytes currently held in the shared playback cache for {@code item}. Because normal playback
     * only reads from that cache (the write sink is disabled), a positive value means the next
     * playback of this exact media item will be served from disk. Returns {@code -1} on failure.
     */
    public static long cachedBytes(@Nullable MediaItem item) {
        if (item == null || item.localConfiguration == null) return -1;
        try {
            Cache cache = MediaSourceFactory.getCache();
            String key = CacheKeyFactory.DEFAULT.buildCacheKey(new DataSpec(item.localConfiguration.uri));
            long total = 0;
            for (CacheSpan span : cache.getCachedSpans(key)) total += span.length;
            return total;
        } catch (Exception e) {
            Log.w(TAG, "cache query failed", e);
            return -1;
        }
    }

    private static void update(String state, @Nullable MediaItem item, long bytes, long positionMs, String detail) {
        String source = source(item);
        latest = new Snapshot(state, source, bytes, positionMs, detail, System.currentTimeMillis());
        Log.d(TAG, state + " source=" + source + " bytes=" + bytes + " position=" + positionMs + " " + detail);
    }

    private static String source(@Nullable MediaItem item) {
        if (item == null || item.localConfiguration == null) return "";
        Uri uri = item.localConfiguration.uri;
        String host = uri.getHost();
        return uri.getScheme() + "://" + (host == null ? "local" : host);
    }

    public record Snapshot(String state, String source, long bytes, long positionMs, String detail, long timestampMs) {
    }

    public record Counters(long started, long completed, long cancelled, long failed, long skipped) {
    }
}
