package com.fongmi.android.tv.cache;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CacheFilesTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private CacheFiles cleaner() throws Exception {
        return new CacheFiles(temporary.getRoot());
    }

    private File file(String name, int bytes) throws Exception {
        File file = new File(temporary.getRoot(), name);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), new byte[bytes]);
        return file;
    }

    @Test public void removesOnlyRequestedTreeAndReportsActualBytes() throws Exception {
        file("mpv/shader", 17);
        file("mpv/nested/icc", 23);
        File untouched = file("user-import", 41);
        CacheResult result = cleaner().clear(new File(temporary.getRoot(), "mpv"));
        assertEquals(40, result.releasedBytes);
        assertEquals(0, result.failedFiles);
        assertTrue(untouched.exists());
        assertFalse(new File(temporary.getRoot(), "mpv").exists());
    }

    @Test public void neverDeletesCacheRootOrTraversesParent() throws Exception {
        File file = file("font.ttf", 5);
        assertEquals(1, cleaner().clear(temporary.getRoot()).skippedFiles);
        assertEquals(1, cleaner().clear(new File(temporary.getRoot(), "sub/../font.ttf")).skippedFiles);
        assertTrue(file.exists());
    }

    @Test public void refusesSiblingWithSharedPathPrefix() throws Exception {
        File outside = Files.createTempFile(temporary.getRoot().getParentFile().toPath(), temporary.getRoot().getName(), ".apk").toFile();
        try {
            assertEquals(1, cleaner().clear(outside).skippedFiles);
            assertTrue(outside.exists());
        } finally { outside.delete(); }
    }

    @Test public void symbolicLinksNeverReachOutsideOrIntoOtherCategories() throws Exception {
        File precious = file("user/font.ttf", 19);
        File managed = new File(temporary.getRoot(), "mpv");
        managed.mkdirs();
        Files.createSymbolicLink(new File(managed, "fonts").toPath(), precious.getParentFile().toPath());
        assertEquals(0, cleaner().size(managed));
        assertFalse(cleaner().isTreeSafe(managed));
        CacheResult result = cleaner().clear(managed);
        assertEquals(Integer.valueOf(1), result.reasons.get(CacheResult.Reason.UNSAFE_PATH));
        assertTrue(precious.exists());
        assertTrue(managed.exists());
    }

    @Test public void acceptsAnAliasedRootWithoutAcceptingDescendantSymlinks() throws Exception {
        File real = temporary.newFolder("real");
        File alias = new File(temporary.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), real.toPath());
        File target = new File(alias, "old.apk");
        Files.write(target.toPath(), new byte[7]);
        assertEquals(7, new CacheFiles(alias).clear(target).releasedBytes);
        assertTrue(real.exists());
    }

    @Test public void leaseProtectsOldPartialFileFromManualAndRetentionCleanup() throws Exception {
        File target = file("ota/update.apk.part", 31);
        assertTrue(target.setLastModified(1));
        try (CacheLease ignored = CacheLease.acquire(target)) {
            CacheResult manual = cleaner().clear(target);
            CacheResult retention = cleaner().clear(target, System.currentTimeMillis());
            assertEquals(Integer.valueOf(1), manual.reasons.get(CacheResult.Reason.IN_USE));
            assertEquals(Integer.valueOf(1), retention.reasons.get(CacheResult.Reason.IN_USE));
            assertTrue(target.exists());
        }
        assertEquals(31, cleaner().clear(target, System.currentTimeMillis()).releasedBytes);
    }

    @Test public void directoryLeaseProtectsFilesCreatedAfterAcquisition() throws Exception {
        File directory = new File(temporary.getRoot(), "mpv");
        try (CacheLease ignored = CacheLease.acquire(directory)) {
            File target = file("mpv/later-shader", 11);
            assertEquals(1, cleaner().clear(target).skippedFiles);
            assertTrue(target.exists());
        }
    }

    @Test public void multipleOwnersMustAllReleaseBeforeCleanup() throws Exception {
        File target = file("ota/update.apk", 13);
        CacheLease first = CacheLease.acquire(target);
        try (CacheLease second = CacheLease.acquire(target)) {
            first.close();
            first.close();
            assertEquals(1, cleaner().clear(target).skippedFiles);
        }
        assertEquals(13, cleaner().clear(target).releasedBytes);
    }

    @Test public void timedInstallerLeaseProtectsCompletedApk() throws Exception {
        File target = file("ota/update.apk", 13);
        CacheLease.protectFor(target, TimeUnit.MINUTES.toMillis(10));
        assertEquals(1, cleaner().clear(target).skippedFiles);
        assertTrue(target.exists());
    }

    @Test public void interruptedCleanupLeavesFilesIntact() throws Exception {
        File target = file("old.apk", 13);
        Thread.currentThread().interrupt();
        try {
            CacheResult result = cleaner().clear(target);
            assertTrue(result.cancelled);
            assertEquals(0, result.releasedBytes);
            assertTrue(target.exists());
        } finally { Thread.interrupted(); }
    }

    @Test public void preservesRecentFilesDuringRetention() throws Exception {
        File target = file("old.apk", 13);
        CacheResult result = cleaner().clear(target, target.lastModified());
        assertEquals(Integer.valueOf(1), result.reasons.get(CacheResult.Reason.TOO_RECENT));
        assertTrue(target.exists());
    }

    @Test public void failedDeletionDoesNotClaimFreedBytes() throws Exception {
        File target = file("cannot-delete.apk", 29);
        File failure = new File(target.getPath()) {
            @Override public boolean delete() { return false; }
        };
        CacheResult result = cleaner().clear(failure);
        assertEquals(0, result.releasedBytes);
        assertEquals(1, result.failedFiles);
        assertTrue(target.exists());
    }

    @Test public void leaseAcquisitionAndDeletionCannotOverlap() throws Exception {
        File target = file("race.apk", 43);
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch allowDelete = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);
        File pausedDelete = new File(target.getPath()) {
            @Override public boolean delete() {
                deleting.countDown();
                try { if (!allowDelete.await(3, TimeUnit.SECONDS)) return false; }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return false; }
                return super.delete();
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CacheFiles cleaner = cleaner();
            Future<CacheResult> clearing = executor.submit(() -> cleaner.clear(pausedDelete));
            assertTrue(deleting.await(3, TimeUnit.SECONDS));
            Future<?> leasing = executor.submit(() -> {
                try (CacheLease ignored = CacheLease.acquire(target)) { acquired.countDown(); }
            });
            assertFalse(acquired.await(100, TimeUnit.MILLISECONDS));
            allowDelete.countDown();
            assertEquals(43, clearing.get(3, TimeUnit.SECONDS).releasedBytes);
            leasing.get(3, TimeUnit.SECONDS);
            assertEquals(0, acquired.getCount());
        } finally { allowDelete.countDown(); executor.shutdownNow(); }
    }
}
