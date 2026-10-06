package com.fongmi.android.tv.syncplay;

/** Explicit connection settings. Construction never opens a connection. */
public final class SyncplayConfig {
    public static final int DEFAULT_PORT = 8999;
    public final String host;
    public final int port;
    public final String username;
    public final String room;
    public final String password;
    public final boolean requireTls;

    public SyncplayConfig(String host, int port, String username, String room, String password, boolean requireTls) {
        String address = clean(host, 253, "server");
        if (address.startsWith("[") && address.endsWith("]")) address = address.substring(1, address.length() - 1);
        if (address.contains("/") || address.contains("@") || address.contains("?") || address.contains("#")
                || address.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("server");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port");
        try {
            if (address.contains(":")) new java.net.URI("http://[" + address + "]:" + port);
            else address = java.net.IDN.toASCII(address, java.net.IDN.USE_STD3_ASCII_RULES);
        } catch (Exception error) { throw new IllegalArgumentException("server"); }
        this.host = address;
        this.port = port;
        this.username = clean(username, 16, "username");
        this.room = clean(room, 35, "room");
        if (this.room.startsWith("+")) throw new IllegalArgumentException("controlled_room");
        this.password = password == null ? "" : password;
        this.requireTls = requireTls;
    }

    private static String clean(String value, int maximum, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > maximum
                || text.codePoints().anyMatch(c -> Character.isISOControl(c))) throw new IllegalArgumentException(field);
        return text;
    }
}
