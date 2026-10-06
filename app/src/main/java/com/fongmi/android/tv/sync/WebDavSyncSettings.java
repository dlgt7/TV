package com.fongmi.android.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import okhttp3.HttpUrl;

/** Credentials live outside the ordinary preference bundle and never enter a sync document. */
public final class WebDavSyncSettings {
    private WebDavSyncSettings() { }
    private static SharedPreferences preferences() { return App.get().getSharedPreferences("webdav-sync", Context.MODE_PRIVATE); }
    public static final class Options {
        public final String url, username, password;
        public final boolean automatic, keeps, history, subscriptions, preferences;
        public Options(String url, String username, String password, boolean automatic, boolean keeps, boolean history, boolean subscriptions, boolean preferences) {
            this.url = normalize(url); this.username = username == null ? "" : username; this.password = password == null ? "" : password;
            this.automatic = automatic; this.keeps = keeps; this.history = history; this.subscriptions = subscriptions; this.preferences = preferences;
        }
        public boolean configured() { return !url.isEmpty(); }
        public Set<String> kinds() {
            Set<String> kinds = new HashSet<>(); if (keeps) kinds.add("keep"); if (history) kinds.add("history");
            if (subscriptions) kinds.add("subscription"); if (preferences) kinds.add("preference"); return kinds;
        }
        public String endpointKey() { return SyncDocument.hash(url + "\n" + username); }
        public String fingerprint() { return SyncDocument.hash(url + "\n" + username + "\n" + password + "\n" + automatic + keeps + history + subscriptions + preferences); }
    }
    public static synchronized Options get() {
        SharedPreferences p = preferences();
        return new Options(p.getString("url", ""), p.getString("username", ""), p.getString("password", ""),
                p.getBoolean("automatic", false), p.getBoolean("keeps", true), p.getBoolean("history", true),
                p.getBoolean("subscriptions", false), p.getBoolean("preferences", false));
    }
    public static synchronized void save(Options options) {
        if (options.configured() && options.kinds().isEmpty()) throw new IllegalArgumentException("SELECT_SYNC_DATA");
        Options previous = get();
        SharedPreferences.Editor edit = preferences().edit().putString("url", options.url).putString("username", options.username).putString("password", options.password)
                .putBoolean("automatic", options.automatic).putBoolean("keeps", options.keeps).putBoolean("history", options.history)
                .putBoolean("subscriptions", options.subscriptions).putBoolean("preferences", options.preferences);
        if (!previous.endpointKey().equals(options.endpointKey())) edit.remove("last_success");
        if (!edit.commit()) throw new IllegalStateException("SETTINGS_SAVE_FAILED");
        if (!previous.fingerprint().equals(options.fingerprint())) WebDavSyncManager.get().configurationChanged();
    }
    public static synchronized String deviceId() {
        SharedPreferences p = preferences(); String id = p.getString("device", "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        if (!p.edit().putString("device", id).commit()) throw new IllegalStateException("DEVICE_ID_SAVE_FAILED");
        return id;
    }
    public static long lastSuccess() { return preferences().getLong("last_success", 0); }
    static synchronized void succeeded(String endpoint) {
        if (get().endpointKey().equals(endpoint)) preferences().edit().putLong("last_success", System.currentTimeMillis()).apply();
    }
    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        HttpUrl parsed = HttpUrl.parse(value.trim());
        if (parsed == null || !parsed.isHttps() || !parsed.username().isEmpty() || !parsed.password().isEmpty() || parsed.fragment() != null)
            throw new IllegalArgumentException("HTTPS_WEBDAV_URL_REQUIRED");
        if (parsed.encodedPath().endsWith("/")) parsed = parsed.newBuilder().addPathSegment("tv-sync-v1.json").build();
        return parsed.toString();
    }
}
