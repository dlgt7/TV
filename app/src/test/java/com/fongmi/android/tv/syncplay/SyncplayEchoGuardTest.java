package com.fongmi.android.tv.syncplay;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SyncplayEchoGuardTest {
    private final SyncplayEchoGuard guard = new SyncplayEchoGuard();

    @Test public void remoteSeekAndItsLaterAdjustmentDoNotEcho() {
        guard.beginSeek(100);
        assertTrue(guard.consumeSeek(false, 100));
        guard.endSeek();
        assertTrue(guard.consumeSeek(true, 350));
        assertFalse(guard.consumeSeek(true, 400));
    }

    @Test public void immediateUserSeekIsNeverSwallowedEvenAtSameTarget() {
        guard.beginSeek(100);
        assertTrue(guard.consumeSeek(false, 100));
        guard.endSeek();
        // No target tolerance is involved: the next explicit SEEK belongs to the user.
        assertFalse(guard.consumeSeek(false, 101));
        assertFalse(guard.consumeSeek(true, 102));
    }

    @Test public void oldAdjustmentExpiresWithoutHoldingBackUserEvents() {
        guard.beginSeek(100); guard.endSeek();
        assertFalse(guard.consumeSeek(true, 2601));
        assertFalse(guard.consumeSeek(false, 2602));
    }

    @Test public void rebindingKernelOrMediaClearsEveryPreviousExpectation() {
        guard.beginSeek(100); guard.endSeek();
        guard.expectPause(true, 100); guard.expectSpeed(.97f, 100);
        guard.reset();
        assertFalse(guard.consumeSeek(true, 101));
        assertFalse(guard.consumePause(true, 101));
        assertFalse(guard.consumeSpeed(.97f, 101));
    }

    @Test public void oppositeUserPauseOrSpeedSupersedesPendingRemoteCallback() {
        guard.expectPause(true, 100);
        assertFalse(guard.consumePause(false, 101));
        assertFalse(guard.consumePause(true, 102));
        guard.expectSpeed(.97f, 100);
        assertFalse(guard.consumeSpeed(1.5f, 101));
        assertFalse(guard.consumeSpeed(.97f, 102));
    }

    @Test public void pauseAndSpeedEchoesAreSingleUseAndBounded() {
        guard.expectPause(true, 100); guard.expectSpeed(1.03f, 100);
        assertTrue(guard.consumePause(true, 101)); assertTrue(guard.consumeSpeed(1.03f, 101));
        assertFalse(guard.consumePause(true, 102)); assertFalse(guard.consumeSpeed(1.03f, 102));
        guard.expectPause(true, 100); guard.expectSpeed(1.03f, 100);
        assertFalse(guard.consumePause(true, 2601)); assertFalse(guard.consumeSpeed(1.03f, 2601));
    }
}
