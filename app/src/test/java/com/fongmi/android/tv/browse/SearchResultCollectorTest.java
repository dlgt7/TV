package com.fongmi.android.tv.browse;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultCollectorTest {

    @Test(timeout = 2000)
    public void shouldCollectReadyResultsWithoutWaitingForEarlierSites() {
        SettableFuture<List<String>> stalled = SettableFuture.create();
        SettableFuture<List<String>> alsoStalled = SettableFuture.create();
        SettableFuture<List<String>> ready = completed(Collections.nCopies(50, "ready"));

        List<String> results = SearchResultCollector.collect(List.of(stalled, alsoStalled, ready), 50, deadline(10_000));

        assertEquals(50, results.size());
        assertEquals("ready", results.get(0));
        assertTrue(stalled.isCancelled());
        assertTrue(alsoStalled.isCancelled());
    }

    @Test(timeout = 2000)
    public void shouldShareOneDeadlineAcrossAllPendingSites() {
        List<SettableFuture<List<String>>> pending = new ArrayList<>();
        for (int i = 0; i < 20; i++) pending.add(SettableFuture.create());

        List<String> results = SearchResultCollector.collect(pending, 50, deadline(200));

        assertTrue(results.isEmpty());
        for (SettableFuture<?> future : pending) assertTrue(future.isCancelled());
    }

    @Test(timeout = 2000)
    public void shouldKeepPartialResultsAtDeadlineAndCancelPendingSites() {
        SettableFuture<List<String>> pending = SettableFuture.create();

        List<String> results = SearchResultCollector.collect(List.of(pending, completed(List.of("partial"))), 50, deadline(50));

        assertEquals(List.of("partial"), results);
        assertTrue(pending.isCancelled());
    }

    @Test
    public void shouldCollectAlreadyCompletedSitesWhenDeadlineHasElapsed() {
        SettableFuture<List<String>> pending = SettableFuture.create();

        List<String> results = SearchResultCollector.collect(List.of(pending, completed(List.of("ready"))), 50, System.nanoTime() - 1);

        assertEquals(List.of("ready"), results);
        assertTrue(pending.isCancelled());
    }

    @Test
    public void shouldSkipFailedAndCancelledSites() {
        SettableFuture<List<String>> failed = SettableFuture.create();
        failed.setException(new IllegalStateException("site unavailable"));
        SettableFuture<List<String>> cancelled = SettableFuture.create();
        cancelled.cancel(false);

        List<String> results = SearchResultCollector.collect(List.of(failed, cancelled, completed(List.of("success"))), 50, deadline(1000));

        assertEquals(List.of("success"), results);
    }

    @Test
    public void shouldRetainEntireFinalBatchForRelevanceSorting() {
        List<String> results = SearchResultCollector.collect(List.of(completed(List.of("first")), completed(List.of("other", "exact"))), 2, deadline(1000));

        assertEquals(List.of("first", "other", "exact"), results);
    }

    @Test(timeout = 3000)
    public void shouldInterruptActualSiteJobsAfterReachingLimit() throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            ListenableFuture<List<String>> pending = executor.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                    return List.of();
                } catch (InterruptedException error) {
                    interrupted.countDown();
                    throw error;
                }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));

            assertEquals(List.of("ready"), SearchResultCollector.collect(List.of(pending, completed(List.of("ready"))), 1, deadline(10_000)));

            assertTrue(pending.isCancelled());
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(timeout = 3000)
    public void shouldCancelSitesAndPreserveCollectorInterruption() throws Exception {
        SettableFuture<List<String>> pending = SettableFuture.create();
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<List<String>> results = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread collector = new Thread(() -> {
            started.countDown();
            results.set(SearchResultCollector.collect(List.of(pending), 50, deadline(10_000)));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            collector.start();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            collector.interrupt();
            collector.join(1000);

            assertFalse(collector.isAlive());
            assertTrue(interrupted.get());
            assertTrue(pending.isCancelled());
            assertEquals(List.of(), results.get());
        } finally {
            collector.interrupt();
            collector.join(1000);
        }
    }

    private static SettableFuture<List<String>> completed(List<String> result) {
        SettableFuture<List<String>> future = SettableFuture.create();
        future.set(result);
        return future;
    }

    private static long deadline(long timeoutMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }
}
