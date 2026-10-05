package com.github.catvod.net.ech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.conscrypt.DomainEncryptionMode;
import org.conscrypt.NetworkSecurityPolicy;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public class ConscryptEchSocketFactoryTest {

    private final RecordingFactory legacy = new RecordingFactory();
    private final TestConfigProvider config = new TestConfigProvider();
    private final ConscryptEchSocketFactory factory = new ConscryptEchSocketFactory(
            legacy, new RejectingTrustManager(), config);

    @Test
    public void disabledPreservesEveryOverloadAndCipherListWithoutResolving() throws Exception {
        config.enabled = false;
        config.failure = new IOException("Resolver must not be called while disabled");
        assertSame(legacy.defaultCiphers, factory.getDefaultCipherSuites());
        assertSame(legacy.supportedCiphers, factory.getSupportedCipherSuites());
        assertHostnameOverloads(443);
        assertHostlessOverloads();
        assertEquals(7, legacy.calls);
        assertTrue(config.hostnames.isEmpty());
    }

    @Test
    public void noConfigurationPreservesOriginalSocketAndAllHostnameArguments() throws Exception {
        config.enabled = true;
        config.value = null;
        assertHostnameOverloads(443);
        assertEquals(List.of("ech.example", "ech.example", "ech.example"), config.hostnames);
        assertEquals(3, legacy.calls);
    }

    @Test
    public void nonDefaultPortDoesNotUseDefaultServiceEchRecords() throws Exception {
        config.enabled = true;
        config.failure = new IOException("Port-specific service has not been queried");
        assertHostnameOverloads(8443);
        assertTrue(config.hostnames.isEmpty());
        assertEquals(3, legacy.calls);
    }

    @Test
    public void hostlessOverloadsDoNotResolveOrInventAHostname() throws Exception {
        config.enabled = true;
        config.failure = new IOException("Do not resolve an IP or reverse-resolve a socket");
        assertHostlessOverloads();
        assertTrue(config.hostnames.isEmpty());
        assertEquals(4, legacy.calls);
    }

    @Test
    public void resolverFailureDoesNotSilentlyCreateALegacySocket() {
        config.enabled = true;
        config.failure = new IOException("Configuration lookup failed");
        IOException error = assertThrows(IOException.class,
                () -> factory.createSocket(new Socket(), "ech.example", 443, true));
        assertSame(config.failure, error);
        assertEquals(0, legacy.calls);
    }

    @Test
    public void emptyConfigurationFailsAsTlsErrorInsteadOfDowngrading() {
        config.enabled = true;
        config.value = new byte[0];
        assertThrows(SSLException.class,
                () -> factory.createSocket(new Socket(), "ech.example", 443, true));
        assertEquals(List.of("ech.example"), config.hostnames);
        assertEquals(0, legacy.calls);
    }

    @Test
    public void originalFactoryFailureIsNotWrappedOrRetried() {
        legacy.failure = new IOException("Original TLS factory failure");
        IOException error = assertThrows(IOException.class,
                () -> factory.createSocket(new Socket(), "ech.example", 443, false));
        assertSame(legacy.failure, error);
        assertEquals(1, legacy.calls);
    }

    @Test
    public void policyIsPubliclyDiscoverableWithoutChangingBasicTrustDecisions() throws Exception {
        RejectingTrustManager original = new RejectingTrustManager();
        ConscryptEchSocketFactory.PolicyTrustManager wrapper =
                new ConscryptEchSocketFactory.PolicyTrustManager(original);
        Object policy = wrapper.getClass().getMethod("getNetworkSecurityPolicy").invoke(wrapper);
        assertTrue(policy instanceof NetworkSecurityPolicy);
        assertEquals(DomainEncryptionMode.ENABLED,
                ((NetworkSecurityPolicy) policy).getDomainEncryptionMode("ech.example"));
        assertSame(original.issuers, wrapper.getAcceptedIssuers());
        X509Certificate[] chain = new X509Certificate[0];
        assertCertificateFailure(original.failure, () -> wrapper.checkServerTrusted(chain, "RSA"));
        assertCertificateFailure(original.failure, () -> wrapper.checkClientTrusted(chain, "RSA"));
        assertCertificateFailure(original.failure,
                () -> wrapper.checkServerTrusted(chain, "RSA", new Socket()));
        assertCertificateFailure(original.failure,
                () -> wrapper.checkClientTrusted(chain, "RSA", (SSLEngine) null));
    }

    @Test
    public void extendedTrustManagerReceivesOriginalSocketAndEngine() throws Exception {
        RecordingExtendedTrustManager original = new RecordingExtendedTrustManager();
        ConscryptEchSocketFactory.PolicyTrustManager wrapper =
                new ConscryptEchSocketFactory.PolicyTrustManager(original);
        X509Certificate[] chain = new X509Certificate[0];
        Socket socket = new Socket();
        SSLEngine engine = SSLContext.getDefault().createSSLEngine("ech.example", 443);

        assertCertificateFailure(original.failure,
                () -> wrapper.checkServerTrusted(chain, "RSA", socket));
        original.assertCall("serverSocket", chain, "RSA", socket);
        assertCertificateFailure(original.failure,
                () -> wrapper.checkClientTrusted(chain, "EC", socket));
        original.assertCall("clientSocket", chain, "EC", socket);
        assertCertificateFailure(original.failure,
                () -> wrapper.checkServerTrusted(chain, "EC", engine));
        original.assertCall("serverEngine", chain, "EC", engine);
        assertCertificateFailure(original.failure,
                () -> wrapper.checkClientTrusted(chain, "RSA", engine));
        original.assertCall("clientEngine", chain, "RSA", engine);
    }

    @Test
    public void legacyContextAwareTrustFailureIsUnwrappedWithoutBasicFallback() {
        LegacyContextTrustManager original = new LegacyContextTrustManager();
        ConscryptEchSocketFactory.PolicyTrustManager wrapper =
                new ConscryptEchSocketFactory.PolicyTrustManager(original);
        Socket socket = new Socket();
        assertCertificateFailure(original.failure,
                () -> wrapper.checkServerTrusted(new X509Certificate[0], "RSA", socket));
        assertSame(socket, original.socket);
        assertEquals(0, original.basicCalls);
    }

    private void assertHostnameOverloads(int port) throws Exception {
        InetAddress local = InetAddress.getLoopbackAddress();
        Socket transport = new Socket();
        assertSame(legacy.result, factory.createSocket(transport, "ech.example", port, false));
        legacy.assertCall("layered", transport, "ech.example", port, false);
        assertSame(legacy.result, factory.createSocket("ech.example", port));
        legacy.assertCall("hostname", "ech.example", port);
        assertSame(legacy.result, factory.createSocket("ech.example", port, local, 41234));
        legacy.assertCall("hostnameLocal", "ech.example", port, local, 41234);
    }

    private void assertHostlessOverloads() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        Socket transport = new Socket();
        InputStream consumed = new ByteArrayInputStream(new byte[]{1, 2, 3});
        assertSame(legacy.result, factory.createSocket());
        legacy.assertCall("unconnected");
        assertSame(legacy.result, factory.createSocket(address, 443));
        legacy.assertCall("address", address, 443);
        assertSame(legacy.result, factory.createSocket(address, 443, address, 41234));
        legacy.assertCall("addressLocal", address, 443, address, 41234);
        assertSame(legacy.result, factory.createSocket(transport, consumed, true));
        legacy.assertCall("consumed", transport, consumed, true);
    }

    private static void assertCertificateFailure(CertificateException expected, ThrowingRunnable call) {
        assertSame(expected, assertThrows(CertificateException.class, call));
    }

    private static final class TestConfigProvider implements ConscryptEchSocketFactory.ConfigProvider {
        boolean enabled;
        byte[] value;
        IOException failure;
        final List<String> hostnames = new ArrayList<>();

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public byte[] resolve(String hostname) throws IOException {
            hostnames.add(hostname);
            if (failure != null) throw failure;
            return value;
        }
    }

    private static final class RecordingFactory extends SSLSocketFactory {
        final Socket result = new Socket();
        final String[] defaultCiphers = {"legacy-default"};
        final String[] supportedCiphers = {"legacy-supported"};
        int calls;
        String method;
        Object[] arguments;
        IOException failure;

        @Override public String[] getDefaultCipherSuites() { return defaultCiphers; }
        @Override public String[] getSupportedCipherSuites() { return supportedCiphers; }
        @Override public Socket createSocket() throws IOException { return record("unconnected"); }
        @Override public Socket createSocket(Socket s, String h, int p, boolean close) throws IOException { return record("layered", s, h, p, close); }
        @Override public Socket createSocket(String h, int p) throws IOException { return record("hostname", h, p); }
        @Override public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException { return record("hostnameLocal", h, p, l, lp); }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException { return record("address", h, p); }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException { return record("addressLocal", h, p, l, lp); }
        @Override public Socket createSocket(Socket s, InputStream in, boolean close) throws IOException { return record("consumed", s, in, close); }

        private Socket record(String name, Object... args) throws IOException {
            calls++;
            method = name;
            arguments = args;
            if (failure != null) throw failure;
            return result;
        }

        void assertCall(String expected, Object... args) {
            assertEquals(expected, method);
            assertArrayEquals(args, arguments);
        }
    }

    public static class RejectingTrustManager implements X509TrustManager {
        final X509Certificate[] issuers = new X509Certificate[0];
        final CertificateException failure = new CertificateException("Original trust rejection");
        int basicCalls;

        @Override public X509Certificate[] getAcceptedIssuers() { return issuers; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { basicCalls++; throw failure; }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { basicCalls++; throw failure; }
    }

    public static final class LegacyContextTrustManager extends RejectingTrustManager {
        Socket socket;

        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            this.socket = socket;
            throw failure;
        }
    }

    private static final class RecordingExtendedTrustManager extends X509ExtendedTrustManager {
        final CertificateException failure = new CertificateException("Original extended rejection");
        String method;
        Object[] arguments;

        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] c, String a) { throw new AssertionError("Lost client context"); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a) { throw new AssertionError("Lost server context"); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { record("clientSocket", c, a, s); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { record("serverSocket", c, a, s); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { record("clientEngine", c, a, e); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { record("serverEngine", c, a, e); }

        void record(String name, Object... args) throws CertificateException {
            method = name;
            arguments = args;
            throw failure;
        }

        void assertCall(String expected, Object... args) {
            assertEquals(expected, method);
            assertArrayEquals(args, arguments);
        }
    }
}
