package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeekConfirmKeySequenceTest {
    private static final int CENTER = 23;
    private static final int ENTER = 66;

    @Test
    public void seekConfirmationConsumesRepeatsAndReleaseExactlyOnce() {
        SeekConfirmKeySequence keys = new SeekConfirmKeySequence();
        keys.claim(CENTER, 100);
        assertTrue(keys.consume(CENTER, 100, false));
        assertTrue(keys.consume(CENTER, 100, false));
        assertTrue(keys.consume(CENTER, 100, true));
        assertFalse(keys.consume(CENTER, 100, true));
    }

    @Test
    public void ordinaryPlayPausePressIsNotConsumed() {
        SeekConfirmKeySequence keys = new SeekConfirmKeySequence();
        assertFalse(keys.consume(CENTER, 100, false));
        assertFalse(keys.consume(CENTER, 100, true));
        keys.claim(CENTER, 200);
        assertTrue(keys.consume(CENTER, 200, true));
        assertFalse(keys.consume(CENTER, 300, false));
        assertFalse(keys.consume(CENTER, 300, true));
    }

    @Test
    public void lostReleaseDoesNotSwallowNextPress() {
        SeekConfirmKeySequence keys = new SeekConfirmKeySequence();
        keys.claim(ENTER, 100);
        assertFalse(keys.consume(ENTER, 200, false));
        assertFalse(keys.consume(ENTER, 200, true));
    }

    @Test
    public void unrelatedKeyDoesNotReleaseSeekConfirmation() {
        SeekConfirmKeySequence keys = new SeekConfirmKeySequence();
        keys.claim(ENTER, 100);
        assertFalse(keys.consume(CENTER, 100, false));
        assertFalse(keys.consume(CENTER, 100, true));
        assertTrue(keys.consume(ENTER, 100, true));
    }
}
