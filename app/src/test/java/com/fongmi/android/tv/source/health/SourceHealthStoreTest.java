package com.fongmi.android.tv.source.health;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class SourceHealthStoreTest {
    @Test public void emptyIsReachableAndResetsConsecutiveFailuresWithIndependentStages() {
        SourceHealthStore store = new SourceHealthStore(() -> 100L);
        store.record("config/site", true, SourceHealthStore.Outcome.FAILURE, 20);
        store.record("config/site", true, SourceHealthStore.Outcome.FAILURE, 30);
        store.record("config/site", false, SourceHealthStore.Outcome.SUCCESS, 50);
        assertEquals(2, store.get("config/site").search.consecutiveFailures);
        assertEquals(50, store.get("config/site").detail.elapsedMs);
        store.record("config/site", true, SourceHealthStore.Outcome.EMPTY, 10);
        assertEquals(0, store.get("config/site").search.consecutiveFailures);
        assertEquals(SourceHealthStore.Outcome.EMPTY, store.get("config/site").search.outcome);
        assertEquals(SourceHealthStore.Outcome.SUCCESS, store.get("config/site").detail.outcome);
    }

    @Test public void expiresEachStageIndependentlyAndDoesNotKeepClockRollbackData() {
        AtomicLong now = new AtomicLong(100);
        SourceHealthStore store = new SourceHealthStore(now::get);
        store.record("source", true, SourceHealthStore.Outcome.FAILURE, 100);
        now.addAndGet(SourceHealthStore.MAX_AGE_MS / 2);
        store.record("source", false, SourceHealthStore.Outcome.SUCCESS, 20);
        now.addAndGet(SourceHealthStore.MAX_AGE_MS / 2);
        assertNull(store.get("source").search);
        assertNotNull(store.get("source").detail);
        now.set(0);
        assertNull(store.get("source").detail);
        assertEquals(0, store.size());
    }

    @Test public void boundsHistoryTo512RecentEntriesAndClearResetsAll() {
        SourceHealthStore store = new SourceHealthStore(() -> 100L);
        for (int i = 0; i < 1000; i++) store.record("source-" + i, true, SourceHealthStore.Outcome.SUCCESS, i);
        assertEquals(512, store.size());
        assertNull(store.get("source-0").search);
        assertEquals(999, store.get("source-999").search.elapsedMs);
        store.clear();
        assertEquals(0, store.size());
    }

    @Test public void isolatesIdenticalSiteNamesByConfigurationHash() {
        SourceHealthStore store = new SourceHealthStore(() -> 100L);
        String first = SourceHealthManager.hash("https://a.test/config?token=private") + "/same-site";
        String second = SourceHealthManager.hash("https://b.test/config?token=private") + "/same-site";
        assertFalse(first.contains("private"));
        assertNotEquals(first, second);
        store.record(first, true, SourceHealthStore.Outcome.FAILURE, 20);
        assertNull(store.get(second).search);
    }

    @Test public void ignoresCancellationAndTimeoutCausesButKeepsOrdinarySourceErrors() {
        assertTrue(SourceHealthManager.ignored(new RuntimeException(new java.util.concurrent.CancellationException())));
        assertTrue(SourceHealthManager.ignored(new java.net.SocketTimeoutException()));
        assertTrue(SourceHealthManager.ignored(new java.util.concurrent.TimeoutException()));
        assertTrue(SourceHealthManager.ignored(new InterruptedException()));
        assertFalse(SourceHealthManager.ignored(new java.net.UnknownHostException()));
        assertFalse(SourceHealthManager.ignored(new IllegalArgumentException()));
        assertFalse(SourceHealthManager.ignored(null));
    }

    @Test public void doesNotBlameSourceForGlobalOfflineAtEitherEnd() {
        java.io.IOException failure = new java.io.IOException();
        assertFalse(SourceHealthManager.shouldRecord(SourceHealthStore.Outcome.FAILURE, failure, false, true));
        assertFalse(SourceHealthManager.shouldRecord(SourceHealthStore.Outcome.FAILURE, failure, true, false));
        assertTrue(SourceHealthManager.shouldRecord(SourceHealthStore.Outcome.FAILURE, failure, true, true));
        assertTrue(SourceHealthManager.shouldRecord(SourceHealthStore.Outcome.SUCCESS, null, false, false));
        assertTrue(SourceHealthManager.shouldRecord(SourceHealthStore.Outcome.EMPTY, null, false, false));
        assertFalse(SourceHealthManager.shouldRecord(SourceHealthStore.Outcome.FAILURE, new java.util.concurrent.TimeoutException(), true, true));
    }
}
