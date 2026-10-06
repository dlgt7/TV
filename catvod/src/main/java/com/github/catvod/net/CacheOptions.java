package com.github.catvod.net;

import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Cache durations are milliseconds, independent of the HTTP request timeout. */
final class CacheOptions {

    final long ttl;
    final boolean stale;
    final boolean persist;

    CacheOptions(long ttl, boolean stale, boolean persist) {
        if (ttl < 0) throw new IllegalArgumentException("Cache ttl must not be negative");
        this.ttl = ttl;
        this.stale = stale;
        this.persist = persist;
    }

    static CacheOptions from(String json) {
        JsonObject options = Json.strict(json).getAsJsonObject();
        JsonElement ttl = options.get("ttl");
        if (ttl == null || !ttl.isJsonPrimitive() || !ttl.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Cache ttl is required");
        }
        long duration;
        try {
            duration = ttl.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Cache ttl must be an integer", e);
        }
        return new CacheOptions(duration, flag(options, "stale", true), flag(options, "persist", false));
    }

    private static boolean flag(JsonObject options, String key, boolean fallback) {
        JsonElement value = options.get(key);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Cache " + key + " must be boolean");
        }
        return value.getAsBoolean();
    }
}
