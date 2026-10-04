package com.fongmi.android.tv.player.subtitle;

import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import java.io.ByteArrayOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class DvbSeekMapTest {
    @Test public void leavesNonDvbSeekingUnchanged() {
        DvbSeekMap index = index();
        assertFalse(index.hasDvb());
        assertEquals(2000, index.getSeekPoints(200).first.position);
        assertEquals(1_000_000, index.getDurationUs());
    }
    @Test public void seeksBeforeTheActivePageInsteadOfTheNextUpdate() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        index.packet(1, 100, 500, page(10, 1));
        index.packet(1, 150, 900, page(10, 0));
        index.packet(1, 250, 1800, page(10, 2));
        assertEquals(500, index.getSeekPoints(200).first.position);
        assertEquals(1800, index.getSeekPoints(300).first.position);
        assertEquals(0, index.getSeekPoints(50).first.position);
    }
    @Test public void unvisitedTargetUsesTheLatestKnownAcquisition() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        index.packet(1, 100, 500, page(10, 1));
        assertEquals(500, index.getSeekPoints(900_000).first.position);
    }
    @Test public void retainsDisplayAndAncillaryDependenciesBeforeAcquisition() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        index.packet(1, 10, 50, segment(0x14, 10, 0, 2, 207, 2, 63));
        index.packet(1, 20, 100, segment(0x12, 20, 7, 0));
        index.packet(1, 30, 200, segment(0x13, 20, 0, 9, 0));
        index.packet(1, 100, 500, page(10, 1));
        assertEquals(50, index.getSeekPoints(120).first.position);
        // All dependencies have now been retransmitted; the next index can move on.
        index.packet(1, 150, 700, concat(segment(0x14, 10, 0, 2, 207, 2, 63),
                segment(0x12, 20, 7, 0), segment(0x13, 20, 0, 9, 0), page(10, 1)));
        assertEquals(700, index.getSeekPoints(160).first.position);
    }
    @Test public void usesTheEarliestRequiredTrackAndDoesNotIndexOtherServices() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        index.register(2, 30, 40);
        index.packet(1, 100, 500, page(10, 1));
        assertEquals(0, index.getSeekPoints(200).first.position);
        index.packet(2, 90, 400, page(30, 1));
        index.packet(2, 150, 900, page(99, 1));
        assertEquals(400, index.getSeekPoints(200).first.position);
    }
    @Test public void rejectsTruncatedSegmentsAndDoesNotTreatNormalClearAsAcquisition() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        index.packet(1, 100, 500, page(10, 1));
        byte[] broken = page(10, 1);
        broken[5] = 100;
        index.packet(1, 200, 900, broken);
        index.packet(1, 300, 1200, page(10, 0));
        assertEquals(500, index.getSeekPoints(400).first.position);
    }
    @Test public void boundedIndexStillPrerollsBothOldAndNewTargets() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        for (int i = 1; i < 3 * DvbSeekMap.MAX_ENTRIES; i++) index.packet(1, i, i * 5L, page(10, 1));
        assertEquals(5, index.getSeekPoints(1).first.position);
        for (int time : new int[]{100, 3000, 6000, 12000}) {
            SeekPoint point = index.getSeekPoints(time).first;
            assertTrue(point.position > 0);
            assertTrue(point.position <= time * 5L);
        }
    }
    @Test public void seekResetsOnlyTransientDependenciesAndFormatChangesInvalidateIndex() {
        DvbSeekMap index = index();
        index.register(1, 10, 20);
        index.packet(1, 100, 500, page(10, 1));
        index.reset();
        assertEquals(500, index.getSeekPoints(200).first.position);
        index.register(1, 30, 40);
        assertEquals(0, index.getSeekPoints(200).first.position);
    }
    private static DvbSeekMap index() {
        DvbSeekMap index = new DvbSeekMap();
        index.delegate(new SeekMap() {
            @Override public boolean isSeekable() { return true; }
            @Override public long getDurationUs() { return 1_000_000; }
            @Override public SeekPoints getSeekPoints(long timeUs) { return new SeekPoints(new SeekPoint(timeUs, timeUs * 10)); }
        });
        return index;
    }
    private static byte[] page(int id, int state) { return segment(0x10, id, 15, state << 2); }
    private static byte[] segment(int type, int page, int... payload) {
        byte[] data = new byte[6 + payload.length];
        data[0] = 0x0f;
        data[1] = (byte) type;
        data[2] = (byte) (page >> 8);
        data[3] = (byte) page;
        data[4] = (byte) (payload.length >> 8);
        data[5] = (byte) payload.length;
        for (int i = 0; i < payload.length; i++) data[6 + i] = (byte) payload[i];
        return data;
    }
    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] part : parts) data.write(part, 0, part.length);
        return data.toByteArray();
    }
}
