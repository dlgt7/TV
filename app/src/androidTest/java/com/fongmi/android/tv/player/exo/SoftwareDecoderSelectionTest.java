package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.*;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.player.engine.PlayerEngine;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Exercises the actual fork's track mapping and selection, without network or codec instances. */
@RunWith(AndroidJUnit4.class)
public final class SoftwareDecoderSelectionTest {
    private static final TrackGroup AUDIO = new TrackGroup(new Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC).setChannelCount(2).setSampleRate(48000).build());
    private static final TrackGroup VIDEO = new TrackGroup(new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(640).setHeight(360).build());

    @Test public void softwareFilterUsesClassificationRatherThanNamesOrAccelerationAlone() {
        MediaCodecInfo hardware = decoder("c2.vendor.avc.decoder", true, false);
        MediaCodecInfo unknown = decoder("unclassified.decoder", false, false);
        MediaCodecInfo platform = decoder("c2.android.avc.decoder", false, true);
        MediaCodecInfo vendorSoftware = decoder("vendor.software.decoder", false, true);
        assertEquals(List.of(platform, vendorSoftware),
                ExoUtil.softwareDecoders(List.of(hardware, unknown, platform, vendorSoftware)));
        assertTrue(ExoUtil.softwareDecoders(List.of(hardware, unknown)).isEmpty());
    }

    @Test public void softModeSelectsPlatformSoftwareAudioAndVideoWithoutFfmpeg() throws Exception {
        onMain(() -> {
            TrackSelectorResult result = select(PlayerEngine.SOFT, platform(true, true),
                    new TrackGroupArray(AUDIO, VIDEO), selector -> {});
            assertNotNull(result.selections[0]);
            assertNotNull(result.selections[1]);
        });
    }

    @Test public void softModeStillPrefersAnAvailableFfmpegRenderer() throws Exception {
        onMain(() -> {
            RendererCapabilities[] renderers = {
                    capability("MediaCodecVideoRenderer", C.TRACK_TYPE_VIDEO, true),
                    capability("FfmpegVideoRenderer", C.TRACK_TYPE_VIDEO, true)};
            TrackSelectorResult soft = select(PlayerEngine.SOFT, renderers, new TrackGroupArray(VIDEO), selector -> {});
            assertNull(soft.selections[0]);
            assertNotNull(soft.selections[1]);
            TrackSelectorResult hard = select(PlayerEngine.HARD, renderers, new TrackGroupArray(VIDEO), selector -> {});
            assertNotNull(hard.selections[0]);
            assertNull(hard.selections[1]);
        });
    }

    @Test public void noSoftwareVideoDecoderRaisesAnExplicitFormatError() throws Exception {
        onMain(() -> {
            ExoPlaybackException failure = assertThrows(ExoPlaybackException.class,
                    () -> select(PlayerEngine.SOFT, platform(true, false),
                            new TrackGroupArray(AUDIO, VIDEO), selector -> {}));
            assertEquals(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, failure.errorCode);
            assertEquals(MimeTypes.VIDEO_H264, failure.rendererFormat.sampleMimeType);
        });
    }

    @Test public void anUnsupportedAlternativeDoesNotRejectPlayableAudio() throws Exception {
        onMain(() -> {
            TrackGroup unsupported = new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_DTS).build());
            TrackSelectorResult result = select(PlayerEngine.SOFT, platform(true, true),
                    new TrackGroupArray(unsupported, AUDIO, VIDEO), selector -> {});
            assertEquals(AUDIO, result.selections[0].getTrackGroup());
        });
    }

    @Test public void aTrickPlayTrackCannotHideAMissingMainVideoDecoder() throws Exception {
        onMain(() -> {
            TrackGroup main = new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build());
            TrackGroup preview = new TrackGroup(VIDEO.getFormat(0).buildUpon().setRoleFlags(C.ROLE_FLAG_TRICK_PLAY).build());
            ExoPlaybackException failure = assertThrows(ExoPlaybackException.class,
                    () -> select(PlayerEngine.SOFT, platform(true, true),
                            new TrackGroupArray(main, preview), selector -> {}));
            assertEquals(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, failure.errorCode);
            assertEquals(MimeTypes.VIDEO_H265, failure.rendererFormat.sampleMimeType);
        });
    }

    @Test public void intentionallyDisabledVideoDoesNotBecomeADecoderFailure() throws Exception {
        onMain(() -> {
            select(PlayerEngine.SOFT, platform(true, false), new TrackGroupArray(AUDIO, VIDEO),
                    selector -> selector.setParameters(selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)));
            select(PlayerEngine.SOFT, platform(true, false), new TrackGroupArray(AUDIO, VIDEO),
                    selector -> selector.setParameters(selector.buildUponParameters().setRendererDisabled(1, true)));
            select(PlayerEngine.SOFT, platform(true, false), new TrackGroupArray(AUDIO, VIDEO),
                    selector -> selector.setParameters(selector.buildUponParameters().addOverride(new TrackSelectionOverride(VIDEO, List.of()))));
        });
    }

    private static MediaCodecInfo decoder(String name, boolean accelerated, boolean software) {
        return MediaCodecInfo.newInstance(name, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H264,
                null, accelerated, software, false, false, false);
    }

    private static RendererCapabilities[] platform(boolean audio, boolean video) {
        return new RendererCapabilities[]{capability("MediaCodecAudioRenderer", C.TRACK_TYPE_AUDIO, audio),
                capability("MediaCodecVideoRenderer", C.TRACK_TYPE_VIDEO, video)};
    }

    private static RendererCapabilities capability(String name, int type, boolean supported) {
        return new RendererCapabilities() {
            @Override public String getName() { return name; }
            @Override public int getTrackType() { return type; }
            @Override public int supportsFormat(Format format) {
                if (MimeTypes.getTrackType(format.sampleMimeType) != type) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
                boolean handled = supported && (type == C.TRACK_TYPE_AUDIO
                        ? MimeTypes.AUDIO_AAC.equals(format.sampleMimeType) : MimeTypes.VIDEO_H264.equals(format.sampleMimeType));
                return RendererCapabilities.create(handled ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_SUBTYPE);
            }
            @Override public int supportsMixedMimeTypeAdaptation() { return RendererCapabilities.ADAPTIVE_NOT_SUPPORTED; }
        };
    }

    private static TrackSelectorResult select(int mode, RendererCapabilities[] renderers, TrackGroupArray groups,
            Consumer<DecodeTrackSelector> configure) throws ExoPlaybackException {
        DecodeTrackSelector selector = (DecodeTrackSelector) ExoUtil.buildTrackSelector(mode);
        configure.accept(selector);
        selector.init(parameters -> {}, BandwidthMeter.NO_OP);
        try {
            return selector.selectTracks(renderers, groups, new MediaSource.MediaPeriodId(new Object()), Timeline.EMPTY);
        } finally { selector.release(); }
    }

    private interface CheckedRunnable { void run() throws Exception; }

    private static void onMain(CheckedRunnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() instanceof Exception error) throw error;
        if (failure.get() instanceof Error error) throw error;
    }
}
