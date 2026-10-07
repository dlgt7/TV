package com.fongmi.android.tv.update;

import static org.junit.Assert.*;

import com.fongmi.android.tv.cache.CacheFiles;
import com.fongmi.android.tv.cache.CacheLease;
import com.fongmi.android.tv.cache.CacheResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

public class UpdateDownloaderTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final byte[] APK = apk(65536);

    private static byte[] apk(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 42);
        bytes[0] = 'P'; bytes[1] = 'K'; bytes[2] = 3; bytes[3] = 4;
        return bytes;
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value & 255));
        }
        return result.toString();
    }

    private static MockResponse response(byte[] bytes) {
        return new MockResponse().setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(new Buffer().write(bytes));
    }

    private static List<String> sources(MockWebServer server) {
        return Arrays.asList(server.url("/mirror.apk").toString(), server.url("/github.apk").toString());
    }

    private UpdateDownloader downloader() {
        return new UpdateDownloader(new OkHttpClient());
    }

    private File target() {
        return new File(temporary.getRoot(), "update.apk");
    }

    private void assertClean() {
        assertFalse(target().exists());
        assertFalse(new File(target().getPath() + ".part").exists());
        assertFalse(CacheLease.isInUse(target()));
        assertFalse(CacheLease.isInUse(new File(target().getPath() + ".part")));
    }

    @Test public void cleanupCannotRemoveInFlightOrInstallerApk() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK));
            CacheFiles cleaner = new CacheFiles(temporary.getRoot());
            downloader().download(sources(server), target(), APK.length, hash(APK), partial -> {
                assertTrue(CacheLease.isInUse(target()));
                CacheResult duringValidation = cleaner.clear(partial);
                assertEquals(Integer.valueOf(1), duringValidation.reasons.get(CacheResult.Reason.IN_USE));
                assertTrue(partial.exists());
            }, null);
            CacheResult awaitingInstaller = cleaner.clear(target());
            assertEquals(Integer.valueOf(1), awaitingInstaller.reasons.get(CacheResult.Reason.IN_USE));
            assertArrayEquals(APK, Files.readAllBytes(target().toPath()));
            assertFalse(CacheLease.isInUse(new File(target().getPath() + ".part")));
        }
    }

    private void assertFallback(MockResponse broken) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(broken);
            server.enqueue(response(APK));
            File result = downloader().download(sources(server), target(), APK.length, hash(APK), file -> {}, null);
            assertArrayEquals(APK, Files.readAllBytes(result.toPath()));
            assertEquals("/mirror.apk", server.takeRequest().getPath());
            assertEquals("/github.apk", server.takeRequest().getPath());
            assertEquals(2, server.getRequestCount());
            assertFalse(new File(target().getPath() + ".part").exists());
        }
    }

    @Test public void verifiesBytesHashAndApkBeforePublishingCompletion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK));
            List<Integer> progress = new ArrayList<>();
            AtomicInteger validations = new AtomicInteger();
            File result = downloader().download(sources(server), target(), APK.length, hash(APK).toUpperCase(), file -> {
                assertFalse(target().exists());
                assertEquals("update.apk.part", file.getName());
                assertArrayEquals(APK, Files.readAllBytes(file.toPath()));
                assertFalse(progress.contains(100));
                validations.incrementAndGet();
            }, progress::add);
            assertEquals(target(), result);
            assertArrayEquals(APK, Files.readAllBytes(result.toPath()));
            assertEquals(1, validations.get());
            assertEquals(1, server.getRequestCount());
            assertEquals(Integer.valueOf(0), progress.get(0));
            assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
            for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) > progress.get(i - 1));
        }
    }

    @Test public void httpFailureFallsBack() throws Exception {
        assertFallback(new MockResponse().setResponseCode(503).setBody("Unavailable"));
    }

    @Test public void followsReleaseAssetRedirectBeforeTryingAnotherSource() throws Exception {
        try (MockWebServer server = new MockWebServer(); MockWebServer cdn = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", cdn.url("/asset.apk")));
            cdn.enqueue(response(APK));
            downloader().download(sources(server), target(), APK.length, hash(APK), file -> {}, null);
            assertArrayEquals(APK, Files.readAllBytes(target().toPath()));
            assertEquals(1, server.getRequestCount());
            assertEquals(1, cdn.getRequestCount());
        }
    }

    @Test public void htmlResponseFallsBack() throws Exception {
        assertFallback(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Blocked</html>"));
    }

    @Test public void htmlDisguisedAsApkFallsBack() throws Exception {
        byte[] html = new byte[APK.length];
        Arrays.fill(html, (byte) ' ');
        System.arraycopy("<html>".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, html, 0, 6);
        assertFallback(response(html));
    }

    @Test public void emptyResponseFallsBack() throws Exception {
        assertFallback(response(new byte[0]));
    }

    @Test public void truncatedResponseFallsBack() throws Exception {
        assertFallback(response(APK).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
    }

    @Test public void stalledSourceTimesOutAndFallsBack() throws Exception {
        assertFallback(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    }

    @Test public void incorrectDeclaredSizeFallsBack() throws Exception {
        assertFallback(response(apk(1024)));
    }

    @Test public void incorrectHashFallsBackAndResetsProgress() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            byte[] corrupted = APK.clone();
            corrupted[100] ^= 1;
            server.enqueue(response(corrupted)); server.enqueue(response(APK));
            List<Integer> progress = new ArrayList<>();
            downloader().download(sources(server), target(), APK.length, hash(APK), file -> {}, progress::add);
            assertArrayEquals(APK, Files.readAllBytes(target().toPath()));
            assertEquals(2, server.getRequestCount());
            assertEquals(2, java.util.Collections.frequency(progress, 0));
            assertEquals(1, java.util.Collections.frequency(progress, 100));
        }
    }

    @Test public void failedApkValidationFallsBack() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK)); server.enqueue(response(APK));
            AtomicInteger validations = new AtomicInteger();
            downloader().download(sources(server), target(), APK.length, hash(APK), file -> {
                if (validations.incrementAndGet() == 1) throw new IOException("Wrong APK package");
            }, null);
            assertEquals(2, validations.get());
            assertEquals(2, server.getRequestCount());
            assertArrayEquals(APK, Files.readAllBytes(target().toPath()));
        }
    }

    @Test public void chunkedOversizedBodyFallsBack() throws Exception {
        assertFallback(new MockResponse().setChunkedBody(new Buffer().write(apk(APK.length + 4096)), 4096));
    }

    @Test public void unknownLengthStillRequiresACompleteNonemptyApk() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setChunkedBody(new Buffer().write(APK), 4096));
            List<Integer> progress = new ArrayList<>();
            downloader().download(sources(server), target(), -1, null, file -> {}, progress::add);
            assertArrayEquals(APK, Files.readAllBytes(target().toPath()));
            assertEquals(Arrays.asList(0, 100), progress);
        }
    }

    @Test public void allFailuresRemoveOldTargetAndPartialFile() throws Exception {
        Files.write(target().toPath(), APK);
        Files.write(new File(target().getPath() + ".part").toPath(), APK);
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(response(APK).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
            IOException failure = assertThrows(IOException.class, () -> downloader().download(
                    sources(server), target(), APK.length, null, file -> {}, null));
            assertEquals(2, failure.getSuppressed().length);
            assertClean();
        }
    }

    @Test public void cancellationFromProgressNeverValidatesCompletesOrFallsBack() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK)); server.enqueue(response(APK));
            UpdateDownloader downloader = downloader();
            List<Integer> progress = new ArrayList<>();
            assertThrows(InterruptedIOException.class, () -> downloader.download(sources(server), target(),
                    APK.length, null, file -> fail("Cancelled download was validated"), percent -> {
                        progress.add(percent);
                        if (percent > 0) downloader.cancel();
                    }));
            assertEquals(1, server.getRequestCount());
            assertFalse(progress.contains(100));
            assertClean();
        }
    }

    @Test public void cancellationWhileWaitingForNetworkNeverStartsAnotherSource() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK).throttleBody(1024, 1, TimeUnit.SECONDS));
            server.enqueue(response(APK));
            UpdateDownloader downloader = downloader();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> done = executor.submit(() -> {
                    try {
                        downloader.download(sources(server), target(), APK.length, null, file -> {}, null);
                        return false;
                    } catch (InterruptedIOException expected) { return true; }
                });
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
                downloader.cancel();
                assertTrue(done.get(3, TimeUnit.SECONDS));
                assertEquals(1, server.getRequestCount());
                assertClean();
            } finally { executor.shutdownNow(); }
        }
    }

    @Test public void cancellationDuringValidationPreventsRenameAndFallback() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK)); server.enqueue(response(APK));
            UpdateDownloader downloader = downloader();
            CountDownLatch validating = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> done = executor.submit(() -> {
                    try {
                        downloader.download(sources(server), target(), APK.length, null, file -> {
                            validating.countDown();
                            try { release.await(3, TimeUnit.SECONDS); }
                            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                        }, percent -> assertNotEquals(100, percent));
                        return false;
                    } catch (InterruptedIOException expected) { return true; }
                });
                assertTrue(validating.await(3, TimeUnit.SECONDS));
                downloader.cancel();
                release.countDown();
                assertTrue(done.get(3, TimeUnit.SECONDS));
                assertEquals(1, server.getRequestCount());
                assertClean();
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test public void cancellationAtFinalProgressRemovesRenamedFile() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(APK)); server.enqueue(response(APK));
            UpdateDownloader downloader = downloader();
            assertThrows(InterruptedIOException.class, () -> downloader.download(sources(server), target(),
                    APK.length, null, file -> {}, percent -> {
                        if (percent == 100) downloader.cancel();
                    }));
            assertEquals(1, server.getRequestCount());
            assertClean();
        }
    }

    @Test public void alreadyCancelledDownloadDoesNotStartAnyRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            UpdateDownloader downloader = downloader();
            downloader.cancel();
            assertThrows(InterruptedIOException.class, () -> downloader.download(
                    sources(server), target(), APK.length, null, file -> {}, percent -> fail("Cancelled progress")));
            assertEquals(0, server.getRequestCount());
            assertClean();
        }
    }
}
