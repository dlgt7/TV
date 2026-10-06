package com.fongmi.android.tv.syncplay;

import com.github.catvod.utils.Prefers;

public final class SyncplaySettings {
    private SyncplaySettings() { }
    public static String host() { return Prefers.getString("syncplay_host", ""); }
    public static int port() { return Prefers.getInt("syncplay_port", SyncplayConfig.DEFAULT_PORT); }
    public static String username() { return Prefers.getString("syncplay_username", "TV"); }
    public static String room() { return Prefers.getString("syncplay_room", ""); }
    public static boolean tls() { return Prefers.getBoolean("syncplay_tls", true); }
    public static void save(SyncplayConfig config) {
        Prefers.put("syncplay_host", config.host); Prefers.put("syncplay_port", config.port);
        Prefers.put("syncplay_username", config.username); Prefers.put("syncplay_room", config.room);
        Prefers.put("syncplay_tls", config.requireTls);
        // Server passwords belong to this connection only; saving settings never joins a room.
    }
}
