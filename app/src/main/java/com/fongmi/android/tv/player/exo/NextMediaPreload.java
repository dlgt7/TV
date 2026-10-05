package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.exoplayer.source.preload.PreloadException;
import androidx.media3.exoplayer.source.preload.PreloadManagerListener;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.PreloadSetting;

/** One bounded next-item buffer. Current playback always gets network priority. */
final class NextMediaPreload implements Player.Listener {
    private final DefaultPreloadManager manager;
    final ExoPlayer player;
    private MediaItem pending;
    private long startMs;
    private boolean added;
    private boolean consumed;
    private boolean allowed;
    private boolean released;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferences = (prefs, key) -> {
        if ("preload_next".equals(key)) com.fongmi.android.tv.App.post(() -> { if (!released) update(); });
    };

    NextMediaPreload(int decode, Player.Listener listener, com.fongmi.android.tv.player.subtitle.AdvancedSubtitleController subtitles) {
        DefaultPreloadManager.Builder builder = new DefaultPreloadManager.Builder(App.get(),
                ignored -> allowed ? DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(startMs, 10_000) : null)
                .setMediaSourceFactory(new MediaSourceFactory(subtitles))
                .setTrackSelectorFactory(context -> {
                    com.fongmi.android.tv.player.track.DualSubtitleTrackSelector selector = new com.fongmi.android.tv.player.track.DualSubtitleTrackSelector(
                            (androidx.media3.exoplayer.trackselection.DecodeTrackSelector) ExoUtil.buildTrackSelector(decode), subtitles.secondaryRendererCount());
                    subtitles.addSelector(selector);
                    return selector;
                })
                .setRenderersFactory(subtitles.renderers(ExoUtil.buildPlaybackRenderersFactory(decode)))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setPlayerTargetBufferBytes(PlayerId.PRELOAD.name, 64 * 1024 * 1024).build());
        manager = builder.build();
        player = ExoUtil.buildPlayer(decode, listener, builder);
        player.addListener(this);
        com.github.catvod.utils.Prefers.getPrefers().registerOnSharedPreferenceChangeListener(preferences);
        manager.addListener(new PreloadManagerListener() {
            @Override public void onError(PreloadException error) {
                if (pending != null && pending.equals(error.mediaItem)) clear();
            }
        });
    }

    void preload(MediaItem item, long positionMs) {
        clear();
        pending = item;
        startMs = Math.max(0, positionMs);
        update();
    }

    MediaSource take(MediaItem item) {
        if (pending == null || !added || consumed || !sameMedia(pending, item)) {
            clear();
            return null;
        }
        MediaSource source = manager.getMediaSource(pending);
        consumed = source != null;
        return source;
    }

    static boolean sameMedia(MediaItem a, MediaItem b) {
        return a.mediaId.equals(b.mediaId) && java.util.Objects.equals(a.localConfiguration, b.localConfiguration)
                && ExoUtil.extractHeaders(a).equals(ExoUtil.extractHeaders(b));
    }

    private void update() {
        if (!PreloadSetting.isNextEpisode()) { clear(); return; }
        if (pending == null || consumed) return;
        // stop() during an episode handoff is followed synchronously by take(). Keep the
        // prepared source alive until the new request either consumes or explicitly clears it.
        if (player.getPlaybackState() == Player.STATE_IDLE) return;
        allowed = PreloadSetting.isNextEpisode() && player.getPlaybackState() == Player.STATE_READY
                && !player.isLoading() && !player.isCurrentMediaItemLive();
        if (!allowed && added) {
            manager.remove(pending);
            added = false;
        } else if (allowed && !added) {
            manager.add(pending, 0);
            added = true;
            manager.invalidate();
        }
    }

    void clear() {
        if (pending != null && added) manager.remove(pending);
        pending = null;
        added = false;
        consumed = false;
        allowed = false;
    }

    void release() {
        released = true;
        com.github.catvod.utils.Prefers.getPrefers().unregisterOnSharedPreferenceChangeListener(preferences);
        player.removeListener(this);
        clear();
        manager.release();
    }

    @Override public void onPlaybackStateChanged(int state) { update(); }
    @Override public void onIsLoadingChanged(boolean loading) { update(); }
    @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            allowed = false;
            if (pending != null && added && !consumed) { manager.remove(pending); added = false; }
        }
    }
}
