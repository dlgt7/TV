package com.fongmi.android.tv.test;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.exo.ExoPlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.subtitle.AssTextRenderer;
import com.fongmi.android.tv.player.util.PlayerHelper;
import com.fongmi.android.tv.setting.AdvancedSubtitleSetting;
import com.fongmi.android.tv.setting.AudioEffectSetting;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerCoreTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CorePlaybackActivity activity;
    private ExoPlayerEngine engine;
    private final AtomicReference<PlaybackException> error = new AtomicReference<>();
    private String base;

    @Before public void setup() {
        base = InstrumentationRegistry.getArguments().getString("core_base", "http://10.0.2.2:9980/");
        main(() -> {
            AdvancedSubtitleSetting.ass(true);
            AudioEffectSetting.preset(0);
            Prefers.put("player_engine", 0);
            Prefers.put("decode", 1);
            Prefers.put("tunnel", false);
        });
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            engine = new ExoPlayerEngine(1, new Player.Listener() {
                @Override public void onPlayerError(PlaybackException value) { error.set(value); }
            });
            activity.view.setPlayer(engine.getPlayer());
            engine.bindPlayerView(activity.view);
        });
        assertTrue("Native libass failed to load", AssTextRenderer.available());
    }

    @After public void cleanup() {
        main(() -> {
            if (engine != null) { engine.bindPlayerView(null); activity.view.setPlayer(null); engine.release(); }
            if (activity != null) activity.finish();
            AudioEffectSetting.preset(0);
        });
    }

    @Test public void embeddedAssAndIndependentSecondarySurviveSeekAndPausedRebind() {
        start("styled.mkv", false);
        select("zho", false);
        await(() -> pixels("AssOverlayView", 0) > 100, "primary ASS frame");
        select("jpn", true);
        await(() -> pixels("AssOverlayView", 1) > 100, "secondary ASS frame");
        main(() -> engine.getPlayer().seekTo(12_000));
        await(() -> engine.getPlayer().getCurrentPosition() >= 12_000 && pixels("AssOverlayView", 0) > 100, "ASS after seek");
        main(() -> engine.getPlayer().pause());
        SystemClock.sleep(200);
        main(() -> { engine.bindPlayerView(null); engine.bindPlayerView(activity.view); });
        await(() -> pixels("AssOverlayView", 0) > 100 && pixels("AssOverlayView", 1) > 100, "paused overlay rebind");
        main(() -> engine.getPlayer().setTrackSelectionParameters(engine.getPlayer().getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()));
        await(() -> {
            int primary = pixels("AssOverlayView", 0), secondary = pixels("AssOverlayView", 1);
            android.util.Log.i("CoreSubtitles", "disabled primary=" + primary + " secondary=" + secondary);
            return primary == 0 && secondary > 100;
        }, "independent secondary selection");
        main(() -> engine.setSecondaryTrack(null));
        await(() -> pixels("AssOverlayView", 1) == 0, "secondary disabled");
    }

    @Test public void sidecarAssRendersAndRetainsItsAnimation() {
        start("base.mp4", true);
        select("zho", false);
        await(() -> pixels("AssOverlayView", 0) > 100, "sidecar ASS frame");
        long before = pictureHash("AssOverlayView", 0);
        main(() -> engine.getPlayer().seekTo(15_000));
        await(() -> engine.getPlayer().getCurrentPosition() >= 15_000 && pixels("AssOverlayView", 0) > 100, "sidecar after seek");
        await(() -> hashOnMain("AssOverlayView", 0) != before, "ASS move tag changes rendered position");
    }

    @Test public void twoSrtTracksPlayWithDspAndRemainPausedAfterRebuild() {
        main(() -> AudioEffectSetting.preset(1));
        main(() -> { activity.view.setPlayer(null); engine.rebuild(); activity.view.setPlayer(engine.getPlayer()); engine.bindPlayerView(activity.view); });
        start("dual-srt.mkv", false);
        select("zho", false);
        select("eng", true);
        await(() -> pixels("SubtitleView", 0) > 100, "secondary SRT frame");
        main(() -> engine.getPlayer().pause());
        long position = value(() -> engine.getPlayer().getCurrentPosition());
        main(() -> {
            activity.view.setPlayer(null);
            engine.rebuild();
            activity.view.setPlayer(engine.getPlayer()); engine.bindPlayerView(activity.view);
            engine.start(spec("dual-srt.mkv"), position);
            engine.getPlayer().pause();
        });
        await(() -> engine.getPlayer().getPlaybackState() == Player.STATE_READY, "Dsp rebuild");
        main(() -> assertFalse(engine.getPlayer().getPlayWhenReady()));
    }

    private PlaySpec spec(String path) { return PlaySpec.from("core-test", base + path, Map.of("X-Core-Media", path), MediaMetadata.EMPTY); }
    private void start(String file, boolean sidecar) {
        main(() -> {
            PlaySpec spec = spec(file);
            if (sidecar) spec.setSub(Sub.from("ASS test", base + "sample.ass", "zho", "text/x-ssa"));
            engine.start(spec, 0);
        });
        await(() -> engine.getPlayer().getPlaybackState() == Player.STATE_READY && !engine.getPlayer().getCurrentTracks().isEmpty(), "playback ready");
    }
    private void select(String language, boolean secondary) {
        main(() -> {
            for (Tracks.Group group : engine.getPlayer().getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int t=0; t<group.length; t++) if (androidx.media3.common.util.Util.normalizeLanguageCode(language).equals(group.getTrackFormat(t).language)) {
                    if (secondary) engine.setSecondaryTrack(PlayerHelper.describeFormat(group.getTrackFormat(t)));
                    else engine.getPlayer().setTrackSelectionParameters(engine.getPlayer().getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), t)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
                    return;
                }
            }
            fail("Missing subtitle language " + language + ": " + engine.getPlayer().getCurrentTracks().getGroups());
        });
    }
    private void await(BooleanSupplier condition, String reason) {
        long end = SystemClock.elapsedRealtime() + 25_000;
        while (SystemClock.elapsedRealtime() < end) {
            if (error.get() != null) throw new AssertionError(reason, error.get());
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            main(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("Timeout: " + reason);
    }
    private void main(Runnable action) { instrumentation.runOnMainSync(action); }
    private long value(java.util.function.LongSupplier action) {
        java.util.concurrent.atomic.AtomicLong result = new java.util.concurrent.atomic.AtomicLong();
        main(() -> result.set(action.getAsLong())); return result.get();
    }
    private View overlay(String name, int index) {
        ViewGroup subtitles = activity.view.getSubtitleView();
        for (int i=0;i<subtitles.getChildCount();i++) {
            View child = subtitles.getChildAt(i);
            if (name.equals(child.getClass().getSimpleName()) && index-- == 0) return child;
        }
        return null;
    }
    private int pixels(String name, int index) {
        View view = overlay(name,index);
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return 0;
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()]; bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int count=0; for(int pixel:pixels) if((pixel >>> 24)>0) count++; bitmap.recycle();return count;
    }
    private long pictureHash(String name,int index) {
        return value(() -> hashOnMain(name, index));
    }
    private long hashOnMain(String name, int index) {
            View view=overlay(name,index); Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
            int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
            int hash=java.util.Arrays.hashCode(pixels);bitmap.recycle();return hash;
    }
}
