package com.github.catvod.net;

import org.junit.Test;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.SocketFactory;
import mockwebserver3.*;
import okhttp3.*;
import okhttp3.tls.*;
import static org.junit.Assert.*;

/** Real TLS, hostname checks and dial attempts; all remote addresses map to local fixtures. */
public class CloudflareRouteFallbackTest {
    private static final String HOST = "metadata.test";
    private static final String ORIGIN = "172.64.229.10";
    private static final String EDGE = "104.16.0.1";

    @Test public void tcpFailureRecoversWithoutChangingHostSniPathOrCredentialsAndRemembersRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.edge.enqueue(ok()); f.edge.enqueue(ok());
            assertEquals(200, f.call());
            int initial = Collections.frequency(f.sockets.dials, ORIGIN);
            assertEquals(200, f.call());
            assertEquals(initial, Collections.frequency(f.sockets.dials, ORIGIN));
            RecordedRequest request = f.edge.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/3/discover/movie?api_key=test-key", request.getTarget());
            assertEquals(HOST, request.getHeaders().get(":authority"));
            assertEquals(List.of(HOST), request.getHandshakeServerNames());
            assertEquals("test-token", request.getHeaders().get("Authorization"));
            assertEquals(0, f.origin.getRequestCount());
            RecordedRequest second = f.edge.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(second);
            assertEquals(request.getConnectionIndex(), second.getConnectionIndex());
        }
    }

    @Test public void rememberedFailureReturnsToOriginalAndClearsRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.edge.enqueue(ok()); assertEquals(200, f.call());
            f.client.connectionPool().evictAll();
            f.sockets.ports.remove(EDGE);
            f.sockets.ports.put(ORIGIN, f.origin.getPort());
            f.origin.enqueue(ok()); assertEquals(200, f.call());
            f.origin.enqueue(ok()); assertEquals(200, f.call());
            assertEquals(2, f.origin.getRequestCount());
        }
    }

    @Test public void disabledOverridesNonCloudflareMixedAddressesAndOtherHostsNeverReroute() throws Exception {
        for (String mode : List.of("disabled", "override", "non-cf", "mixed", "other-host")) {
            try (Fixture f = new Fixture()) {
                if (mode.equals("disabled")) f.enabled.set(false);
                if (mode.equals("override")) f.override.set(true);
                if (mode.equals("non-cf")) f.dns = ips("192.0.2.1");
                if (mode.equals("mixed")) f.dns = ips(ORIGIN, "192.0.2.1");
                try (Response ignored = f.client.newCall(f.request(mode.equals("other-host") ? "other.test" : HOST)).execute()) { fail(mode); }
                catch (IOException expected) { }
                assertFalse(mode, f.sockets.dials.contains(EDGE));
                assertEquals(0, f.edge.getRequestCount());
            }
        }
    }

    @Test public void rejectedCertificateNeverFallsThroughToAnotherRoute() throws Exception {
        try (Fixture f = new Fixture(); MockWebServer wrong = new MockWebServer()) {
            HeldCertificate cert = new HeldCertificate.Builder().addSubjectAlternativeName("wrong.test").build();
            HandshakeCertificates server = new HandshakeCertificates.Builder().heldCertificate(cert).build();
            HandshakeCertificates trust = new HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate()).build();
            wrong.useHttps(server.sslSocketFactory()); wrong.start();
            f.sockets.ports.put(ORIGIN, wrong.getPort());
            OkHttpClient client = f.client.newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager()).build();
            try (Response ignored = client.newCall(f.request(HOST)).execute()) { fail(); }
            catch (javax.net.ssl.SSLPeerUnverifiedException expected) { }
            assertFalse(f.sockets.dials.contains(EDGE));
            assertEquals(0, wrong.getRequestCount());
        }
    }

    @Test public void cancellationDoesNotStartAnEdgeAttempt() throws Exception {
        try (Fixture f = new Fixture()) {
            Call call = f.client.newCall(f.request(HOST));
            call.cancel();
            try (Response ignored = call.execute()) { fail(); } catch (IOException expected) { }
            assertTrue(f.sockets.dials.isEmpty());
        }
    }

    @Test public void httpThrottleIsReturnedOnceAndDisablingClearsRememberedRoute() throws Exception {
        try (Fixture f = new Fixture()) {
            f.edge.enqueue(ok()); assertEquals(200, f.call());
            f.edge.enqueue(new MockResponse.Builder().code(429).addHeader("Retry-After", "60").build());
            assertEquals(429, f.call());
            f.enabled.set(false); f.router.clear();
            f.sockets.ports.put(ORIGIN, f.origin.getPort());
            f.origin.enqueue(ok()); assertEquals(200, f.call());
            assertEquals(2, f.edge.getRequestCount());
            assertEquals(1, f.origin.getRequestCount());
        }
    }

    @Test public void strictMetadataRedirectPolicyNeverDowngradesHttpsToPlaintext() throws Exception {
        try (Fixture f = new Fixture(); MockWebServer plaintext = new MockWebServer()) {
            plaintext.start();
            f.sockets.ports.put(ORIGIN, f.origin.getPort());
            f.origin.enqueue(new MockResponse.Builder().code(302)
                    .addHeader("Location", plaintext.url("/3/configuration?api_key=test-key")).build());
            OkHttpClient.Builder builder = f.client.newBuilder()
                    .followRedirects(false).followSslRedirects(false);
            // Exercise the same custom redirect interceptor installed by OkHttp.trustedClient().
            builder.interceptors().add(0, new ProxyRedirectInterceptor(null));
            try (Response response = builder.build().newCall(f.request(HOST)).execute()) {
                assertEquals(302, response.code());
                assertTrue(response.request().url().isHttps());
            }
            assertEquals(1, f.origin.getRequestCount());
            assertEquals(0, plaintext.getRequestCount());
            assertFalse(f.sockets.dials.contains(EDGE));
        }
    }

    private static MockResponse ok() { return new MockResponse.Builder().body("{}").build(); }
    private static List<InetAddress> ips(String... values) throws UnknownHostException {
        List<InetAddress> result = new ArrayList<>();
        for (String value : values) result.add(InetAddress.getByName(value));
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        final MockWebServer origin = new MockWebServer(), edge = new MockWebServer();
        final AtomicBoolean enabled = new AtomicBoolean(true), override = new AtomicBoolean(false);
        final MappedSockets sockets = new MappedSockets();
        final CloudflareRouteFallback router;
        final OkHttpClient client;
        volatile List<InetAddress> dns = ips(ORIGIN);
        Fixture() throws Exception {
            HeldCertificate cert = new HeldCertificate.Builder().addSubjectAlternativeName(HOST).build();
            HandshakeCertificates tls = new HandshakeCertificates.Builder().heldCertificate(cert).addTrustedCertificate(cert.certificate()).build();
            origin.useHttps(tls.sslSocketFactory()); edge.useHttps(tls.sslSocketFactory()); origin.start(); edge.start();
            sockets.ports.put(EDGE, edge.getPort());
            router = new CloudflareRouteFallback(enabled::get, u -> HOST.equals(u.host()), h -> override.get(),
                    com.github.catvod.net.ech.CloudflareAddressRanges::contains, ips(EDGE));
            client = new OkHttpClient.Builder().dns(h -> dns).proxy(Proxy.NO_PROXY).socketFactory(sockets)
                    .sslSocketFactory(tls.sslSocketFactory(), tls.trustManager()).addInterceptor(router)
                    .callTimeout(5, TimeUnit.SECONDS).build();
        }
        Request request(String host) { return new Request.Builder().url("https://" + host + "/3/discover/movie?api_key=test-key").header("Authorization", "test-token").build(); }
        int call() throws IOException { try (Response r = client.newCall(request(HOST)).execute()) { r.body().string(); return r.code(); } }
        public void close() { router.clear(); client.dispatcher().cancelAll(); client.connectionPool().evictAll(); origin.close(); edge.close(); }
    }

    private static final class MappedSockets extends SocketFactory {
        final Map<String, Integer> ports = new ConcurrentHashMap<>();
        final List<String> dials = new CopyOnWriteArrayList<>();
        public Socket createSocket() {
            return new Socket() {
                @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    String ip = ((InetSocketAddress) endpoint).getAddress().getHostAddress();
                    dials.add(ip);
                    Integer port = ports.get(ip);
                    if (port == null) throw new ConnectException("Fixture route unreachable");
                    super.connect(new InetSocketAddress("127.0.0.1", port), timeout);
                }
            };
        }
        public Socket createSocket(String host, int port) throws IOException { throw new UnsupportedOperationException(); }
        public Socket createSocket(InetAddress host, int port) throws IOException { throw new UnsupportedOperationException(); }
        public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { throw new UnsupportedOperationException(); }
        public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { throw new UnsupportedOperationException(); }
    }
}
