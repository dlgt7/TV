package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.engine.PlaybackRecoveryPolicy;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;

import java.util.concurrent.TimeUnit;

/** Owns one ExoPlayer instance and all resources whose lifecycle must match that instance. */
final class ExoPlayerSession {

    private final Runnable retryRunnable = this::retryTransient;
    private final ExoVolumeGain volumeGain;
    private final PreCache preCache;
    private final ExoPlayer player;
    private final int decode;

    private PlaySpec spec;
    private int attempts;
    private long retryPositionMs;
    private boolean released;

    ExoPlayerSession(int decode, Player.Listener listener) {
        this.decode = decode == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD;
        this.player = ExoUtil.buildPlayer(this.decode, listener);
        this.preCache = new PreCache();
        this.volumeGain = new ExoVolumeGain();
        this.volumeGain.attach(player);
    }

    ExoPlayer player() {
        return player;
    }

    void setVolumeGain(float gain) {
        volumeGain.setGain(gain);
    }

    void start(PlaySpec spec, long startPositionMs) {
        this.spec = spec;
        cancelPendingRetry();
        attempts = 0;
        startInternal(startPositionMs);
    }

    void preload(PlaySpec spec, long startPositionMs) {
        if (spec != null) preCache.preload(MediaItemFactory.from(spec, decode), startPositionMs);
    }

    void clearPreload() {
        preCache.clearPreload();
    }

    void stop() {
        cancelPendingRetry();
        preCache.stop();
        player.stop();
    }

    void resetErrorBudget() {
        attempts = 0;
    }

    boolean isLive() {
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    PlayerEngine.ErrorAction handleError(PlaybackException error) {
        PlaybackRecoveryPolicy.Action action = PlaybackRecoveryPolicy.decide(error.errorCode, attempts);
        return switch (action) {
            case SEEK_DEFAULT -> seekToDefaultPosition();
            case SWITCH_DECODE -> PlayerEngine.ErrorAction.DECODE;
            case RETRY_FORMAT -> retryFormat(error.errorCode);
            case RETRY_TRANSIENT -> retryTransientLater();
            case FATAL -> PlayerEngine.ErrorAction.FATAL;
        };
    }

    void release() {
        if (released) return;
        released = true;
        cancelPendingRetry();
        preCache.release();
        volumeGain.release();
        player.release();
        spec = null;
    }

    private void startInternal(long positionMs) {
        if (released || spec == null) return;
        MediaItem item = MediaItemFactory.from(spec, decode);
        player.setMediaItem(item, positionMs);
        preCache.start(player, item);
        player.prepare();
        player.play();
    }

    private PlayerEngine.ErrorAction seekToDefaultPosition() {
        if (attempts++ >= PlaybackRecoveryPolicy.MAX_ATTEMPTS) return PlayerEngine.ErrorAction.FATAL;
        player.seekToDefaultPosition();
        player.prepare();
        return PlayerEngine.ErrorAction.RECOVERED;
    }

    private PlayerEngine.ErrorAction retryFormat(int errorCode) {
        if (spec == null) return PlayerEngine.ErrorAction.FATAL;
        attempts++;
        spec.setFormat(ExoUtil.getMimeType(errorCode));
        startInternal(player.getCurrentPosition());
        return PlayerEngine.ErrorAction.RECOVERED;
    }

    private PlayerEngine.ErrorAction retryTransientLater() {
        attempts++;
        retryPositionMs = Math.max(0, player.getCurrentPosition());
        cancelPendingRetry();
        App.post(retryRunnable, PlaybackRecoveryPolicy.retryDelayMs(attempts - 1));
        return PlayerEngine.ErrorAction.RECOVERED;
    }

    private void retryTransient() {
        if (!released && spec != null) startInternal(retryPositionMs);
    }

    private void cancelPendingRetry() {
        App.removeCallbacks(retryRunnable);
    }
}
