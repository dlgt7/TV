package com.fongmi.android.tv.utils;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HistoryProgressTextTest {
    @Test public void showsExactTimeEvenWhenReplacementLinkHasNoDuration() {
        assertEquals("32:18", HistoryProgressText.elapsed(1_938_900, 0));
        assertEquals("1:00:00", HistoryProgressText.elapsed(3_600_000, -1));
        assertEquals("125:03:07", HistoryProgressText.elapsed(450_187_000, 0));
    }

    @Test public void clampsMissingAndStalePositions() {
        assertEquals("00:00", HistoryProgressText.elapsed(-1, 50_000));
        assertEquals("00:05", HistoryProgressText.elapsed(55_000, 5_000));
        assertEquals("00:59", HistoryProgressText.elapsed(59_999, 60_000));
    }
}
