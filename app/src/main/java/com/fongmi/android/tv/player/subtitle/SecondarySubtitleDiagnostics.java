package com.fongmi.android.tv.player.subtitle;

import java.util.concurrent.atomic.AtomicInteger;

/** Lightweight counters used by debug smoke tests and diagnostics. */
public final class SecondarySubtitleDiagnostics {

    private static final AtomicInteger loaded = new AtomicInteger();
    private static final AtomicInteger failed = new AtomicInteger();
    private static final AtomicInteger rendered = new AtomicInteger();
    private static volatile String lastError = "";

    private SecondarySubtitleDiagnostics() {
    }

    static void onLoaded() {
        loaded.incrementAndGet();
    }

    static void onFailed(Throwable error) {
        failed.incrementAndGet();
        lastError = error == null ? "unknown" : error.getClass().getSimpleName();
    }

    static void onRendered() {
        rendered.incrementAndGet();
    }

    public static void reset() {
        loaded.set(0);
        failed.set(0);
        rendered.set(0);
        lastError = "";
    }

    public static Snapshot snapshot() {
        return new Snapshot(loaded.get(), failed.get(), rendered.get(), lastError);
    }

    public record Snapshot(int loaded, int failed, int rendered, String lastError) {
    }
}
