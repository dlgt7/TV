package com.fongmi.android.tv.debug;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;

import com.fongmi.android.tv.player.engine.PlaybackRecoveryPolicy;
import com.fongmi.android.tv.player.subtitle.ExternalFont;
import com.fongmi.android.tv.setting.SubtitleSetting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ADB driven self-check for the parts of this round that cannot be exercised by the preload
 * end-to-end test: the recovery decision table, the external-font selection round-trip, and the
 * effect of the selected font on actual subtitle rendering.
 *
 * <p>All three are pure logic with no UI surface, which is exactly why they are easy to get subtly
 * wrong and hard to notice at runtime. The font checks in particular pin the multi-face (TTC) bug:
 * the selection used to persist only the file path, so every reload reported face 0.
 *
 * <p>Run: {@code adb shell am start -n com.fongmi.android.tv/.debug.SelfCheckActivity}
 * then read {@code logcat -s SelfCheck:V}. Exits with a {@code RESULT passed=.. failed=..} line.
 */
@UnstableApi
public final class SelfCheckActivity extends Activity {

    public static final String TAG = "SelfCheck";
    private static final int RENDER_WIDTH = 480;
    private static final int RENDER_HEIGHT = 120;
    private static final String RENDER_TEXT = "Hamburgefonstiv 0123456789";
    private final List<String> failures = new ArrayList<>();
    private int passed;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        // Several checks deliberately clear the selection, so snapshot it up front and put it back
        // at the end. A test must never leave the device reconfigured.
        ExternalFont.Entry saved = SubtitleSetting.getFontEntry();
        checkRecoveryPolicy();
        checkRetryBackoff();
        checkFontRoundTrip();
        checkCaptionStyle();
        checkFontRendering();
        SubtitleSetting.putFontSelection(saved);
        Log.i(TAG, "RESTORED font=" + (saved == null ? "<none>" : saved.path() + " face=" + saved.faceIndex()));
        // Escape hatch for repairing a selection damaged by an earlier run.
        String setFontPath = getIntent().getStringExtra("setFontPath");
        if (setFontPath != null) {
            SubtitleSetting.putFontPath(setFontPath);
            Log.i(TAG, "SET font=" + SubtitleSetting.getFontPath() + " face=" + SubtitleSetting.getFontFaceIndex() + " family=" + SubtitleSetting.getFontFamily());
        }
        Log.i(TAG, "RESULT passed=" + passed + " failed=" + failures.size());
        for (String failure : failures) Log.e(TAG, "FAILED " + failure);
        finish();
    }

    private void expect(String name, Object actual, Object expected) {
        if (expected.equals(actual)) {
            passed++;
            Log.i(TAG, "PASS " + name + "=" + actual);
        } else {
            failures.add(name + " expected=" + expected + " actual=" + actual);
        }
    }

    /** Every retryable class must exhaust its budget and then stop, instead of spinning forever. */
    private void checkRecoveryPolicy() {
        PlaybackRecoveryPolicy.Action seek = PlaybackRecoveryPolicy.Action.SEEK_DEFAULT;
        PlaybackRecoveryPolicy.Action decode = PlaybackRecoveryPolicy.Action.SWITCH_DECODE;
        PlaybackRecoveryPolicy.Action transientRetry = PlaybackRecoveryPolicy.Action.RETRY_TRANSIENT;
        PlaybackRecoveryPolicy.Action formatRetry = PlaybackRecoveryPolicy.Action.RETRY_FORMAT;
        PlaybackRecoveryPolicy.Action fatal = PlaybackRecoveryPolicy.Action.FATAL;
        int budget = PlaybackRecoveryPolicy.MAX_ATTEMPTS;

        expect("live_window", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, 0), seek);
        expect("decode_init", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, 0), decode);
        expect("decode_query", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, 0), decode);
        expect("decoding", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_DECODING_FAILED, 0), decode);
        expect("decode_exceeds", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, 0), decode);

        // Transient transport failures used to fall through to FATAL; they must now retry, but only
        // while budget remains.
        expect("net_failed_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, 0), transientRetry);
        expect("net_timeout_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, 0), transientRetry);
        expect("bad_http_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 0), transientRetry);
        expect("timeout_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_TIMEOUT, 0), transientRetry);
        expect("net_timeout_exhausted", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, budget), fatal);
        expect("net_failed_exhausted", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, budget + 5), fatal);

        expect("container_malformed_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 0), formatRetry);
        expect("manifest_malformed_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, 0), formatRetry);
        expect("container_unsupported_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, 0), formatRetry);
        expect("io_unspecified_first", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, 0), formatRetry);
        // This is the regression the old code had: an unbounded parse retry loop.
        expect("container_malformed_exhausted", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, budget), fatal);

        expect("unspecified_is_fatal", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_UNSPECIFIED, 0), fatal);
        expect("drm_is_fatal", PlaybackRecoveryPolicy.decide(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, 0), fatal);
    }

    private void checkRetryBackoff() {
        long first = PlaybackRecoveryPolicy.retryDelayMs(0);
        long second = PlaybackRecoveryPolicy.retryDelayMs(1);
        long capped = PlaybackRecoveryPolicy.retryDelayMs(99);
        expect("backoff_first", first, 500L);
        expect("backoff_second", second, 1000L);
        if (second > first) passed++;
        else failures.add("backoff_not_increasing first=" + first + " second=" + second);
        // Must saturate rather than overflow into a negative delay.
        if (capped > 0 && capped <= 3000L) passed++;
        else failures.add("backoff_not_capped value=" + capped);
    }

    /**
     * Round-trips a real multi-face font (if one exists on the device) through the settings layer.
     * The family and face index must survive, which is the whole point of the fix.
     */
    private void checkFontRoundTrip() {
        ExternalFont.Entry original = SubtitleSetting.getFontEntry();
        File ttc = findMultiFaceFont();
        if (ttc == null) {
            Log.w(TAG, "SKIP font_round_trip: no multi-face font found under /system/fonts");
        } else {
            List<ExternalFont.Entry> entries = ExternalFont.getEntries(ttc);
            expect("ttc_face_count", entries.size() > 1, true);
            ExternalFont.Entry chosen = entries.get(entries.size() - 1);
            Log.i(TAG, "chosen face index=" + chosen.faceIndex() + " family=" + chosen.family() + " file=" + ttc.getName());
            SubtitleSetting.putFontSelection(chosen);
            expect("roundtrip_path", SubtitleSetting.getFontPath(), chosen.path());
            expect("roundtrip_face", SubtitleSetting.getFontFaceIndex(), chosen.faceIndex());
            // The regression: this used to report the collection's first family, not the selected one.
            expect("roundtrip_family", SubtitleSetting.getFontFamily(), chosen.family());
            ExternalFont.Entry reloaded = SubtitleSetting.getFontEntry();
            expect("roundtrip_reloaded_face", reloaded == null ? -1 : reloaded.faceIndex(), chosen.faceIndex());
            expect("roundtrip_has_custom", SubtitleSetting.hasCustomFont(), true);
            expect("roundtrip_typeface_nonnull", SubtitleSetting.getTypeface() != null, true);
        }
        // Never leave the user's own selection overwritten by a test.
        SubtitleSetting.putFontSelection(original);
        expect("restored_path", SubtitleSetting.getFontPath(), original == null ? "" : original.path());
        expect("restored_face", SubtitleSetting.getFontFaceIndex(), original == null ? 0 : original.faceIndex());
    }

    @Nullable
    private File findMultiFaceFont() {
        File dir = new File("/system/fonts");
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (!file.getName().endsWith(".ttc")) continue;
            if (ExternalFont.getEntries(file).size() > 1) return file;
        }
        return null;
    }

    /** captionStyle()/applyStyle() run on every playback start, so they must not throw. */
    private void checkCaptionStyle() {
        try {
            CaptionStyleCompat style = SubtitleSetting.captionStyle(this);
            expect("caption_style_nonnull", style != null, true);
            CaptionStyleCompat layered = SubtitleSetting.withTypeface(CaptionStyleCompat.DEFAULT);
            expect("with_typeface_nonnull", layered != null, true);
            // With no font selected the base style must pass through untouched.
            SubtitleSetting.putFontSelection(null);
            expect("with_typeface_passthrough", SubtitleSetting.withTypeface(CaptionStyleCompat.DEFAULT) == CaptionStyleCompat.DEFAULT, true);
            SubtitleView view = new SubtitleView(this);
            SubtitleSetting.applyStyle(view);
            expect("apply_style_ok", true, true);
        } catch (Throwable e) {
            failures.add("caption_style_threw " + e);
            Log.e(TAG, "caption style check threw", e);
        }
    }

    /**
     * Renders the same cue through the real {@link SubtitleView} twice — once with the default
     * style and once with a custom font layered on — and compares the resulting pixels. This is the
     * only check that proves the selected font reaches the screen; a non-throwing
     * {@code captionStyle()} would still pass if the typeface were silently dropped.
     */
    private void checkFontRendering() {
        CaptionStyleCompat base = new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
        long[] baseline = renderSignature(base);
        if (baseline[INK] == 0) {
            // Drawing nothing would make every comparison vacuously equal, so report rather than pass.
            Log.w(TAG, "SKIP font_rendering: SubtitleView produced no ink while detached from a window");
            return;
        }
        expect("render_baseline_has_ink", true, true);

        ExternalFont.Entry original = SubtitleSetting.getFontEntry();
        ExternalFont.Entry candidate = pickRenderFont();
        if (candidate == null) {
            Log.w(TAG, "SKIP font_rendering: no usable font file on device");
            return;
        }

        SubtitleSetting.putFontSelection(candidate);
        long[] styled = renderSignature(SubtitleSetting.withTypeface(base));
        SubtitleSetting.putFontSelection(original);

        Log.i(TAG, "render baseline ink=" + baseline[INK] + " right=" + baseline[RIGHT]
                + " | custom(" + candidate.family() + ") ink=" + styled[INK] + " right=" + styled[RIGHT]);
        expect("render_typeface_changes_pixels", styled[HASH] != baseline[HASH], true);
        // Glyph advance widths must move too; otherwise only antialiasing changed.
        expect("render_typeface_changes_ink_width", styled[RIGHT] != baseline[RIGHT], true);

        long[] restored = renderSignature(base);
        expect("render_restored_matches_baseline", restored[HASH], baseline[HASH]);
    }

    /** Prefer the user's own installed font, falling back to a system collection face. */
    @Nullable
    private ExternalFont.Entry pickRenderFont() {
        for (ExternalFont.Entry entry : ExternalFont.getAll()) return entry;
        File ttc = findMultiFaceFont();
        if (ttc == null) return null;
        List<ExternalFont.Entry> entries = ExternalFont.getEntries(ttc);
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    private static final int HASH = 0;
    private static final int RIGHT = 1;
    private static final int INK = 2;

    /** @return {pixel hash, rightmost inked column, inked pixel count} */
    private long[] renderSignature(CaptionStyleCompat style) {
        SubtitleView view = new SubtitleView(this);
        view.setApplyEmbeddedStyles(false);
        view.setApplyEmbeddedFontSizes(false);
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION);
        view.setStyle(style);
        view.setCues(Collections.singletonList(new Cue.Builder().setText(RENDER_TEXT).build()));
        Bitmap bitmap = Bitmap.createBitmap(RENDER_WIDTH, RENDER_HEIGHT, Bitmap.Config.ARGB_8888);
        view.measure(View.MeasureSpec.makeMeasureSpec(RENDER_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(RENDER_HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, RENDER_WIDTH, RENDER_HEIGHT);
        view.draw(new Canvas(bitmap));
        long hash = 1125899906842597L;
        int right = -1;
        int ink = 0;
        for (int y = 0; y < RENDER_HEIGHT; y++) {
            for (int x = 0; x < RENDER_WIDTH; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) == 0) continue;
                ink++;
                if (x > right) right = x;
                hash = hash * 31 + pixel;
            }
        }
        bitmap.recycle();
        return new long[]{hash, right, ink};
    }
}
