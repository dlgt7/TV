package com.fongmi.android.tv.cache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** In-process ownership shared by downloads, players, and every cache cleanup entry point. */
public final class CacheLease implements AutoCloseable {

    static final Object GATE = new Object();
    private static final List<CacheLease> leases = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();
    private final long expiresAt;
    private boolean closed;

    private CacheLease(long durationMs, File... files) {
        expiresAt = durationMs > 0 ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs) : 0;
        for (File file : files) paths.add(identity(file));
    }

    public static CacheLease acquire(File... files) {
        return register(0, files);
    }

    /** Keeps a completed APK available while its FileProvider URI is handed to the installer. */
    public static void protectFor(File file, long durationMs) {
        if (durationMs <= 0) return;
        register(Math.min(durationMs, TimeUnit.DAYS.toMillis(1)), file);
    }

    private static CacheLease register(long durationMs, File... files) {
        synchronized (GATE) {
            prune();
            CacheLease lease = new CacheLease(durationMs, files);
            leases.add(lease);
            return lease;
        }
    }

    public static boolean isInUse(File file) {
        synchronized (GATE) {
            prune();
            String path = identity(file);
            for (CacheLease lease : leases) {
                for (String held : lease.paths) {
                    if (contains(held, path) || contains(path, held)) return true;
                }
            }
            return false;
        }
    }

    private static boolean contains(String parent, String child) {
        return child.equals(parent) || child.startsWith(parent + File.separator);
    }

    private static String identity(File file) {
        if (file == null) throw new IllegalArgumentException("Missing cache lease path");
        try {
            return file.getCanonicalPath();
        } catch (IOException unavailable) {
            // Cleanup rejects paths whose canonical identity cannot be checked.
            return file.getAbsolutePath();
        }
    }

    private static void prune() {
        long now = System.nanoTime();
        Iterator<CacheLease> iterator = leases.iterator();
        while (iterator.hasNext()) {
            CacheLease lease = iterator.next();
            if (lease.closed || lease.expiresAt != 0 && now - lease.expiresAt >= 0) iterator.remove();
        }
    }

    @Override
    public void close() {
        synchronized (GATE) {
            closed = true;
            leases.remove(this);
        }
    }
}
