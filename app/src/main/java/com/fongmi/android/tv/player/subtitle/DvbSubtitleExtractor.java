package com.fongmi.android.tv.player.subtitle;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleTranscodingExtractorOutput;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Restores the active DVB page when a TS seek lands between subtitle updates. */
final class DvbSubtitleExtractor implements Extractor {
    private static final int MAX_PACKET_BYTES = 4 * 1024 * 1024;
    // TsExtractor buffers 50 TS packets. The read position can be ahead of the
    // current PES start by at most this amount; keep that packet in the seek range.
    private static final int TS_READ_BUFFER_BYTES = 50 * 188;
    private final Extractor delegate;
    private final SubtitleParser.Factory parsers;
    private final DvbSeekMap index = new DvbSeekMap();
    private final Map<Integer, Output> outputs = new HashMap<>();
    private SubtitleTranscodingExtractorOutput transcoder;
    private long readPosition;
    private long pendingPosition = C.INDEX_UNSET;

    DvbSubtitleExtractor(SubtitleParser.Factory parsers) {
        this(new TsExtractor(TsExtractor.MODE_SINGLE_PMT, TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
                SubtitleParser.Factory.UNSUPPORTED, new TimestampAdjuster(0),
                new DefaultTsPayloadReaderFactory(0), TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10), parsers);
    }
    DvbSubtitleExtractor(Extractor delegate, SubtitleParser.Factory parsers) {
        this.delegate = delegate;
        this.parsers = parsers;
    }
    @Override public boolean sniff(ExtractorInput input) throws IOException { return delegate.sniff(input); }
    @Override public void init(ExtractorOutput target) {
        transcoder = new SubtitleTranscodingExtractorOutput(target, parsers);
        delegate.init(new ExtractorOutput() {
            @Override public TrackOutput track(int id, int type) {
                if (type != C.TRACK_TYPE_TEXT) return transcoder.track(id, type);
                return outputs.computeIfAbsent(id, ignored -> new Output(id, transcoder.track(id, type)));
            }
            @Override public void endTracks() { transcoder.endTracks(); }
            @Override public void seekMap(SeekMap map) { index.delegate(map); target.seekMap(index); }
        });
    }
    @Override public int read(ExtractorInput input, PositionHolder position) throws IOException {
        if (pendingPosition != C.INDEX_UNSET) {
            long next = pendingPosition;
            pendingPosition = C.INDEX_UNSET;
            if (input.getPosition() != next) { position.position = next; return RESULT_SEEK; }
        }
        readPosition = Math.max(0, input.getPosition() - TS_READ_BUFFER_BYTES);
        return delegate.read(input, position);
    }
    @Override public void seek(long position, long timeUs) {
        for (Output output : outputs.values()) output.data.reset();
        if (transcoder != null) transcoder.resetSubtitleParsers();
        index.reset();
        if (index.hasDvb()) {
            pendingPosition = timeUs == 0 ? 0 : index.getSeekPoints(timeUs).first.position;
            // A nonzero time makes TsExtractor's binary search skip the acquisition
            // again. Its established zero-based timestamp offset remains valid.
            delegate.seek(pendingPosition, 0);
        } else {
            pendingPosition = C.INDEX_UNSET;
            delegate.seek(position, timeUs);
        }
    }
    @Override public void release() {
        delegate.release();
        outputs.clear();
    }

    private final class Output implements TrackOutput {
        final int id;
        final TrackOutput target;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        boolean dvb;
        long packetPosition;
        Output(int id, TrackOutput target) { this.id = id; this.target = target; }
        @Override public void durationUs(long durationUs) { target.durationUs(durationUs); }
        @Override public void format(Format format) {
            dvb = MimeTypes.APPLICATION_DVBSUBS.equals(format.sampleMimeType);
            if (dvb && !format.initializationData.isEmpty() && format.initializationData.get(0).length >= 4) {
                ParsableByteArray init = new ParsableByteArray(format.initializationData.get(0));
                index.register(id, init.readUnsignedShort(), init.readUnsignedShort());
            }
            target.format(format);
        }
        @Override public int sampleData(DataReader input, int length, boolean allowEnd, int part) throws IOException {
            if (!dvb || part != SAMPLE_DATA_PART_MAIN) return target.sampleData(input, length, allowEnd, part);
            byte[] chunk = new byte[Math.min(length, 8192)];
            int count = input.read(chunk, 0, chunk.length);
            if (count == C.RESULT_END_OF_INPUT) { if (allowEnd) return count; throw new EOFException(); }
            sampleData(new ParsableByteArray(chunk, count), count, part);
            return count;
        }
        @Override public void sampleData(ParsableByteArray input, int length, int part) {
            if (!dvb || part != SAMPLE_DATA_PART_MAIN) { target.sampleData(input, length, part); return; }
            if (data.size() == 0) packetPosition = readPosition;
            if (data.size() + (long) length > MAX_PACKET_BYTES) {
                // A DVB PES is much smaller than this. Never retain unbounded damaged input.
                throw new IllegalArgumentException("DVB packet exceeds 4 MiB");
            }
            data.write(input.getData(), input.getPosition(), length);
            input.skipBytes(length);
        }
        @Override public void sampleMetadata(long timeUs, int flags, int size, int offset, CryptoData crypto) {
            if (!dvb) { target.sampleMetadata(timeUs, flags, size, offset, crypto); return; }
            byte[] bytes = data.toByteArray();
            int start = bytes.length - offset - size;
            if (start < 0 || offset < 0 || size < 0) throw new IllegalArgumentException("Invalid DVB packet bounds");
            byte[] packet = java.util.Arrays.copyOfRange(bytes, start, start + size);
            index.packet(id, timeUs, packetPosition, packet);
            // Deliver only complete PES samples. A seek must not leave partial bytes
            // in the transcoder, and DvbParser expects a byte slice starting at zero.
            target.sampleData(new ParsableByteArray(packet), packet.length);
            target.sampleMetadata(timeUs, flags, packet.length, 0, crypto);
            data.reset();
            if (offset > 0) data.write(bytes, bytes.length - offset, offset);
        }
    }
}
