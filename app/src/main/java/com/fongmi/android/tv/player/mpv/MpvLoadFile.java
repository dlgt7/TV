package com.fongmi.android.tv.player.mpv;

import java.util.Locale;

/** Command layout for the bundled mpv (0.38+): URL, flags, index, then per-file options. */
final class MpvLoadFile {
    private MpvLoadFile() { }

    static boolean deferInitialSeek(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u8") || lower.startsWith("http://") || lower.startsWith("https://");
    }

    static String[] command(String url, long startPositionMs) {
        if (startPositionMs <= 0 || deferInitialSeek(url)) return new String[]{"loadfile", url, "replace"};
        return new String[]{"loadfile", url, "replace", "-1",
                "start=" + String.format(Locale.US, "%.3f", startPositionMs / 1000.0)};
    }
}
