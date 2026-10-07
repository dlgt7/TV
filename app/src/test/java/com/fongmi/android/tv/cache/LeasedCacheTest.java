package com.fongmi.android.tv.cache;

import static org.junit.Assert.*;

import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.cache.ContentMetadataMutations;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LeasedCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void writerKeepsRealFilesProtectedAfterPlayerLeaseHasClosed() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache();
        LeasedCache cache = new LeasedCache(owner, directory);
        CacheSpan hole;
        try (CacheLease player = CacheLease.acquire(directory)) {
            hole = cache.startReadWrite("episode", 0, 20);
        }
        File partial = new File(directory, "tail.part");
        Files.write(partial.toPath(), new byte[20]);
        CacheFiles cleaner = new CacheFiles(temporary.getRoot());
        assertEquals(1, cleaner.clear(directory).skippedFiles);
        cache.commitFile(partial, 20);
        assertEquals(1, cleaner.clear(directory).skippedFiles);
        cache.releaseHoleSpan(hole);
        assertEquals(20, cleaner.clear(directory).releasedBytes);
        assertEquals(1, owner.releases);
    }

    @Test public void nonblockingMissCachedReadAndAcquisitionErrorsDoNotLeak() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache();
        LeasedCache cache = new LeasedCache(owner, directory);
        owner.result = null;
        assertNull(cache.startReadWriteNonBlocking("episode", 0, 1));
        assertFalse(CacheLease.isInUse(directory));
        owner.result = new CacheSpan("episode", 0, 1, 0, new File(directory, "cached"));
        assertTrue(cache.startReadWrite("episode", 0, 1).isCached);
        assertFalse(CacheLease.isInUse(directory));
        owner.failAcquire = true;
        assertThrows(Cache.CacheException.class, () -> cache.startReadWriteNonBlocking("episode", 0, 1));
        assertThrows(Cache.CacheException.class, () -> cache.startReadWrite("episode", 0, 1));
        assertFalse(CacheLease.isInUse(directory));
    }

    @Test public void writerBlockedInsideOwnerDoesNotHoldCleanupGate() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache();
        owner.entered = new CountDownLatch(1);
        owner.unblock = new CountDownLatch(1);
        LeasedCache cache = new LeasedCache(owner, directory);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CacheSpan> waiting = executor.submit(() -> cache.startReadWrite("episode", 0, 1));
            assertTrue(owner.entered.await(2, TimeUnit.SECONDS));
            Future<Boolean> gateAvailable = executor.submit(() -> {
                // Cleanup must return IN_USE immediately, without waiting for the owner.
                return new CacheFiles(temporary.getRoot()).clear(directory).skippedFiles == 1;
            });
            assertTrue(gateAvailable.get(1, TimeUnit.SECONDS));
            owner.unblock.countDown();
            cache.releaseHoleSpan(waiting.get(2, TimeUnit.SECONDS));
            assertFalse(CacheLease.isInUse(directory));
        } finally { owner.unblock.countDown(); executor.shutdownNow(); }
    }

    @Test public void interruptedBlockedAcquisitionReleasesItsLease() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache();
        owner.entered = new CountDownLatch(1);
        owner.unblock = new CountDownLatch(1);
        LeasedCache cache = new LeasedCache(owner, directory);
        CountDownLatch finished = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try { cache.startReadWrite("episode", 0, 1); }
            catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            catch (Cache.CacheException unexpected) { throw new AssertionError(unexpected); }
            finally { finished.countDown(); }
        });
        writer.start();
        try {
            assertTrue(owner.entered.await(2, TimeUnit.SECONDS));
            writer.interrupt();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertFalse(CacheLease.isInUse(directory));
        } finally { owner.unblock.countDown(); writer.join(2000); }
    }

    @Test public void cleanupCannotPassBetweenHoleAcquisitionAndLeaseRegistration() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache() {
            @Override public CacheSpan startReadWriteNonBlocking(String key, long position, long length) {
                assertTrue(CacheLease.isInUse(directory));
                return result;
            }
        };
        LeasedCache cache = new LeasedCache(owner, directory);
        CacheSpan hole = cache.startReadWriteNonBlocking("episode", 0, 1);
        assertTrue(CacheLease.isInUse(directory));
        cache.releaseHoleSpan(hole);
        assertFalse(CacheLease.isInUse(directory));
    }

    @Test public void releasingOneOfParallelHolesDoesNotExposeOtherWriter() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache();
        LeasedCache cache = new LeasedCache(owner, directory);
        CacheSpan first = cache.startReadWrite("episode", 0, 10);
        owner.result = new CacheSpan("episode", 10, 10);
        CacheSpan second = cache.startReadWriteNonBlocking("episode", 10, 10);
        cache.releaseHoleSpan(first);
        assertTrue(CacheLease.isInUse(directory));
        cache.releaseHoleSpan(second);
        assertFalse(CacheLease.isInUse(directory));
    }

    @Test public void failedOwnerReleaseKeepsProtectionUntilOwnerReallyReleases() throws Exception {
        File directory = temporary.newFolder("exo");
        FakeCache owner = new FakeCache();
        LeasedCache cache = new LeasedCache(owner, directory);
        CacheSpan hole = cache.startReadWrite("episode", 0, 10);
        owner.failRelease = true;
        assertThrows(IllegalStateException.class, () -> cache.releaseHoleSpan(hole));
        assertTrue(CacheLease.isInUse(directory));
        owner.failRelease = false;
        cache.releaseHoleSpan(hole);
        assertFalse(CacheLease.isInUse(directory));
    }

    private static class FakeCache implements Cache {
        CacheSpan result = new CacheSpan("episode", 0, 20);
        boolean failAcquire, failRelease;
        int releases;
        CountDownLatch entered, unblock;
        public CacheSpan startReadWrite(String key, long position, long length) throws InterruptedException, CacheException {
            if (entered != null) entered.countDown();
            if (unblock != null && !unblock.await(3, TimeUnit.SECONDS)) throw new CacheException("fixture timeout");
            return startReadWriteNonBlocking(key, position, length);
        }
        public CacheSpan startReadWriteNonBlocking(String key, long position, long length) throws CacheException {
            if (failAcquire) throw new CacheException("fixture acquisition failure");
            return result;
        }
        public void releaseHoleSpan(CacheSpan span) {
            if (failRelease) throw new IllegalStateException("fixture release failure");
            releases++;
        }
        public long getUid() { return 1; }
        public void release() {}
        public NavigableSet<CacheSpan> addListener(String key, Listener listener) { return new TreeSet<>(); }
        public void removeListener(String key, Listener listener) {}
        public NavigableSet<CacheSpan> getCachedSpans(String key) { return new TreeSet<>(); }
        public Set<String> getKeys() { return Collections.emptySet(); }
        public long getCacheSpace() { return 0; }
        public File startFile(String key, long position, long length) { throw new UnsupportedOperationException(); }
        public void commitFile(File file, long length) {}
        public void removeResource(String key) {}
        public void removeSpan(CacheSpan span) {}
        public boolean isCached(String key, long position, long length) { return false; }
        public long getCachedLength(String key, long position, long length) { return 0; }
        public long getCachedBytes(String key, long position, long length) { return 0; }
        public void applyContentMetadataMutations(String key, ContentMetadataMutations mutations) {}
        public ContentMetadata getContentMetadata(String key) { return null; }
    }
}
