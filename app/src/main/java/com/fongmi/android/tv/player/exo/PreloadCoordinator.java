package com.fongmi.android.tv.player.exo;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Path;

/** Conservative foreground-first budget; never infers remote bandwidth from proxy reads. */
final class PreloadCoordinator implements Player.Listener {
    private static final long TICK_MS = 1_000;
    private static final long SEEK_COOLDOWN_MS = 3_000;
    private static final long MIN_FREE_BYTES = 128L * 1024 * 1024;

    private final PreloadBudget budget = new PreloadBudget();
    private final Runnable tick = this::tick;
    private ExoPlayer player;
    private Handler handler;
    private long seekUntilMs;
    private long resourceCheckMs;
    private boolean resourcesAvailable;

    void attach(ExoPlayer player) {
        detach();
        this.player = player;
        handler = new Handler(player.getApplicationLooper());
        player.addListener(this);
        tick();
    }

    void detach() {
        if (handler != null) handler.removeCallbacks(tick);
        if (player != null) player.removeListener(this);
        budget.reset();
        player = null;
        handler = null;
        seekUntilMs = 0;
        resourceCheckMs = 0;
    }

    PreloadBudget.Lease acquire(PreloadBudget.Owner owner, Runnable cancel) {
        refresh();
        return budget.acquire(owner, SystemClock.elapsedRealtime(), cancel);
    }

    void release(PreloadBudget.Lease lease) { budget.release(lease); }
    void cancelWaiting(PreloadBudget.Owner owner) { budget.cancelWaiting(owner); }

    boolean hasResources() {
        long now = SystemClock.elapsedRealtime();
        if (now >= resourceCheckMs) {
            ActivityManager manager = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            if (manager != null) manager.getMemoryInfo(info);
            Runtime runtime = Runtime.getRuntime();
            long heapAvailable = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
            resourcesAvailable = !info.lowMemory && heapAvailable >= 32L * 1024 * 1024
                    && Path.cache().getUsableSpace() >= MIN_FREE_BYTES;
            resourceCheckMs = now + TICK_MS;
        }
        return resourcesAvailable;
    }

    private void refresh() {
        long now = SystemClock.elapsedRealtime();
        boolean ready = player != null && player.getPlaybackState() == Player.STATE_READY
                && !player.isLoading() && !player.isCurrentMediaItemLive()
                && now >= seekUntilMs && hasResources()
                && PreCachePolicy.hasPlaybackReserve(player.getTotalBufferedDuration(), player.getDuration(),
                        player.getBufferedPosition(), player.getPlaybackParameters().speed);
        budget.update(ready, now);
    }

    private void tick() {
        refresh();
        if (handler != null) handler.postDelayed(tick, TICK_MS);
    }

    @Override public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_IDLE && handler != null) handler.post(this::refresh);
        else refresh();
    }
    @Override public void onIsLoadingChanged(boolean loading) { onPlaybackStateChanged(player == null ? Player.STATE_IDLE : player.getPlaybackState()); }
    @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            seekUntilMs = SystemClock.elapsedRealtime() + SEEK_COOLDOWN_MS;
            refresh();
        }
    }
}
