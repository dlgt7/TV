package com.fongmi.android.tv.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** A short, bounded cooldown for failed image requests, measured with a monotonic clock. */
public final class ImageRetryPolicy {

    private final Map<String, Long> failures = new LinkedHashMap<>();
    private final int capacity;
    private final long retryDelayMs;
    private final LongSupplier clock;

    public ImageRetryPolicy() {
        this(256, 30_000, () -> System.nanoTime() / 1_000_000);
    }

    ImageRetryPolicy(int capacity, long retryDelayMs, LongSupplier clock) {
        this.capacity = capacity;
        this.retryDelayMs = retryDelayMs;
        this.clock = clock;
    }

    public synchronized boolean canLoad(String key) {
        Long failedAt = failures.get(key);
        if (failedAt == null) return true;
        if (clock.getAsLong() - failedAt < retryDelayMs) return false;
        failures.remove(key);
        return true;
    }

    public synchronized void onFailure(String key) {
        // Refresh insertion order so capacity eviction keeps the most recent failures.
        failures.remove(key);
        failures.put(key, clock.getAsLong());
        if (failures.size() > capacity) failures.remove(failures.keySet().iterator().next());
    }

    public synchronized void onSuccess(String key) {
        failures.remove(key);
    }

    public synchronized void clear() {
        failures.clear();
    }
}
