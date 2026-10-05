package com.fongmi.android.tv.player.subtitle;

import androidx.media3.common.C;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupSeekMapTest {
    @Test public void startsFromZeroBeforeAnyKnownEpoch() {
        SupSeekMap index = new SupSeekMap();
        assertTrue(index.isSeekable());
        assertEquals(C.TIME_UNSET, index.getDurationUs());
        assertEquals(0, index.getSeekPoints(10_000_000).first.position);
    }
    @Test public void seeksToPrecedingEpochToReconstructPaletteAndObjects() {
        SupSeekMap index = new SupSeekMap();
        index.add(2_000_000, 100);
        index.add(8_000_000, 500);
        assertEquals(100, index.getSeekPoints(6_000_000).first.position);
        assertEquals(500, index.getSeekPoints(8_000_000).first.position);
        assertEquals(0, index.getSeekPoints(1_000_000).first.position);
        index.finish(10_000_000);
        assertEquals(10_000_000, index.getDurationUs());
    }
    @Test public void repeatedExtractionDoesNotChangeSeekBoundary() {
        SupSeekMap index = new SupSeekMap();
        index.add(2_000_000, 100);
        index.add(2_000_000, 100);
        assertEquals(100, index.getSeekPoints(3_000_000).first.position);
    }
}
