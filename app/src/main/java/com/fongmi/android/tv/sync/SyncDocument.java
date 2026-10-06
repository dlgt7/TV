package com.fongmi.android.tv.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import okhttp3.HttpUrl;

/** Portable records never contain local database IDs or a device's complete preferences. */
public final class SyncDocument {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_RECORDS = 20_000;
    public static final Set<String> KINDS = Set.of("keep", "history", "subscription", "preference");
    public static final Set<String> PREFERENCES = Set.of("danmaku_show", "danmaku_text_scale", "danmaku_transparency");
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "keep", Set.of("type", "siteName", "vodName", "vodPic", "createTime"),
            "history", Set.of("vodName", "vodPic", "vodFlag", "vodRemarks", "createTime", "position", "duration", "opening", "ending", "openingSource", "endingSource"),
            "subscription", Set.of("url", "type", "name"),
            "preference", Set.of("value"));
    public final Map<String, Entry> entries = new TreeMap<>();

    public static final class Entry {
        public final String kind, scope, key, device;
        public final long revision;
        public final boolean deleted;
        public final JsonObject value;

        public Entry(String kind, String scope, String key, long revision, String device, boolean deleted, JsonObject value) {
            if (!KINDS.contains(kind) || key == null || key.isEmpty() || key.length() > 8192
                    || scope == null || !(scope.matches("[0-9a-f]{64}") || scope.equals("global"))
                    || device == null || device.length() > 128 || revision < 0 || revision == Long.MAX_VALUE)
                throw new IllegalArgumentException("INVALID_SYNC_RECORD");
            if (kind.equals("preference") && (!scope.equals("global") || !PREFERENCES.contains(key)))
                throw new IllegalArgumentException("UNSUPPORTED_SYNC_PREFERENCE");
            this.kind = kind; this.scope = scope; this.key = key; this.revision = revision;
            this.device = device; this.deleted = deleted;
            this.value = value == null ? new JsonObject() : value.deepCopy();
            if (deleted && !this.value.isEmpty()) throw new IllegalArgumentException("INVALID_SYNC_TOMBSTONE");
            for (String field : this.value.keySet())
                if (!FIELDS.get(kind).contains(field)) throw new IllegalArgumentException("UNSUPPORTED_SYNC_FIELD");
            if (canonical(this.value).length() > 64 * 1024) throw new IllegalArgumentException("SYNC_RECORD_TOO_LARGE");
            if (!deleted) validate(kind, scope, key, this.value);
        }

        public String id() { return hash(kind + "\n" + scope + "\n" + key); }
        public String fingerprint() { return hash(canonical(value)); }
        public Entry changed(long revision, String device) { return new Entry(kind, scope, key, revision, device, false, value); }
        public Entry tombstone(long revision, String device) { return new Entry(kind, scope, key, revision, device, true, null); }
        public JsonObject json() {
            JsonObject out = new JsonObject(); out.addProperty("kind", kind); out.addProperty("scope", scope);
            out.addProperty("key", key); out.addProperty("revision", revision); out.addProperty("device", device);
            out.addProperty("deleted", deleted); out.add("value", value.deepCopy()); return out;
        }
    }

    private static long integer(JsonObject value, String key) {
        JsonElement item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("INVALID_SYNC_NUMBER");
        try { return item.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("INVALID_SYNC_NUMBER"); }
    }
    private static void validate(String kind, String scope, String key, JsonObject value) {
        if (kind.equals("history")) {
            if (scope.equals("global")) throw new IllegalArgumentException("INVALID_HISTORY_SCOPE");
            integer(value, "createTime"); integer(value, "position"); integer(value, "duration");
            for (String boundary : new String[]{"opening", "ending"}) if (value.has(boundary)) integer(value, boundary);
        } else if (kind.equals("keep")) {
            long type = integer(value, "type"); integer(value, "createTime");
            if (type < 0 || type > 2 || (type == 0 && scope.equals("global")) || (type != 0 && !scope.equals("global"))) throw new IllegalArgumentException("INVALID_KEEP_SCOPE");
        } else if (kind.equals("subscription")) {
            long type = integer(value, "type");
            if (!key.equals("subscription") || type < 0 || type > 2 || !value.has("url")
                    || !portableSubscription(value.get("url").getAsString()) || !scope.equals(scope((int) type, value.get("url").getAsString()))) throw new IllegalArgumentException("INVALID_SUBSCRIPTION_SCOPE");
        } else if (kind.equals("preference")) {
            JsonElement setting = value.get("value");
            if (setting == null || !setting.isJsonPrimitive()) throw new IllegalArgumentException("INVALID_SYNC_PREFERENCE");
            if (key.equals("danmaku_show")) {
                if (!setting.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("INVALID_SYNC_PREFERENCE");
            } else {
                if (!setting.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("INVALID_SYNC_PREFERENCE");
                float number = setting.getAsFloat(), low = key.equals("danmaku_text_scale") ? .5f : 0f, high = key.equals("danmaku_text_scale") ? 3f : .9f;
                if (!Float.isFinite(number) || number < low || number > high) throw new IllegalArgumentException("INVALID_SYNC_PREFERENCE");
            }
        }
        for (var entry : value.entrySet()) {
            String name = entry.getKey();
            if (Set.of("vodName", "vodPic", "vodFlag", "vodRemarks", "siteName", "openingSource", "endingSource", "url", "name").contains(name)
                    && (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("INVALID_SYNC_STRING");
        }
    }
    public SyncDocument copy() { SyncDocument copy = new SyncDocument(); copy.entries.putAll(entries); return copy; }
    public long latestRevision() { return entries.values().stream().mapToLong(e -> e.revision).max().orElse(0); }
    public String encode() {
        JsonObject out = new JsonObject(); out.addProperty("schema", "tv-webdav-sync"); out.addProperty("version", 1);
        JsonArray rows = new JsonArray(); entries.values().forEach(e -> rows.add(e.json())); out.add("records", rows);
        String text = out.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES || entries.size() > MAX_RECORDS)
            throw new IllegalArgumentException("SYNC_DOCUMENT_TOO_LARGE");
        return text;
    }
    public static SyncDocument decode(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("SYNC_DOCUMENT_TOO_LARGE");
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        if (!"tv-webdav-sync".equals(json.get("schema").getAsString()) || json.get("version").getAsInt() != 1)
            throw new IllegalArgumentException("UNSUPPORTED_SYNC_DOCUMENT");
        JsonArray rows = json.getAsJsonArray("records");
        if (rows == null || rows.size() > MAX_RECORDS) throw new IllegalArgumentException("SYNC_DOCUMENT_TOO_LARGE");
        SyncDocument result = new SyncDocument();
        for (JsonElement item : rows) {
            JsonObject row = item.getAsJsonObject();
            Entry entry = new Entry(row.get("kind").getAsString(), row.get("scope").getAsString(), row.get("key").getAsString(),
                    row.get("revision").getAsLong(), row.get("device").getAsString(), row.get("deleted").getAsBoolean(), row.getAsJsonObject("value"));
            if (result.entries.put(entry.id(), entry) != null) throw new IllegalArgumentException("DUPLICATE_SYNC_RECORD");
        }
        return result;
    }
    public static boolean portableSubscription(String value) {
        HttpUrl url = value == null ? null : HttpUrl.parse(value);
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty()) return false;
        for (String key : url.queryParameterNames()) if (key.toLowerCase(java.util.Locale.ROOT).matches(".*(?:token|password|passwd|secret|auth|key|signature).*")) return false;
        return true;
    }
    public static String scope(int type, String url) { return hash(type + "\n" + (url == null ? "" : url.trim())); }
    public static String canonical(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonArray()) { JsonArray result = new JsonArray(); value.getAsJsonArray().forEach(e -> result.add(JsonParser.parseString(canonical(e)))); return result.toString(); }
        if (!value.isJsonObject()) return value.toString();
        JsonObject result = new JsonObject();
        new TreeMap<>(value.getAsJsonObject().asMap()).forEach((key, child) -> result.add(key, JsonParser.parseString(canonical(child))));
        return result.toString();
    }
    public static String hash(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(Character.forDigit(value >>> 4 & 15, 16)).append(Character.forDigit(value & 15, 16));
            return result.toString();
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
