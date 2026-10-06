package com.fongmi.android.tv.sync;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;


/** Direct DAO writes preserve cid ownership and compare the complete DB row inside a transaction. */
final class WebDavSyncStore implements SyncCoordinator.Store {
    private static final Gson GSON = new Gson();
    private final AppDatabase db;
    private final WebDavSyncSettings.Options options;
    private final Set<String> kinds;
    private final BooleanSupplier allowed;
    private record Row(SyncDocument.Entry entry, String fingerprint, Object bean) { }
    WebDavSyncStore(AppDatabase db, WebDavSyncSettings.Options options, BooleanSupplier allowed) { this.db = db; this.options = options; this.allowed = allowed; kinds = options.kinds(); }

    static SyncCoordinator.Journal journal(File directory, String endpoint) {
        return new SyncCoordinator.Journal() {
            final AtomicFile file = new AtomicFile(new File(directory, endpoint + ".json"));
            public SyncMerger.State read() throws IOException {
                // openRead restores AtomicFile's backup after an interrupted write, even if base is absent.
                try (FileInputStream input = file.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    for (int count; (count = input.read(buffer)) != -1; ) {
                        if (bytes.size() + count > 2L * SyncDocument.MAX_BYTES) throw new IOException("LOCAL_SYNC_STATE_TOO_LARGE");
                        bytes.write(buffer, 0, count);
                    }
                    return SyncMerger.State.decode(bytes.toString(StandardCharsets.UTF_8.name()));
                } catch (FileNotFoundException missing) {
                    if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) throw missing;
                    return new SyncMerger.State();
                } catch (RuntimeException e) { throw new IOException("INVALID_LOCAL_SYNC_STATE"); }
            }
            public void write(SyncMerger.State state) throws IOException {
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("SYNC_DIRECTORY_UNAVAILABLE");
                FileOutputStream output = file.startWrite();
                try { output.write(state.encode().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
                catch (Exception e) { file.failWrite(output); throw new IOException("SYNC_STATE_SAVE_FAILED"); }
            }
        };
    }
    private static JsonObject fields(Object bean, String... names) {
        JsonObject full = GSON.toJsonTree(bean).getAsJsonObject(), result = new JsonObject();
        for (String name : names) if (full.has(name)) {
            if (name.equals("vodPic") && !SyncDocument.portableSubscription(full.get(name).getAsString())) continue;
            result.add(name, full.get(name).deepCopy());
        }
        return result;
    }
    private Map<String, Row> rows() {
        Map<String, Row> result = new HashMap<>(); Map<Integer, Config> configs = new HashMap<>();
        for (Config config : db.getConfigDao().findAll()) {
            configs.put(config.getId(), config);
            if (!options.subscriptions || !SyncDocument.portableSubscription(config.getUrl())) continue;
            SyncDocument.Entry entry = new SyncDocument.Entry("subscription", SyncDocument.scope(config.getType(), config.getUrl()), "subscription",
                    Math.max(1, config.getTime()), "", false, fields(config, "url", "type", "name"));
            add(result, entry, config);
        }
        if (options.keeps) for (Keep keep : db.getKeepDao().findAll()) {
            Config config = configs.get(keep.getCid());
            if (keep.getType() == Keep.TYPE_VOD && config == null) continue;
            String scope = keep.getType() == Keep.TYPE_VOD ? SyncDocument.scope(config.getType(), config.getUrl()) : "global";
            SyncDocument.Entry entry = new SyncDocument.Entry("keep", scope, keep.getKey(), Math.max(1, keep.getCreateTime()), "", false,
                    fields(keep, "type", "siteName", "vodName", "vodPic", "createTime")); add(result, entry, keep);
        }
        if (options.history) for (History history : db.getHistoryDao().findAll()) {
            Config config = configs.get(history.getCid()); if (config == null) continue;
            SyncDocument.Entry entry = new SyncDocument.Entry("history", SyncDocument.scope(config.getType(), config.getUrl()), history.getKey(), Math.max(1, history.getCreateTime()), "", false,
                    fields(history, "vodName", "vodPic", "vodFlag", "vodRemarks", "createTime", "position", "duration", "opening", "ending", "openingSource", "endingSource"));
            add(result, entry, history);
        }
        if (options.preferences) {
            Map<String, ?> preferences = Prefers.getPrefers().getAll();
            for (String key : SyncDocument.PREFERENCES) if (preferences.containsKey(key)) {
                JsonObject value = new JsonObject(); value.add("value", GSON.toJsonTree(preferences.get(key)));
                SyncDocument.Entry entry = new SyncDocument.Entry("preference", "global", key, 0, "", false, value);
                result.put(entry.id(), new Row(entry, entry.fingerprint(), preferences.get(key)));
            }
        }
        return result;
    }
    private static void add(Map<String, Row> rows, SyncDocument.Entry entry, Object bean) {
        rows.put(entry.id(), new Row(entry, SyncDocument.hash(SyncDocument.canonical(GSON.toJsonTree(bean))), bean));
    }
    private static SyncCoordinator.Snapshot view(Map<String, Row> rows) {
        Map<String, SyncDocument.Entry> entries = new HashMap<>(); Map<String, String> fingerprints = new HashMap<>();
        rows.forEach((id, row) -> { entries.put(id, row.entry); fingerprints.put(id, row.fingerprint); });
        return new SyncCoordinator.Snapshot(entries, fingerprints);
    }
    @Override public SyncCoordinator.Snapshot snapshot() {
        AtomicReference<SyncCoordinator.Snapshot> result = new AtomicReference<>(); db.runInTransaction(() -> result.set(view(rows()))); return result.get();
    }
    @Override public SyncCoordinator.Applied apply(SyncDocument document, SyncCoordinator.Snapshot before) {
        Set<String> settled = new HashSet<>(); int[] counts = new int[3];
        AtomicReference<SyncCoordinator.Snapshot> committed = new AtomicReference<>();
        db.runInTransaction(() -> {
            if (!allowed.getAsBoolean() || !options.fingerprint().equals(WebDavSyncSettings.get().fingerprint())) throw new IllegalStateException("SYNC_SETTINGS_CHANGED");
            Map<String, Row> current = rows();
            // New subscriptions must exist before matching remote progress to their local IDs.
            for (String kind : new String[]{"subscription", "keep", "history"}) {
                if (!kinds.contains(kind)) continue;
                for (SyncDocument.Entry entry : document.entries.values()) {
                    if (!entry.kind.equals(kind)) continue;
                    String id = entry.id(); Row row = current.get(id);
                    if (!Objects.equals(before.fingerprints.get(id), row == null ? null : row.fingerprint)) { counts[2]++; continue; }
                    if (row != null && !entry.deleted && entry.fingerprint().equals(row.entry.fingerprint())) { settled.add(id); continue; }
                    int action = write(entry, row);
                    if (action < 0) { counts[2]++; continue; }
                    settled.add(id); if (action > 0) counts[entry.deleted ? 1 : 0]++;
                }
            }
            // Capture before releasing the DB lock: a player write after commit must remain a new edit.
            committed.set(view(rows()));
        });
        Map<String, SyncDocument.Entry> afterEntries = new HashMap<>(committed.get().entries);
        Map<String, String> afterFingerprints = new HashMap<>(committed.get().fingerprints);
        if (options.preferences) applyPreferences(document, before, settled, counts, afterEntries, afterFingerprints);
        return new SyncCoordinator.Applied(new SyncCoordinator.Snapshot(afterEntries, afterFingerprints), settled, counts[0], counts[1], counts[2]);
    }
    private Config config(String scope) {
        for (Config config : db.getConfigDao().findAll()) if (scope.equals(SyncDocument.scope(config.getType(), config.getUrl()))) return config;
        return null;
    }
    /** Returns -1 for a collision/unavailable scope, 0 for no mutation, 1 for mutation. */
    private int write(SyncDocument.Entry entry, Row current) {
        switch (entry.kind) {
            case "subscription": {
                if (entry.deleted) {
                    // Imported subscriptions are never removed automatically: this could be the active source.
                    // The tombstone remains in the cloud and prevents importing it onto an empty device again.
                    return current == null ? 0 : -1;
                }
                JsonObject value = entry.value;
                String url = value.get("url").getAsString(); int type = value.get("type").getAsInt();
                if (!SyncDocument.portableSubscription(url) || type < 0 || type > 2 || !entry.scope.equals(SyncDocument.scope(type, url))) return -1;
                Config config = config(entry.scope);
                if (config == null) {
                    config = new Config(); config.setType(type); config.setUrl(url); config.setTime(0);
                    config.setName(string(value, "name")); db.getConfigDao().insert(config);
                } else { config.setName(string(value, "name")); db.getConfigDao().update(config); }
                return 1;
            }
            case "keep": {
                if (entry.deleted) {
                    if (current == null) return 0;
                    Keep keep = (Keep) current.bean;
                    if (keep.getType() == Keep.TYPE_DISCOVER) db.getKeepDao().deleteDiscover(keep.getKey());
                    else if (keep.getType() == Keep.TYPE_LIVE) db.getKeepDao().delete(keep.getKey());
                    else db.getKeepDao().delete(keep.getCid(), keep.getKey());
                    return 1;
                }
                int type = entry.value.get("type").getAsInt();
                if (type < 0 || type > 2) return -1;
                Config config = type == Keep.TYPE_VOD ? config(entry.scope) : null;
                if (type == Keep.TYPE_VOD ? config == null : !entry.scope.equals("global")) return -1;
                for (Keep other : db.getKeepDao().findAll()) if (entry.key.equals(other.getKey()) && (current == null || other.getCid() != ((Keep) current.bean).getCid() || other.getType() != type)) return -1;
                Keep keep = GSON.fromJson(entry.value, Keep.class); keep.setKey(entry.key); keep.setCid(config == null ? 0 : config.getId());
                db.getKeepDao().insertOrUpdate(keep); return 1;
            }
            case "history": {
                if (entry.deleted) {
                    if (current == null) return 0;
                    History history = (History) current.bean; db.getHistoryDao().delete(history.getCid(), history.getKey()); return 1;
                }
                Config config = config(entry.scope); if (config == null) return -1;
                for (History other : db.getHistoryDao().findAll()) if (entry.key.equals(other.getKey()) && other.getCid() != config.getId()) return -1;
                JsonObject raw = current == null ? GSON.toJsonTree(new History()).getAsJsonObject() : GSON.toJsonTree(current.bean).getAsJsonObject();
                entry.value.entrySet().forEach(e -> raw.add(e.getKey(), e.getValue().deepCopy()));
                // Decoder, speed, scale and the old device's ephemeral episode URL never cross devices.
                History history = GSON.fromJson(raw, History.class); history.setKey(entry.key); history.setCid(config.getId());
                history.setEpisodeUrl(""); // Match the imported episode label and resolve a fresh local source URL.
                db.getHistoryDao().insertOrUpdate(history); return 1;
            }
            default: return -1;
        }
    }
    private void applyPreferences(SyncDocument document, SyncCoordinator.Snapshot before, Set<String> settled, int[] counts,
                                  Map<String, SyncDocument.Entry> afterEntries, Map<String, String> afterFingerprints) {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable action = () -> {
            try {
                if (!allowed.getAsBoolean() || !options.fingerprint().equals(WebDavSyncSettings.get().fingerprint())) return;
                SharedPreferences preferences = Prefers.getPrefers(); Map<String, ?> current = preferences.getAll();
                SharedPreferences.Editor edit = preferences.edit(); Set<String> accepted = new HashSet<>(); int changed = 0, deleted = 0;
                Map<String, SyncDocument.Entry> appliedValues = new HashMap<>();
                for (SyncDocument.Entry entry : document.entries.values()) {
                    if (!entry.kind.equals("preference")) continue;
                    JsonObject value = new JsonObject(); if (current.containsKey(entry.key)) value.add("value", GSON.toJsonTree(current.get(entry.key)));
                    String fingerprint = current.containsKey(entry.key) ? SyncDocument.hash(SyncDocument.canonical(value)) : null;
                    if (!Objects.equals(fingerprint, before.fingerprints.get(entry.id()))) { counts[2]++; continue; }
                    if (entry.deleted) { if (current.containsKey(entry.key)) { edit.remove(entry.key); deleted++; } }
                    else {
                        JsonElement setting = entry.value.get("value");
                        if (entry.key.equals("danmaku_show")) {
                            if (setting == null || !setting.isJsonPrimitive() || !setting.getAsJsonPrimitive().isBoolean()) { counts[2]++; continue; }
                            edit.putBoolean(entry.key, setting.getAsBoolean());
                        } else {
                            float number = setting.getAsFloat(); float low = entry.key.equals("danmaku_text_scale") ? .5f : 0f;
                            float high = entry.key.equals("danmaku_text_scale") ? 3f : .9f;
                            if (!Float.isFinite(number) || number < low || number > high) { counts[2]++; continue; }
                            edit.putFloat(entry.key, number);
                        }
                        if (!entry.fingerprint().equals(fingerprint)) changed++;
                        JsonObject applied = new JsonObject();
                        if (entry.key.equals("danmaku_show")) applied.addProperty("value", setting.getAsBoolean());
                        else applied.addProperty("value", setting.getAsFloat());
                        appliedValues.put(entry.id(), new SyncDocument.Entry("preference", "global", entry.key, 0, "", false, applied));
                    }
                    accepted.add(entry.id());
                }
                if (!edit.commit()) throw new IllegalStateException("SYNC_PREFERENCES_SAVE_FAILED");
                settled.addAll(accepted); counts[0] += changed; counts[1] += deleted;
                for (String id : accepted) {
                    SyncDocument.Entry applied = appliedValues.get(id);
                    if (applied == null) { afterEntries.remove(id); afterFingerprints.remove(id); }
                    else { afterEntries.put(id, applied); afterFingerprints.put(id, applied.fingerprint()); }
                }
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) action.run(); else new Handler(Looper.getMainLooper()).post(action);
        try { if (!done.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("SYNC_PREFERENCES_TIMEOUT"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("SYNC_CANCELED"); }
        if (failure.get() != null) throw new IllegalStateException("SYNC_PREFERENCES_FAILED", failure.get());
    }
    private static String string(JsonObject value, String key) { return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : ""; }
}
