package com.fongmi.android.tv.sync;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Networking is outside the local transaction; a store applies only records still matching its snapshot. */
public final class SyncCoordinator {
    public interface Remote {
        Fetched get() throws IOException;
        boolean put(SyncDocument document, Fetched fetched) throws IOException;
    }
    public interface Journal {
        SyncMerger.State read() throws IOException;
        void write(SyncMerger.State state) throws IOException;
    }
    public interface Store {
        Snapshot snapshot();
        Applied apply(SyncDocument document, Snapshot before);
    }
    public static final class Snapshot {
        public final Map<String, SyncDocument.Entry> entries;
        public final Map<String, String> fingerprints;
        public Snapshot(Map<String, SyncDocument.Entry> entries, Map<String, String> fingerprints) {
            this.entries = Map.copyOf(entries); this.fingerprints = Map.copyOf(fingerprints);
        }
    }
    public static final class Applied {
        public final Snapshot after;
        public final Set<String> settled;
        public final int updated, deleted, skipped;
        public Applied(Snapshot after, Set<String> settled, int updated, int deleted, int skipped) {
            this.after = after; this.settled = Set.copyOf(settled); this.updated = updated; this.deleted = deleted; this.skipped = skipped;
        }
    }
    public static final class Fetched {
        public final SyncDocument document;
        public final String etag;
        public final boolean exists;
        public Fetched(SyncDocument document, String etag, boolean exists) { this.document = document; this.etag = etag; this.exists = exists; }
    }
    public static final class Failure extends IOException {
        public final String code;
        public Failure(String code) { super(code); this.code = code; }
    }
    public static Applied sync(Store store, Journal journal, Remote remote, Set<String> enabled, String device, long now) throws IOException {
        SyncMerger.State state = journal.read();
        Snapshot before = store.snapshot();
        SyncMerger.observe(state, before.entries, enabled, device, now);
        // Persist pending edits/deletes before network I/O. A dropped connection cannot erase them.
        journal.write(state);
        for (int attempt = 0; attempt < 3; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new Failure("SYNC_CANCELED");
            Fetched fetched = remote.get();
            SyncDocument merged = SyncMerger.merge(state.document, fetched.document);
            if (!fetched.exists || !merged.encode().equals(fetched.document.encode())) {
                if (!remote.put(merged, fetched)) continue;
            }
            Applied applied = store.apply(merged, before);
            SyncMerger.acknowledge(state, merged, applied.after.entries, applied.settled);
            journal.write(state);
            return applied;
        }
        throw new Failure("REMOTE_CHANGED_RETRY");
    }
}
