package com.fongmi.android.tv.drive;

import static com.fongmi.android.tv.drive.DriveCheckResult.Status.*;
import static org.junit.Assert.*;

import org.junit.Test;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

public class DriveLinkCheckerTest {
    private static final String QUARK = "https://pan.quark.cn/s/abcd1234";
    private static MockResponse ok() {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"code\":0,\"data\":{\"stoken\":\"fixture\"}}");
    }

    private DriveLinkChecker checker(MockWebServer server) {
        return checker(server, new OkHttpClient(), 3000);
    }

    private DriveLinkChecker checker(MockWebServer server, OkHttpClient client, long deadline) {
        return new DriveLinkChecker(client, new DriveLinkChecker.Endpoints(server.url("/quark").toString(),
                server.url("/ali").toString(), server.url("/115").toString()), deadline);
    }

    @Test public void onlyAnonymousMetadataIsRequestedAndResultOmitsSecrets() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(ok());
            OkHttpClient authenticated = new OkHttpClient.Builder().cookieJar(new CookieJar() {
                public List<Cookie> loadForRequest(HttpUrl url) { fail("Stored cookies must not be loaded"); return new ArrayList<>(); }
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) { fail("Cookies must not be saved"); }
            }).eventListener(new EventListener() {
                @Override public void callStart(Call call) { fail("Inherited request logging must not see share credentials"); }
            }).addInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("Authorization", "private").build())).build();
            DriveCheckResult result = checker(server, authenticated, 3000).check(QUARK + "?pwd=a1B2&token=private");
            assertEquals(OK, result.status);
            assertEquals(QUARK, result.normalizedUrl);
            RecordedRequest request = server.takeRequest();
            assertEquals("/quark", request.getPath());
            assertNull(request.getHeader("Authorization"));
            assertNull(request.getHeader("Cookie"));
            assertTrue(request.getBody().readUtf8().contains("\"passcode\":\"a1B2\""));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test public void checksAliPasswordAnd115OnlyOneMetadataItem() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"share_token\":\"fixture\"}"));
            server.enqueue(new MockResponse().setBody("{\"state\":true,\"data\":{\"share_state\":1}}"));
            DriveLinkChecker checker = checker(server);
            assertEquals(OK, checker.check("https://www.alipan.com/s/abcd1234 提取码:1a2b").status);
            RecordedRequest ali = server.takeRequest();
            assertEquals("/ali", ali.getPath());
            assertTrue(ali.getBody().readUtf8().contains("\"share_pwd\":\"1a2b\""));
            assertEquals(OK, checker.check("https://115.com/s/abcd1234?password=z9X8").status);
            String query = server.takeRequest().getPath();
            assertTrue(query.contains("limit=1"));
            assertTrue(query.contains("receive_code=z9X8"));
        }
    }

    @Test public void unsupportedAndMissing115CodeDoNotRequestNetwork() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            DriveLinkChecker checker = checker(server);
            assertEquals(UNSUPPORTED, checker.check("https://evil.test/s/abcd1234").status);
            assertEquals(LOCKED, checker.check("https://115.com/s/abcd1234").status);
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test public void redirectNeverReachesAnotherHost() throws Exception {
        try (MockWebServer server = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", other.url("/credential-trap")));
            assertEquals(UNCERTAIN, checker(server).check(QUARK).status);
            assertEquals(0, other.getRequestCount());
        }
    }

    @Test public void bodyAndTotalDeadlineAreBounded() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(new String(new char[300000])));
            assertEquals(UNCERTAIN, checker(server).check(QUARK).status);
            server.enqueue(new MockResponse().setChunkedBody(new String(new char[300000]), 4096));
            assertEquals(UNCERTAIN, checker(server).check(QUARK + "?pwd=zz99").status);
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            long start = System.nanoTime();
            assertEquals(UNCERTAIN, checker(server, new OkHttpClient(), 150).check(QUARK + "?pwd=a1B2").status);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
        }
    }

    @Test public void cacheEvictsOldEntriesInsteadOfGrowingWithoutBound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            DriveLinkChecker checker = checker(server);
            for (int i = 0; i < 129; i++) {
                server.enqueue(ok());
                assertEquals(OK, checker.check(QUARK + i).status);
            }
            assertEquals(129, server.getRequestCount());
            server.enqueue(ok());
            assertEquals(OK, checker.check(QUARK + "0").status);
            assertEquals(130, server.getRequestCount());
            assertEquals(OK, checker.check(QUARK + "128").status);
            assertEquals(130, server.getRequestCount());
        }
    }

    @Test public void deduplicatesAcrossInstancesAndCachesByPassword() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(ok().setHeadersDelay(200, TimeUnit.MILLISECONDS));
            ExecutorService executor = Executors.newFixedThreadPool(6);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<DriveCheckResult>> futures = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    DriveLinkChecker checker = checker(server);
                    futures.add(executor.submit(() -> { start.await(); return checker.check(QUARK); }));
                }
                start.countDown();
                for (Future<DriveCheckResult> future : futures) assertEquals(OK, future.get(3, TimeUnit.SECONDS).status);
                assertEquals(1, server.getRequestCount());
                assertEquals(OK, checker(server).check(QUARK).status);
                assertEquals(1, server.getRequestCount());
                server.enqueue(new MockResponse().setBody("{\"code\":41008}"));
                assertEquals(LOCKED, checker(server).check(QUARK + "?pwd=zzzz").status);
                assertEquals(2, server.getRequestCount());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test public void cancelClosesOwnerRequestAndDoesNotPoisonOtherCallers() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            server.enqueue(ok());
            DriveLinkChecker owner = checker(server);
            DriveLinkChecker next = checker(server);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> cancelled = executor.submit(() -> {
                    try { owner.check(QUARK); return false; }
                    catch (InterruptedIOException expected) { return true; }
                });
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
                Future<DriveCheckResult> waiter = executor.submit(() -> next.check(QUARK));
                owner.cancel();
                assertTrue(cancelled.get(2, TimeUnit.SECONDS));
                assertEquals(OK, waiter.get(2, TimeUnit.SECONDS).status);
                assertEquals(2, server.getRequestCount());
            } finally { owner.cancel(); next.cancel(); executor.shutdownNow(); }
        }
    }

    @Test public void cancelWaiterLeavesSharedOwnerRunning() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(ok().setHeadersDelay(250, TimeUnit.MILLISECONDS));
            DriveLinkChecker owner = checker(server);
            DriveLinkChecker waiter = checker(server);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<DriveCheckResult> first = executor.submit(() -> owner.check(QUARK));
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
                Future<Boolean> second = executor.submit(() -> {
                    try { waiter.check(QUARK); return false; }
                    catch (InterruptedIOException expected) { return true; }
                });
                waiter.cancel();
                assertTrue(second.get(2, TimeUnit.SECONDS));
                assertEquals(OK, first.get(2, TimeUnit.SECONDS).status);
                assertEquals(1, server.getRequestCount());
            } finally { owner.cancel(); waiter.cancel(); executor.shutdownNow(); }
        }
    }

    @Test public void globalConcurrencyIsLimitedAcrossDistinctLinks() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            for (int i = 0; i < 4; i++) server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<DriveLinkChecker> checkers = new ArrayList<>();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) {
                    DriveLinkChecker checker = checker(server);
                    checkers.add(checker);
                    String link = QUARK + i;
                    futures.add(executor.submit(() -> { try { checker.check(link); } catch (InterruptedIOException ignored) {} return null; }));
                }
                for (int i = 0; i < 3; i++) assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
            } finally {
                for (DriveLinkChecker checker : checkers) checker.cancel();
                for (Future<?> future : futures) future.get(2, TimeUnit.SECONDS);
                executor.shutdownNow();
            }
        }
    }
}
