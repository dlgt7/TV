package com.fongmi.android.tv.browse;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Collects completed sites within one deadline, retaining the final batch for relevance sorting. */
final class SearchResultCollector {

    static <T> List<T> collect(List<? extends ListenableFuture<List<T>>> futures, int limit, long deadlineNanos) {
        List<T> items = new ArrayList<>();
        BlockingQueue<ListenableFuture<List<T>>> completed = new LinkedBlockingQueue<>();
        try {
            for (ListenableFuture<List<T>> future : futures) {
                future.addListener(() -> completed.add(future), MoreExecutors.directExecutor());
            }
            for (int remaining = futures.size(); remaining > 0 && items.size() < limit; remaining--) {
                if (Thread.currentThread().isInterrupted()) break;
                // Drain ready sites even at the deadline; only pending sites consume the wait budget.
                ListenableFuture<List<T>> future = completed.poll();
                if (future == null) {
                    long waitNanos = deadlineNanos - System.nanoTime();
                    if (waitNanos <= 0) break;
                    future = completed.poll(waitNanos, TimeUnit.NANOSECONDS);
                    if (future == null) break;
                }
                try {
                    List<T> result = future.get();
                    if (result != null) items.addAll(result);
                } catch (ExecutionException | CancellationException ignored) {
                    // A failed site must not hide successful results from another site.
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            for (ListenableFuture<?> future : futures) if (!future.isDone()) future.cancel(true);
        }
        return items;
    }
}
