package com.fongmi.android.tv.player.exo;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;

/** EXO engine facade; per-player resources are owned by {@link ExoPlayerSession}. */
public class ExoPlayerEngine implements PlayerEngine {

    private final ErrorMsgProvider provider;
    private final Player.Listener listener;
    private ExoPlayerSession session;
    private int decode;

    public ExoPlayerEngine(int decode, Player.Listener listener) {
        this.decode = normalizeDecode(decode);
        this.listener = listener;
        this.provider = new ErrorMsgProvider();
        this.session = new ExoPlayerSession(this.decode, listener);
    }

    @Override
    public Type getType() {
        return Type.EXO;
    }

    @Override
    public Player getPlayer() {
        return session.player();
    }

    @Override
    public void release() {
        session.release();
    }

    @Override
    public Player rebuild() {
        session.release();
        session = new ExoPlayerSession(decode, listener);
        return session.player();
    }

    @Override
    public void setVolumeGain(float gain) {
        session.setVolumeGain(gain);
    }

    @Override
    public boolean setDecode(int decode) {
        this.decode = normalizeDecode(decode);
        return true;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        session.start(spec, startPositionMs);
    }

    @Override
    public void preload(PlaySpec spec, long startPositionMs) {
        session.preload(spec, startPositionMs);
    }

    @Override
    public void clearPreload() {
        session.clearPreload();
    }

    @Override
    public void stop() {
        session.stop();
    }

    @Override
    public void resetErrorBudget() {
        session.resetErrorBudget();
    }

    @Override
    public boolean isLive() {
        return session.isLive();
    }

    @Override
    public boolean isVod() {
        return session.isVod();
    }

    @Override
    public String getErrorMessage(PlaybackException error) {
        return provider.get(error);
    }

    @Override
    public ErrorAction handleError(PlaybackException error) {
        return session.handleError(error);
    }

    private static int normalizeDecode(int decode) {
        return decode == SOFT ? SOFT : HARD;
    }
}
