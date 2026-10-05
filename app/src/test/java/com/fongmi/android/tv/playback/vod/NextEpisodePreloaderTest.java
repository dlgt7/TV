package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import org.junit.Test;
import static org.junit.Assert.*;

public class NextEpisodePreloaderTest {
    @Test public void preparesNearRealEndIncludingCreditSkip() {
        assertTrue(NextEpisodePreloader.shouldPrepare(60_000, 100_000, 0));
        assertTrue(NextEpisodePreloader.shouldPrepare(20_000, 100_000, 40_000));
        assertFalse(NextEpisodePreloader.shouldPrepare(1_000, 100_000, 0));
        assertFalse(NextEpisodePreloader.shouldPrepare(100_000, 100_000, 0));
        assertFalse(NextEpisodePreloader.shouldPrepare(1_000, -1, 0));
    }
    @Test public void keysRejectOtherSourceFlagAndEpisodeButAllowTitleChanges() {
        VodPlayRequest request = VodPlayRequest.create("site", new Flag("line"), Episode.create("one", "id"));
        assertTrue(request.matches(VodPlayRequest.create("site", new Flag("line"), Episode.create("renamed", "id"))));
        assertFalse(request.matches(VodPlayRequest.create("other", new Flag("line"), Episode.create("one", "id"))));
        assertFalse(request.matches(VodPlayRequest.create("site", new Flag("other"), Episode.create("one", "id"))));
        assertFalse(request.matches(VodPlayRequest.create("site", new Flag("line"), Episode.create("one", "other"))));
        assertFalse(request.matches((VodPlayRequest) null));
    }
}
