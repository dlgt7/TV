package com.fongmi.android.tv.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Deterministic per-record merge, durable deletes, and acknowledgement of only unchanged local rows. */
public final class SyncMerger {
    private SyncMerger() { }
    public static final class State {
        public SyncDocument document = new SyncDocument();
        public final Map<String, String> observed = new HashMap<>();
        public long clock;
        public String encode() {
            JsonObject result = new JsonObject(); result.addProperty("version", 1); result.addProperty("clock", clock);
            result.add("document", JsonParser.parseString(document.encode()));
            JsonObject fingerprints = new JsonObject(); observed.forEach(fingerprints::addProperty); result.add("observed", fingerprints);
            return result.toString();
        }
        public static State decode(String input) {
            JsonObject object = JsonParser.parseString(input).getAsJsonObject();
            if (object.get("version").getAsInt() != 1) throw new IllegalArgumentException("UNSUPPORTED_SYNC_STATE");
            State result = new State(); result.clock = object.get("clock").getAsLong();
            result.document = SyncDocument.decode(object.get("document").toString());
            object.getAsJsonObject("observed").entrySet().forEach(e -> result.observed.put(e.getKey(), e.getValue().getAsString()));
            return result;
        }
    }
    public static SyncDocument merge(SyncDocument left, SyncDocument right) {
        SyncDocument merged = left.copy();
        right.entries.forEach((id, value) -> merged.entries.merge(id, value, (a, b) -> compare(a, b) >= 0 ? a : b));
        return merged;
    }
    private static int compare(SyncDocument.Entry a, SyncDocument.Entry b) {
        int order = Long.compare(a.revision, b.revision);
        if (order == 0) order = a.device.compareTo(b.device);
        if (order == 0) order = Boolean.compare(a.deleted, b.deleted);
        if (order == 0) order = SyncDocument.canonical(a.value).compareTo(SyncDocument.canonical(b.value));
        return order;
    }
    private static long tick(State state, long now) {
        state.clock = Math.max(Math.max(state.clock + 1, state.document.latestRevision() + 1), now);
        if (state.clock < 0 || state.clock == Long.MAX_VALUE) throw new IllegalArgumentException("INVALID_SYNC_CLOCK");
        return state.clock;
    }
    public static void observe(State state, Map<String, SyncDocument.Entry> local, Set<String> enabled, String device, long now) {
        for (SyncDocument.Entry item : local.values()) {
            if (!enabled.contains(item.kind)) continue;
            String id = item.id(), fingerprint = item.fingerprint(), previous = state.observed.get(id);
            if (!fingerprint.equals(previous)) {
                boolean first = previous == null && !state.document.entries.containsKey(id);
                long revision = first && item.revision > 0 ? item.revision : tick(state, now);
                state.document.entries.put(id, item.changed(revision, device));
                state.clock = Math.max(state.clock, revision);
            }
            state.observed.put(id, fingerprint);
        }
        for (String id : Set.copyOf(state.observed.keySet())) {
            SyncDocument.Entry old = state.document.entries.get(id);
            if (old != null && enabled.contains(old.kind) && !local.containsKey(id)) {
                state.document.entries.put(id, old.tombstone(tick(state, now), device));
                state.observed.remove(id);
            }
        }
    }
    /** Changed/skipped rows keep their pre-request fingerprints so the next sync exports those edits. */
    public static void acknowledge(State state, SyncDocument merged, Map<String, SyncDocument.Entry> after, Set<String> settled) {
        state.document = merged;
        state.clock = Math.max(state.clock, merged.latestRevision());
        for (String id : settled) {
            SyncDocument.Entry item = after.get(id);
            if (item == null) state.observed.remove(id);
            else state.observed.put(id, item.fingerprint());
        }
    }
}
