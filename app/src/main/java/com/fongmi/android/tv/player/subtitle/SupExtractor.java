package com.fongmi.android.tv.player.subtitle;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.CueEncoder;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.pgs.PgsParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Streams SUP display sets instead of decoding and retaining a whole Blu-ray subtitle file. */
public final class SupExtractor implements Extractor {
    private static final int MAX_DISPLAY_SET = 4 * 1024 * 1024;
    private final Format format;
    private final PgsParser parser = new PgsParser();
    private final CueEncoder encoder = new CueEncoder();
    private final SupSeekMap index = new SupSeekMap();
    private final ByteArrayOutputStream display = new ByteArrayOutputStream();
    private final byte[] header = new byte[13];
    private final byte[] payload = new byte[65535];
    private ExtractorOutput extractorOutput;
    private TrackOutput output;
    private long timeUs;

    public SupExtractor(Format format) { this.format = format; }
    public static Format outputFormat(Format format) {
        return format.buildUpon().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(MimeTypes.APPLICATION_PGS)
                .setCueReplacementBehavior(Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE).build();
    }
    @Override public boolean sniff(ExtractorInput input) throws IOException {
        byte[] magic = new byte[2];
        return input.peekFully(magic, 0, 2, true) && magic[0] == 'P' && magic[1] == 'G';
    }
    @Override public void init(ExtractorOutput output) {
        extractorOutput = output;
        this.output = output.track(0, C.TRACK_TYPE_TEXT);
        this.output.format(outputFormat(format));
        output.seekMap(index);
        output.endTracks();
    }
    @Override public int read(ExtractorInput input, PositionHolder position) throws IOException {
        long segmentPosition = input.getPosition();
        if (!input.readFully(header, 0, header.length, true)) {
            if (display.size() != 0) throw ParserException.createForMalformedContainer("Incomplete SUP display set", null);
            index.finish(timeUs);
            extractorOutput.seekMap(index);
            return RESULT_END_OF_INPUT;
        }
        if (header[0] != 'P' || header[1] != 'G') throw ParserException.createForMalformedContainer("Expected PGS SUP header", null);
        int type = header[10] & 255;
        int size = ((header[11] & 255) << 8) | (header[12] & 255);
        input.readFully(payload, 0, size);
        if (display.size() == 0 || type == 0x16) {
            long pts = ((header[2] & 255L) << 24) | ((header[3] & 255L) << 16) | ((header[4] & 255L) << 8) | (header[5] & 255L);
            timeUs = pts * 1_000_000L / 90_000;
        }
        if (type == 0x16 && size >= 8 && (payload[7] & 0xc0) != 0) index.add(timeUs, segmentPosition);
        if (display.size() + size + 3 > MAX_DISPLAY_SET) throw ParserException.createForMalformedContainer("SUP display set exceeds 4 MiB", null);
        display.write(header, 10, 3);
        display.write(payload, 0, size);
        if (type == 0x80) {
            byte[] data = display.toByteArray();
            parser.parse(data, 0, data.length, SubtitleParser.OutputOptions.allCues(), cues -> {
                byte[] sample = encoder.encode(cues.cues, cues.durationUs);
                output.sampleData(new ParsableByteArray(sample), sample.length);
                output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sample.length, 0, null);
            });
            display.reset();
        }
        return RESULT_CONTINUE;
    }
    @Override public void seek(long position, long timeUs) { display.reset(); parser.reset(); this.timeUs = timeUs; }
    @Override public void release() { display.reset(); parser.reset(); }
}
