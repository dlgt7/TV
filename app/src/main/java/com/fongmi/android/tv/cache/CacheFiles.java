package com.fongmi.android.tv.cache;

import java.io.File;
import java.io.IOException;

/** Filesystem operations confined to explicitly registered paths beneath the app cache root. */
public final class CacheFiles {

    private final File root;
    private final File canonicalRoot;

    public CacheFiles(File root) throws IOException {
        this.root = root.getAbsoluteFile();
        canonicalRoot = root.getCanonicalFile();
    }

    /** Android may alias a root ancestor (/data/user/0); descendants must never be symlinks. */
    public boolean isSafe(File file) {
        try {
            File absolute = file.getAbsoluteFile();
            for (String part : absolute.getPath().split("/")) {
                if (part.equals(".") || part.equals("..")) return false;
            }
            if (absolute.equals(root)) return absolute.getCanonicalFile().equals(canonicalRoot);
            String prefix = root.getPath() + File.separator;
            if (!absolute.getPath().startsWith(prefix)) return false;
            File parent = absolute.getParentFile();
            return parent != null && isSafe(parent)
                    && absolute.getCanonicalFile().equals(new File(parent.getCanonicalFile(), absolute.getName()));
        } catch (IOException | SecurityException unavailable) {
            return false;
        }
    }

    public long size(File file) {
        if (!isSafe(file) || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long bytes = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) bytes += size(child);
        return bytes;
    }

    /** Owner APIs can recurse too, so validate the entire tree before handing it to one. */
    public boolean isTreeSafe(File file) {
        if (!isSafe(file)) return false;
        if (!file.isDirectory()) return true;
        File[] children = file.listFiles();
        if (children == null) return false;
        for (File child : children) if (!isTreeSafe(child)) return false;
        return true;
    }

    public CacheResult clear(File file) {
        return clear(file, Long.MAX_VALUE);
    }

    /** Cutoff and leases are checked together immediately before every delete. */
    public CacheResult clear(File file, long olderThan) {
        CacheResult result = new CacheResult();
        clear(file, olderThan, result);
        return result;
    }

    private void clear(File file, long olderThan, CacheResult result) {
        if (Thread.currentThread().isInterrupted()) {
            result.cancel();
            return;
        }
        synchronized (CacheLease.GATE) {
            if (Thread.currentThread().isInterrupted()) {
                result.cancel();
                return;
            }
            if (!isSafe(file) || file.getAbsoluteFile().equals(root)) {
                result.skip(CacheResult.Reason.UNSAFE_PATH);
                return;
            }
            if (!file.exists()) return;
            if (CacheLease.isInUse(file)) {
                result.skip(CacheResult.Reason.IN_USE);
                return;
            }
            if (!file.isDirectory()) {
                if (file.lastModified() >= olderThan) {
                    result.skip(CacheResult.Reason.TOO_RECENT);
                } else {
                    long bytes = file.length();
                    if (delete(file)) result.releasedBytes += bytes;
                    else result.fail(CacheResult.Reason.DELETE_FAILED);
                }
                return;
            }
        }
        // Release the gate between files so a new player/download can acquire ownership.
        File[] children = file.listFiles();
        if (children == null) {
            result.fail(CacheResult.Reason.DELETE_FAILED);
            return;
        }
        for (File child : children) {
            clear(child, olderThan, result);
            if (result.cancelled) return;
        }
        synchronized (CacheLease.GATE) {
            if (Thread.currentThread().isInterrupted()) result.cancel();
            else if (isSafe(file) && !CacheLease.isInUse(file)) {
                File[] remaining = file.listFiles();
                if (remaining != null && remaining.length == 0 && !delete(file)) result.fail(CacheResult.Reason.DELETE_FAILED);
            }
        }
    }

    boolean delete(File file) {
        return file.delete();
    }
}
