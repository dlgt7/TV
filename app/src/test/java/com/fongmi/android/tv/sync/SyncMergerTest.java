package com.fongmi.android.tv.sync;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import org.junit.Test;
import java.util.Map;
import java.util.Set;

public class SyncMergerTest {
    private static final Set<String> HISTORY = Set.of("history");
    static SyncDocument.Entry history(String scope, String key, long position, long revision, String device) {
        JsonObject value = new JsonObject(); value.addProperty("position", position); value.addProperty("duration", 30000L);
        value.addProperty("createTime", revision); value.addProperty("vodRemarks", "Episode 1");
        return new SyncDocument.Entry("history", scope, key, revision, device, false, value);
    }
    static SyncDocument document(SyncDocument.Entry... entries) {
        SyncDocument result = new SyncDocument(); for (SyncDocument.Entry entry : entries) result.entries.put(entry.id(), entry); return result;
    }
    static String scope() { return SyncDocument.scope(0, "https://source.test/config.json"); }

    @Test public void subscriptionScopeUsesUrlAndTypeNotDeviceLocalCidOrMovieName() {
        assertNotEquals(SyncDocument.scope(0, "https://a.test/a"), SyncDocument.scope(0, "https://a.test/b"));
        assertNotEquals(SyncDocument.scope(0, "https://a.test/a"), SyncDocument.scope(1, "https://a.test/a"));
        SyncDocument.Entry a = history(SyncDocument.scope(0, "https://a.test/a"), "same-key", 10, 1, "a");
        SyncDocument.Entry b = history(SyncDocument.scope(0, "https://a.test/b"), "same-key", 20, 1, "a");
        assertEquals(2, SyncMerger.merge(document(a), document(b)).entries.size());
        assertFalse(document(a).encode().contains("https://a.test"));
    }
    @Test public void mergeIsCommutativeAndTieBreakDoesNotDependOnIterationOrder() {
        SyncDocument.Entry a = history(scope(), "key", 100, 9, "a"), b = history(scope(), "key", 200, 9, "b");
        assertEquals(SyncMerger.merge(document(a), document(b)).encode(), SyncMerger.merge(document(b), document(a)).encode());
        assertEquals(200, SyncMerger.merge(document(a), document(b)).entries.get(a.id()).value.get("position").getAsLong());
    }
    @Test public void newEmptyDeviceNeverDeletesExistingRemoteData() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry remote = history(scope(), "remote", 10000, 50, "other");
        SyncMerger.observe(state, Map.of(), HISTORY, "new", 100);
        assertTrue(state.document.entries.isEmpty());
        assertFalse(SyncMerger.merge(state.document, document(remote)).entries.get(remote.id()).deleted);
    }
    @Test public void initialOlderHistoryDoesNotOverwriteNewerRemoteProgress() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry old = history(scope(), "key", 100, 10, ""), recent = history(scope(), "key", 15000, 90, "b");
        SyncMerger.observe(state, Map.of(old.id(), old), HISTORY, "new", 1000);
        assertEquals(15000, SyncMerger.merge(state.document, document(recent)).entries.get(old.id()).value.get("position").getAsLong());
    }
    @Test public void offlineDeleteSurvivesOldRemoteRecordAndJournalReload() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry item = history(scope(), "key", 100, 10, "a");
        SyncMerger.observe(state, Map.of(item.id(), item), HISTORY, "a", 20);
        SyncMerger.observe(state, Map.of(), HISTORY, "a", 30);
        state = SyncMerger.State.decode(state.encode());
        assertTrue(SyncMerger.merge(state.document, document(item)).entries.get(item.id()).deleted);
        assertFalse(state.observed.containsKey(item.id()));
    }
    @Test public void disablingHistorySyncDoesNotCreateDeletionMarkers() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry item = history(scope(), "key", 100, 10, "a");
        SyncMerger.observe(state, Map.of(item.id(), item), HISTORY, "a", 20);
        SyncMerger.observe(state, Map.of(), Set.of("keep"), "a", 30);
        assertFalse(state.document.entries.get(item.id()).deleted);
    }
    @Test public void skippedConcurrentPlayerWriteIsExportedOnTheNextSync() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry before = history(scope(), "key", 100, 10, "a");
        SyncMerger.observe(state, Map.of(before.id(), before), HISTORY, "a", 20);
        SyncDocument.Entry remote = history(scope(), "key", 1000, 30, "b"), changed = history(scope(), "key", 600, 25, "a");
        SyncMerger.acknowledge(state, document(remote), Map.of(changed.id(), changed), Set.of());
        SyncMerger.observe(state, Map.of(changed.id(), changed), HISTORY, "a", 40);
        assertEquals(600, state.document.entries.get(before.id()).value.get("position").getAsLong());
        assertTrue(state.document.entries.get(before.id()).revision > remote.revision);
    }
    @Test public void writeAfterTheApplyTransactionIsStillADetectableLocalEdit() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry before = history(scope(), "key", 100, 10, "a");
        SyncMerger.observe(state, Map.of(before.id(), before), HISTORY, "a", 20);
        SyncDocument.Entry imported = history(scope(), "key", 1000, 30, "b");
        // after must be the snapshot captured INSIDE the transaction, before a player's subsequent save.
        SyncMerger.acknowledge(state, document(imported), Map.of(imported.id(), imported), Set.of(imported.id()));
        SyncDocument.Entry later = history(scope(), "key", 1500, 31, "a");
        SyncMerger.observe(state, Map.of(later.id(), later), HISTORY, "a", 40);
        assertEquals(1500, state.document.entries.get(later.id()).value.get("position").getAsLong());
    }
    @Test public void unknownSubscriptionRowsNotAppliedLocallyAreNotAssumedDeleted() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry remote = history(scope(), "key", 1000, 30, "b");
        SyncMerger.acknowledge(state, document(remote), Map.of(), Set.of());
        SyncMerger.observe(state, Map.of(), HISTORY, "a", 40);
        assertFalse(state.document.entries.get(remote.id()).deleted);
    }
    @Test public void anIntentionalReaddAfterDeletionGetsANewRevision() {
        SyncMerger.State state = new SyncMerger.State(); SyncDocument.Entry item = history(scope(), "key", 100, 10, "a");
        SyncMerger.observe(state, Map.of(item.id(), item), HISTORY, "a", 20);
        SyncMerger.observe(state, Map.of(), HISTORY, "a", 30);
        long deletion = state.document.entries.get(item.id()).revision;
        SyncMerger.observe(state, Map.of(item.id(), item), HISTORY, "a", 40);
        assertFalse(state.document.entries.get(item.id()).deleted);
        assertTrue(state.document.entries.get(item.id()).revision > deletion);
    }
    @Test public void arbitraryPreferencesAndHiddenCredentialFieldsAreRejected() {
        JsonObject value = new JsonObject(); value.addProperty("value", "private");
        assertThrows(IllegalArgumentException.class, () -> new SyncDocument.Entry("preference", "global", "webdav_password", 1, "x", false, value));
        JsonObject history = new JsonObject(); history.addProperty("password", "private");
        assertThrows(IllegalArgumentException.class, () -> new SyncDocument.Entry("history", scope(), "x", 1, "x", false, history));
        assertThrows(RuntimeException.class, () -> SyncDocument.decode("{\"sites\":[]}"));
    }
    @Test public void jsonPropertyOrderDoesNotLookLikeAnUnrelatedLocalEdit() {
        JsonObject a = new JsonObject(), b = new JsonObject(); a.addProperty("x", 1); a.addProperty("y", 2);
        b.addProperty("y", 2); b.addProperty("x", 1); assertEquals(SyncDocument.canonical(a), SyncDocument.canonical(b));
    }
    @Test public void subscriptionsCannotCarryEmbeddedOrQueryCredentials() {
        assertFalse(SyncDocument.portableSubscription("https://name:password@source.test/config"));
        assertFalse(SyncDocument.portableSubscription("https://source.test/config?token=private"));
        assertTrue(SyncDocument.portableSubscription("https://source.test/config.json"));
    }
    @Test public void malformedOrCrossScopePayloadsAreRejectedBeforeAnyMergeWrite() {
        JsonObject value = new JsonObject(); value.addProperty("type", 0); value.addProperty("createTime", 1);
        assertThrows(IllegalArgumentException.class, () -> new SyncDocument.Entry("keep", "global", "x", 1, "a", false, value));
        JsonObject invalid = new JsonObject(); invalid.addProperty("position", 1.5); invalid.addProperty("duration", 10); invalid.addProperty("createTime", 1);
        assertThrows(IllegalArgumentException.class, () -> new SyncDocument.Entry("history", scope(), "x", 1, "a", false, invalid));
    }

}
