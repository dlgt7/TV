package com.fongmi.android.tv.syncplay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class SyncplaySynchronizerTest {
    private final SyncplaySynchronizer sync = new SyncplaySynchronizer();
    private SyncplayProtocol.RemoteState remote(double position, boolean paused, boolean seek, String who) {
        return new SyncplayProtocol.RemoteState(position, paused, seek, who, .1);
    }

    @Test public void onlyReadyFirstOccupantSeedsRoomWithItsCurrentPlayback() {
        var self = new SyncplayProtocol.Member("TV", "room", "Episode 1", 100);
        var peer = new SyncplayProtocol.Member("Other", "room", "Episode 1", 100);
        assertTrue(SyncplaySynchronizer.shouldSeedRoom(SyncplaySynchronizer.Gate.READY, false, "TV", List.of(self)));
        assertFalse(SyncplaySynchronizer.shouldSeedRoom(SyncplaySynchronizer.Gate.READY, true, "TV", List.of(self)));
        assertFalse(SyncplaySynchronizer.shouldSeedRoom(SyncplaySynchronizer.Gate.READY, false, "TV", List.of(self, peer)));
        for (SyncplaySynchronizer.Gate gate : SyncplaySynchronizer.Gate.values()) if (gate != SyncplaySynchronizer.Gate.READY)
            assertFalse(SyncplaySynchronizer.shouldSeedRoom(gate, false, "TV", List.of(self)));
    }

    @Test public void waitsForKnownMatchingMediaAndReadinessBeforeAnyAlignment() {
        var same = List.of(new SyncplayProtocol.Member("Other", "room", "节目 第01集.mkv", 100));
        assertEquals(SyncplaySynchronizer.Gate.WAITING_FOR_ROOM, SyncplaySynchronizer.gate(false, true, "TV", "节目 第01集", 100, same, false));
        assertEquals(SyncplaySynchronizer.Gate.PLAYER_NOT_READY, SyncplaySynchronizer.gate(true, false, "TV", "节目 第01集", 100, same, false));
        assertEquals(SyncplaySynchronizer.Gate.READY, SyncplaySynchronizer.gate(true, true, "TV", "节目 第01集", 100, same, false));
        assertEquals(SyncplaySynchronizer.Gate.DIFFERENT_MEDIA, SyncplaySynchronizer.gate(true, true, "TV", "节目 第02集", 100, same, false));
        assertEquals(SyncplaySynchronizer.Gate.DIFFERENT_MEDIA, SyncplaySynchronizer.gate(true, true, "TV", "节目 第01集", 120, same, false));
        assertEquals(SyncplaySynchronizer.Gate.READY, SyncplaySynchronizer.gate(true, true, "TV", "节目 第02集", 120, same, true));
        assertEquals(SyncplaySynchronizer.Gate.WAITING_FOR_ROOM, SyncplaySynchronizer.gate(true, true, "TV", "节目", 100,
                List.of(new SyncplayProtocol.Member("Other", "room", "", 0)), false));
        assertEquals(SyncplaySynchronizer.Gate.WAITING_FOR_ROOM, SyncplaySynchronizer.gate(true, true, "TV", "节目", 100,
                List.of(new SyncplayProtocol.Member("Other", "room", "节目", 0)), true));
    }

    @Test public void initiallyAlignsAndCompensatesOnlyPlayingNetworkTime() {
        var playing = sync.correction(0, true, 100000, 1, remote(10, false, false, "Other"), "TV", .2);
        assertEquals(Long.valueOf(10300), playing.seekMs); assertEquals(Boolean.FALSE, playing.paused);
        sync.reset(); var paused = sync.correction(0, false, 100000, 1, remote(10, true, false, "Other"), "TV", .2);
        assertEquals(Long.valueOf(10000), paused.seekMs); assertEquals(Boolean.TRUE, paused.paused);
    }

    @Test public void seeksForLargeDriftAndUsesGentleSpeedForSmallDrift() {
        sync.correction(10000, false, 100000, 1, remote(10, false, false, "Other"), "TV", 0);
        var slow = sync.correction(10900, false, 100000, 1, remote(10, false, false, "Other"), "TV", 0);
        assertNull(slow.seekMs); assertEquals(.97f, slow.speed, .001f);
        var fast = sync.correction(9300, false, 100000, 1, remote(10, false, false, "Other"), "TV", 0);
        assertNull(fast.seekMs); assertEquals(1.03f, fast.speed, .001f);
        var hard = sync.correction(3000, false, 100000, 1, remote(10, false, false, "Other"), "TV", 0);
        assertEquals(Long.valueOf(10100), hard.seekMs); assertEquals(1, hard.speed, .001f);
        var stable = sync.correction(10100, false, 100000, .97f, remote(10, false, false, "Other"), "TV", 0);
        assertNull(stable.seekMs); assertEquals(1, stable.speed, .001f);
    }

    @Test public void handlesSeekPauseEchoAndClampsToKnownDuration() {
        sync.correction(10000, false, 100000, 1, remote(10, false, false, "TV"), "TV", 0);
        assertNull(sync.correction(10000, false, 100000, 1, remote(20, false, true, "TV"), "TV", 0).seekMs);
        var forced = sync.correction(10000, false, 100000, 1, remote(20, true, true, "Other"), "TV", 0);
        assertEquals(Long.valueOf(20000), forced.seekMs); assertEquals(Boolean.TRUE, forced.paused);
        assertEquals(Long.valueOf(100000), sync.correction(10, true, 100000, 1, remote(200, true, true, "Other"), "TV", 0).seekMs);
    }
}
