package com.fongmi.android.tv.source.health;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Recent observations only; no content IDs, request text, URLs or persistent preferences. */
public final class SourceHealthStore {
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_AGE_MS = 24 * 60 * 60 * 1000L;
    public enum Outcome { SUCCESS, EMPTY, FAILURE }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final LongSupplier clock;
    public SourceHealthStore() { this(System::currentTimeMillis); }
    SourceHealthStore(LongSupplier clock) { this.clock = clock; }

    public synchronized void record(String key, boolean search, Outcome outcome, long elapsedMs) {
        long now = clock.getAsLong();
        prune(now);
        Entry previous = entries.remove(key);
        if (previous == null) previous = new Entry(null, null);
        Stats prior = search ? previous.search : previous.detail;
        int failures = outcome == Outcome.FAILURE ? Math.min(999, prior == null ? 1 : prior.consecutiveFailures + 1) : 0;
        Stats next = new Stats(outcome, Math.max(0, elapsedMs), failures, now);
        entries.put(key, search ? new Entry(next, previous.detail) : new Entry(previous.search, next));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized Entry get(String key) {
        prune(clock.getAsLong());
        return entries.getOrDefault(key, new Entry(null, null));
    }

    public synchronized void clear() { entries.clear(); }
    synchronized int size() { prune(clock.getAsLong()); return entries.size(); }

    private void prune(long now) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Entry> item = iterator.next();
            Stats search = fresh(item.getValue().search, now);
            Stats detail = fresh(item.getValue().detail, now);
            if (search == null && detail == null) iterator.remove();
            else if (search != item.getValue().search || detail != item.getValue().detail) item.setValue(new Entry(search, detail));
        }
    }

    private Stats fresh(Stats stats, long now) {
        return stats == null || now < stats.updatedAt || now - stats.updatedAt >= MAX_AGE_MS ? null : stats;
    }

    public static final class Entry {
        public final Stats search;
        public final Stats detail;
        Entry(Stats search, Stats detail) { this.search = search; this.detail = detail; }
    }

    public static final class Stats {
        public final Outcome outcome;
        public final long elapsedMs;
        public final int consecutiveFailures;
        public final long updatedAt;
        Stats(Outcome outcome, long elapsedMs, int consecutiveFailures, long updatedAt) {
            this.outcome = outcome;
            this.elapsedMs = elapsedMs;
            this.consecutiveFailures = consecutiveFailures;
            this.updatedAt = updatedAt;
        }
    }
}
