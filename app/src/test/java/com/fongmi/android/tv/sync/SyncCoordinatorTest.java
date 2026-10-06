package com.fongmi.android.tv.sync;

import static org.junit.Assert.*;
import static com.fongmi.android.tv.sync.SyncMergerTest.*;

import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncCoordinatorTest {
    static final class Journal implements SyncCoordinator.Journal {
        String text = new SyncMerger.State().encode(); int writes;
        public SyncMerger.State read() { return SyncMerger.State.decode(text); }
        public void write(SyncMerger.State state) { text = state.encode(); writes++; }
    }
    static final class Store implements SyncCoordinator.Store {
        Map<String, SyncDocument.Entry> entries = new HashMap<>(); int applied;
        public SyncCoordinator.Snapshot snapshot() {
            Map<String, String> fingerprints = new HashMap<>(); entries.forEach((k, v) -> fingerprints.put(k, v.fingerprint()));
            return new SyncCoordinator.Snapshot(entries, fingerprints);
        }
        public SyncCoordinator.Applied apply(SyncDocument document, SyncCoordinator.Snapshot before) {
            applied++; Set<String> stable = new java.util.HashSet<>(); int skipped = 0;
            for (SyncDocument.Entry incoming : document.entries.values()) {
                SyncDocument.Entry current = entries.get(incoming.id());
                if (!java.util.Objects.equals(before.fingerprints.get(incoming.id()), current == null ? null : current.fingerprint())) { skipped++; continue; }
                if (incoming.deleted) entries.remove(incoming.id()); else entries.put(incoming.id(), incoming); stable.add(incoming.id());
            }
            return new SyncCoordinator.Applied(snapshot(), stable, stable.size(), 0, skipped);
        }
    }
    @Test public void networkFailureKeepsPendingDeletionDurablyWithoutApplyingAnything() throws Exception {
        Store store = new Store(); Journal journal = new Journal(); SyncDocument.Entry item = history(scope(), "x", 1, 1, "a");
        SyncMerger.State state = journal.read(); SyncMerger.observe(state, Map.of(item.id(), item), Set.of("history"), "a", 2); journal.write(state);
        SyncCoordinator.Remote remote = new SyncCoordinator.Remote() {
            public SyncCoordinator.Fetched get() throws IOException { throw new IOException("offline"); }
            public boolean put(SyncDocument doc, SyncCoordinator.Fetched fetched) { throw new AssertionError(); }
        };
        assertThrows(IOException.class, () -> SyncCoordinator.sync(store, journal, remote, Set.of("history"), "a", 3));
        assertTrue(journal.read().document.entries.get(item.id()).deleted); assertEquals(0, store.applied);
    }
    @Test public void conflictRefetchesAndMergesOtherDevicesAdditions() throws Exception {
        Store store = new Store(); Journal journal = new Journal(); SyncDocument.Entry ours = history(scope(), "a", 1, 1, "a"), theirs = history(scope(), "b", 2, 2, "b"); store.entries.put(ours.id(), ours);
        AtomicInteger gets = new AtomicInteger(), puts = new AtomicInteger();
        SyncCoordinator.Remote remote = new SyncCoordinator.Remote() {
            public SyncCoordinator.Fetched get() { int n = gets.incrementAndGet(); return new SyncCoordinator.Fetched(n == 1 ? new SyncDocument() : document(theirs), "\"v" + n + "\"", true); }
            public boolean put(SyncDocument doc, SyncCoordinator.Fetched fetched) {
                if (puts.incrementAndGet() == 1) return false;
                assertEquals(2, doc.entries.size()); assertEquals("\"v2\"", fetched.etag); return true;
            }
        };
        SyncCoordinator.sync(store, journal, remote, Set.of("history"), "a", 3);
        assertEquals(2, gets.get()); assertEquals(2, puts.get()); assertEquals(2, store.entries.size());
    }
    @Test public void concurrentPlayerSaveWinsLocallyAndRemainsPendingForNextSync() throws Exception {
        Store store = new Store(); Journal journal = new Journal(); SyncDocument.Entry old = history(scope(), "a", 1, 1, "a"), newest = history(scope(), "a", 9, 9, "a"), remoteItem = history(scope(), "a", 8, 8, "b"); store.entries.put(old.id(), old);
        SyncCoordinator.Remote remote = new SyncCoordinator.Remote() {
            public SyncCoordinator.Fetched get() { store.entries.put(newest.id(), newest); return new SyncCoordinator.Fetched(document(remoteItem), "\"v1\"", true); }
            public boolean put(SyncDocument doc, SyncCoordinator.Fetched fetched) { return true; }
        };
        SyncCoordinator.Applied result = SyncCoordinator.sync(store, journal, remote, Set.of("history"), "a", 3);
        assertEquals(1, result.skipped); assertEquals(9, store.entries.get(old.id()).value.get("position").getAsLong());
        SyncMerger.State saved = journal.read(); SyncMerger.observe(saved, store.entries, Set.of("history"), "a", 10);
        assertEquals(9, saved.document.entries.get(old.id()).value.get("position").getAsLong());
    }
    @Test public void repeatedConflictsAreBoundedAndNeverApplyAnUncommittedMerge() throws Exception {
        Store store = new Store(); Journal journal = new Journal(); AtomicInteger puts = new AtomicInteger();
        SyncCoordinator.Remote remote = new SyncCoordinator.Remote() {
            public SyncCoordinator.Fetched get() { return new SyncCoordinator.Fetched(new SyncDocument(), null, false); }
            public boolean put(SyncDocument doc, SyncCoordinator.Fetched fetched) { puts.incrementAndGet(); return false; }
        };
        assertThrows(SyncCoordinator.Failure.class, () -> SyncCoordinator.sync(store, journal, remote, Set.of("history"), "a", 1));
        assertEquals(3, puts.get()); assertEquals(0, store.applied);
    }
}
