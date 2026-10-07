package com.fongmi.android.tv.utils;

import java.util.Locale;

/** Elapsed playback time, independent of a source's current link or metadata. */
public final class HistoryProgressText {
    public static String elapsed(long positionMs, long durationMs) {
        long position = Math.max(0, positionMs);
        if (durationMs > 0) position = Math.min(position, durationMs);
        long seconds = position / 1_000;
        return seconds < 3_600
                ? String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60);
    }

    private HistoryProgressText() {}
}
