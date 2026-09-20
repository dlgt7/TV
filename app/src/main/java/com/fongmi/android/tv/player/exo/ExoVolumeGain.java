package com.fongmi.android.tv.player.exo;

import android.media.audiofx.LoudnessEnhancer;

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/** Applies gain above unity to ExoPlayer through the platform audio session. */
final class ExoVolumeGain implements Player.Listener {

    private ExoPlayer player;
    private LoudnessEnhancer enhancer;
    private float gain = 1f;
    private int audioSessionId = C.AUDIO_SESSION_ID_UNSET;

    void attach(ExoPlayer player) {
        releaseEnhancer();
        if (this.player != null) this.player.removeListener(this);
        this.player = player;
        this.audioSessionId = player == null ? C.AUDIO_SESSION_ID_UNSET : player.getAudioSessionId();
        if (player != null) player.addListener(this);
        apply();
    }

    void setGain(float gain) {
        this.gain = Math.clamp(gain, 0f, 2f);
        apply();
    }

    @Override
    public void onAudioSessionIdChanged(int audioSessionId) {
        if (this.audioSessionId == audioSessionId) return;
        this.audioSessionId = audioSessionId;
        releaseEnhancer();
        apply();
    }

    void release() {
        releaseEnhancer();
        if (player != null) player.removeListener(this);
        player = null;
        audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    }

    private void apply() {
        if (player == null) return;
        // ExoPlayer clamps Player volume to [0, 1]. Keep attenuation there and use an
        // audio-session effect only for gain above unity.
        player.setVolume(Math.min(gain, 1f));
        if (gain <= 1f || audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            releaseEnhancer();
            return;
        }
        try {
            if (enhancer == null) enhancer = new LoudnessEnhancer(audioSessionId);
            enhancer.setTargetGain(toMillibels(gain));
            enhancer.setEnabled(true);
        } catch (RuntimeException ignored) {
            releaseEnhancer();
        }
    }

    private static int toMillibels(float gain) {
        return Math.max(0, Math.round((float) (2000.0 * Math.log10(gain))));
    }

    private void releaseEnhancer() {
        if (enhancer == null) return;
        try {
            enhancer.setEnabled(false);
        } catch (RuntimeException ignored) {
        }
        enhancer.release();
        enhancer = null;
    }
}
