package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;

import java.util.concurrent.TimeUnit;

public class ExoPlayerEngine implements PlayerEngine {

    private final ErrorMsgProvider provider;
    private final Player.Listener listener;
    private final PreCache preCache;
    private final ExoVolumeGain volumeGain;
    private ExoPlayer player;
    private PlaySpec spec;
    private int decode;

    public ExoPlayerEngine(int decode, Player.Listener listener) {
        decode = decode == SOFT ? SOFT : HARD;
        this.player = ExoUtil.buildPlayer(decode, listener);
        this.provider = new ErrorMsgProvider();
        this.preCache = new PreCache();
        this.volumeGain = new ExoVolumeGain();
        this.listener = listener;
        this.decode = decode;
        this.volumeGain.attach(player);
    }

    @Override
    public Type getType() {
        return Type.EXO;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        preCache.release();
        volumeGain.release();
        player.release();
    }

    @Override
    public Player rebuild() {
        preCache.stop();
        volumeGain.release();
        player.release();
        player = ExoUtil.buildPlayer(decode, listener);
        volumeGain.attach(player);
        return player;
    }

    @Override
    public void setVolumeGain(float gain) {
        volumeGain.setGain(gain);
    }

    @Override
    public boolean setDecode(int decode) {
        // EXO only has soft/hard; HARD_PERFORMANCE is an MPV-only path.
        this.decode = decode == SOFT ? SOFT : HARD;
        return true;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        this.spec = spec;
        startInternal(startPositionMs);
    }

    @Override
    public void preload(PlaySpec spec, long startPositionMs) {
        if (spec != null) preCache.preload(MediaItemFactory.from(spec, decode));
    }

    @Override
    public void clearPreload() {
        preCache.clearPreload();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public boolean isLive() {
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    @Override
    public boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> seekToDefaultPosition();
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED -> ErrorAction.DECODE;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> retryFormat(e.errorCode);
            default -> ErrorAction.FATAL;
        };
    }

    private void startInternal(long position) {
        MediaItem item = MediaItemFactory.from(spec, decode);
        player.setMediaItem(item, position);
        preCache.start(player, item);
        player.prepare();
        player.play();
    }

    private ErrorAction seekToDefaultPosition() {
        player.seekToDefaultPosition();
        player.prepare();
        return ErrorAction.RECOVERED;
    }

    private ErrorAction retryFormat(int errorCode) {
        spec.setFormat(ExoUtil.getMimeType(errorCode));
        startInternal(player.getCurrentPosition());
        return ErrorAction.RECOVERED;
    }
}
