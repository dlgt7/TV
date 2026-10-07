package com.fongmi.android.tv.model;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Global six-slot search budget, including cancelled spiders which ignore interrupts. */
final class BoundedSearchBatch<S, R> {
    static final int PARALLELISM = 6;
    private static final Object GATE = new Object();
    private static final ArrayDeque<BoundedSearchBatch<?, ?>> READY = new ArrayDeque<>();
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(PARALLELISM, PARALLELISM, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(PARALLELISM), runnable -> {
                Thread thread = new Thread(runnable, "source-search");
                thread.setDaemon(true);
                return thread;
            });
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "source-search-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private static int workers;
    static { WORKERS.allowCoreThreadTimeOut(true); TIMER.setRemoveOnCancelPolicy(true); }

    interface Observer<S, R> {
        void success(S source, R result, long elapsedMs);
        void failure(S source, Throwable error, long elapsedMs);
    }

    private final List<Work> active = new ArrayList<>();
    private Iterator<S> pending;
    private Function<S, Callable<R>> factory;
    private Observer<S, R> observer;
    private long timeoutMs;
    private long epoch;

    void start(List<S> sources, Function<S, Callable<R>> factory, Observer<S, R> observer, long timeoutMs) {
        synchronized (GATE) {
            stopLocked();
            this.factory = factory;
            this.observer = observer;
            this.timeoutMs = timeoutMs;
            pending = new ArrayList<>(sources).iterator();
            if (pending.hasNext()) READY.add(this);
            while (!READY.isEmpty() && workers < PARALLELISM) {
                workers++;
                WORKERS.execute(BoundedSearchBatch::drain);
            }
        }
    }

    void stop() { synchronized (GATE) { stopLocked(); } }

    private void stopLocked() {
        epoch++;
        pending = null;
        READY.remove(this);
        for (Work work : active) work.cancel();
        // Actual active slots are released only when their callables return.
    }

    private static void drain() {
        while (true) {
            Runnable work;
            synchronized (GATE) {
                if (READY.isEmpty()) { workers--; return; }
                BoundedSearchBatch<?, ?> batch = READY.removeFirst();
                work = batch.take();
                if (batch.pending != null && batch.pending.hasNext()) READY.addLast(batch);
                else batch.pending = null;
            }
            work.run();
            Thread.interrupted();
        }
    }

    private Runnable take() {
        Work work = new Work(pending.next(), epoch, factory, observer, timeoutMs);
        active.add(work);
        return work;
    }

    private final class Work implements Runnable {
        final S source;
        final long generation;
        final Function<S, Callable<R>> factory;
        final Observer<S, R> observer;
        final long timeoutMs;
        Thread thread;
        ScheduledFuture<?> timeout;
        boolean cancelled;
        Work(S source, long generation, Function<S, Callable<R>> factory, Observer<S, R> observer, long timeoutMs) {
            this.source = source;
            this.generation = generation;
            this.factory = factory;
            this.observer = observer;
            this.timeoutMs = timeoutMs;
        }

        void cancel() {
            cancelled = true;
            if (timeout != null) timeout.cancel(false);
            if (thread != null) thread.interrupt();
        }

        @Override public void run() {
            final long started = System.nanoTime();
            synchronized (GATE) {
                if (cancelled || generation != epoch) { active.remove(this); return; }
                thread = Thread.currentThread();
                timeout = TIMER.schedule(() -> {
                    synchronized (GATE) { if (active.contains(this)) cancel(); }
                }, Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
            }
            R result = null;
            Throwable failure = null;
            try { result = factory.apply(source).call(); }
            catch (Throwable error) { failure = error; }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            boolean deliver;
            synchronized (GATE) {
                timeout.cancel(false);
                // Discard late results even if the timer thread was briefly delayed.
                deliver = !cancelled && generation == epoch && elapsed < timeoutMs;
                active.remove(this);
                thread = null;
            }
            if (!deliver) return;
            try {
                if (failure == null) observer.success(source, result, elapsed);
                else observer.failure(source, failure, elapsed);
            } catch (Throwable ignored) { /* One callback cannot consume a global worker slot. */ }
        }
    }
}
