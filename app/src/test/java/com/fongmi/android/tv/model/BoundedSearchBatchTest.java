package com.fongmi.android.tv.model;

import org.junit.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.Assert.*;

public class BoundedSearchBatchTest {
    private static List<Integer> sites(int count) { return IntStream.range(0, count).boxed().toList(); }

    @Test(timeout = 10000) public void boundsOneHundredTwentySourcesToSixActualCalls() throws Exception {
        BoundedSearchBatch<Integer, Integer> batch = new BoundedSearchBatch<>();
        AtomicInteger running = new AtomicInteger(), maximum = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(6), release = new CountDownLatch(1), results = new CountDownLatch(120);
        try {
            batch.start(sites(120), source -> () -> {
                int count = running.incrementAndGet();
                maximum.accumulateAndGet(count, Math::max);
                entered.countDown();
                try { release.await(); return source; }
                finally { running.decrementAndGet(); }
            }, observer(results, new AtomicInteger()), 5000);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(6, running.get());
            release.countDown();
            assertTrue(results.await(5, TimeUnit.SECONDS));
            assertEquals(6, maximum.get());
        } finally { release.countDown(); batch.stop(); }
    }

    @Test(timeout = 10000) public void repeatedCancelDoesNotReplaceUninterruptibleWorkersOrDeliverStaleResults() throws Exception {
        BoundedSearchBatch<Integer, Integer> batch = new BoundedSearchBatch<>();
        AtomicInteger oldStarted = new AtomicInteger(), oldCallbacks = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(6), release = new CountDownLatch(1), latest = new CountDownLatch(120);
        try {
            batch.start(sites(120), source -> () -> {
                oldStarted.incrementAndGet();
                entered.countDown();
                while (release.getCount() != 0) {
                    try { release.await(); } catch (InterruptedException ignored) {}
                }
                return source;
            }, counting(oldCallbacks), 5000);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 60; i++) {
                batch.start(sites(120), source -> () -> source, counting(oldCallbacks), 5000);
                batch.stop();
            }
            batch.start(sites(120), source -> () -> source, observer(latest, new AtomicInteger()), 5000);
            assertEquals(6, oldStarted.get());
            assertTrue(Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("source-search")).count() <= 6);
            release.countDown();
            assertTrue(latest.await(5, TimeUnit.SECONDS));
            assertEquals(0, oldCallbacks.get());
        } finally { release.countDown(); batch.stop(); }
    }

    @Test(timeout = 10000) public void timeoutStartsWhenCallActuallyRunsRatherThanWhileWaitingInBatch() throws Exception {
        BoundedSearchBatch<Integer, Integer> batch = new BoundedSearchBatch<>();
        CountDownLatch results = new CountDownLatch(12);
        AtomicInteger failures = new AtomicInteger();
        try {
            batch.start(sites(12), source -> () -> { Thread.sleep(300); return source; }, observer(results, failures), 500);
            assertTrue(results.await(4, TimeUnit.SECONDS));
            assertEquals(0, failures.get());
        } finally { batch.stop(); }
    }

    @Test(timeout = 10000) public void ignoresTimeoutAndLateReturnWithoutReportingSourceFailure() throws Exception {
        BoundedSearchBatch<Integer, Integer> batch = new BoundedSearchBatch<>();
        CountDownLatch finished = new CountDownLatch(6);
        AtomicInteger callbacks = new AtomicInteger();
        try {
            batch.start(sites(6), source -> () -> {
                long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
                while (System.nanoTime() < end) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
                finished.countDown();
                return source;
            }, counting(callbacks), 20);
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            Thread.sleep(30);
            assertEquals(0, callbacks.get());
        } finally { batch.stop(); }
    }

    @Test(timeout = 10000) public void sharesBudgetAcrossTwoViewModelsAndContinuesAfterOrdinaryFailure() throws Exception {
        BoundedSearchBatch<Integer, Integer> first = new BoundedSearchBatch<>(), second = new BoundedSearchBatch<>();
        AtomicInteger running = new AtomicInteger(), maximum = new AtomicInteger(), failures = new AtomicInteger();
        CountDownLatch all = new CountDownLatch(120);
        java.util.function.Function<Integer, java.util.concurrent.Callable<Integer>> factory = source -> () -> {
            maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
            try { if (source % 2 == 0) throw new java.io.IOException("source failure"); return source; }
            finally { running.decrementAndGet(); }
        };
        try {
            first.start(sites(60), factory, observer(all, failures), 5000);
            second.start(sites(60), factory, observer(all, failures), 5000);
            assertTrue(all.await(4, TimeUnit.SECONDS));
            assertEquals(60, failures.get());
            assertTrue(maximum.get() <= 6);
        } finally { first.stop(); second.stop(); }
    }

    private static BoundedSearchBatch.Observer<Integer, Integer> observer(CountDownLatch results, AtomicInteger failures) {
        return new BoundedSearchBatch.Observer<>() {
            public void success(Integer source, Integer result, long elapsed) { results.countDown(); }
            public void failure(Integer source, Throwable error, long elapsed) { failures.incrementAndGet(); results.countDown(); }
        };
    }
    private static BoundedSearchBatch.Observer<Integer, Integer> counting(AtomicInteger count) {
        return new BoundedSearchBatch.Observer<>() {
            public void success(Integer source, Integer result, long elapsed) { count.incrementAndGet(); }
            public void failure(Integer source, Throwable error, long elapsed) { count.incrementAndGet(); }
        };
    }
}
