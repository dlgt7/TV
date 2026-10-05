package com.fongmi.android.tv.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;
import androidx.media3.ui.danmaku.DanmakuView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Real local-XML loading and Android Canvas rendering; no media, network, models or preferences. */
@RunWith(AndroidJUnit4.class)
public final class PausedDanmakuVisibilityTest {
    private static final long PAUSED_POSITION_MS = 13_000L;
    private static final int MIN_TEXT_PIXELS = 32;
    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <i>
              <d p="6.0,5,36,16711680,0,0,0,1">EXPIRED RED</d>
              <d p="11.5,4,36,16776960,0,0,0,2">VALID YELLOW BOTTOM</d>
              <d p="12.0,5,36,65280,0,0,0,3">VALID GREEN TOP</d>
              <d p="30.0,4,36,255,0,0,0,4">FUTURE BLUE</d>
            </i>
            """;
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicInteger loaded = new AtomicInteger();
    private final AtomicReference<IOException> loadingError = new AtomicReference<>();
    private CorePlaybackActivity activity;
    private FixedPositionPlayer player;
    private File fixture;

    @Before public void setup() throws Exception {
        fixture = File.createTempFile("paused-danmaku-", ".xml",
                instrumentation.getTargetContext().getCacheDir());
        try (FileOutputStream output = new FileOutputStream(fixture)) {
            output.write(XML.getBytes(StandardCharsets.UTF_8));
        }
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), CorePlaybackActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            player = new FixedPositionPlayer();
            activity.view.setUseController(false);
            activity.view.setDanmakuEnabled(false);
            activity.view.setDanmakuConfig(new DanmakuConfig.Builder()
                    .setTextScale(1f)
                    .setTransparency(0f)
                    .setColorMode(DanmakuConfig.COLOR_MODE_DEFAULT)
                    .setFixedDurationMs(6000L)
                    .setMaxTopLines(3)
                    .setMaxBottomLines(3)
                    .setShowTop(true)
                    .setShowBottom(true)
                    .build());
            activity.view.getDanmakuController().setListener(new DanmakuController.Listener() {
                @Override public void onLoadCompleted(Uri uri, int count) { loaded.set(count); }
                @Override public void onLoadError(Uri uri, IOException error) { loadingError.set(error); }
            });
            activity.view.setPlayer(player);
        });
        await(() -> activity.view.getDanmakuView() != null
                && activity.view.getDanmakuView().getWidth() > 0
                && activity.view.getDanmakuView().getHeight() > 0, "DanmakuView laid out");
    }

    @After public void cleanup() {
        try {
            if (activity != null) main(() -> {
                try {
                    activity.view.setPlayer(null);
                    activity.view.getDanmakuController().release();
                    DanmakuView view = activity.view.getDanmakuView();
                    if (view != null) view.release();
                    if (player != null) player.release();
                } finally {
                    activity.finish();
                }
            });
            instrumentation.waitForIdleSync();
        } finally {
            if (fixture != null && fixture.exists()) {
                assertTrue("Temporary local XML must be removed", fixture.delete());
            }
        }
    }

    @Test public void firstLoadWhilePausedRendersOnlyCurrentlyValidText() {
        assertPausedPlayerUnchanged();
        assertEquals("No text before the source is loaded", 0, snapshot().painted);
        loadThroughPlayerView();
        await(() -> loaded.get() == 4, "all four local XML items parsed");
        awaitCurrentText("first load while paused");
        PixelSnapshot frame = snapshot();
        assertCurrentText(frame);
        main(() -> {
            assertTrue("The view needs a paused render clock", activity.view.getDanmakuView().isStarted());
            assertTrue(activity.view.getDanmakuView().isPaused());
        });
        assertPausedPlayerUnchanged();
    }

    @Test public void hideThenShowWhilePausedRestoresTheSameTextFrameWithoutPlayback() {
        // Prime a normal playing frame so the old implementation reaches the hide/show regression.
        // The first test independently covers loading a source before the player ever plays.
        main(() -> activity.view.setDanmakuSource(Uri.fromFile(fixture)));
        await(() -> loaded.get() == 4, "all four local XML items parsed");
        main(() -> {
            player.startForSetup(10_000L);
            activity.view.setDanmakuEnabled(true);
        });
        awaitCurrentText("playing fixture primed");
        main(() -> player.pauseForSetup(PAUSED_POSITION_MS));
        await(() -> activity.view.getDanmakuView().isPaused(), "render clock paused");
        PixelSnapshot before = snapshot();
        assertCurrentText(before);
        assertPausedPlayerUnchanged();

        main(() -> activity.view.setDanmakuEnabled(false));
        assertEquals("Disabling must remove all painted text while paused", 0, snapshot().painted);
        assertPausedPlayerUnchanged();

        main(() -> activity.view.setDanmakuEnabled(true));
        awaitCurrentText("text restored after paused re-enable");
        PixelSnapshot restored = snapshot();
        assertCurrentText(restored);
        assertArrayEquals("At the same paused position, the same fixed text must be repainted",
                before.pixels, restored.pixels);
        main(() -> assertTrue("Re-enable must preserve the paused render clock",
                activity.view.getDanmakuView().isPaused()));
        assertPausedPlayerUnchanged();
    }

    private void loadThroughPlayerView() {
        main(() -> {
            activity.view.setDanmakuEnabled(true);
            activity.view.setDanmakuSource(Uri.fromFile(fixture));
        });
    }

    private void assertPausedPlayerUnchanged() {
        main(() -> {
            assertEquals(PAUSED_POSITION_MS, player.getCurrentPosition());
            assertFalse(player.getPlayWhenReady());
            assertFalse(player.isPlaying());
            assertEquals("Danmaku changes must not issue player play/pause/seek commands",
                    0, player.playbackMutationCalls);
        });
    }

    private void awaitCurrentText(String reason) {
        await(() -> {
            PixelSnapshot pixels = snapshotOnMain();
            return pixels.green >= MIN_TEXT_PIXELS && pixels.yellow >= MIN_TEXT_PIXELS;
        }, reason);
    }

    private static void assertCurrentText(PixelSnapshot frame) {
        assertTrue("Still-valid top text must be visible", frame.green >= MIN_TEXT_PIXELS);
        assertTrue("Still-valid bottom text must be visible", frame.yellow >= MIN_TEXT_PIXELS);
        assertEquals("Expired red text must not be revived", 0, frame.red);
        assertEquals("Future blue text must not be activated early", 0, frame.blue);
    }

    private PixelSnapshot snapshot() { return read(this::snapshotOnMain); }

    private PixelSnapshot snapshotOnMain() {
        DanmakuView view = activity.view.getDanmakuView();
        assertNotNull(view);
        assertTrue("The real overlay must remain attached", view.isAttachedToWindow());
        assertEquals(View.VISIBLE, view.getVisibility());
        int width = Math.min(480, view.getWidth());
        int height = Math.max(1, Math.round((float) view.getHeight() * width / view.getWidth()));
        assertTrue("The real overlay must have nonzero dimensions", width > 0);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.scale((float) width / view.getWidth(), (float) height / view.getHeight());
            // This is the production DanmakuView draw path; the test never starts/seeks its clock
            // or injects active render items to compensate for the controller's paused behavior.
            view.draw(canvas);
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        } finally {
            bitmap.recycle();
        }
        int painted = 0, green = 0, yellow = 0, red = 0, blue = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha != 0) painted++;
            if (alpha < 32) continue;
            int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
            if (g > 100 && g > r + 40 && g > b + 40) green++;
            if (r > 100 && g > 100 && b < 60) yellow++;
            if (r > 100 && r > g + 40 && r > b + 40) red++;
            if (b > 100 && b > r + 40 && b > g + 40) blue++;
        }
        return new PixelSnapshot(pixels, painted, green, yellow, red, blue);
    }

    private void await(BooleanSupplier condition, String reason) {
        long deadline = SystemClock.elapsedRealtime() + 8000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (loadingError.get() != null) fail("Local XML loading failed: " + loadingError.get().getClass().getSimpleName());
            if (read(condition::getAsBoolean)) return;
            SystemClock.sleep(40L);
        }
        fail("Timed out: " + reason);
    }

    private void main(Runnable action) { read(() -> { action.run(); return null; }); }

    private <T> T read(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set(action.get()); }
            catch (Throwable failure) { error.set(failure); }
        });
        if (error.get() != null) throw new AssertionError("Main-thread test operation failed", error.get());
        return result.get();
    }

    private record PixelSnapshot(int[] pixels, int painted, int green, int yellow, int red, int blue) {}

    /** No decoder or media is created; listeners and commands use the real Media3 base player. */
    private static final class FixedPositionPlayer extends SimpleBasePlayer {
        private long positionMs = PAUSED_POSITION_MS;
        private long playingSinceMs;
        private boolean playWhenReady;
        int playbackMutationCalls;

        FixedPositionPlayer() { super(Looper.getMainLooper()); }

        void startForSetup(long position) {
            positionMs = position;
            playingSinceMs = SystemClock.elapsedRealtime();
            playWhenReady = true;
            invalidateState();
        }

        void pauseForSetup(long position) {
            positionMs = position;
            playWhenReady = false;
            invalidateState();
        }

        @Override protected State getState() {
            return new State.Builder()
                    .setAvailableCommands(new Player.Commands.Builder().addAll(
                            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
                            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).build())
                    .setPlaylist(Collections.singletonList(new MediaItemData.Builder("paused-danmaku-fixture")
                            .setMediaItem(new MediaItem.Builder().setMediaId("paused-danmaku-fixture").build())
                            .setDurationUs(60_000_000L).setIsSeekable(true).build()))
                    .setCurrentMediaItemIndex(0)
                    .setPlaybackState(Player.STATE_READY)
                    .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setContentPositionMs(() -> positionMs
                            + (playWhenReady ? SystemClock.elapsedRealtime() - playingSinceMs : 0L))
                    .build();
        }

        @Override protected ListenableFuture<?> handleSetPlayWhenReady(boolean value) {
            playbackMutationCalls++;
            if (playWhenReady) positionMs += SystemClock.elapsedRealtime() - playingSinceMs;
            playingSinceMs = SystemClock.elapsedRealtime();
            playWhenReady = value;
            return Futures.immediateFuture(null);
        }

        @Override protected ListenableFuture<?> handleSeek(int index, long value, int command) {
            playbackMutationCalls++;
            positionMs = value;
            return Futures.immediateFuture(null);
        }
    }
}
