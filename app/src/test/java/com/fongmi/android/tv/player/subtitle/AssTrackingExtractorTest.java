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
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class AssTrackingExtractorTest {
    @Test public void splitsMetadataOffsetsAndForwardsOriginalBytes() {
        Fixture f = new Fixture(MimeTypes.TEXT_SSA);
        f.data("firstsecond");
        f.track.sampleMetadata(10, C.BUFFER_FLAG_KEY_FRAME, 5, 6, null);
        f.track.sampleMetadata(20, C.BUFFER_FLAG_KEY_FRAME, 6, 0, null);
        assertEquals("first", f.history.since("1", 0).get(0).text());
        assertEquals("second", f.history.since("1", 0).get(1).text());
        assertEquals(20, f.history.since("1", 0).get(1).timeUs());
        assertEquals("firstsecond", f.forwarded.toString(StandardCharsets.UTF_8));
    }
    @Test public void leavesOtherSubtitleFormatsUntouched() {
        Fixture f = new Fixture(MimeTypes.APPLICATION_SUBRIP);
        f.data("SRT text");
        f.track.sampleMetadata(10, C.BUFFER_FLAG_KEY_FRAME, 8, 0, null);
        assertFalse(f.history.contains("1"));
        assertEquals("SRT text", f.forwarded.toString(StandardCharsets.UTF_8));
    }
    @Test public void seekDropsOnlyIncompletePackets() {
        Fixture f = new Fixture(MimeTypes.TEXT_SSA);
        f.data("complete");
        f.track.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, 8, 0, null);
        f.data("partial");
        f.extractor.seek(0, 0);
        f.data("new");
        f.track.sampleMetadata(10, C.BUFFER_FLAG_KEY_FRAME, 3, 0, null);
        assertEquals(2, f.history.since("1", 0).size());
        assertEquals("new", f.history.since("1", 0).get(1).text());
    }
    @Test public void respectsEndOfInputContract() throws Exception {
        Fixture f = new Fixture(MimeTypes.TEXT_SSA);
        DataReader end = (data, offset, length) -> C.RESULT_END_OF_INPUT;
        assertEquals(C.RESULT_END_OF_INPUT, f.track.sampleData(end, 10, true, 0));
        assertThrows(EOFException.class, () -> f.track.sampleData(end, 10, false, 0));
    }
    private static final class Fixture {
        final AssHistory history = new AssHistory();
        final ByteArrayOutputStream forwarded = new ByteArrayOutputStream();
        final TrackOutput track;
        final AssTrackingExtractor extractor;
        Fixture(String mime) {
            TrackOutput[] captured = new TrackOutput[1];
            Extractor delegate = new Extractor() {
                @Override public boolean sniff(ExtractorInput input) { return true; }
                @Override public void init(ExtractorOutput output) { captured[0] = output.track(1, C.TRACK_TYPE_TEXT); }
                @Override public int read(ExtractorInput input, PositionHolder position) { return RESULT_END_OF_INPUT; }
                @Override public void seek(long position, long timeUs) {}
                @Override public void release() {}
            };
            extractor = new AssTrackingExtractor(delegate, history);
            extractor.init(new ExtractorOutput() {
                @Override public TrackOutput track(int id, int type) {
                    return new TrackOutput() {
                        @Override public void format(Format format) {}
                        @Override public int sampleData(DataReader input, int length, boolean allowEnd, int part) { throw new AssertionError("Unexpected direct read"); }
                        @Override public void sampleData(ParsableByteArray input, int length, int part) {
                            forwarded.write(input.getData(), input.getPosition(), length);
                            input.skipBytes(length);
                        }
                        @Override public void sampleMetadata(long time, int flags, int size, int offset, CryptoData crypto) {}
                    };
                }
                @Override public void endTracks() {}
                @Override public void seekMap(SeekMap map) {}
            });
            track = captured[0];
            track.format(new Format.Builder().setId("1").setSampleMimeType(mime).build());
        }
        void data(String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            track.sampleData(new ParsableByteArray(bytes), bytes.length, 0);
        }
    }
}
