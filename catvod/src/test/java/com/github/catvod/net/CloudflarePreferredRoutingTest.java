package com.github.catvod.net;

import static org.junit.Assert.*;

import com.github.catvod.net.ech.CloudflareAddressRanges;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;

import mockwebserver3.MockResponse;
import mockwebserver3.MockResponseBody;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.BufferedSink;

/** Real sockets, TLS verification, HTTP/2 streams and OkHttp's actual pool; no mocked Chain. */
public class CloudflarePreferredRoutingTest {
    private static final String ORIGIN = "tmdb.origin.test";
    private static final String PREFERRED = "best.cf.test";
    private static final String OTHER = "other.origin.test";
    private static final String ORIGINAL_IP = "104.16.1.1";
    private static final String PREFERRED_IP = "104.16.2.1";
    private static final String NON_CF_IP = "192.0.2.1";

    @Test
    public void preferredHttpsPreservesUrlHostSniAndReusesSuccessfulHttp2Connection() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preferred.enqueue(ok("first"));
            f.preferred.enqueue(ok("second"));
            for (String expected : List.of("first", "second")) {
                try (Response response = f.client.newCall(request()).execute()) {
                    assertEquals(Protocol.HTTP_2, response.protocol());
                    assertEquals("https://" + ORIGIN + "/logo?language=zh", response.request().url().toString());
                    assertEquals(expected, response.body().string());
                }
            }
            RecordedRequest first = take(f.preferred);
            RecordedRequest second = take(f.preferred);
            assertEquals(ORIGIN, first.getHeaders().get(":authority"));
            assertEquals(List.of(ORIGIN), first.getHandshakeServerNames());
            assertEquals("/logo?language=zh", first.getTarget());
            assertEquals("Bearer original-secret", first.getHeaders().get("Authorization"));
            assertEquals(first.getConnectionIndex(), second.getConnectionIndex());
            assertEquals(first.getExchangeIndex() + 1, second.getExchangeIndex());
            assertEquals(1, f.dns.count(PREFERRED));
            assertEquals(List.of(PREFERRED_IP), f.sockets.dialed);
            assertEquals(0, f.original.getRequestCount());
        }
    }

    @Test
    public void failedPooledPreferredConnectionFallsBackOnceAndCoolsDown() throws Exception {
        for (int code : new int[]{403, 421, 500, 502, 503, 599}) {
            try (Fixture f = new Fixture()) {
                f.preferred.enqueue(ok("warm"));
                assertEquals("warm", body(f.client));
                f.preferred.enqueue(new MockResponse.Builder().code(code).body("bad edge").build());
                f.original.enqueue(ok("fallback"));
                f.original.enqueue(ok("cooldown"));
                assertEquals("fallback", body(f.client));
                assertEquals("cooldown", body(f.client));
                RecordedRequest warm = take(f.preferred);
                RecordedRequest failed = take(f.preferred);
                RecordedRequest fallback = take(f.original);
                assertEquals(warm.getConnectionIndex(), failed.getConnectionIndex());
                assertEquals(ORIGIN, fallback.getHeaders().get(":authority"));
                assertEquals(List.of(ORIGIN), fallback.getHandshakeServerNames());
                assertEquals("Bearer original-secret", fallback.getHeaders().get("Authorization"));
                assertEquals(2, f.preferred.getRequestCount());
                assertEquals(2, f.original.getRequestCount());
                assertEquals(List.of(PREFERRED_IP, ORIGINAL_IP), f.sockets.dialed);
            }
        }
    }

    @Test
    public void fallbackDoesNotAbortAnotherActiveHttp2Stream() throws Exception {
        try (Fixture f = new Fixture()) {
            CountDownLatch release = new CountDownLatch(1);
            try {
                f.preferred.enqueue(new MockResponse.Builder().body(new MockResponseBody() {
                    public long getContentLength() { return 4; }
                    public void writeTo(BufferedSink sink) throws IOException {
                        sink.writeUtf8("l").flush();
                        await(release);
                        sink.writeUtf8("ive");
                    }
                }).build());
                try (Response active = f.client.newCall(request()).execute()) {
                    assertEquals(Protocol.HTTP_2, active.protocol());
                    RecordedRequest stream = take(f.preferred);
                    f.preferred.enqueue(new MockResponse.Builder().code(503).body("edge failed").build());
                    f.original.enqueue(ok("fallback"));
                    assertEquals("fallback", body(f.client));
                    RecordedRequest failed = take(f.preferred);
                    assertEquals(stream.getConnectionIndex(), failed.getConnectionIndex());
                    release.countDown();
                    assertEquals("live", active.body().string());
                }
                assertEquals(List.of(PREFERRED_IP, ORIGINAL_IP), f.sockets.dialed);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void preferredTcpFailureFallsBackToOriginalDns() throws Exception {
        try (Fixture f = new Fixture()) {
            int closedPort;
            try (ServerSocket unused = new ServerSocket(0)) { closedPort = unused.getLocalPort(); }
            f.sockets.ports.put(PREFERRED_IP, closedPort);
            f.original.enqueue(ok("original after TCP failure"));
            assertEquals("original after TCP failure", body(f.client));
            assertEquals(List.of(PREFERRED_IP, ORIGINAL_IP), f.sockets.dialed);
        }
    }

    @Test
    public void preferredCertificateMismatchFallsBackWithoutDisablingVerification() throws Exception {
        try (Fixture f = new Fixture(); MockWebServer wrong = new MockWebServer()) {
            HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName(PREFERRED).build();
            HandshakeCertificates serverTls = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
            HandshakeCertificates trusted = new HandshakeCertificates.Builder()
                    .addTrustedCertificate(f.certificate.certificate()).addTrustedCertificate(certificate.certificate()).build();
            wrong.useHttps(serverTls.sslSocketFactory());
            wrong.start();
            wrong.enqueue(ok("must not accept preferred hostname"));
            f.sockets.ports.put(PREFERRED_IP, wrong.getPort());
            OkHttpClient client = f.client.newBuilder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager()).build();
            f.original.enqueue(ok("original with valid origin certificate"));
            assertEquals("original with valid origin certificate", body(client));
            assertEquals(0, wrong.getRequestCount());
            assertEquals(List.of(PREFERRED_IP, ORIGINAL_IP), f.sockets.dialed);
        }
    }

    @Test
    public void originalFallbackFailureIsReturnedWithoutAnotherPreferredAttempt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preferred.enqueue(new MockResponse.Builder().code(403).body("preferred failed").build());
            f.original.enqueue(new MockResponse.Builder().code(502).body("original failed").build());
            try (Response response = f.client.newCall(request()).execute()) {
                assertEquals(502, response.code());
                assertEquals("original failed", response.body().string());
            }
            assertEquals(1, f.preferred.getRequestCount());
            assertEquals(1, f.original.getRequestCount());
        }
    }

    @Test
    public void retryAfterAndOrdinaryApplicationErrorsDoNotTriggerFallback() throws Exception {
        for (int code : new int[]{403, 421, 503, 401, 404, 429}) {
            try (Fixture f = new Fixture()) {
                MockResponse.Builder response = new MockResponse.Builder().code(code).body("respect response");
                if (code == 403 || code == 421 || code == 503) response.addHeader("Retry-After", "60");
                f.preferred.enqueue(response.build());
                try (Response actual = f.client.newCall(request()).execute()) {
                    assertEquals(code, actual.code());
                }
                assertEquals(0, f.original.getRequestCount());
            }
        }
    }

    @Test
    public void onlyAllCloudflareOriginalAndPreferredAddressSetsAreEligible() throws Exception {
        List<List<String>> origins = List.of(List.of(NON_CF_IP), List.of(ORIGINAL_IP, NON_CF_IP));
        for (List<String> addresses : origins) {
            try (Fixture f = new Fixture()) {
                f.dns.addresses.put(ORIGIN, ips(addresses));
                f.original.enqueue(ok("ordinary origin"));
                assertEquals("ordinary origin", body(f.client));
                assertEquals(0, f.dns.count(PREFERRED));
                assertEquals(0, f.preferred.getRequestCount());
            }
        }
        try (Fixture f = new Fixture()) {
            f.dns.addresses.put(PREFERRED, ips(List.of(PREFERRED_IP, NON_CF_IP)));
            f.original.enqueue(ok("mixed preferred is rejected"));
            assertEquals("mixed preferred is rejected", body(f.client));
            assertEquals(0, f.preferred.getRequestCount());
        }
    }

    @Test
    public void nonEligibleDestinationsKeepOriginalConnectTimeoutAndPooledConnection() throws Exception {
        for (String mode : List.of("non-cf-origin", "mixed-preferred", "failed-preferred", "same-address")) {
            try (Fixture f = new Fixture()) {
                if (mode.equals("non-cf-origin")) f.dns.addresses.put(ORIGIN, ips(List.of(NON_CF_IP)));
                if (mode.equals("mixed-preferred")) f.dns.addresses.put(PREFERRED, ips(List.of(PREFERRED_IP, NON_CF_IP)));
                if (mode.equals("failed-preferred")) f.dns.failPreferred = true;
                if (mode.equals("same-address")) f.dns.addresses.put(PREFERRED, ips(List.of(ORIGINAL_IP)));
                f.domain.set("");
                f.original.enqueue(ok("existing original pool"));
                assertEquals("existing original pool", body(f.client));
                RecordedRequest baseline = take(f.original);
                f.domain.set(PREFERRED);
                f.original.enqueue(ok("retained original pool"));
                assertEquals("retained original pool", body(f.client));
                RecordedRequest retained = take(f.original);
                assertEquals(mode, baseline.getConnectionIndex(), retained.getConnectionIndex());
                assertEquals(mode, baseline.getExchangeIndex() + 1, retained.getExchangeIndex());
                assertEquals(mode, List.of(5000), f.sockets.connectTimeouts);
                // Also check a fresh original connection receives the unchanged configured timeout.
                f.client.connectionPool().evictAll();
                f.original.enqueue(ok("fresh original connection"));
                assertEquals("fresh original connection", body(f.client));
                assertEquals(mode, List.of(5000, 5000), f.sockets.connectTimeouts);
            }
        }
    }

    @Test
    public void hostsOverridesDisabledSettingsPostAndCustomPortStayOnOriginalRoute() throws Exception {
        for (String mode : List.of("origin-hosts", "preferred-hosts", "disabled", "post", "port", "ip", "own-dns", "no-retry")) {
            try (Fixture f = new Fixture()) {
                Request request = request();
                OkHttpClient client = f.client;
                switch (mode) {
                    case "origin-hosts": f.overrides.add(ORIGIN); break;
                    case "preferred-hosts": f.overrides.add(PREFERRED); break;
                    case "disabled": f.domain.set(""); break;
                    case "post": request = request.newBuilder().post(RequestBody.create("query", null)).build(); break;
                    case "port": request = request.newBuilder().url("https://" + ORIGIN + ":8443/logo").build(); break;
                    case "ip": request = request.newBuilder().url("https://" + ORIGINAL_IP + "/logo").build(); break;
                    case "own-dns": client = client.newBuilder().dns(host -> f.dns.lookup(host)).build(); break;
                    case "no-retry": client = client.newBuilder().retryOnConnectionFailure(false).build(); break;
                    default: throw new AssertionError(mode);
                }
                f.original.enqueue(ok(mode));
                try (Response response = client.newCall(request).execute()) { assertEquals(mode, response.body().string()); }
                assertEquals(mode, 0, f.dns.count(PREFERRED));
                assertEquals(mode, 0, f.preferred.getRequestCount());
            }
        }
    }

    @Test
    public void plainHttpIsNotRouted() throws Exception {
        try (Fixture f = new Fixture(); MockWebServer cleartext = new MockWebServer()) {
            cleartext.start();
            f.sockets.ports.put(ORIGINAL_IP, cleartext.getPort());
            cleartext.enqueue(ok("plain HTTP"));
            try (Response response = f.client.newCall(request().newBuilder().url("http://" + ORIGIN + "/logo").build()).execute()) {
                assertEquals("plain HTTP", response.body().string());
            }
            assertEquals(0, f.dns.count(PREFERRED));
        }
    }

    @Test
    public void configuredProxySelectorRetainsItsRoutingPolicy() throws Exception {
        try (Fixture f = new Fixture()) {
            // Direct is first, but a proxy is available: our feature must leave the whole policy alone.
            ProxySelector selector = new ProxySelector() {
                public List<Proxy> select(URI uri) {
                    return List.of(Proxy.NO_PROXY, new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 9)));
                }
                public void connectFailed(URI uri, SocketAddress address, IOException error) { }
            };
            OkHttpClient client = f.client.newBuilder().proxy(null).proxySelector(selector).build();
            f.original.enqueue(ok("proxy policy retained"));
            assertEquals("proxy policy retained", body(client));
            assertEquals(0, f.dns.count(PREFERRED));
        }
    }

    @Test
    public void explicitHttpConnectProxyIsUsedWithoutPreferredDns() throws Exception {
        try (Fixture f = new Fixture()) {
            f.original.enqueue(new MockResponse.Builder().inTunnel().build());
            f.original.enqueue(ok("proxy tunnel"));
            OkHttpClient client = f.client.newBuilder().socketFactory(SocketFactory.getDefault())
                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", f.original.getPort()))).build();
            assertEquals("proxy tunnel", body(client));
            assertEquals("CONNECT", take(f.original).getMethod());
            assertEquals(ORIGIN, take(f.original).getHeaders().get(":authority"));
            assertEquals(0, f.dns.count(PREFERRED));
            assertEquals(0, f.preferred.getRequestCount());
        }
    }

    @Test
    public void headAndHttp1UseSameSafeFallbackPolicy() throws Exception {
        try (Fixture f = new Fixture()) {
            OkHttpClient client = f.client.newBuilder().protocols(List.of(Protocol.HTTP_1_1)).build();
            f.preferred.enqueue(new MockResponse.Builder().code(403).build());
            f.original.enqueue(new MockResponse.Builder().code(200).build());
            try (Response response = client.newCall(request().newBuilder().head().build()).execute()) {
                assertEquals(200, response.code());
                assertEquals(Protocol.HTTP_1_1, response.protocol());
            }
            assertEquals("HEAD", take(f.preferred).getMethod());
            RecordedRequest fallback = take(f.original);
            assertEquals("HEAD", fallback.getMethod());
            assertEquals(ORIGIN, fallback.getHeaders().get("Host"));
        }
    }

    @Test
    public void concurrentOriginsShareOnePreferredDnsLookup() throws Exception {
        try (Fixture f = new Fixture()) {
            f.dns.addresses.put(OTHER, ips(List.of(ORIGINAL_IP)));
            f.dns.blockPreferred = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                f.preferred.enqueue(ok("shared lookup"));
                f.preferred.enqueue(ok("shared lookup"));
                Future<String> first = workers.submit(() -> body(f.client));
                Future<String> second = workers.submit(() -> {
                    try (Response response = f.client.newCall(request().newBuilder()
                            .url("https://" + OTHER + "/logo").build()).execute()) {
                        return response.body().string();
                    }
                });
                assertTrue(f.dns.originsStarted.await(2, TimeUnit.SECONDS));
                assertTrue(f.dns.preferredStarted.await(2, TimeUnit.SECONDS));
                f.dns.blockPreferred.countDown();
                assertEquals("shared lookup", first.get(3, TimeUnit.SECONDS));
                assertEquals("shared lookup", second.get(3, TimeUnit.SECONDS));
                assertEquals(1, f.dns.count(PREFERRED));
                assertEquals(2, f.preferred.getRequestCount());
            } finally {
                f.dns.blockPreferred.countDown();
                workers.shutdownNow();
            }
        }
    }

    @Test
    public void completedDnsWorkersAcceptNextLookupBeforeReturningToTheirQueue() throws Exception {
        try (Fixture f = new Fixture()) {
            ThreadPoolExecutor resolver = resolver(f.router);
            CountDownLatch completed = new CountDownLatch(2), release = new CountDownLatch(1);
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                holdCompletedWorkers(resolver, completed, release);
                f.preferred.enqueue(ok("queued preferred lookup"));
                f.original.enqueue(ok("incorrectly rejected optional DNS"));
                Future<String> response = caller.submit(() -> body(f.client));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (!response.isDone() && resolver.getQueue().isEmpty() && System.nanoTime() < deadline)
                    Thread.sleep(1);
                // Future completion wakes its waiter before the worker returns to take().
                // Keep that real executor handoff window open until the next request arrives.
                boolean queued = !resolver.getQueue().isEmpty();
                release.countDown();
                assertEquals("queued preferred lookup", response.get(3, TimeUnit.SECONDS));
                assertTrue("The next lookup must wait briefly instead of being negatively cached", queued);
                assertEquals(1, f.dns.count(PREFERRED)); assertEquals(0, f.original.getRequestCount());
            } finally {
                release.countDown(); caller.shutdownNow();
            }
        }
    }

    @Test
    public void queueWaitCountsTowardsTheExistingDnsTimeout() throws Exception {
        try (Fixture f = new Fixture()) {
            CountDownLatch completed = new CountDownLatch(2), release = new CountDownLatch(1);
            try {
                holdCompletedWorkers(resolver(f.router), completed, release);
                f.original.enqueue(ok("bounded queue wait"));
                long started = System.nanoTime();
                assertEquals("bounded queue wait", body(f.client));
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertTrue("Queue wait must not add another DNS timeout: " + elapsed, elapsed < 4000);
                assertEquals(0, f.dns.count(PREFERRED)); assertEquals(0, f.preferred.getRequestCount());
            } finally { release.countDown(); }
        }
    }

    private static ThreadPoolExecutor resolver(CloudflarePreferredInterceptor router) throws Exception {
        java.lang.reflect.Field field = CloudflarePreferredInterceptor.class.getDeclaredField("resolver");
        field.setAccessible(true); return (ThreadPoolExecutor) field.get(router);
    }

    private static void holdCompletedWorkers(ThreadPoolExecutor resolver, CountDownLatch completed,
                                             CountDownLatch release) throws Exception {
        for (int i = 0; i < 2; i++) {
            FutureTask<Void> task = new FutureTask<>(() -> null) {
                @Override protected void done() {
                    completed.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            };
            resolver.execute(task);
        }
        assertTrue("Both DNS futures complete before their workers are released", completed.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void redirectToNonCloudflareHostUsesThatHostsOwnDnsAndTlsIdentity() throws Exception {
        try (Fixture f = new Fixture()) {
            f.dns.addresses.put(OTHER, ips(List.of(NON_CF_IP)));
            f.preferred.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "https://" + OTHER + "/redirected").build());
            f.original.enqueue(ok("redirect target"));
            assertEquals("redirect target", body(f.client));
            RecordedRequest target = take(f.original);
            assertEquals(OTHER, target.getHeaders().get(":authority"));
            assertEquals(List.of(OTHER), target.getHandshakeServerNames());
            assertNull(target.getHeaders().get("Authorization"));
            assertEquals(List.of(PREFERRED_IP, NON_CF_IP), f.sockets.dialed);
        }
    }

    @Test
    public void preferredDnsFailureIsNegativelyCachedAndExpires() throws Exception {
        try (Fixture f = new Fixture()) {
            f.dns.failPreferred = true;
            f.original.enqueue(ok("first"));
            f.original.enqueue(ok("cached negative"));
            f.original.enqueue(ok("expired negative"));
            assertEquals("first", body(f.client));
            f.client.connectionPool().evictAll();
            assertEquals("cached negative", body(f.client));
            assertEquals(1, f.dns.count(PREFERRED));
            f.clock.addAndGet(10_001);
            f.client.connectionPool().evictAll();
            assertEquals("expired negative", body(f.client));
            assertEquals(2, f.dns.count(PREFERRED));
        }
    }

    @Test
    public void stalledPreferredDnsHasBoundedDelayAndNegativeCache() throws Exception {
        try (Fixture f = new Fixture()) {
            f.dns.blockPreferred = new CountDownLatch(1);
            try {
                f.original.enqueue(ok("bounded"));
                f.original.enqueue(ok("cached"));
                long started = System.nanoTime();
                assertEquals("bounded", body(f.client));
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertTrue("Optional DNS lookup must not stall for full system DNS timeout: " + elapsedMs, elapsedMs < 4000);
                f.client.connectionPool().evictAll();
                assertEquals("cached", body(f.client));
                assertEquals(1, f.dns.count(PREFERRED));
            } finally {
                f.dns.blockPreferred.countDown();
            }
        }
    }

    @Test
    public void cancellationDuringPreferredDnsDoesNotStartAnyConnection() throws Exception {
        try (Fixture f = new Fixture()) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            f.dns.blockPreferred = new CountDownLatch(1);
            try {
                Call call = f.client.newCall(request());
                Future<Boolean> result = worker.submit(() -> {
                    try (Response ignored = call.execute()) { return false; }
                    catch (IOException expected) { return true; }
                });
                assertTrue(f.dns.preferredStarted.await(2, TimeUnit.SECONDS));
                call.cancel();
                assertTrue(result.get(2, TimeUnit.SECONDS));
                assertTrue(f.sockets.dialed.isEmpty());
                assertEquals(0, f.original.getRequestCount());
            } finally {
                f.dns.blockPreferred.countDown();
                worker.shutdownNow();
            }
        }
    }

    @Test
    public void wholeCallTimeoutDoesNotStartOriginalFallback() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preferred.enqueue(new MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).body("late").build());
            OkHttpClient client = f.client.newBuilder().callTimeout(300, TimeUnit.MILLISECONDS).build();
            try (Response ignored = client.newCall(request()).execute()) { fail("call must time out"); }
            catch (InterruptedIOException expected) { }
            assertEquals(0, f.original.getRequestCount());
            assertEquals(List.of(PREFERRED_IP), f.sockets.dialed);
        }
    }

    @Test
    public void cooldownExpiresAndConfigurationGenerationDoesNotReuseOldPreferredPool() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preferred.enqueue(new MockResponse.Builder().code(403).body("bad").build());
            f.original.enqueue(ok("fallback"));
            assertEquals("fallback", body(f.client));
            f.clock.addAndGet(60_001);
            f.preferred.enqueue(ok("recovered"));
            assertEquals("recovered", body(f.client));
            RecordedRequest first = take(f.preferred);
            RecordedRequest recovered = take(f.preferred);
            assertNotEquals(first.getConnectionIndex(), recovered.getConnectionIndex());
            f.router.clear();
            f.preferred.enqueue(ok("new settings generation"));
            assertEquals("new settings generation", body(f.client));
            assertNotEquals(recovered.getConnectionIndex(), take(f.preferred).getConnectionIndex());
            assertEquals(3, f.dns.count(PREFERRED));
        }
    }

    @Test
    public void settingsCleanupClosesIdleTlsSocketOffCallerThread() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preferred.enqueue(ok("idle TLS"));
            assertEquals("idle TLS", body(f.client));
            MappingSocket socket = f.sockets.sockets.getFirst();
            assertFalse(socket.isClosed());
            IdleConnectionEvictor evictor = new IdleConnectionEvictor(f.client.connectionPool()::evictAll);
            f.router.clear();
            evictor.request();
            assertTrue(socket.closed.await(3, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), socket.closingThread);
        }
    }

    @Test
    public void preferredDomainValidationRejectsUrlsCredentialsPortsAndAddressLiterals() {
        assertEquals("best.cf.test", CloudflarePreferredSettings.normalize(" Best.CF.Test. "));
        assertEquals("", CloudflarePreferredSettings.normalize("  "));
        for (String invalid : List.of("https://best.cf.test", "best.cf.test:443", "u:p@best.cf.test", "104.16.1.1", "[2606:4700::1]", "localhost", "bad..test", "bad.test/path")) {
            try { CloudflarePreferredSettings.normalize(invalid); fail(invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }

    private static Request request() {
        return new Request.Builder().url("https://" + ORIGIN + "/logo?language=zh")
                .header("Authorization", "Bearer original-secret").build();
    }

    private static String body(OkHttpClient client) throws IOException {
        try (Response response = client.newCall(request()).execute()) { return response.body().string(); }
    }

    private static MockResponse ok(String body) { return new MockResponse.Builder().body(body).build(); }

    private static RecordedRequest take(MockWebServer server) throws InterruptedException {
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull("Expected real server request", request);
        return request;
    }

    private static void await(CountDownLatch latch) throws IOException {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("Test release timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    private static List<InetAddress> ips(List<String> values) throws UnknownHostException {
        List<InetAddress> result = new ArrayList<>();
        for (String value : values) result.add(InetAddress.getByName(value));
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        final MockWebServer original = new MockWebServer();
        final MockWebServer preferred = new MockWebServer();
        final HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName(ORIGIN)
                .addSubjectAlternativeName(OTHER).addSubjectAlternativeName(ORIGINAL_IP).build();
        final TestDns dns = new TestDns();
        final AtomicReference<String> domain = new AtomicReference<>(PREFERRED);
        final AtomicLong clock = new AtomicLong(1_000_000);
        final Set<String> overrides = ConcurrentHashMap.newKeySet();
        final MappingSocketFactory sockets = new MappingSocketFactory();
        final CloudflarePreferredInterceptor router;
        final OkHttpClient client;

        Fixture() throws Exception {
            HandshakeCertificates tls = new HandshakeCertificates.Builder().heldCertificate(certificate)
                    .addTrustedCertificate(certificate.certificate()).build();
            original.useHttps(tls.sslSocketFactory());
            preferred.useHttps(tls.sslSocketFactory());
            original.start();
            preferred.start();
            sockets.ports.put(ORIGINAL_IP, original.getPort());
            sockets.ports.put(NON_CF_IP, original.getPort());
            sockets.ports.put(PREFERRED_IP, preferred.getPort());
            dns.addresses.put(ORIGIN, ips(List.of(ORIGINAL_IP)));
            dns.addresses.put(PREFERRED, ips(List.of(PREFERRED_IP)));
            router = new CloudflarePreferredInterceptor(dns, domain::get, overrides::contains,
                    CloudflareAddressRanges::contains, clock::get);
            client = new OkHttpClient.Builder().dns(dns).proxy(Proxy.NO_PROXY)
                    .socketFactory(sockets).sslSocketFactory(tls.sslSocketFactory(), tls.trustManager())
                    .connectTimeout(5, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
                    .callTimeout(6, TimeUnit.SECONDS).addInterceptor(router)
                    .addNetworkInterceptor(router.networkInterceptor()).build();
        }

        public void close() {
            router.clear();
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            original.close();
            preferred.close();
        }
    }

    private static final class TestDns implements Dns {
        final Map<String, List<InetAddress>> addresses = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        final CountDownLatch preferredStarted = new CountDownLatch(1);
        final CountDownLatch originsStarted = new CountDownLatch(2);
        volatile boolean failPreferred;
        volatile CountDownLatch blockPreferred;

        public List<InetAddress> lookup(String host) throws UnknownHostException {
            counts.computeIfAbsent(host, ignored -> new AtomicInteger()).incrementAndGet();
            if (host.equals(ORIGIN) || host.equals(OTHER)) originsStarted.countDown();
            if (host.equals(PREFERRED)) {
                preferredStarted.countDown();
                if (failPreferred) throw new UnknownHostException("Deliberate preferred DNS failure");
                if (blockPreferred != null) {
                    try { await(blockPreferred); }
                    catch (IOException e) { throw new UnknownHostException("Preferred resolver interrupted"); }
                }
            }
            List<InetAddress> result = addresses.get(host);
            if (result == null) throw new UnknownHostException("Unconfigured test host: " + host);
            return result;
        }

        int count(String host) { return counts.getOrDefault(host, new AtomicInteger()).get(); }
    }

    /** Only the transport endpoint is mapped. OkHttp still sees real CF routes and origin port 443. */
    private static final class MappingSocketFactory extends SocketFactory {
        final Map<String, Integer> ports = new ConcurrentHashMap<>();
        final List<String> dialed = new CopyOnWriteArrayList<>();
        final List<Integer> connectTimeouts = new CopyOnWriteArrayList<>();
        final List<MappingSocket> sockets = new CopyOnWriteArrayList<>();
        public Socket createSocket() {
            MappingSocket socket = new MappingSocket(this);
            sockets.add(socket);
            return socket;
        }
        public Socket createSocket(String host, int port) throws IOException { return connect(new InetSocketAddress(host, port)); }
        public Socket createSocket(InetAddress host, int port) throws IOException { return connect(new InetSocketAddress(host, port)); }
        public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { throw new UnsupportedOperationException(); }
        public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { throw new UnsupportedOperationException(); }
        private Socket connect(InetSocketAddress address) throws IOException {
            Socket socket = createSocket();
            socket.connect(address);
            return socket;
        }
    }

    private static final class MappingSocket extends Socket {
        final MappingSocketFactory owner;
        final CountDownLatch closed = new CountDownLatch(1);
        volatile Thread closingThread;
        MappingSocket(MappingSocketFactory owner) { this.owner = owner; }
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            InetSocketAddress target = (InetSocketAddress) endpoint;
            String address = target.getAddress().getHostAddress();
            owner.dialed.add(address);
            owner.connectTimeouts.add(timeout);
            Integer port = owner.ports.get(address);
            if (port == null) throw new IOException("No test mapping for " + address);
            super.connect(new InetSocketAddress("127.0.0.1", port), timeout);
        }
        public synchronized void close() throws IOException {
            closingThread = Thread.currentThread();
            super.close();
            closed.countDown();
        }
    }
}
