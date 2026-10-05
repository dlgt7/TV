package com.fongmi.android.tv.player.subtitle;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssHistoryTest {
    @Test public void preservesOverlappingEventsWithIdenticalStartTimes() {
        AssHistory history = new AssHistory();
        history.add("1", 0, "long dialogue");
        history.add("1", 0, "short animation");
        assertEquals(2, history.since("1", 0).size());
        assertEquals("long dialogue", history.since("1", 0).get(0).text());
    }
    @Test public void repeatedExtractionAfterSeekDoesNotDuplicateEvents() {
        AssHistory history = new AssHistory();
        history.add("1", 0, "dialogue");
        history.add("1", 0, "dialogue");
        assertEquals(1, history.since("1", 0).size());
        assertTrue(history.since("1", 1).isEmpty());
    }
    @Test public void keepsTrackHistoriesIndependent() {
        AssHistory history = new AssHistory();
        history.add("1", 0, "primary");
        history.add("2", 0, "secondary");
        assertEquals("secondary", history.since("2", 0).get(0).text());
        assertFalse(history.contains("3"));
    }
    @Test public void evictsOldPacketsWithoutLosingTheReaderCursor() {
        AssHistory history = new AssHistory(1024, 2);
        history.add("1", 0, "old");
        history.add("2", 0, "other track");
        long cursor = history.since("1", 0).get(0).sequence() + 1;
        history.add("1", 1, "new");
        assertEquals("new", history.since("1", cursor).get(0).text());
        assertEquals(1, history.since("1", 0).size());
        history.add("1", 0, "old");
        assertEquals(2, history.since("1", cursor).size());
        assertTrue(history.since("2", 0).isEmpty());
    }
    @Test public void boundsRetainedTextButAcceptsLaterEvents() {
        AssHistory history = new AssHistory(8, 10);
        history.add("1", 0, "12345");
        history.add("1", 1, "1234");
        history.add("1", 2, "next");
        assertEquals(1, history.since("1", 0).size());
        assertEquals("next", history.since("1", 0).get(0).text());
    }
}
