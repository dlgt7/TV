package com.fongmi.android.tv.player.subtitle;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.IndexSeekMap;
import androidx.media3.extractor.TrackOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Keeps sidecar ASS intact; Media3's unknown-subtitle extractor otherwise discards it. */
public final class ExternalAssExtractor implements Extractor {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Format format;
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final byte[] chunk = new byte[8192];
    private TrackOutput output;
    private ExtractorOutput extractorOutput;
    private boolean emitted;

    public ExternalAssExtractor(Format format) { this.format = format; }
    @Override public boolean sniff(ExtractorInput input) { return true; }
    @Override public void init(ExtractorOutput output) {
        extractorOutput = output;
        this.output = output.track(0, C.TRACK_TYPE_TEXT);
        this.output.format(format);
        // Reread the complete script from byte zero while retaining the requested media time.
        // An Unseekable map clamps this child period to zero and breaks MergingMediaPeriod.
        output.seekMap(new IndexSeekMap(new long[]{0}, new long[]{0}, C.TIME_UNSET));
        output.endTracks();
    }
    @Override public int read(ExtractorInput input, PositionHolder position) throws IOException {
        if (emitted) return RESULT_END_OF_INPUT;
        int count = input.read(chunk, 0, chunk.length);
        if (count != C.RESULT_END_OF_INPUT) {
            if (data.size() + count > MAX_BYTES) throw ParserException.createForMalformedContainer("ASS file exceeds 16 MiB", null);
            data.write(chunk, 0, count);
            return RESULT_CONTINUE;
        }
        byte[] file = data.toByteArray();
        extractorOutput.seekMap(new IndexSeekMap(new long[]{0}, new long[]{0}, AssPacket.durationUs(file)));
        output.sampleData(new ParsableByteArray(file), file.length);
        output.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, file.length, 0, null);
        data.reset();
        emitted = true;
        return RESULT_END_OF_INPUT;
    }
    @Override public void seek(long position, long timeUs) { data.reset(); emitted = false; }
    @Override public void release() { data.reset(); }
}
