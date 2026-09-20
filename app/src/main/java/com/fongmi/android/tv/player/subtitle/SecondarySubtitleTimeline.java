package com.fongmi.android.tv.player.subtitle;

import androidx.annotation.NonNull;
import androidx.media3.common.Format;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Public-Media3 prototype for a second, independently rendered subtitle stream.
 *
 * <p>This deliberately owns only parsing and timeline lookup. It does not alter ExoPlayer's text
 * renderer or pretend to provide the private dual-renderer/libass implementation used upstream.
 * A UI overlay can query {@link #cuesAt(long)} with the current playback position and render the
 * returned cues in a second {@code SubtitleView}.
 */
@UnstableApi
public final class SecondarySubtitleTimeline {

    private final List<CuesWithTiming> samples;

    private SecondarySubtitleTimeline(List<CuesWithTiming> samples) {
        this.samples = samples;
    }

    @NonNull
    public static SecondarySubtitleTimeline parse(byte[] data, String sampleMimeType) {
        if (data == null) throw new IllegalArgumentException("Missing subtitle data");
        Format format = new Format.Builder().setSampleMimeType(sampleMimeType).build();
        DefaultSubtitleParserFactory factory = new DefaultSubtitleParserFactory();
        if (!factory.supportsFormat(format)) throw new IllegalArgumentException("Unsupported subtitle type: " + sampleMimeType);
        SubtitleParser parser = factory.create(format);
        List<CuesWithTiming> samples = new ArrayList<>();
        parser.parse(data, 0, data.length, SubtitleParser.OutputOptions.allCues(), samples::add);
        parser.reset();
        samples.sort(Comparator.comparingLong(sample -> sample.startTimeUs));
        return new SecondarySubtitleTimeline(List.copyOf(samples));
    }

    @NonNull
    public List<Cue> cuesAt(long positionMs) {
        if (positionMs < 0 || samples.isEmpty()) return Collections.emptyList();
        long positionUs = positionMs * 1000L;
        List<Cue> result = new ArrayList<>();
        for (CuesWithTiming sample : samples) {
            if (sample.startTimeUs > positionUs) break;
            if (sample.endTimeUs > positionUs) result.addAll(sample.cues);
        }
        return result.isEmpty() ? Collections.emptyList() : List.copyOf(result);
    }

    public int sampleCount() {
        return samples.size();
    }
}
