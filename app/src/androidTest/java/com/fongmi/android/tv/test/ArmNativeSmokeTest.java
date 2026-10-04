package com.fongmi.android.tv.test;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.mpv.MpvPlayerEngine;
import com.fongmi.android.tv.player.util.PlayerHelper;
import com.fongmi.android.tv.setting.AudioEffectSetting;
import com.fongmi.android.tv.utils.MpvLogCollector;
import com.fongmi.chaquo.Loader;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import is.xyz.mpv.MPVLib;
import is.xyz.mpv.MPVNode;

/**
 * ARM device checks using only the synthetic base.mp4 and styled.mkv fixtures.
 * Pass core_base=http://DEVICE_REACHABLE_HOST:9980/ to AndroidJUnitRunner.
 * Uses the debug-only surface activity, so it creates no playback history.
 */
@RunWith(AndroidJUnit4.class)
public class ArmNativeSmokeTest {
    private static final String TAG = "ArmNativeSmoke";
    private static final long TIMEOUT_MS = 30_000;
    private static final Map<String, Object> TEST_SETTINGS = Map.ofEntries(
            Map.entry("mpv_gpu_next", false), Map.entry("mpv_vulkan", false),
            Map.entry("mpv_hdr", 2), Map.entry("mpv_anime4k", 0),
            Map.entry("subtitle_libass", true), Map.entry("subtitle_text_size", 0f),
            Map.entry("subtitle_position", 0f), Map.entry("audio_effect_preset", 0));

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicReference<PlaybackException> error = new AtomicReference<>();
    private final AtomicBoolean firstFrame = new AtomicBoolean();
    private final Map<String, Object> savedSettings = new HashMap<>();
    private CorePlaybackActivity activity;
    private MpvPlayerEngine engine;
    private boolean settingsChanged;

    @Before public void requireArmDevice() {
        CoreFixtureServer.ensureStarted();
        assertTrue("Run the ARM APK on an ARM device: " + Arrays.toString(Build.SUPPORTED_ABIS),
                Arrays.stream(Build.SUPPORTED_ABIS).anyMatch(abi -> abi.startsWith("arm")));
        savedSettings.putAll(Prefers.getPrefers().getAll());
    }

    @AfterClass public static void stopFixtureServer() { CoreFixtureServer.stop(); }

    @After public void cleanup() {
        try {
            main(() -> {
                try {
                    if (engine != null) {
                        if (activity != null) activity.view.setPlayer(null);
                        engine.release();
                    }
                } finally {
                    if (activity != null) activity.finish();
                }
            });
            if (engine != null) {
                long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
                while (!MpvPlayerEngine.isAvailable() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
                assertTrue("MPV native destroy did not complete", MpvPlayerEngine.isAvailable());
            }
        } finally {
            if (settingsChanged) {
                SharedPreferences.Editor editor = Prefers.getPrefers().edit();
                for (String key : TEST_SETTINGS.keySet()) {
                    Object value = savedSettings.get(key);
                    if (value == null) editor.remove(key);
                    else if (value instanceof Boolean v) editor.putBoolean(key, v);
                    else if (value instanceof Integer v) editor.putInt(key, v);
                    else if (value instanceof Long v) editor.putLong(key, v);
                    else if (value instanceof Float v) editor.putFloat(key, v);
                    else if (value instanceof String v) editor.putString(key, v);
                    else if (value instanceof Set<?> values) {
                        Set<String> strings = new java.util.HashSet<>();
                        for (Object item : values) strings.add((String) item);
                        editor.putStringSet(key, strings);
                    }
                    else throw new AssertionError("Unexpected preference type for " + key);
                }
                assertTrue("Could not restore original playback settings", editor.commit());
            }
        }
    }

    @Test public void productionPythonLoaderExecutesNativeExtensions() throws Exception {
        // Use the real custom Platform and bundled app module; no spider download or source configuration.
        new Loader();
        // Chaquopy is an implementation dependency of :chaquo, not part of this module's public API.
        Class<?> python = Class.forName("com.chaquo.python.Python");
        Object instance = python.getMethod("getInstance").invoke(null);
        Object builtins = python.getMethod("getModule", String.class).invoke(instance, "builtins");
        Method call = Class.forName("com.chaquo.python.PyObject").getMethod("callAttr", String.class, Object[].class);
        Object globals = call.invoke(builtins, "dict", new Object[0]);
        call.invoke(builtins, "exec", new Object[]{"""
                import platform, ssl, sqlite3, sys, ujson, requests
                from lxml import etree
                from Crypto.Cipher import AES
                assert sys.version_info[:2] == (3, 10), sys.version
                assert platform.machine().startswith(('arm', 'aarch64')), platform.machine()
                assert ssl.create_default_context().get_ciphers()
                connection = sqlite3.connect(':memory:')
                try:
                    assert connection.execute('select 6 * 7').fetchone()[0] == 42
                finally:
                    connection.close()
                assert ujson.loads(ujson.dumps({'text': 'ARM 字幕'}))['text'] == 'ARM 字幕'
                assert etree.fromstring(b'<root><value>42</value></root>').xpath('string(value)') == '42'
                key = bytes.fromhex('000102030405060708090a0b0c0d0e0f')
                plain = bytes.fromhex('00112233445566778899aabbccddeeff')
                assert AES.new(key, AES.MODE_ECB).encrypt(plain).hex() == '69c4e0d86a7b0430d8cdb78070b4c55a'
                native_report = platform.machine() + ' / Python ' + platform.python_version() + ' / ' + ssl.OPENSSL_VERSION
                """, globals});
        Object report = call.invoke(builtins, "eval", new Object[]{"native_report", globals});
        assertNotNull(report);
        Log.i(TAG, "Python native extensions passed: " + report);
    }

    @Test public void mpvRendersSeeksAndAppliesNativeAudioFilters() throws IOException {
        start("base.mp4");
        verifyExtendedNodeApis();
        assertAdvances();
        seekPaused(25_000);
        seekPaused(5_000);
        verifyFastThumbnail();
        String spdif = read(() -> property("audio-spdif"));
        main(() -> { AudioEffectSetting.preset(2); engine.applyAudioEffects(); engine.getPlayer().play(); });
        await(() -> property("af").contains("tv-core-audio")
                && property("af").contains("equalizer")
                && property("af").contains("dynaudnorm")
                && property("af").contains("alimiter"), "native equalizer, normalizer and limiter");
        await(() -> number("audio-out-params/samplerate") > 0 && !property("current-ao").isEmpty(), "active native audio output");
        assertAdvances();
        Log.i(TAG, "MPV audio output=" + read(() -> property("current-ao")) + ", filters=" + read(() -> property("af")));
        main(() -> { AudioEffectSetting.preset(0); engine.applyAudioEffects(); });
        await(() -> !property("af").contains("tv-core-audio") && spdif.equals(property("audio-spdif")), "audio effects removed and passthrough restored");
        assertAdvances();
    }

    private void verifyExtendedNodeApis() {
        // These synchronous libmpv calls are thread-safe. Keep JNI errors and assertions on the test thread.
        MPVNode tracks = MPVLib.INSTANCE.getPropertyNode("track-list");
        assertTrue("track-list JNI must return an array node", tracks instanceof MPVNode.ArrayNode);
        MPVNode[] entries = ((MPVNode.ArrayNode) tracks).getValue();
        boolean video = false;
        boolean audio = false;
        for (MPVNode entry : entries) {
            assertTrue("track-list entry must be a map node", entry instanceof MPVNode.MapNode);
            MPVNode type = ((MPVNode.MapNode) entry).getValue().get("type");
            assertTrue("track type must be a string node", type instanceof MPVNode.StringNode);
            video |= "video".equals(((MPVNode.StringNode) type).getValue());
            audio |= "audio".equals(((MPVNode.StringNode) type).getValue());
        }
        assertTrue("Native track-list must contain the fixture's video and audio tracks", video && audio);
        // get_property is JSON IPC-only; expand-text is a real libmpv command returning a string node.
        MPVNode version = MPVLib.INSTANCE.commandNode("expand-text", "${mpv-version}");
        assertTrue("commandNode must return the MPV version string", version instanceof MPVNode.StringNode);
        String value = ((MPVNode.StringNode) version).getValue();
        assertFalse("Native MPV version is empty", value.trim().isEmpty());
        assertEquals("Node and string property APIs disagree", property("mpv-version"), value);
        Log.i(TAG, "Extended node JNI passed: version=" + value + ", trackCount=" + entries.length);
    }

    private void verifyFastThumbnail() throws IOException {
        File fixture = new File(instrumentation.getTargetContext().getFilesDir(), "core-fixtures/base.mp4");
        File downloaded = null;
        Bitmap thumbnail = null;
        try {
            if (!fixture.isFile()) {
                downloaded = File.createTempFile("arm-native-thumbnail-", ".mp4", instrumentation.getTargetContext().getCacheDir());
                downloadThumbnailFixture(downloaded);
                fixture = downloaded;
            }
            assertTrue("Thumbnail fixture is empty", fixture.length() > 0);
            // This direct FFmpeg path runs off the UI thread and deliberately uses software decoding.
            MPVLib.INSTANCE.setThumbnailJavaVM(instrumentation.getTargetContext().getApplicationContext());
            thumbnail = MPVLib.INSTANCE.grabThumbnailFast(fixture.getAbsolutePath(), 5.0, 160, false);
            assertNotNull("grabThumbnailFast returned no bitmap when requesting the 5-second position", thumbnail);
            assertEquals("Thumbnail width", 160, thumbnail.getWidth());
            assertEquals("Thumbnail must preserve the 640x360 fixture aspect ratio", 90, thumbnail.getHeight());
            int[] pixels = new int[thumbnail.getWidth() * thumbnail.getHeight()];
            thumbnail.getPixels(pixels, 0, thumbnail.getWidth(), 0, 0, thumbnail.getWidth(), thumbnail.getHeight());
            int visible = 0;
            for (int pixel : pixels) if ((pixel >>> 24) != 0 && (pixel & 0x00ffffff) != 0) visible++;
            assertTrue("Thumbnail contains no decoded fixture image", visible > pixels.length / 2);
            Log.i(TAG, "Fast thumbnail JNI passed: 160x90, requestedPositionSeconds=5, visiblePixels=" + visible);
        } finally {
            try {
                if (thumbnail != null) thumbnail.recycle();
                MPVLib.INSTANCE.clearThumbnailCache();
            } finally {
                if (downloaded != null) assertTrue("Could not delete temporary thumbnail fixture", !downloaded.exists() || downloaded.delete());
            }
        }
    }

    private void downloadThumbnailFixture(File target) throws IOException {
        String base = CoreFixtureServer.baseUrl();
        if (!base.endsWith("/")) base += "/";
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "base.mp4").openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("X-Core-Media", "base.mp4");
        connection.setRequestProperty("Accept-Encoding", "identity");
        long limit = 8L * 1024 * 1024;
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Thumbnail fixture HTTP status " + status);
            if (connection.getContentLengthLong() > limit) throw new IOException("Thumbnail fixture exceeds 8 MiB");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                long received = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    received += count;
                    if (received > limit) throw new IOException("Thumbnail fixture exceeds 8 MiB");
                    if (SystemClock.elapsedRealtime() > deadline) throw new IOException("Thumbnail fixture download timed out");
                    output.write(buffer, 0, count);
                }
                if (received == 0) throw new IOException("Thumbnail fixture download is empty");
            }
        } finally {
            connection.disconnect();
        }
    }

    @Test public void mpvRendersIndependentAssAndSecondarySubtitlesAfterSeek() throws Exception {
        start("styled.mkv");
        String primary = select("zho", false);
        String secondary = select("eng", true);
        await(() -> property("sub-text").contains("ASS primary, styled"), "primary ASS before first seek");
        seekPaused(25_000); // The moving fixture caption ends at 20 seconds: remaining video is static.
        Log.i(TAG, "Subtitle probe expected=" + primary + "/" + secondary + read(() -> "; sid=" + property("sid")
                + "; secondary-sid=" + property("secondary-sid") + "; sub-text=" + property("sub-text")
                + "; secondary-sub-text=" + property("secondary-sub-text") + "; time=" + number("time-pos")));
        await(() -> primary.equals(property("sid")) && secondary.equals(property("secondary-sid"))
                && property("sub-text").contains("ASS primary, styled")
                && property("secondary-sub-text").contains("English secondary track"), "both decoded subtitle tracks after seek");
        int[] both = surfacePixels();
        main(() -> engine.getPlayer().setTrackSelectionParameters(engine.getPlayer().getTrackSelectionParameters()
                .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()));
        await(() -> "no".equals(property("sid")) && secondary.equals(property("secondary-sid"))
                && property("secondary-sub-text").contains("English secondary track"), "secondary remains selected without primary");
        int[] secondaryOnly = awaitSurfaceChange(both, "Primary ASS did not change rendered video pixels");
        main(() -> engine.setSecondaryTrack(null));
        await(() -> "no".equals(property("secondary-sid")), "secondary disabled");
        awaitSurfaceChange(secondaryOnly, "Secondary subtitle did not change rendered video pixels");
        select("zho", false);
        select("eng", true);
        seekPaused(22_000);
        await(() -> property("sub-text").contains("ASS primary, styled")
                && property("secondary-sub-text").contains("English secondary track"), "both subtitle decoders after backward seek");
    }

    private void start(String file) {
        settingsChanged = true;
        TEST_SETTINGS.forEach(Prefers::put);
        assertTrue("MPV native library unavailable", MpvPlayerEngine.isAvailable());
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),
                CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String base = CoreFixtureServer.baseUrl();
        if (!base.endsWith("/")) base += "/";
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Core-Media", file);
        PlaySpec spec = PlaySpec.from("arm-native-smoke", base + file, headers, MediaMetadata.EMPTY);
        main(() -> {
            engine = new MpvPlayerEngine(PlayerEngine.SOFT, new Player.Listener() {
                @Override public void onPlayerError(PlaybackException value) { error.set(value); }
                @Override public void onRenderedFirstFrame() { firstFrame.set(true); }
            });
            activity.view.setPlayer(engine.getPlayer());
            engine.start(spec, 0);
            engine.getPlayer().play();
        });
        await(() -> firstFrame.get() && engine.getPlayer().getPlaybackState() == Player.STATE_READY
                && engine.getPlayer().getVideoSize().width == 640 && engine.getPlayer().getVideoSize().height == 360,
                "MPV rendered 640x360 fixture");
        Log.i(TAG, "MPV decoder=" + read(() -> property("hwdec-current")) + ", video=" + read(() -> property("video-codec")));
    }

    private String select(String language, boolean secondary) {
        return read(() -> {
            for (Tracks.Group group : engine.getPlayer().getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int index = 0; index < group.length; index++) {
                    if (!Util.normalizeLanguageCode(language).equals(group.getTrackFormat(index).language)) continue;
                    if (secondary) engine.setSecondaryTrack(PlayerHelper.describeFormat(group.getTrackFormat(index)));
                    else engine.getPlayer().setTrackSelectionParameters(engine.getPlayer().getTrackSelectionParameters()
                            .buildUpon().setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), index))
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
                    return group.getTrackFormat(index).id;
                }
            }
            throw new AssertionError("Missing fixture subtitle: " + language);
        });
    }

    private void seekPaused(long position) {
        main(() -> { engine.getPlayer().pause(); engine.getPlayer().seekTo(position); });
        await(() -> Math.abs(number("time-pos") * 1000 - position) < 1000
                && Math.abs(engine.getPlayer().getCurrentPosition() - position) < 1000
                && engine.getPlayer().getPlaybackState() == Player.STATE_READY, "paused seek to " + position);
        double before = read(() -> number("time-pos"));
        SystemClock.sleep(400);
        assertEquals("Native playback advanced while paused", before, read(() -> number("time-pos")), 0.2);
    }

    private void assertAdvances() {
        double before = read(() -> number("time-pos"));
        await(() -> number("time-pos") > before + 0.5 && engine.getPlayer().isPlaying(), "native playback clock advances");
    }

    private int[] surfacePixels() throws InterruptedException {
        SystemClock.sleep(350); // Allow the paused native VO to redraw its changed OSD.
        View surface = read(() -> activity.view.getVideoSurfaceView());
        Bitmap bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        try {
            if (surface instanceof SurfaceView view) {
                CountDownLatch copied = new CountDownLatch(1);
                AtomicInteger result = new AtomicInteger(-1);
                main(() -> PixelCopy.request(view, bitmap, code -> { result.set(code); copied.countDown(); }, new Handler(Looper.getMainLooper())));
                assertTrue("Timed out copying MPV video surface", copied.await(5, TimeUnit.SECONDS));
                assertEquals("Could not read rendered MPV video surface", PixelCopy.SUCCESS, result.get());
            } else if (surface instanceof TextureView view) {
                assertNotNull("MPV texture has no frame", read(() -> view.getBitmap(bitmap)));
            } else throw new AssertionError("Unsupported MPV video surface: " + surface);
            int[] pixels = new int[640 * 360];
            bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 360);
            return pixels;
        } finally {
            bitmap.recycle();
        }
    }

    private int[] awaitSurfaceChange(int[] before, String reason) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (error.get() != null) throw new AssertionError(reason, error.get());
            int[] pixels = surfacePixels();
            if (changedPixels(before, pixels) > 40) return pixels;
        }
        throw new AssertionError(reason);
    }

    private static int changedPixels(int[] before, int[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            int delta = Math.abs((before[i] >> 16 & 255) - (after[i] >> 16 & 255))
                    + Math.abs((before[i] >> 8 & 255) - (after[i] >> 8 & 255))
                    + Math.abs((before[i] & 255) - (after[i] & 255));
            if (delta > 40) changed++;
        }
        return changed;
    }

    private String property(String key) {
        String value = MPVLib.INSTANCE.getPropertyString(key);
        return value == null ? "" : value;
    }

    private double number(String key) {
        Double value = MPVLib.INSTANCE.getPropertyDouble(key);
        return value == null ? -1 : value;
    }

    private void await(BooleanSupplier condition, String reason) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (error.get() != null) throw new AssertionError(reason, error.get());
            if (read(condition::getAsBoolean)) return;
            SystemClock.sleep(100);
        }
        fail("Timeout: " + reason + "; MPV logs=" + MpvLogCollector.getLogs());
    }

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private <T> T read(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        main(() -> result.set(action.get()));
        return result.get();
    }
}
