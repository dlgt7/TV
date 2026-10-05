package com.fongmi.android.tv.test;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.ParserException;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.ai.subtitle.PcmTapAudioProcessor;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.ExoPlayerEngine;
import com.fongmi.android.tv.player.exo.MediaSourceFactory;
import com.fongmi.android.tv.player.exo.SourcePreloadProbe;
import com.fongmi.android.tv.player.exo.SourceAudioEffectProbe;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.mpv.MpvPlayerEngine;
import com.fongmi.android.tv.setting.AudioEffectSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import is.xyz.mpv.MPVLib;
import is.xyz.mpv.MPVNode;

/**
 * Explicit live-media validation of files/source-validation/playback-cases.json.
 * Uses the functional probe's URLs by default; source_refresh_url opts into resolving only played cases.
 * source_case_index is zero-based (-1 selects the normal ordered/limited cases).
 * source_download_diagnostic enables the separate bounded local-file seek diagnostic.
 * Never loads a VodConfig. Public reports exclude URLs, headers and raw exception messages.
 */
@RunWith(AndroidJUnit4.class)
public final class PythonSourcePlaybackTest {
    private static final Map<String, Object> SETTINGS = Map.ofEntries(
            Map.entry("preload", false), Map.entry("preload_next", true), Map.entry("tunnel", false),
            Map.entry("audio_effect_preset", 0), Map.entry("ai_subtitle_enabled", false),
            Map.entry("mpv_gpu_next", false), Map.entry("mpv_vulkan", false),
            Map.entry("mpv_hdr", 2), Map.entry("mpv_anime4k", 0));
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context target = instrumentation.getTargetContext();
    private final AtomicReference<PlaybackException> error = new AtomicReference<>();
    private final AtomicBoolean frame = new AtomicBoolean();
    private final AtomicLong videoFrames = new AtomicLong();
    private final AtomicLong videoPtsUs = new AtomicLong(C.TIME_UNSET);
    private final AtomicLong videoReleaseNs = new AtomicLong();
    private final AtomicLong mpvRestarts = new AtomicLong();
    private final AtomicReference<String> videoDecoder = new AtomicReference<>("");
    private final AtomicReference<String> audioDecoder = new AtomicReference<>("");
    private final MPVLib.EventObserver mpvObserver = new MPVLib.EventObserver() {
        @Override public void eventProperty(String property) {}
        @Override public void eventProperty(String property, long value) {}
        @Override public void eventProperty(String property, boolean value) {}
        @Override public void eventProperty(String property, String value) {}
        @Override public void eventProperty(String property, double value) {}
        @Override public void eventProperty(String property, MPVNode value) {}
        @Override public void event(int id, MPVNode value) {
            if (id == MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART) mpvRestarts.incrementAndGet();
        }
    };
    private final List<JSONObject> cases = new ArrayList<>();
    private final JSONArray results = new JSONArray();
    private Map<String, ?> saved;
    private CorePlaybackActivity activity;
    private PlayerEngine engine;
    private ExoPlayer pcmPlayer;
    private String sourceSha;
    private String reportName = "setup";
    private long timeoutMs;
    private int decodeMode;
    private String decodeName;
    private int caseLimit;
    private int caseIndex;
    private String refreshUrl;
    private SourceCaseRefresh refresher;
    private final Set<Integer> refreshedCases = new HashSet<>();
    private JSONObject privateManifest;
    private File manifestFile;
    private String failureCode;

    @Before public void setup() throws Exception {
        timeoutMs = Math.max(15, Math.min(120, Long.parseLong(InstrumentationRegistry.getArguments()
                .getString("source_playback_timeout_seconds", "45")))) * 1000;
        decodeName = InstrumentationRegistry.getArguments().getString("source_decode_mode", "hard").toLowerCase(java.util.Locale.ROOT);
        assertTrue("source_decode_mode must be hard or soft", "hard".equals(decodeName) || "soft".equals(decodeName));
        decodeMode = "hard".equals(decodeName) ? PlayerEngine.HARD : PlayerEngine.SOFT;
        caseLimit = Integer.parseInt(InstrumentationRegistry.getArguments().getString("source_case_limit", "0"));
        assertTrue("source_case_limit must be nonnegative (0 means all)", caseLimit >= 0);
        caseIndex = Integer.parseInt(InstrumentationRegistry.getArguments().getString("source_case_index", "-1"));
        refreshUrl = InstrumentationRegistry.getArguments().getString("source_refresh_url", "");
        manifestFile = new File(target.getFilesDir(), "source-validation/playback-cases.json");
        try (FileInputStream stream = new FileInputStream(manifestFile); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                assertTrue("Playback manifest exceeds 4 MiB", bytes.size() + length <= 4 * 1024 * 1024);
                bytes.write(buffer, 0, length);
            }
            privateManifest = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            sourceSha = privateManifest.getString("sourceSha256");
            assertTrue("Missing source content hash", sourceSha.matches("[0-9a-f]{64}"));
            JSONArray entries = privateManifest.getJSONArray("cases");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.getJSONObject(i);
                if (!item.has("sourceSha256")) item.put("sourceSha256", sourceSha);
                cases.add(item);
            }
        }
        assertFalse("Functional probe produced no playable cases", cases.isEmpty());
        assertTrue("source_case_index is outside the zero-based manifest range", caseIndex >= -1 && caseIndex < cases.size());
        saved = new HashMap<>(Prefers.getPrefers().getAll());
        SETTINGS.forEach(Prefers::put);
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(target, CorePlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void cleanup() throws Exception {
        try {
            main(() -> {
                try {
                    if (engine != null) {
                        if (engine instanceof MpvPlayerEngine) MPVLib.removeObserver(mpvObserver);
                        engine.bindPlayerView(null); activity.view.setPlayer(null); engine.release();
                    }
                    if (pcmPlayer != null) pcmPlayer.release();
                } finally { if (activity != null) activity.finish(); }
            });
            if (engine instanceof MpvPlayerEngine) {
                long end = SystemClock.elapsedRealtime() + 15_000;
                while (!MpvPlayerEngine.isAvailable() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
                assertTrue("Native MPV release timed out", MpvPlayerEngine.isAvailable());
            }
        } finally {
            try { if (refresher != null) refresher.close(); }
            finally { try { restoreSettings(); } finally { checkpoint(); } }
        }
    }

    @Test public void exoPlaysResolvedEpisodesAndKeepsStateAcrossAudioEffects() throws Exception {
        reportName = "exo-" + decodeName;
        createEngine(false);
        exerciseCases();
    }

    @Test public void exoOtherDecodeModePlaysTheSameResolvedEpisodes() throws Exception {
        decodeMode = decodeMode == PlayerEngine.HARD ? PlayerEngine.SOFT : PlayerEngine.HARD;
        decodeName = decodeMode == PlayerEngine.HARD ? "hard" : "soft";
        reportName = "exo-" + decodeName;
        createEngine(false);
        exerciseCases();
    }

    @Test public void mpvPlaysResolvedEpisodesAndKeepsStateAcrossAudioEffects() throws Exception {
        reportName = "mpv";
        createEngine(true);
        exerciseCases();
    }

    @Test public void exoSeeksTheSelectedLoopbackStreamAsACompletePrivateFile() throws Exception {
        reportName = "exo-local-file-" + decodeName;
        org.junit.Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString("source_download_diagnostic", "false")));
        assertTrue("Local-file diagnostic requires source_case_index", caseIndex >= 0);
        JSONObject record = record(caseIndex).put("scope", "LOCAL_FILE_SEEK_DIAGNOSTIC_ONLY")
                .put("localFileSeekAttempted", false);
        File file = null;
        PlaySpec local = null;
        try {
            record.put("stage", "resolve");
            refreshCase(caseIndex);
            record.put("sourceSha256", cases.get(caseIndex).optString("sourceSha256", sourceSha));
            PlaySpec remote = spec(cases.get(caseIndex), caseIndex).checkUa();
            assertTrue("Local-file diagnostic accepts only loopback streams",
                    "127.0.0.1".equals(remote.getUri().getHost()) || "localhost".equals(remote.getUri().getHost()));
            record.put("stage", "download-from-zero");
            file = new File(manifestFile.getParentFile(), "download-case-" + caseIndex + ".mp4");
            assertTrue("Could not replace the private diagnostic file", !file.exists() || file.delete());
            SourceStreamProbe.downloadAndCheck(remote, file, record);
            local = PlaySpec.from("source-local-" + caseIndex, android.net.Uri.fromFile(file).toString(), Map.of(), MediaMetadata.EMPTY);
            record.put("stage", "prepare-local-file").put("localFileSeekAttempted", true).put("localFileSeekPass", false);
            createEngine(false);
            start(local, 0);
            awaitReady(local);
            advance();
            record.put("diagnostic", diagnostic(local));
            captureVideoEvidence(record);
            long duration = value(() -> engine.getPlayer().getDuration());
            assertTrue("Episode duration too short or unknown for bidirectional seek", duration >= 6_000);
            record.put("durationMs", duration).put("stage", "seek-forward");
            record.put("forwardSeek", seekPaused(Math.min(15_000, duration / 2)));
            record.put("stage", "seek-backward");
            record.put("backwardSeek", seekPaused(Math.min(2_000, duration / 4)));
            record.put("localFileSeekPass", true).put("stage", "range-assertions");
            assertTrue("HTTP Range responses differ from the complete file", record.getBoolean("rangeChecksPass"));
            record.put("status", "PASS").put("stage", "complete");
        } catch (Exception | AssertionError failure) {
            record.put("status", "FAIL").put("failureType", failure.getClass().getSimpleName());
            if (failureCode != null) record.put("failureCode", failureCode);
            if (error.get() != null) record.put("playbackErrorCode", error.get().errorCode);
            record.put("errorDetails", errorDetails(error.get() != null ? error.get() : failure));
            record.put("diagnostic", diagnostic(local));
            throw new AssertionError("Local-file seek diagnostic failed; inspect the sanitized playback report");
        } finally {
            if (engine != null) main(engine::stop);
            if (file != null && file.isFile()) {
                if (record.optJSONObject("fullGet") != null && record.getJSONObject("fullGet").optBoolean("complete")) {
                    record.put("privateFileName", file.getName());
                } else record.put("incompleteFileDeleted", file.delete());
            }
            checkpoint();
        }
    }

    @Test public void listPlatformAvcAndHevcDecoders() throws Exception {
        reportName = "codecs";
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (codec.isEncoder()) continue;
            for (String type : codec.getSupportedTypes()) {
                if (!"video/hevc".equalsIgnoreCase(type) && !"video/avc".equalsIgnoreCase(type)) continue;
                JSONObject item = new JSONObject().put("name", codec.getName()).put("mime", type);
                if (Build.VERSION.SDK_INT >= 29) item.put("softwareOnly", codec.isSoftwareOnly())
                        .put("hardwareAccelerated", codec.isHardwareAccelerated()).put("vendor", codec.isVendor());
                try {
                    JSONArray profiles = new JSONArray();
                    for (MediaCodecInfo.CodecProfileLevel p : codec.getCapabilitiesForType(type).profileLevels) {
                        profiles.put(new JSONObject().put("profile", p.profile).put("level", p.level));
                    }
                    item.put("profiles", profiles);
                } catch (RuntimeException e) { item.put("capabilityError", e.getClass().getSimpleName()); }
                results.put(item);
            }
        }
        checkpoint();
    }

    private void exerciseCases() throws Exception {
        int failures = 0;
        boolean effectsVerified = false;
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) if (caseIndex < 0 || index == caseIndex) order.add(index);
        order.sort((a, b) -> {
            int series = cases.get(a).optString("seriesId").compareTo(cases.get(b).optString("seriesId"));
            return series != 0 ? series : Integer.compare(cases.get(a).optInt("episodeIndex"), cases.get(b).optInt("episodeIndex"));
        });
        if (caseLimit > 0 && order.size() > caseLimit) order = new ArrayList<>(order.subList(0, caseLimit));
        int previous = -1;
        for (int index : order) {
            JSONObject record = record(index);
            long started = SystemClock.elapsedRealtime();
            PlaySpec expected = null;
            failureCode = null;
            try {
                record.put("stage", "resolve");
                refreshCase(index);
                record.put("sourceSha256", cases.get(index).optString("sourceSha256", sourceSha));
                PlaySpec spec = spec(cases.get(index), index);
                expected = spec;
                record.put("stage", "prepare");
                start(spec, 0);
                awaitReady(spec);
                advance();
                record.put("diagnostic", diagnostic(spec));
                captureVideoEvidence(record);
                record.put("width", value(() -> engine.getPlayer().getVideoSize().width));
                record.put("height", value(() -> engine.getPlayer().getVideoSize().height));
                record.put("audioTracks", value(() -> trackCount(C.TRACK_TYPE_AUDIO)));
                record.put("subtitleTracks", value(() -> trackCount(C.TRACK_TYPE_TEXT)));
                record.put("subtitleCoverage", value(() -> trackCount(C.TRACK_TYPE_TEXT)) == 0 ? "NO_SELECTABLE_SUBTITLE_TRACK" : "TRACKS_PRESENT_RENDERING_NOT_ASSERTED");
                if (engine instanceof MpvPlayerEngine) record.put("decoder", MPVLib.INSTANCE.getPropertyString("hwdec-current"));
                long duration = value(() -> engine.getPlayer().getDuration());
                record.put("durationMs", duration);
                assertTrue("Episode duration too short or unknown for bidirectional seek", duration >= 6_000);
                record.put("stage", "seek-forward");
                record.put("forwardSeek", seekPaused(Math.min(15_000, duration / 2)));
                record.put("stage", "seek-backward");
                record.put("backwardSeek", seekPaused(Math.min(2_000, duration / 4)));
                record.put("forwardBackwardSeek", true);
                if (previous >= 0 && cases.get(previous).optString("seriesId").equals(cases.get(index).optString("seriesId"))
                        && cases.get(index).optInt("episodeIndex") == cases.get(previous).optInt("episodeIndex") + 1) {
                    record.put("switchedFromPreviousEpisode", true);
                }
                if (!effectsVerified) {
                    record.put("stage", "audio-effects");
                    verifyAudioEffects(spec, record);
                    record.put("stage", "playback-controls");
                    verifyPlaybackControls(record);
                    effectsVerified = true;
                    record.put("effectsEnabledDisabledWithPauseAndPosition", true);
                }
                main(() -> engine.getPlayer().play());
                advance();
                record.put("status", "PASS");
                record.put("stage", "complete");
                previous = index;
            } catch (Exception | AssertionError failure) {
                failures++;
                record.put("status", "FAIL");
                record.put("failureType", failure.getClass().getSimpleName());
                if (failureCode != null) record.put("failureCode", failureCode);
                if (error.get() != null) record.put("playbackErrorCode", error.get().errorCode);
                record.put("errorDetails", errorDetails(error.get() != null ? error.get() : failure));
                record.put("diagnostic", diagnostic(expected));
                previous = -1;
            } finally {
                record.put("elapsedMs", SystemClock.elapsedRealtime() - started);
                checkpoint();
            }
        }
        assertEquals("Source cases failed; last failure=" + failureCode + "; inspect the sanitized playback report", 0, failures);
        assertTrue("No source case completed the audio effects check", effectsVerified);
    }

    @Test public void exoConsumesTheCompletedNextEpisodeSource() throws Exception {
        reportName = "preload";
        createEngine(false);
        int[] pair = nextEpisodePair();
        refreshCase(pair[0]);
        refreshCase(pair[1]);
        PlaySpec first = spec(cases.get(pair[0]), pair[0]);
        PlaySpec second = spec(cases.get(pair[1]), pair[1]);
        JSONObject record = record(pair[0]);
        record.put("nextCaseIndex", pair[1]);
        start(first, 0);
        awaitReady(first);
        advance();
        main(() -> engine.getPlayer().pause());
        await(() -> !engine.getPlayer().isLoading(), "current buffer ready for preloading");
        SourcePreloadProbe probe = value(() -> new SourcePreloadProbe((ExoPlayerEngine) engine, second));
        try {
            main(() -> engine.preload(second, 0));
            await(() -> {
                if (probe.getError() != null) throw new AssertionError("Next source preload error");
                return probe.isCompleted();
            }, "next source fully preloaded");
            record.put("completedPreload", true);
            main(() -> {
                engine.stop();
                error.set(null);
                frame.set(false);
                engine.start(second, 0);
                probe.assertConsumedPreparedSource();
            });
            awaitReady(second);
            advance();
            record.put("consumedSamePreparedSource", true);
            await(() -> !engine.getPlayer().isLoading(), "next episode buffer ready");
            SourcePreloadProbe cancellation = value(() -> new SourcePreloadProbe((ExoPlayerEngine) engine, first));
            try {
                main(() -> engine.preload(first, 0));
                await(() -> cancellation.getMediaSource() != null, "new speculative source added");
                main(() -> PreloadSetting.putNextEpisode(false));
                await(() -> cancellation.getMediaSource() == null, "disabled preload cleared");
                record.put("disableClearsPreload", true);
            } finally { main(cancellation::close); }
            record.put("status", "PASS");
        } finally { main(probe::close); checkpoint(); }
    }

    @Test public void exoSourceAudioReachesTheProductionPcmTapWithoutAnAsrModel() throws Exception {
        reportName = "pcm";
        int index = caseIndex < 0 ? 0 : caseIndex;
        refreshCase(index);
        JSONObject record = record(index);
        AtomicLong samples = new AtomicLong();
        AtomicLong audible = new AtomicLong();
        AtomicInteger rate = new AtomicInteger();
        PlaySpec first = spec(cases.get(index), index);
        main(() -> {
            DefaultRenderersFactory renderers = new DefaultRenderersFactory(target) {
                @Override protected AudioSink buildAudioSink(@NonNull Context context, boolean floatOutput, boolean playbackParams) {
                    return new DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
                            .setAudioProcessors(new AudioProcessor[]{new PcmTapAudioProcessor((mono, sampleRate) -> {
                                rate.set(sampleRate);
                                samples.addAndGet(mono.length);
                                for (float sample : mono) if (Float.isFinite(sample) && Math.abs(sample) > 0.0001f) audible.incrementAndGet();
                            })})
                            .setAudioOutputProvider(new AudioTrackAudioOutputProvider.Builder(null).build()).build();
                }
            }.setEnableDecoderFallback(true);
            pcmPlayer = new ExoPlayer.Builder(target).setRenderersFactory(renderers)
                    .setMediaSourceFactory(new MediaSourceFactory()).build();
            pcmPlayer.addListener(listener());
            pcmPlayer.setTrackSelectionParameters(pcmPlayer.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
            pcmPlayer.setVolume(0);
            pcmPlayer.setMediaItem(MediaItemFactory.from(first));
            pcmPlayer.prepare();
            pcmPlayer.play();
        });
        await(() -> rate.get() > 0 && samples.get() >= rate.get() && audible.get() > 100,
                "decoded source PCM with nonzero samples at the production tap");
        record.put("sampleRate", rate.get());
        record.put("samples", samples.get());
        record.put("nonzeroSamples", audible.get());
        record.put("coverage", "DECODED_PCM_TAP_ONLY_ASR_AND_TRANSLATION_NOT_RUN");
        record.put("status", "PASS");
    }

    private void createEngine(boolean mpv) {
        if (mpv) assertTrue("MPV native library unavailable", MpvPlayerEngine.isAvailable());
        main(() -> {
            engine = mpv ? new MpvPlayerEngine(decodeMode, listener()) : new ExoPlayerEngine(decodeMode, listener());
            if (mpv) MPVLib.addObserver(mpvObserver);
            else observeVideoFrames();
            activity.view.setPlayer(engine.getPlayer());
            engine.bindPlayerView(activity.view);
        });
    }

    private Player.Listener listener() {
        return new Player.Listener() {
            @Override public void onPlayerError(PlaybackException failure) { error.set(failure); }
            @Override public void onRenderedFirstFrame() { frame.set(true); }
        };
    }

    private void start(PlaySpec spec, long position) {
        main(() -> {
            error.set(null); frame.set(false); videoFrames.set(0); videoPtsUs.set(C.TIME_UNSET);
            engine.start(spec, position); engine.getPlayer().play();
        });
    }

    private void observeVideoFrames() {
        ((ExoPlayer) engine.getPlayer()).addAnalyticsListener(new AnalyticsListener() {
            @Override public void onVideoDecoderInitialized(EventTime time, String name, long initializedMs, long durationMs) { videoDecoder.set(name); }
            @Override public void onAudioDecoderInitialized(EventTime time, String name, long initializedMs, long durationMs) { audioDecoder.set(name); }
        });
        ((ExoPlayer) engine.getPlayer()).setVideoFrameMetadataListener((ptsUs, releaseNs, format, mediaFormat) -> {
            videoPtsUs.set(ptsUs);
            videoReleaseNs.set(releaseNs);
            videoFrames.incrementAndGet();
        });
    }

    private void awaitReady(PlaySpec spec) {
        await(() -> frame.get() && engine.getPlayer().getPlaybackState() == Player.STATE_READY
                && (engine instanceof MpvPlayerEngine || videoFrames.get() > 0)
                && engine.getPlayer().getVideoSize().width > 0 && engine.getPlayer().getVideoSize().height > 0
                && engine.getPlayer().getCurrentMediaItem() != null
                && engine.getPlayer().getCurrentMediaItem().localConfiguration != null
                && spec.getUri().equals(engine.getPlayer().getCurrentMediaItem().localConfiguration.uri), "rendered source video");
    }

    private JSONObject diagnostic(PlaySpec expected) {
        return value(() -> {
            JSONObject out = new JSONObject();
            if (engine == null) return out;
            try {
                Player player = engine.getPlayer();
                androidx.media3.common.MediaItem current = player.getCurrentMediaItem();
                out.put("decodeMode", decodeName).put("state", player.getPlaybackState())
                        .put("playWhenReady", player.getPlayWhenReady()).put("isPlaying", player.isPlaying())
                        .put("positionMs", player.getCurrentPosition()).put("frame", frame.get())
                        .put("videoFrameCallbacks", videoFrames.get()).put("videoPresentationTimeUs", videoPtsUs.get())
                        .put("width", player.getVideoSize().width).put("height", player.getVideoSize().height)
                        .put("videoDecoderName", videoDecoder.get()).put("audioDecoderName", audioDecoder.get());
                out.put("expectedUriMatches", expected != null && current != null && current.localConfiguration != null
                        && expected.getUri().equals(current.localConfiguration.uri));
                out.put("currentMediaIdMatches", expected != null && current != null
                        && MediaItemFactory.from(expected).mediaId.equals(current.mediaId));
                JSONArray tracks = new JSONArray();
                for (Tracks.Group group : player.getCurrentTracks().getGroups()) for (int i = 0; i < group.length; i++) {
                    Format f = group.getTrackFormat(i);
                    tracks.put(new JSONObject().put("type", group.getType()).put("supported", group.isTrackSupported(i))
                            .put("supportCode", group.getTrackSupport(i)).put("selected", group.isTrackSelected(i))
                            .put("mime", f.sampleMimeType).put("codecs", f.codecs).put("width", f.width).put("height", f.height)
                            .put("sampleRate", f.sampleRate).put("channels", f.channelCount));
                }
                out.put("tracks", tracks);
                if (engine instanceof MpvPlayerEngine) out.put("nativeDecoder", MPVLib.INSTANCE.getPropertyString("hwdec-current"))
                        .put("videoCodec", MPVLib.INSTANCE.getPropertyString("video-codec"));
                return out;
            } catch (org.json.JSONException e) { throw new IllegalStateException("Could not collect playback diagnostic"); }
        });
    }

    private void advance() {
        long before = value(() -> engine.getPlayer().getCurrentPosition());
        await(() -> engine.getPlayer().isPlaying() && engine.getPlayer().getCurrentPosition() > before + 700, "source playback clock advances");
    }

    private void captureVideoEvidence(JSONObject record) throws Exception {
        View surface = value(() -> activity.view.getVideoSurfaceView());
        Bitmap bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        try {
            if (surface instanceof SurfaceView view) {
                CountDownLatch done = new CountDownLatch(1);
                AtomicInteger code = new AtomicInteger(-1);
                main(() -> PixelCopy.request(view, bitmap, result -> { code.set(result); done.countDown(); }, new Handler(Looper.getMainLooper())));
                assertTrue("Video surface copy timed out", done.await(5, TimeUnit.SECONDS));
                assertEquals("Video surface has no readable frame", PixelCopy.SUCCESS, code.get());
            } else if (surface instanceof TextureView view) assertNotNull(value(() -> view.getBitmap(bitmap)));
            else throw new AssertionError("Unsupported video surface");
            int[] pixels = new int[160 * 90];
            bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 90);
            int nonblack = 0;
            int opaque = 0;
            for (int pixel : pixels) { if ((pixel >>> 24) > 0) opaque++; if ((pixel & 0xffffff) != 0) nonblack++; }
            assertTrue("Video surface was entirely transparent", opaque > 0);
            record.put("videoSurfaceCaptured", true);
            record.put("nonblackVideoPixels", nonblack);
            record.put("videoPixelHash", Integer.toHexString(java.util.Arrays.hashCode(pixels)));
        } finally { bitmap.recycle(); }
    }

    private JSONObject seekPaused(long position) throws Exception {
        long previousFrames = videoFrames.get();
        long previousRestarts = mpvRestarts.get();
        long previousPosition = value(() -> engine.getPlayer().getCurrentPosition());
        long ptsOffsetUs = videoPtsUs.get() - previousPosition * 1000;
        main(() -> { engine.getPlayer().pause(); engine.getPlayer().seekTo(position); });
        await(() -> engine.getPlayer().getPlaybackState() == Player.STATE_READY
                && Math.abs(engine.getPlayer().getCurrentPosition() - position) < 1500, "paused source seek");
        if (engine instanceof MpvPlayerEngine) {
            // This pinned MPV has no video-pts property. PLAYBACK_RESTART is emitted after
            // video/audio synchronization completes, including the newly displayed paused frame.
            await(() -> {
                Double pts = MPVLib.INSTANCE.getPropertyDouble("time-pos");
                Boolean seeking = MPVLib.INSTANCE.getPropertyBoolean("seeking");
                return mpvRestarts.get() > previousRestarts && Boolean.FALSE.equals(seeking)
                        && pts != null && Math.abs(pts * 1000 - position) < 1500;
            }, "new native video restart at seek target");
        } else {
            await(() -> videoFrames.get() > previousFrames
                    && Math.abs(videoPtsUs.get() - (position * 1000 + ptsOffsetUs)) < 1_500_000
                    && videoReleaseNs.get() <= System.nanoTime(), "new Exo frame presented at seek target");
        }
        long before = value(() -> engine.getPlayer().getCurrentPosition());
        SystemClock.sleep(350);
        assertFalse(value(() -> engine.getPlayer().getPlayWhenReady()));
        assertEquals("Source position advanced while paused", before, value(() -> engine.getPlayer().getCurrentPosition()).longValue(), 300);
        JSONObject evidence = new JSONObject().put("requestedPositionMs", position)
                .put("newFrameAtTarget", true);
        if (engine instanceof MpvPlayerEngine) evidence.put("nativeRestartEvents", mpvRestarts.get() - previousRestarts);
        else evidence.put("presentationTimeUs", videoPtsUs.get()).put("newFrameCallbacks", videoFrames.get() - previousFrames);
        captureVideoEvidence(evidence);
        return evidence;
    }

    private void verifyAudioEffects(PlaySpec spec, JSONObject record) throws Exception {
        for (int preset : new int[]{2, 0}) {
            long position = value(() -> engine.getPlayer().getCurrentPosition());
            main(() -> {
                AudioEffectSetting.preset(preset);
                if (engine instanceof MpvPlayerEngine) engine.applyAudioEffects();
                else {
                    engine.bindPlayerView(null);
                    activity.view.setPlayer(null);
                    engine.rebuild();
                    observeVideoFrames();
                    activity.view.setPlayer(engine.getPlayer());
                    engine.bindPlayerView(activity.view);
                    engine.start(spec, position);
                }
                engine.getPlayer().pause();
            });
            await(() -> engine.getPlayer().getPlaybackState() == Player.STATE_READY
                    && Math.abs(engine.getPlayer().getCurrentPosition() - position) < 1500, "audio effect pipeline ready at preserved position");
            assertFalse(value(() -> engine.getPlayer().getPlayWhenReady()));
            if (engine instanceof MpvPlayerEngine) {
                await(() -> {
                    String filters = MPVLib.INSTANCE.getPropertyString("af");
                    return preset == 0 ? filters == null || !filters.contains("tv-core-audio")
                            : filters != null && filters.contains("tv-core-audio") && filters.contains("dynaudnorm");
                }, "native audio filter state");
            }
            main(() -> engine.getPlayer().play());
            advance();
            if (engine instanceof ExoPlayerEngine) {
                SourceAudioEffectProbe.Snapshot snapshot = value(() -> SourceAudioEffectProbe.snapshot((ExoPlayer) engine.getPlayer()))
                        .get(5, TimeUnit.SECONDS);
                if (preset != 0) {
                    long end = SystemClock.elapsedRealtime() + timeoutMs;
                    while (!snapshot.processed() && SystemClock.elapsedRealtime() < end) {
                        SystemClock.sleep(100);
                        snapshot = value(() -> SourceAudioEffectProbe.snapshot((ExoPlayer) engine.getPlayer())).get(5, TimeUnit.SECONDS);
                    }
                    assertTrue("Production audio effect processor not installed", snapshot.installed());
                    assertTrue("Production audio effect processor not active", snapshot.active());
                    assertTrue("Production audio effect processor did not process source PCM", snapshot.processed());
                    record.put("exoDsp", new JSONObject().put("installed", true).put("active", true)
                            .put("processedSourcePcm", true).put("power", snapshot.power())
                            .put("filterStateMagnitude", snapshot.filterStateMagnitude()));
                } else {
                    assertFalse("Disabled effects left the processor in the production sink", snapshot.installed());
                    record.put("exoDspRemovedOnDisable", true);
                }
            }
            main(() -> engine.getPlayer().pause());
        }
    }

    private void verifyPlaybackControls(JSONObject record) throws Exception {
        PlaybackParameters originalSpeed = value(() -> engine.getPlayer().getPlaybackParameters());
        float originalVolume = value(() -> engine.getPlayer().getVolume());
        boolean originallyPlaying = value(() -> engine.getPlayer().getPlayWhenReady());
        JSONArray checks = new JSONArray();
        record.put("playbackControls", checks);
        try {
            for (float speed : new float[]{1.5f, 1f}) {
                main(() -> { engine.getPlayer().setPlaybackSpeed(speed); engine.getPlayer().play(); });
                await(() -> Math.abs(engine.getPlayer().getPlaybackParameters().speed - speed) < 0.01f,
                        "playback speed applied");
                if (engine instanceof MpvPlayerEngine) {
                    await(() -> {
                        Double actual = MPVLib.INSTANCE.getPropertyDouble("speed");
                        return actual != null && Math.abs(actual - speed) < 0.01;
                    }, "native playback speed applied");
                }
                JSONObject check = new JSONObject().put("speed", speed);
                checks.put(check);
                verifyControlProgress(check);
            }
            for (float volume : new float[]{0f, originalVolume}) {
                main(() -> engine.getPlayer().setVolume(volume));
                await(() -> Math.abs(engine.getPlayer().getVolume() - volume) < 0.01f, "player volume applied");
                if (engine instanceof MpvPlayerEngine) {
                    await(() -> {
                        Double actual = MPVLib.INSTANCE.getPropertyDouble("volume");
                        return actual != null && Math.abs(actual - volume * 100) < 0.1;
                    }, "native player volume applied");
                }
                JSONObject check = new JSONObject().put("volume", volume);
                checks.put(check);
                verifyControlProgress(check);
            }
        } finally {
            main(() -> {
                engine.getPlayer().setPlaybackParameters(originalSpeed);
                engine.getPlayer().setVolume(originalVolume);
                engine.getPlayer().setPlayWhenReady(originallyPlaying);
            });
        }
        record.put("speedAndVolumeRestored", true);
    }

    private void verifyControlProgress(JSONObject check) throws Exception {
        long frames = videoFrames.get();
        advance();
        if (engine instanceof ExoPlayerEngine) await(() -> videoFrames.get() > frames, "video frames continue after control change");
        captureVideoEvidence(check);
        check.put("playbackClockAdvanced", true);
    }

    private PlaySpec spec(JSONObject item, int index) throws Exception {
        String parse = item.optString("parse", "0");
        assertTrue("Source requires an unresolved parser", "0".equals(parse) || "false".equals(parse));
        String url = item.getString("url");
        assertTrue("Source returned an empty URL", !url.isEmpty());
        android.net.Uri uri = android.net.Uri.parse(url);
        assertTrue("Source is not an HTTP(S) playback URL", "http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
        assertFalse("Unregistered Python proxy: resolve through a temporary source route before playback",
                ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost())) && uri.getPort() == 9978 && "/proxy".equals(uri.getPath()));
        Map<String, String> headers = new HashMap<>();
        JSONObject raw = item.optJSONObject("headers");
        if (raw != null) for (Iterator<String> keys = raw.keys(); keys.hasNext();) { String key = keys.next(); headers.put(key, raw.getString(key)); }
        return PlaySpec.from("source-validation-" + index, url, headers, MediaMetadata.EMPTY);
    }

    private void refreshCase(int index) throws Exception {
        if (refreshUrl.isEmpty() || refreshedCases.contains(index)) return;
        if (refresher == null) refresher = new SourceCaseRefresh(target, refreshUrl);
        sourceSha = refresher.refresh(cases.get(index));
        privateManifest.put("sourceSha256", sourceSha);
        android.util.AtomicFile output = new android.util.AtomicFile(manifestFile);
        FileOutputStream stream = output.startWrite();
        try {
            stream.write(privateManifest.toString(2).getBytes(StandardCharsets.UTF_8));
            output.finishWrite(stream);
        } catch (Exception failure) {
            output.failWrite(stream);
            throw new IllegalStateException("PRIVATE_MANIFEST_UPDATE_FAILED");
        }
        refreshedCases.add(index);
    }

    private static JSONObject errorDetails(Throwable failure) throws Exception {
        JSONObject details = new JSONObject();
        JSONArray types = new JSONArray();
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.size() < 12 && seen.add(cause); cause = cause.getCause()) {
            types.put(cause.getClass().getSimpleName());
            if (cause instanceof PlaybackException playback
                    && playback.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
                details.put("summary", "READ_POSITION_OUT_OF_RANGE");
            }
            if (cause instanceof HttpDataSource.InvalidResponseCodeException http) {
                details.put("httpResponseCode", http.responseCode).put("summary", "HTTP_RESPONSE_ERROR");
            }
            boolean parser = cause instanceof ParserException;
            if (parser) details.put("parserExceptionType", cause.getClass().getSimpleName()).put("summary", "PARSER_ERROR");
            if (cause instanceof java.io.EOFException) details.put("summary", "UNEXPECTED_EOF");
            String message = cause.getMessage();
            boolean nal = parser && message != null && (message.startsWith("Invalid NAL length")
                    || message.startsWith("NAL length") || message.startsWith("Negative NAL length"));
            if (nal) details.put("summary", "INVALID_NAL_LENGTH");
            if (parser || cause instanceof IndexOutOfBoundsException) {
                if (!parser) details.put("summary", "BUFFER_BOUNDS_ERROR");
                // Only named decimal bounds are copied; never include the exception's raw text.
                JSONObject numbers = new JSONObject();
                Matcher bounds = Pattern.compile("(?i)\\b(length|offset|index|position|limit|start|end)\\s*[:=]\\s*(-?\\d{1,18})(?!\\d)")
                        .matcher(message == null ? "" : message);
                while (bounds.find()) numbers.put(bounds.group(1).toLowerCase(java.util.Locale.ROOT), Long.parseLong(bounds.group(2)));
                if (numbers.length() > 0) details.put("bounds", numbers);
            }
        }
        return details.put("causeTypes", types);
    }

    private int trackCount(int type) {
        int count = 0;
        for (Tracks.Group group : engine.getPlayer().getCurrentTracks().getGroups()) if (group.getType() == type) count += group.length;
        return count;
    }

    private int[] nextEpisodePair() {
        for (int i = 0; i < cases.size(); i++) for (int j = 0; j < cases.size(); j++) {
            JSONObject a = cases.get(i), b = cases.get(j);
            if (a.optInt("episodeIndex") == 1 && b.optInt("episodeIndex") == 2
                    && !a.optString("seriesId").isEmpty() && a.optString("seriesId").equals(b.optString("seriesId"))) return new int[]{i, j};
        }
        throw new AssertionError("Manifest lacks first and second episodes of the same series");
    }

    private JSONObject record(int index) throws Exception {
        JSONObject item = cases.get(index);
        JSONObject result = new JSONObject().put("caseIndex", index).put("episodeIndex", item.optInt("episodeIndex"))
                .put("requestedQuality", item.optString("requestedQuality")).put("status", "INCOMPLETE")
                .put("sourceSha256", item.optString("sourceSha256", sourceSha));
        results.put(result);
        return result;
    }

    private void checkpoint() throws Exception {
        File external = target.getExternalFilesDir(null);
        assertNotNull("External playback report directory unavailable", external);
        File directory = new File(external, "source-validation");
        assertTrue("Could not create playback report directory", directory.isDirectory() || directory.mkdirs());
        JSONObject report = new JSONObject().put("schema", "tv.python-source-playback.v1").put("sourceSha256", sourceSha)
                .put("engine", reportName).put("caseCount", cases.size()).put("results", results)
                .put("decodeMode", decodeName).put("caseLimit", caseLimit)
                .put("selectedCaseIndex", caseIndex).put("refreshRequested", refreshUrl != null && !refreshUrl.isEmpty())
                .put("hardwareScope", "ANDROID_NODE_NO_PHYSICAL_HDMI_ASSERTION");
        try (FileOutputStream output = new FileOutputStream(new File(directory, "source-playback-" + reportName + ".json"))) {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void restoreSettings() {
        if (saved == null) return;
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        for (String key : SETTINGS.keySet()) {
            Object old = saved.get(key);
            if (old == null) editor.remove(key);
            else if (old instanceof Boolean v) editor.putBoolean(key, v);
            else if (old instanceof Integer v) editor.putInt(key, v);
            else if (old instanceof Long v) editor.putLong(key, v);
            else if (old instanceof Float v) editor.putFloat(key, v);
            else if (old instanceof String v) editor.putString(key, v);
            else if (old instanceof Set<?> set) { Set<String> copy = new HashSet<>(); for (Object v : set) copy.add((String) v); editor.putStringSet(key, copy); }
        }
        assertTrue("Could not restore playback settings", editor.commit());
    }

    private void await(BooleanSupplier condition, String reason) {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            if (error.get() != null) throw new AssertionError(reason + "; playbackErrorCode=" + error.get().errorCode);
            if (engine instanceof ExoPlayerEngine && value(() -> {
                Player player = engine.getPlayer();
                if (player.getPlaybackState() != Player.STATE_READY || player.getCurrentTracks().isEmpty()) return false;
                for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                    if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
                    for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i)) return false;
                }
                return true;
            })) {
                failureCode = "READY_WITHOUT_SELECTED_VIDEO_TRACK";
                throw new AssertionError(failureCode);
            }
            if (value(condition::getAsBoolean)) return;
            SystemClock.sleep(100);
        }
        failureCode = "TIMEOUT_" + reason.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        throw new AssertionError("Timeout: " + reason);
    }

    private void main(Runnable action) { value(() -> { action.run(); return null; }); }
    private <T> T value(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { result.set(action.get()); } catch (Throwable e) { failure.set(e); } });
        if (failure.get() != null) throw new AssertionError("UI operation failed: " + failure.get().getClass().getSimpleName());
        return result.get();
    }
}
