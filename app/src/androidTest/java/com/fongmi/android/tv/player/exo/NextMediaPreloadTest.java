package com.fongmi.android.tv.player.exo;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.subtitle.AdvancedSubtitleController;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.fongmi.android.tv.test.CoreFixtureServer;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NextMediaPreloadTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicReference<PlaybackException> error = new AtomicReference<>();
    private NextMediaPreload next;
    private PreloadCoordinator coordinator;
    private AdvancedSubtitleController subtitles;
    private CorePlaybackActivity activity;

    @Test public void reusesPreparedSourceWithIsolatedHeadersAndCancelsOnDisable() {
        CoreFixtureServer.ensureStarted();
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String base = CoreFixtureServer.baseUrl();
        MediaItem first = item(base, "base.mp4"), second = item(base, "styled.mkv");
        try {
            main(() -> {
                PreloadSetting.putNextEpisode(true);
                subtitles = new AdvancedSubtitleController();
                coordinator = new PreloadCoordinator();
                next = new NextMediaPreload(1, new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException e) { error.set(e); }
                }, subtitles, coordinator);
                activity.view.setPlayer(next.player);
                subtitles.activate(first);
                next.player.setMediaItem(first); next.player.prepare(); next.player.play();
            });
            await(() -> next.player.getPlaybackState() == Player.STATE_READY && !next.player.isLoading(), "current buffer ready");
            main(() -> {
                PreloadBudget.Lease current = coordinator.acquire(PreloadBudget.Owner.CURRENT, () -> {});
                assertNotNull("Current item can reserve the background connection", current);
                next.preload(second, 0);
                assertNull("Next item must wait while current-item pre-cache owns the connection", next.take(second));
                coordinator.release(current);
                next.preload(second, 0);
            });
            SystemClock.sleep(1500);
            AtomicReference<MediaSource> source = new AtomicReference<>();
            main(() -> {
                next.player.stop();
                source.set(next.take(second));
                assertNotNull("Prepared next item was discarded during stop", source.get());
                subtitles.activate(second);
                next.player.setMediaSource(source.get()); next.player.prepare(); next.player.play();
                next.clear();
            });
            await(() -> next.player.getPlaybackState() == Player.STATE_READY && next.player.getCurrentPosition() > 0, "preloaded episode handoff");
            main(() -> {
                next.preload(first, 0);
                PreloadSetting.putNextEpisode(false);
            });
            SystemClock.sleep(200);
            main(() -> assertNull("Disabling must release speculative media", next.take(first)));
            assertNull(error.get());
        } finally {
            main(() -> {
                activity.view.setPlayer(null);
                if (coordinator != null) coordinator.detach();
                if (next != null) { next.release(); next.player.release(); }
                if (subtitles != null) subtitles.release();
                activity.finish(); PreloadSetting.putNextEpisode(true);
            });
        }
    }

    @AfterClass public static void stopFixtureServer() { CoreFixtureServer.stop(); }

    private MediaItem item(String base, String file) {
        return MediaItemFactory.from(PlaySpec.from("next-test", base + file, Map.of("X-Core-Media", file), MediaMetadata.EMPTY));
    }
    private void main(Runnable runnable) { instrumentation.runOnMainSync(runnable); }
    private void await(BooleanSupplier condition, String message) {
        long deadline=SystemClock.elapsedRealtime()+25000;
        while(SystemClock.elapsedRealtime()<deadline) {
            if(error.get()!=null) throw new AssertionError(message,error.get());
            AtomicReference<Boolean> ready=new AtomicReference<>(false);main(()->ready.set(condition.getAsBoolean()));
            if(ready.get()) return; SystemClock.sleep(100);
        }
        fail(message);
    }
}
