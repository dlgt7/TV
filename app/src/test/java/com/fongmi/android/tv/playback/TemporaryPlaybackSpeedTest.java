package com.fongmi.android.tv.playback;

import org.junit.Test;
import static org.junit.Assert.*;

public class TemporaryPlaybackSpeedTest {
    @Test public void repeatedLongPressDoesNotRememberTemporarySpeedAsOriginal() {
        TemporaryPlaybackSpeed hold = new TemporaryPlaybackSpeed();
        assertTrue(hold.begin(1.25f));
        assertFalse(hold.begin(3f));
        assertEquals(1.25f, hold.end(), 0f);
        assertNull(hold.end());
    }

    @Test public void cancellationAllowsNextHoldToCaptureNewUserSpeed() {
        TemporaryPlaybackSpeed hold = new TemporaryPlaybackSpeed();
        assertNull(hold.end());
        hold.begin(1.5f);
        hold.end();
        assertTrue(hold.begin(2f));
        assertEquals(2f, hold.end(), 0f);
    }
}
