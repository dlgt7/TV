package com.fongmi.android.tv.player.mpv;

import org.junit.Test;
import static org.junit.Assert.*;

public class MpvVideoOutputRecoveryTest {
    @Test public void recognizesOnlyActualVideoOutputInitializationFailures() {
        assertTrue(MpvVideoOutputRecovery.isContextFailure("vo/gpu-next", "Failed initializing any suitable GPU context!"));
        assertTrue(MpvVideoOutputRecovery.isContextFailure("vo/gpu", "Failed initializing any suitable GPU context!"));
        assertTrue(MpvVideoOutputRecovery.isContextFailure("cplayer", "Error opening/initializing VO window/selected video_out device."));
        assertTrue(MpvVideoOutputRecovery.isContextFailure("cplayer", "Error opening/initializing the VO window/selected video_out device."));
        assertFalse(MpvVideoOutputRecovery.isContextFailure("cplayer", "No video dimensions (possibly audio only)"));
        assertFalse(MpvVideoOutputRecovery.isContextFailure("ffmpeg", "HTTP error 401"));
        assertFalse(MpvVideoOutputRecovery.isContextFailure("vd", "Failed to initialize decoder"));
        assertFalse(MpvVideoOutputRecovery.isContextFailure("audio", "Failed initializing any suitable GPU context!"));
    }
    @Test public void vulkanGpuNextUsesAtMostTwoOpenGlAttempts() {
        MpvVideoOutputRecovery recovery = new MpvVideoOutputRecovery();
        assertEquals("gpu-next", recovery.next(true, "gpu-next"));
        assertEquals("gpu", recovery.next(true, "gpu-next"));
        assertNull(recovery.next(true, "gpu"));
        assertNull(recovery.next(true, "gpu"));
    }
    @Test public void alreadyOpenGlGpuNextFallsBackToLegacyGpuOnlyOnce() {
        MpvVideoOutputRecovery recovery = new MpvVideoOutputRecovery();
        assertEquals("gpu", recovery.next(false, "gpu-next"));
        assertNull(recovery.next(false, "gpu"));
    }
    @Test public void failedDefaultOpenGlGpuHasNoIdenticalRetry() {
        assertNull(new MpvVideoOutputRecovery().next(false, "gpu"));
    }
    @Test public void backendOverrideAndBudgetSurviveEpisodeAndDecoderChanges() {
        MpvVideoOutputRecovery recovery = new MpvVideoOutputRecovery();
        assertEquals("gpu", recovery.next(true, "gpu"));
        recovery.beginPlayback();
        assertEquals("gpu", recovery.driver("gpu-next"));
        assertFalse(recovery.hasFailure());
        assertNull(recovery.next(true, "gpu-next"));
    }
    @Test public void terminalFailureRejectsLateCallbacksEvenWithTheCurrentGeneration() {
        MpvVideoOutputRecovery recovery = new MpvVideoOutputRecovery();
        recovery.next(true, "gpu"); assertNull(recovery.next(false, "gpu"));
        assertTrue(recovery.isTerminal());
        assertFalse(recovery.recordFailure(recovery.generation()));
        recovery.clearFailure(); assertTrue(recovery.isTerminal());
        recovery.beginPlayback(); assertFalse(recovery.isTerminal());
        assertEquals("gpu", recovery.driver("gpu-next"));
        assertNull(recovery.next(true, "gpu-next"));
        assertTrue(recovery.isTerminal());
    }
    @Test public void queuedErrorsFromPreviousAttemptCannotSpendAnotherRetry() {
        MpvVideoOutputRecovery recovery = new MpvVideoOutputRecovery();
        long old = recovery.generation(); assertTrue(recovery.recordFailure(old));
        recovery.next(true, "gpu-next"); assertFalse(recovery.hasFailure());
        assertFalse(recovery.recordFailure(old)); assertFalse(recovery.hasFailure());
        assertTrue(recovery.recordFailure(recovery.generation())); assertTrue(recovery.hasFailure());
        recovery.beginPlayback(); assertFalse(recovery.hasFailure());
    }
}
