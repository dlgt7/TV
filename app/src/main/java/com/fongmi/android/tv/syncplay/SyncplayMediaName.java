package com.fongmi.android.tv.syncplay;

import java.util.regex.Pattern;

/** Produces a display-only room identifier without exporting transport addresses. */
public final class SyncplayMediaName {
    private static final String UNKNOWN = "[Unknown video]";
    private static final Pattern ADDRESS = Pattern.compile(
            "(?i)(?:://|[a-z][a-z0-9+.-]*\\s*:\\s*[/\\\\]"
                    + "|(?:https?|file|content|ftp|rtsp|rtmp|udp|smb|magnet|data):"
                    + "|(?:^|\\s)(?:/|\\\\|[a-z]:[/\\\\])"
                    + "|\\bwww\\."
                    + "|[a-z0-9.-]+\\.[a-z]{2,}(?::[0-9]+)?[/\\?#]"
                    + "|\\S+:\\S+@\\S+"
                    + "|[?&][^\\s=]+="
                    + "|[/\\\\][^\\r\\n]*\\.(?:mp4|mkv|m4v|webm|avi|mov|m3u8|ts)(?:$|[?#])"
                    + "|\\.(?:mp4|mkv|m4v|webm|avi|mov|m3u8|ts)[?#]"
                    + "|%[0-9a-f]{2})");

    private SyncplayMediaName() { }

    public static String from(CharSequence title, CharSequence episode) {
        String name = displayPart(title);
        String detail = displayPart(episode);
        if (name.isEmpty()) name = UNKNOWN;
        if (!detail.isEmpty() && !detail.equals(name)) name += " " + detail;
        return name.codePointCount(0, name.length()) <= 250 ? name : name.substring(0, name.offsetByCodePoints(0, 250));
    }

    public static boolean isUnknown(String name) {
        return name != null && (name.equals(UNKNOWN) || name.startsWith(UNKNOWN + " "));
    }

    private static String displayPart(CharSequence value) {
        if (value == null) return "";
        String text = value.toString().replaceAll("[\\p{Cntrl}]+", " ").trim();
        // Do not use an address basename: signed paths and filenames can contain
        // credentials too. Encoded octets are conservatively treated as addresses.
        return ADDRESS.matcher(text.replaceAll("\\p{Cf}", "")).find() ? "" : text;
    }
}
