package com.fongmi.android.tv.debug;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.player.exo.ExoUtil;

/**
 * ADB-driven ExoPlayer smoke test.
 *
 * <p>Extras: url (required), decode 0=soft/prefer-ext 1=hard (default 1)
 */
@UnstableApi
public final class ExoSmokeActivity extends Activity implements Player.Listener {

    public static final String TAG = "ExoSmoke";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private int decode;
    private boolean firstFrame;
    private volatile String decoderName = "";

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        String url = getIntent().getStringExtra("url");
        if (url == null) throw new IllegalArgumentException("Missing url extra");
        decode = getIntent().getIntExtra("decode", 1);
        PlayerView view = new PlayerView(this);
        view.setUseController(false);
        setContentView(view);
        player = ExoUtil.buildPlayer(decode, this);
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                ExoSmokeActivity.this.decoderName = decoderName;
                Log.i(TAG, "DECODER name=" + decoderName + " initMs=" + initializationDurationMs);
            }
        });
        view.setPlayer(player);
        Log.i(TAG, "START url=" + url + " decode=" + decode);
        player.setMediaItem(MediaItem.fromUri(url));
        player.setPlayWhenReady(true);
        player.prepare();
        player.play();
        handler.postDelayed(this::report, 12_000);
    }

    private void report() {
        if (player == null) return;
        VideoSize size = player.getVideoSize();
        boolean ready = player.getPlaybackState() == Player.STATE_READY;
        boolean ok = ready && player.getCurrentPosition() > 0 && size.width > 0 && size.height > 0 && firstFrame;
        Log.i(TAG, "RESULT ok=" + ok
                + " state=" + player.getPlaybackState()
                + " playing=" + player.isPlaying()
                + " positionMs=" + player.getCurrentPosition()
                + " durationMs=" + player.getDuration()
                + " decode=" + decode
                + " decoder=" + decoderName
                + " firstFrame=" + firstFrame
                + " video=" + size.width + "x" + size.height);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        Log.i(TAG, "STATE " + state);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        Log.i(TAG, "PLAYING " + isPlaying);
    }

    @Override
    public void onRenderedFirstFrame() {
        firstFrame = true;
        VideoSize size = player != null ? player.getVideoSize() : VideoSize.UNKNOWN;
        Log.i(TAG, "RENDERED_FIRST_FRAME decode=" + decode + " video=" + size.width + "x" + size.height + " decoder=" + decoderName);
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e(TAG, "ERROR code=" + error.errorCode + " message=" + error.getMessage(), error);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) player.release();
        player = null;
        super.onDestroy();
    }
}
