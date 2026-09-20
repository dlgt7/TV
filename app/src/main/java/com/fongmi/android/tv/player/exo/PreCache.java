package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.preload.DiskPreloadManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.PreloadSetting;

/** Pre-caches the next episode into the same disk cache used by normal playback. */
public class PreCache {

    private final PriorityTaskManager priorityTaskManager;
    private DiskPreloadManager manager;
    private MediaItem preloadedItem;
    private ExoPlayer player;

    public PreCache() {
        this.priorityTaskManager = new PriorityTaskManager();
    }

    public void start(ExoPlayer player, MediaItem mediaItem) {
        this.player = player;
        // Keep a matching preload alive until the following episode starts preloading. The normal
        // CacheDataSource can consume its spans without relying on a private MediaSource hand-off.
        if (preloadedItem != null && !preloadedItem.equals(mediaItem)) stopManager();
    }

    public void preload(MediaItem mediaItem) {
        if (player == null || mediaItem == null || !PreloadSetting.isPreload() || !canPreload(mediaItem)) {
            stopManager();
            return;
        }
        if (mediaItem.equals(preloadedItem) && manager != null) return;
        stopManager();
        preloadedItem = mediaItem;
        manager = createManager(mediaItem);
        player.setPriorityTaskManager(priorityTaskManager);
        manager.start(player, mediaItem, createOptions());
    }

    public void clearPreload() {
        stopManager();
    }

    public void stop() {
        stopManager();
        player = null;
    }

    public void release() {
        stop();
    }

    private void stopManager() {
        if (manager != null) manager.release();
        manager = null;
        preloadedItem = null;
        if (player != null) player.setPriorityTaskManager(null);
    }

    private DiskPreloadManager createManager(MediaItem mediaItem) {
        return new DiskPreloadManager.Builder(App.get(), MediaSourceFactory.getCache(), MediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem)), ExoUtil.buildRenderersFactory())
                .setPriorityTaskManager(priorityTaskManager)
                .build();
    }

    private DiskPreloadManager.Options createOptions() {
        return DiskPreloadManager.Options.builder()
                .setDurationMs(PreloadSetting.getPreloadDurationMs())
                .setMaxThreads(PreloadSetting.getPreloadThreads())
                .build();
    }

    private boolean canPreload(MediaItem mediaItem) {
        if (mediaItem.localConfiguration == null) return false;
        String scheme = mediaItem.localConfiguration.uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
