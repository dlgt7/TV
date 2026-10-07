package com.fongmi.android.tv.cache;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CacheResult {

    public enum Reason { IN_USE, UNMANAGED, UNSAFE_PATH, TOO_RECENT, OWNER_FAILED, DELETE_FAILED, CANCELLED }

    public long releasedBytes;
    public int skippedFiles;
    public int failedFiles;
    public boolean cancelled;
    private final EnumMap<Reason, Integer> counts = new EnumMap<>(Reason.class);
    public final Map<Reason, Integer> reasons = Collections.unmodifiableMap(counts);

    void skip(Reason reason) {
        skippedFiles++;
        count(reason);
    }

    void fail(Reason reason) {
        failedFiles++;
        count(reason);
    }

    void cancel() {
        if (cancelled) return;
        cancelled = true;
        skip(Reason.CANCELLED);
    }

    private void count(Reason reason) {
        counts.put(reason, counts.containsKey(reason) ? counts.get(reason) + 1 : 1);
    }

    void add(CacheResult other) {
        releasedBytes += other.releasedBytes;
        skippedFiles += other.skippedFiles;
        failedFiles += other.failedFiles;
        cancelled |= other.cancelled;
        for (Map.Entry<Reason, Integer> entry : other.counts.entrySet()) {
            counts.put(entry.getKey(), counts.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }
    }
}
