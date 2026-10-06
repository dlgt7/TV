package com.fongmi.android.tv.player.mpv;

import org.junit.Test;

import static org.junit.Assert.*;

public class MpvSeekPrerollTest {
    @Test
    public void recognizesDeclaredProxiedAndDemuxerDetectedHls() {
        assertTrue(MpvSeekPreroll.isHls("application/x-mpegURL", "/proxy", null));
        assertTrue(MpvSeekPreroll.isHls("application/vnd.apple.mpegurl", "/opaque", null));
        assertTrue(MpvSeekPreroll.isHls(null, "/master.M3U8", null));
        assertTrue(MpvSeekPreroll.isHls(null, "/opaque", "hls,applehttp"));
        assertFalse(MpvSeekPreroll.isHls("video/mp4", "/movie.mp4", "mov,mp4,m4a,3gp,3g2,mj2"));
        assertFalse(MpvSeekPreroll.isHls(null, "/proxy", null));
    }

    @Test
    public void restoresConfiguredValueWhenLeavingHls() {
        MpvSeekPreroll policy = new MpvSeekPreroll();
        assertEquals(5, policy.apply(0.75), 0);
        assertEquals(5, policy.apply(5), 0);
        assertEquals(0.75, policy.restore(5), 0);
        assertEquals(0.75, policy.restore(0.75), 0);
    }

    @Test
    public void preservesLargerUserPreroll() {
        MpvSeekPreroll policy = new MpvSeekPreroll();
        assertEquals(8, policy.apply(8), 0);
        assertEquals(8, policy.restore(8), 0);
    }

    @Test
    public void doesNotOverwriteRuntimeUserChangeWhenRestoring() {
        MpvSeekPreroll policy = new MpvSeekPreroll();
        policy.apply(0);
        assertEquals(2, policy.restore(2), 0);
        assertEquals(2, policy.restore(2), 0);
    }

    @Test
    public void keepsUpdatedBaselineIfPrerollIsAppliedAgain() {
        MpvSeekPreroll policy = new MpvSeekPreroll();
        policy.apply(0);
        assertEquals(5, policy.apply(2), 0);
        assertEquals(2, policy.restore(5), 0);
    }

    @Test
    public void invalidNativeValueCannotPoisonNextMedia() {
        MpvSeekPreroll policy = new MpvSeekPreroll();
        assertTrue(Double.isNaN(policy.apply(Double.NaN)));
        assertEquals(1, policy.restore(1), 0);
        assertEquals(5, policy.apply(1), 0);
        assertEquals(1, policy.restore(5), 0);
    }
}
