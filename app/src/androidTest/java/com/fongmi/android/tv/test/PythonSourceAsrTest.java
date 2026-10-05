package com.fongmi.android.tv.test;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.ui.SubtitleView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.ai.subtitle.AiLanguage;
import com.fongmi.android.tv.ai.subtitle.AiSubtitleRuntime;
import com.fongmi.android.tv.ai.subtitle.AiSubtitleSettings;
import com.fongmi.android.tv.ai.subtitle.AsrModel;
import com.fongmi.android.tv.ai.subtitle.AsrModelManager;
import com.fongmi.android.tv.ai.subtitle.SherpaSubtitleController;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.ExoPlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in, real Exo PCM -> local Mandarin ASR -> visible subtitle check, capped at 90 seconds.
 * Models must already be installed in the target package's files/asr-models directory.
 * Reads private source-validation/playback-cases.json; source_case_index optionally selects
 * a zero-based direct first-episode case. source_refresh_url refreshes only that private case.
 * No controller.offer calls, injected cues, remote translation, or persistent source configuration.
 */
@RunWith(AndroidJUnit4.class)
public final class PythonSourceAsrTest {
    private static final Map<String, Object> SETTINGS = Map.ofEntries(
            Map.entry("ai_subtitle_enabled", true),
            Map.entry("ai_subtitle_language", "zh"),
            Map.entry("ai_subtitle_translation_provider", "OFF"),
            Map.entry("ai_subtitle_subtitle_mode", "BILINGUAL"),
            Map.entry("audio_effect_preset", 0),
            Map.entry("preload", false),
            Map.entry("preload_next", false),
            Map.entry("tunnel", false));
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context target = instrumentation.getTargetContext();
    private final JSONObject report = new JSONObject();
    private final JSONArray displays = new JSONArray();
    private final JSONArray cleanupErrors = new JSONArray();
    private final AtomicReference<PlaybackException> playbackError = new AtomicReference<>();
    private final AtomicBoolean firstFrame = new AtomicBoolean();
    private final Map<String, Object> savedSettings = new HashMap<>();
    private CorePlaybackActivity activity;
    private ExoPlayerEngine engine;
    private AiSubtitleRuntime runtime;
    private SourceCaseRefresh refresher;
    private Field subtitleCues;
    private File reportFile;
    private Object lastPaintedCues;
    private boolean settingsChanged;
    private long started;
    private long baselineRecognized;
    private long baselineOffered;
    private long recognizedDelta;
    private long offeredDelta;
    private long playbackPosition;
    private long observationStarted = -1;
    private int displayedEvents;
    private int maximumCharacters;
    private int selectedAudioTracks;
    private String stage = "setup";

    @Test(timeout = 90_000L)
    public void realMandarinSpeechProducesVisibleOriginalSubtitles() throws Exception {
        started = SystemClock.elapsedRealtime();
        // Reserve time to release the player, close the optional source helper and restore settings.
        long observationDeadline = started + 75_000L;
        boolean passed = false;
        report.put("schema", "tv.python-source-asr.v1");
        report.put("status", "RUNNING");
        report.put("startedAtEpochMs", System.currentTimeMillis());
        report.put("totalBudgetSeconds", 90);
        report.put("observationBudgetSeconds", 75);
        report.put("language", "zh");
        report.put("model", AsrModel.MANDARIN_ZIPFORMER_CTC.folder);
        report.put("translationProvider", "OFF");
        report.put("coverage", "REAL_EXO_AUDIO_LOCAL_ASR_ORIGINAL_SUBTITLE_DISPLAY");
        report.put("hardwareScope", "ANDROID_NODE_NO_PHYSICAL_HDMI_ASSERTION");
        report.put("displays", displays);
        report.put("cleanupErrors", cleanupErrors);
        try {
            File external = target.getExternalFilesDir(null);
            require(external != null, "REPORT_DIRECTORY_UNAVAILABLE", true);
            reportFile = new File(new File(external, "source-validation"), "source-asr.json");
            saveReport();

            stage = "model-validation";
            AsrModelManager models = new AsrModelManager(target);
            report.put("totalRamMb", models.getTotalRamMb());
            report.put("requiredRamMb", AsrModel.MANDARIN_ZIPFORMER_CTC.minRamMb);
            report.put("modelFilesVerified", models.isInstalled(AiLanguage.MANDARIN));
            require(report.getBoolean("modelFilesVerified"), "MANDARIN_MODEL_NOT_INSTALLED_OR_INVALID", true);
            require(models.canRun(AiLanguage.MANDARIN), "MODEL_RAM_REQUIREMENT_NOT_MET", true);

            stage = "private-case";
            JSONObject manifest = readJson(new File(target.getFilesDir(), "source-validation/playback-cases.json"));
            String sourceSha = manifest.getString("sourceSha256");
            require(sourceSha.matches("[0-9a-f]{64}"), "INVALID_SOURCE_CONTENT_HASH", false);
            JSONArray cases = manifest.getJSONArray("cases");
            int requested = Integer.parseInt(InstrumentationRegistry.getArguments().getString("source_case_index", "-1"));
            int selected = selectFirstEpisode(cases, requested);
            require(selected >= 0, "NO_DIRECT_FIRST_EPISODE_CASE", true);
            JSONObject item = new JSONObject(cases.getJSONObject(selected).toString());
            report.put("sourceSha256", sourceSha);
            report.put("caseIndex", selected);
            report.put("episodeIndex", 1);

            String refreshUrl = InstrumentationRegistry.getArguments().getString("source_refresh_url", "");
            report.put("sourceRefreshElapsedMs", 0);
            if (!refreshUrl.isEmpty()) {
                stage = "source-refresh";
                refresher = new SourceCaseRefresh(target, refreshUrl);
                long refreshStarted = SystemClock.elapsedRealtime();
                try {
                    report.put("sourceSha256", refresher.refresh(item));
                } finally {
                    report.put("sourceRefreshElapsedMs", SystemClock.elapsedRealtime() - refreshStarted);
                }
                report.put("sourceRefreshed", true);
            } else {
                report.put("sourceRefreshed", false);
            }
            require(isDirectFirstEpisode(item), "REFRESHED_CASE_IS_NOT_A_DIRECT_FIRST_EPISODE", true);
            PlaySpec spec = PlaySpec.from("source-asr-probe", item.getString("url"),
                    readHeaders(item.optJSONObject("headers")), MediaMetadata.EMPTY);
            long remainingAfterPreparation = observationDeadline - SystemClock.elapsedRealtime();
            report.put("preparationElapsedMs", SystemClock.elapsedRealtime() - started);
            report.put("availableObservationMs", Math.max(0, remainingAfterPreparation));
            require(remainingAfterPreparation >= 30_000L, "PREPARATION_LEFT_INSUFFICIENT_ASR_BUDGET", true);

            stage = "player-setup";
            Map<String, ?> current = Prefers.getPrefers().getAll();
            for (String key : SETTINGS.keySet()) if (current.containsKey(key)) savedSettings.put(key, current.get(key));
            settingsChanged = true;
            SETTINGS.forEach(Prefers::put);
            require(AiSubtitleSettings.isEnabled()
                    && AiSubtitleSettings.getLanguage() == AiLanguage.MANDARIN
                    && AiSubtitleSettings.getTranslationProvider() == AiSubtitleSettings.TranslationProvider.OFF,
                    "LOCAL_ASR_SETTINGS_NOT_APPLIED", false);
            subtitleCues = SubtitleView.class.getDeclaredField("cues");
            subtitleCues.setAccessible(true);
            activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(target, CorePlaybackActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(() -> {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                runtime = AiSubtitleRuntime.get();
                baselineRecognized = runtime.metrics().recognizedSegments;
                baselineOffered = runtime.metrics().offeredChunks;
                // This must precede creating the Exo audio sink: the production factory checks enabled().
                runtime.attachPlayerView(activity.view);
                engine = new ExoPlayerEngine(PlayerEngine.HARD, new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException error) { playbackError.set(error); }
                    @Override public void onRenderedFirstFrame() { firstFrame.set(true); }
                    @Override public void onTracksChanged(Tracks tracks) { runtime.onTracksChanged(tracks); }
                    @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                                 Player.PositionInfo newPosition, int reason) {
                        runtime.onPlaybackPositionDiscontinuity();
                    }
                });
                // Only ASR may populate the observed view: native/subtitle-file cues are not evidence.
                engine.getPlayer().setTrackSelectionParameters(engine.getPlayer().getTrackSelectionParameters()
                        .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                activity.view.setPlayer(engine.getPlayer());
                engine.bindPlayerView(activity.view);
                engine.start(spec, 0);
            });

            stage = "speech-and-display";
            long observationBudget = observationDeadline - SystemClock.elapsedRealtime();
            report.put("availableObservationMs", Math.max(0, observationBudget));
            require(observationBudget >= 30_000L, "PLAYER_SETUP_LEFT_INSUFFICIENT_ASR_BUDGET", true);
            observationStarted = SystemClock.elapsedRealtime();
            long nextCheckpoint = 0;
            while (SystemClock.elapsedRealtime() < observationDeadline) {
                PlaybackException error = playbackError.get();
                if (error != null) {
                    report.put("playbackErrorCode", error.errorCode);
                    throw new ProbeFailure("PLAYER_ERROR", false);
                }
                observeSubtitleView();
                SherpaSubtitleController.Metrics metrics = runtime.metrics();
                recognizedDelta = Math.max(0, metrics.recognizedSegments - baselineRecognized);
                offeredDelta = Math.max(0, metrics.offeredChunks - baselineOffered);
                updateMetrics(metrics);
                if (SystemClock.elapsedRealtime() >= nextCheckpoint || displayedEvents > 0) {
                    saveReport();
                    nextCheckpoint = SystemClock.elapsedRealtime() + 1000;
                }
                if (firstFrame.get() && playbackPosition > 1000 && selectedAudioTracks > 0
                        && offeredDelta > 0 && recognizedDelta > 0 && displayedEvents > 0) {
                    passed = true;
                    break;
                }
                SystemClock.sleep(100);
            }
            if (!passed) {
                String reason = !firstFrame.get() ? "NO_RENDERED_VIDEO_WITHIN_BUDGET"
                        : selectedAudioTracks == 0 ? "NO_SELECTED_AUDIO_TRACK"
                        : offeredDelta == 0 ? "NO_PRODUCTION_PCM_DELIVERED"
                        : recognizedDelta == 0 ? "NO_ASR_SEGMENT_WITHIN_BUDGET"
                        : "ASR_RECOGNIZED_BUT_NO_VISIBLE_SUBTITLE";
                throw new ProbeFailure(reason, false);
            }
            stage = "complete";
            report.put("status", "PASS");
        } catch (ProbeFailure error) {
            passed = false;
            report.put("status", error.limitation ? "LIMITATION" : "FAIL");
            report.put("failureCode", error.code);
        } catch (Throwable error) {
            passed = false;
            report.put("status", "FAIL");
            report.put("failureCode", "UNEXPECTED_" + error.getClass().getSimpleName());
        } finally {
            report.put("stage", stage);
            report.put("asrObservationStarted", observationStarted >= 0);
            report.put("observedPlaybackMs", observationStarted < 0 ? 0
                    : SystemClock.elapsedRealtime() - observationStarted);
            cleanup();
            if (cleanupErrors.length() > 0) {
                passed = false;
                report.put("status", "FAIL");
            }
            report.put("elapsedMs", SystemClock.elapsedRealtime() - started);
            report.put("finishedAtEpochMs", System.currentTimeMillis());
            report.put("recognizedSegments", recognizedDelta);
            report.put("displayedSubtitleEvents", displayedEvents);
            report.put("maximumDisplayedCharacters", maximumCharacters);
            saveReport();
        }
        assertTrue("ASR/display evidence incomplete; see source-validation/source-asr.json", passed);
    }

    /**
     * Reads the actual SubtitleView cue list, never the separate runtime status TextView.
     * Paints that attached, visible view onto a transparent bitmap and requires nontransparent
     * subtitle output. No cues or recognized text are inserted or changed by this test.
     */
    private void observeSubtitleView() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        main(() -> {
            try {
                playbackPosition = engine.getPlayer().getCurrentPosition();
                selectedAudioTracks = 0;
                for (Tracks.Group group : engine.getPlayer().getCurrentTracks().getGroups()) {
                    if (group.getType() == C.TRACK_TYPE_AUDIO) for (int i = 0; i < group.length; i++) {
                        if (group.isTrackSelected(i)) selectedAudioTracks++;
                    }
                }
                SubtitleView view = activity.view.getSubtitleView();
                Object raw = subtitleCues.get(view);
                if (!(raw instanceof List<?> cues) || raw == lastPaintedCues || cues.isEmpty()
                        || !view.isShown() || view.getWindowVisibility() != View.VISIBLE
                        || view.getAlpha() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0
                        || !view.getGlobalVisibleRect(new Rect())) return;
                int characters = 0;
                for (Object entry : cues) if (entry instanceof Cue cue && cue.text != null) {
                    characters += cue.text.toString().trim().length();
                }
                if (characters == 0) return;
                int width = Math.min(480, view.getWidth());
                int height = Math.max(1, Math.round((float) view.getHeight() * width / view.getWidth()));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                int painted = 0;
                try {
                    Canvas canvas = new Canvas(bitmap);
                    canvas.scale((float) width / view.getWidth(), (float) height / view.getHeight());
                    view.draw(canvas);
                    int[] pixels = new int[width * height];
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                    for (int pixel : pixels) if ((pixel >>> 24) != 0) painted++;
                } finally {
                    bitmap.recycle();
                }
                if (painted < 10) return;
                lastPaintedCues = raw;
                displayedEvents++;
                maximumCharacters = Math.max(maximumCharacters, characters);
                if (displays.length() < 32) displays.put(new JSONObject()
                        .put("elapsedMs", SystemClock.elapsedRealtime() - started)
                        .put("characters", characters).put("paintedPixels", painted));
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new ProbeFailure("SUBTITLE_DISPLAY_OBSERVATION_FAILED", false);
    }

    private void updateMetrics(SherpaSubtitleController.Metrics metrics) throws Exception {
        report.put("productionPcmChunks", offeredDelta);
        report.put("recognizedSegments", recognizedDelta);
        report.put("droppedPcmChunks", metrics.droppedChunks);
        report.put("droppedSpeechSegments", metrics.droppedSpeechSegments);
        report.put("maxPcmOfferMicros", metrics.maxOfferMicros);
        report.put("firstVideoFrame", firstFrame.get());
        report.put("playbackPositionMs", playbackPosition);
        report.put("selectedAudioTracks", selectedAudioTracks);
        report.put("displayedSubtitleEvents", displayedEvents);
        report.put("maximumDisplayedCharacters", maximumCharacters);
        report.put("subtitleEvidence", "ATTACHED_VISIBLE_SUBTITLE_VIEW_NONEMPTY_CUES_AND_DRAWN_PIXELS");
    }

    private void cleanup() throws Exception {
        boolean settingsRestored = !settingsChanged;
        try {
            main(() -> {
                try {
                    if (runtime != null) {
                        runtime.stopSession();
                        if (activity != null) runtime.detachPlayerView(activity.view);
                    }
                } finally {
                    try {
                        if (engine != null) {
                            engine.bindPlayerView(null);
                            if (activity != null) activity.view.setPlayer(null);
                            engine.release();
                        }
                    } finally {
                        if (activity != null) activity.finish();
                    }
                }
            });
        } catch (Throwable error) {
            cleanupErrors.put("PLAYER_CLEANUP_FAILED");
        }
        try {
            if (refresher != null) refresher.close();
        } catch (Throwable error) {
            cleanupErrors.put("SOURCE_REFRESH_CLEANUP_FAILED");
        } finally {
            if (settingsChanged) {
                settingsRestored = true;
                SharedPreferences.Editor editor = Prefers.getPrefers().edit();
                for (String key : SETTINGS.keySet()) {
                    Object value = savedSettings.get(key);
                    if (value == null) editor.remove(key);
                    else if (value instanceof Boolean item) editor.putBoolean(key, item);
                    else if (value instanceof String item) editor.putString(key, item);
                    else if (value instanceof Integer item) editor.putInt(key, item);
                    else if (value instanceof Long item) editor.putLong(key, item);
                    else if (value instanceof Float item) editor.putFloat(key, item);
                    else if (value instanceof Set<?> items) {
                        java.util.HashSet<String> strings = new java.util.HashSet<>();
                        for (Object item : items) strings.add((String) item);
                        editor.putStringSet(key, strings);
                    } else {
                        settingsRestored = false;
                        cleanupErrors.put("UNKNOWN_SETTING_TYPE");
                    }
                }
                if (!editor.commit()) {
                    settingsRestored = false;
                    cleanupErrors.put("SETTINGS_RESTORE_COMMIT_FAILED");
                }
                Map<String, ?> restored = Prefers.getPrefers().getAll();
                for (String key : SETTINGS.keySet()) {
                    if (restored.containsKey(key) != savedSettings.containsKey(key)
                            || !java.util.Objects.equals(restored.get(key), savedSettings.get(key))) {
                        cleanupErrors.put("SETTINGS_RESTORE_MISMATCH");
                        settingsRestored = false;
                        break;
                    }
                }
            }
        }
        report.put("settingsRestored", settingsRestored);
    }

    private static JSONObject readJson(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int read;
            while ((read = input.read(bytes)) != -1) {
                require(output.size() + read <= 4 * 1024 * 1024, "PRIVATE_MANIFEST_TOO_LARGE", false);
                output.write(bytes, 0, read);
            }
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        }
    }

    private static int selectFirstEpisode(JSONArray cases, int requested) {
        if (requested >= 0) return requested < cases.length() && isDirectFirstEpisode(cases.optJSONObject(requested))
                ? requested : -1;
        for (int i = 0; i < cases.length(); i++) if (isDirectFirstEpisode(cases.optJSONObject(i))) return i;
        return -1;
    }

    private static boolean isDirectFirstEpisode(JSONObject item) {
        if (item == null || item.optInt("episodeIndex") != 1 || item.optInt("parse", -1) != 0) return false;
        try {
            URI uri = URI.create(item.optString("url"));
            String host = uri.getHost();
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && host != null
                    && !"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)
                    && !"[::1]".equals(host) && !"::1".equals(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Map<String, String> readHeaders(JSONObject object) throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (object != null) for (Iterator<String> keys = object.keys(); keys.hasNext();) {
            String key = keys.next();
            require(object.get(key) instanceof String, "INVALID_PRIVATE_HEADER_TYPE", false);
            headers.put(key, object.getString(key));
        }
        return headers;
    }

    private void saveReport() throws Exception {
        if (reportFile == null) return;
        File directory = reportFile.getParentFile();
        require(directory.isDirectory() || directory.mkdirs(), "REPORT_DIRECTORY_UNAVAILABLE", true);
        File temporary = new File(directory, reportFile.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        require(temporary.renameTo(reportFile), "REPORT_REPLACE_FAILED", true);
    }

    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new ProbeFailure("MAIN_THREAD_" + failure.get().getClass().getSimpleName(), false);
        }
    }

    private static void require(boolean condition, String code, boolean limitation) {
        if (!condition) throw new ProbeFailure(code, limitation);
    }

    private static final class ProbeFailure extends AssertionError {
        final String code;
        final boolean limitation;

        ProbeFailure(String code, boolean limitation) {
            super(code);
            this.code = code;
            this.limitation = limitation;
        }
    }
}
