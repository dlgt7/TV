package com.fongmi.android.tv.player;

import android.net.Uri;
import android.os.SystemClock;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.test.CoreFixtureServer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Exercises the manager's actual rebuild/start ordering, including asynchronous prepare. */
@RunWith(AndroidJUnit4.class)
public class PlayerRebuildStateTest {
    private PlayerManager manager;
    private final AtomicReference<String> error = new AtomicReference<>();

    @Test public void pausedSpeedSurvivesAudioAndDecodeRebuilds() {
        CoreFixtureServer.ensureStarted();
        int originalEngine = PlayerSetting.getEngine();
        boolean originalTunnel = PlayerSetting.isTunnel();
        try {
            main(() -> {
                PlayerSetting.putEngine(PlayerSetting.ENGINE_EXO);
                PlayerSetting.putTunnel(false);
                manager = new PlayerManager(new EmptyCallback() {
                    @Override public void onError(String message) { error.set(message); }
                });
                manager.start(PlaySpec.from("rebuild-state", CoreFixtureServer.baseUrl() + "base.mp4",
                        Map.of("X-Core-Media", "base.mp4"), MediaMetadata.EMPTY), 25_000, 0);
            });
            awaitReady();
            main(() -> {
                manager.setSpeed(1.5f);
                manager.getPlayer().pause();
                Player previous = manager.getPlayer();
                manager.rebuildAudioPipeline();
                assertNotSame(previous, manager.getPlayer());
                assertPausedSpeed();
            });
            awaitReady();
            main(() -> {
                assertPausedSpeed();
                Player previous = manager.getPlayer();
                manager.toggleDecode();
                assertNotSame(previous, manager.getPlayer());
                assertPausedSpeed();
            });
            awaitReady();
            main(() -> {
                assertPausedSpeed();
                // Retire the replacement before it has finished preparing. Queued events
                // from either previous player must not change the final player's state.
                manager.rebuildAudioPipeline();
                manager.rebuildAudioPipeline();
                assertPausedSpeed();
            });
            awaitReady();
            main(this::assertPausedSpeed);
            assertNull(error.get());
        } finally {
            main(() -> {
                if (manager != null) manager.release();
                PlayerSetting.putEngine(originalEngine);
                com.github.catvod.utils.Prefers.put("tunnel", originalTunnel);
            });
            CoreFixtureServer.stop();
        }
    }

    private void assertPausedSpeed() {
        assertFalse("Rebuilding must not resume paused playback", manager.getPlayer().getPlayWhenReady());
        assertEquals(1.5f, manager.getSpeed(), 0.001f);
    }

    private void awaitReady() {
        long deadline = SystemClock.elapsedRealtime() + 25_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (error.get() != null) fail(error.get());
            AtomicBoolean ready = new AtomicBoolean();
            main(() -> ready.set(manager.getPlaybackState() == Player.STATE_READY));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("Player did not become ready");
    }

    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }

    private static class EmptyCallback implements PlayerManager.Callback {
        @Override public void onPrepare() {}
        @Override public void onTracksChanged() {}
        @Override public void onDecodeChanged() {}
        @Override public void onMediaOptionsChanged() {}
        @Override public void onError(String msg) {}
        @Override public void onPlayerRebuild(Player player) {}
        @Override public void onDanmakuSourceChanged(Uri uri) {}
        @Override public void onDanmakuConfigChanged(DanmakuConfig config) {}
        @Override public void onDanmakuEnabledChanged(boolean enabled) {}
        @Override public void onDanmakuSent(String text) {}
    }
}
