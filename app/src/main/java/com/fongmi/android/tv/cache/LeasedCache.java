package com.fongmi.android.tv.cache;

import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.cache.ContentMetadataMutations;

import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;

/** Keeps manual cleanup away from actual Media3 writes, including asynchronous cancellation tails. */
public final class LeasedCache implements Cache {

    private final Cache delegate;
    private final File directory;
    private final Map<CacheSpan, CacheLease> holes = new IdentityHashMap<>();

    public LeasedCache(Cache delegate, File directory) {
        this.delegate = delegate;
        this.directory = directory;
    }

    @Override
    public CacheSpan startReadWrite(String key, long position, long length) throws InterruptedException, CacheException {
        // Acquire ownership BEFORE asking SimpleCache for a span. The potentially blocking
        // call runs without GATE, so a different writer can release its hole and wake us.
        CacheLease lease = CacheLease.acquire(directory);
        boolean transferred = false;
        try {
            CacheSpan span = delegate.startReadWrite(key, position, length);
            transferred = hold(span, lease);
            return span;
        } finally {
            if (!transferred) lease.close();
        }
    }

    @Override
    public CacheSpan startReadWriteNonBlocking(String key, long position, long length) throws CacheException {
        CacheLease lease = CacheLease.acquire(directory);
        boolean transferred = false;
        try {
            CacheSpan span = delegate.startReadWriteNonBlocking(key, position, length);
            transferred = hold(span, lease);
            return span;
        } finally {
            if (!transferred) lease.close();
        }
    }

    private boolean hold(CacheSpan span, CacheLease lease) {
        if (span == null || span.isCached) return false;
        synchronized (holes) { holes.put(span, lease); }
        return true;
    }

    @Override
    public void releaseHoleSpan(CacheSpan holeSpan) {
        // The owner releases the actual write lock before its protection is removed.
        // If the owner throws, keep protection: the lock's state is then unconfirmed.
        delegate.releaseHoleSpan(holeSpan);
        CacheLease lease;
        synchronized (holes) { lease = holes.remove(holeSpan); }
        if (lease != null) lease.close();
    }

    @Override public long getUid() { return delegate.getUid(); }
    @Override public NavigableSet<CacheSpan> addListener(String key, Listener listener) { return delegate.addListener(key, listener); }
    @Override public void removeListener(String key, Listener listener) { delegate.removeListener(key, listener); }
    @Override public NavigableSet<CacheSpan> getCachedSpans(String key) { return delegate.getCachedSpans(key); }
    @Override public Set<String> getKeys() { return delegate.getKeys(); }
    @Override public long getCacheSpace() { return delegate.getCacheSpace(); }
    @Override public File startFile(String key, long position, long length) throws CacheException { return delegate.startFile(key, position, length); }
    @Override public void commitFile(File file, long length) throws CacheException { delegate.commitFile(file, length); }
    @Override public void removeResource(String key) { delegate.removeResource(key); }
    @Override public void removeSpan(CacheSpan span) { delegate.removeSpan(span); }
    @Override public boolean isCached(String key, long position, long length) { return delegate.isCached(key, position, length); }
    @Override public long getCachedLength(String key, long position, long length) { return delegate.getCachedLength(key, position, length); }
    @Override public long getCachedBytes(String key, long position, long length) { return delegate.getCachedBytes(key, position, length); }
    @Override public void applyContentMetadataMutations(String key, ContentMetadataMutations mutations) throws CacheException { delegate.applyContentMetadataMutations(key, mutations); }
    @Override public ContentMetadata getContentMetadata(String key) { return delegate.getContentMetadata(key); }

    @Override
    public void release() {
        delegate.release();
        CacheLease[] leases;
        synchronized (holes) {
            leases = holes.values().toArray(new CacheLease[0]);
            holes.clear();
        }
        for (CacheLease lease : leases) lease.close();
    }
}
