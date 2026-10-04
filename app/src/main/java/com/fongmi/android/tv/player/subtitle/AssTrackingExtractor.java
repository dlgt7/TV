package com.fongmi.android.tv.player.subtitle;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Captures raw ASS before unselected SampleQueues discard overlapping events. */
final class AssTrackingExtractor implements Extractor {
    private final Extractor delegate;
    private final AssHistory history;
    private final Map<Integer, Output> outputs = new HashMap<>();

    AssTrackingExtractor(Extractor delegate, AssHistory history) { this.delegate = delegate; this.history = history; }
    @Override public boolean sniff(ExtractorInput input) throws IOException { return delegate.sniff(input); }
    @Override public void init(ExtractorOutput output) {
        delegate.init(new ExtractorOutput() {
            @Override public TrackOutput track(int id, int type) {
                if (type != C.TRACK_TYPE_TEXT) return output.track(id, type);
                return outputs.computeIfAbsent(id, ignored -> new Output(output.track(id, type)));
            }
            @Override public void endTracks() { output.endTracks(); }
            @Override public void seekMap(SeekMap map) { output.seekMap(map); }
        });
    }
    @Override public int read(ExtractorInput input, PositionHolder position) throws IOException { return delegate.read(input, position); }
    @Override public void seek(long position, long timeUs) {
        for (Output output : outputs.values()) { output.data.reset(); output.overflow = false; }
        delegate.seek(position, timeUs);
    }
    @Override public void release() { delegate.release(); outputs.clear(); }

    private final class Output implements TrackOutput {
        private final TrackOutput target;
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private String id;
        private boolean ass;
        private boolean overflow;
        Output(TrackOutput target) { this.target = target; }
        @Override public void durationUs(long duration) { target.durationUs(duration); }
        @Override public void format(Format format) {
            ass = MimeTypes.TEXT_SSA.equals(format.sampleMimeType);
            id = format.id;
            if (ass) history.register(id);
            target.format(format);
        }
        @Override public int sampleData(DataReader input, int length, boolean allowEnd, int part) throws IOException {
            if (!ass || part != SAMPLE_DATA_PART_MAIN) return target.sampleData(input, length, allowEnd, part);
            byte[] chunk = new byte[Math.min(length, 8192)];
            int count = input.read(chunk, 0, chunk.length);
            if (count == C.RESULT_END_OF_INPUT) { if (allowEnd) return count; throw new EOFException(); }
            sampleData(new ParsableByteArray(chunk, count), count, part);
            return count;
        }
        @Override public void sampleData(ParsableByteArray input, int length, int part) {
            if (ass && part == SAMPLE_DATA_PART_MAIN) {
                if (data.size() + (long) length > 16 * 1024 * 1024) overflow = true;
                if (!overflow) data.write(input.getData(), input.getPosition(), length);
            }
            target.sampleData(input, length, part);
        }
        @Override public void sampleMetadata(long time, int flags, int size, int offset, CryptoData crypto) {
            if (ass) {
                byte[] bytes = data.toByteArray();
                int start = bytes.length - offset - size;
                if (!overflow && crypto == null && offset >= 0 && start >= 0 && size >= 0) history.add(id, time, new String(bytes, start, size, StandardCharsets.UTF_8));
                data.reset();
                if (!overflow && offset > 0 && offset <= bytes.length) data.write(bytes, bytes.length - offset, offset);
                overflow = false;
            }
            target.sampleMetadata(time, flags, size, offset, crypto);
        }
    }
}
