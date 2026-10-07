package com.fongmi.android.tv.player.mpv;

import android.os.SystemClock;

import androidx.media3.common.Player;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.cache.CacheLease;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.github.catvod.utils.Path;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Native ownership must outlive Java release, without relying on PlayerManager's outer lease. */
@RunWith(AndroidJUnit4.class)
public class MpvCacheLeaseTest {
    private MpvPlayerEngine engine;

    @Test public void failedSecondCreateAndRepeatedReleaseRetainTheCorrectNativeLease() {
        try {
            main(() -> {
                assertFalse("Test requires no existing player cache owner", CacheLease.isInUse(Path.mpvCache()));
                engine = new MpvPlayerEngine(PlayerEngine.HARD, new Player.Listener() {});
                assertTrue(CacheLease.isInUse(Path.mpvCache()));
                try {
                    new MpvPlayerEngine(PlayerEngine.HARD, new Player.Listener() {});
                    fail("A second live native instance must be rejected");
                } catch (IllegalStateException expected) {
                    assertTrue("Rejected creation must not close the first native owner's lease", CacheLease.isInUse(Path.mpvCache()));
                }
                engine.release();
                engine.release();
            });
            long deadline = SystemClock.elapsedRealtime() + 15_000;
            while (CacheLease.isInUse(Path.mpvCache()) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            assertFalse("Successful native destruction must release shader/ICC cache ownership", CacheLease.isInUse(Path.mpvCache()));
        } finally {
            main(() -> { if (engine != null) engine.release(); });
        }
    }

    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
}
