package com.github.catvod.net.ech;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class EchDnsResolverCloudflareTest {
    private static final String DONOR = "crypto.cloudflare.com";
    private static final byte[] CONFIG = {0, 5, (byte) 0xfe, 0x0d, 0, 1, 42};
    private static final byte[] V4 = {104, 16, 1, 2};
    private static final byte[] V6 = {0x26, 6, 0x47, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

    @Test public void publishedConfigurationWinsWithoutAddressOrDonorQueries() {
        List<String> queries = new ArrayList<>();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            queries.add(question(q));
            assertEquals(65, type(q));
            return ech(q, 60);
        }, () -> 0);
        assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("A.EXAMPLE."));
        assertEquals("ech_config_available", resolver.lastReason());
        assertArrayEquals(CONFIG, resolver.resolve("a.example"));
        assertEquals(List.of("a.example"), queries);
    }

    @Test public void absentHttpsBorrowsThroughSameTransportAndKeepsPublishedCacheSeparate() {
        List<String> queries = new ArrayList<>();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            queries.add(question(q) + ":" + type(q));
            return standard(q, 60, 60, 60);
        }, () -> 0);
        byte[] borrowed = resolver.resolveWithCloudflareFallback("a.example");
        assertArrayEquals(CONFIG, borrowed);
        assertEquals("cloudflare_ech_fallback", resolver.lastReason());
        borrowed[0] = 99;
        assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("A.EXAMPLE."));
        assertNull(resolver.resolve("a.example"));
        assertEquals("no_https_record", resolver.lastReason());
        assertEquals(List.of("a.example:65", "a.example:1", "a.example:28", DONOR + ":65"), queries);
    }

    @Test public void httpsWithoutEchCanBorrowButMalformedOrUnsupportedServiceCannot() {
        EchDnsResolver noEch = new EchDnsResolver((q, t) -> type(q) == 65 && !question(q).equals(DONOR)
                ? response(q, 65, 60, new byte[]{0, 1, 0}) : standard(q, 60, 60, 60), () -> 0);
        assertArrayEquals(CONFIG, noEch.resolveWithCloudflareFallback("a.example"));
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver invalid = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet(); return new byte[]{1, 2};
        }, () -> 0);
        assertNull(invalid.resolveWithCloudflareFallback("a.example"));
        assertEquals("message_size", invalid.lastReason());
        assertEquals(1, calls.get());
        EchDnsResolver unavailable = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet(); return response(q, 65, 60, new byte[]{0, 0, 0});
        }, () -> 0);
        assertNull(unavailable.resolveWithCloudflareFallback("a.example"));
        assertEquals("service_unavailable", unavailable.lastReason());
        assertEquals(2, calls.get());
    }

    @Test public void eitherAddressFamilyAloneMayEstablishEligibility() {
        for (int availableType : new int[]{1, 28}) {
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> type(q) != 65 && type(q) != availableType
                    ? empty(q) : standard(q, 60, 60, 60), () -> 0);
            assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
            assertEquals("cloudflare_ech_fallback", resolver.lastReason());
        }
    }

    @Test public void rejectsMixedNetworksInEitherAddressFamilyBeforeQueryingDonor() {
        for (int mixedType : new int[]{1, 28}) {
            List<String> queries = new ArrayList<>();
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
                queries.add(question(q));
                if (type(q) == mixedType) return response(q, mixedType, 60,
                        mixedType == 1 ? V4 : V6,
                        mixedType == 1 ? new byte[]{8, 8, 8, 8} : new byte[16]);
                return standard(q, 60, 60, 60);
            }, () -> 0);
            assertNull(resolver.resolveWithCloudflareFallback("a.example"));
            assertEquals("cloudflare_non_cf_address", resolver.lastReason());
            assertFalse(queries.contains(DONOR));
        }
    }

    @Test public void noAddressesAndEitherFamilyFailureNeverBorrow() {
        EchDnsResolver empty = new EchDnsResolver((q, t) -> empty(q), () -> 0);
        assertNull(empty.resolveWithCloudflareFallback("a.example"));
        assertEquals("cloudflare_no_addresses", empty.lastReason());
        for (int failedType : new int[]{1, 28}) {
            List<String> queries = new ArrayList<>();
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
                queries.add(question(q));
                if (type(q) == failedType) throw new IOException("private target URL");
                return standard(q, 60, 60, 60);
            }, () -> 0);
            assertNull(resolver.resolveWithCloudflareFallback("a.example"));
            assertEquals("doh_io", resolver.lastReason());
            assertFalse(queries.contains(DONOR));
        }
    }

    @Test public void malformedAndDnsErrorAddressResponsesAreNotEmptySuccesses() {
        for (boolean malformed : new boolean[]{true, false}) {
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
                if (type(q) == 28) {
                    if (malformed) return new byte[]{1, 2};
                    byte[] answer = empty(q); answer[3] = (byte) 0x83; return answer;
                }
                assertNotEquals(DONOR, question(q));
                return standard(q, 60, 60, 60);
            }, () -> 0);
            assertNull(resolver.resolveWithCloudflareFallback("a.example"));
            assertEquals(malformed ? "message_size" : "dns_rcode", resolver.lastReason());
        }
    }

    @Test public void followsAddressCnamesAndHonorsTheirShortTtl() {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet();
            if (type(q) == 1 && question(q).equals("a.example")) return alias(q, "edge.example", 2);
            return standard(q, 100, 100, 100);
        }, now::get);
        assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
        assertEquals(5, calls.get());
        now.set(1999); resolver.resolveWithCloudflareFallback("a.example");
        assertEquals(5, calls.get());
        now.set(2000); assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
        assertEquals(8, calls.get());
    }

    @Test public void addressAliasCyclesNeverReachTheDonor() {
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            assertNotEquals(DONOR, question(q));
            if (type(q) == 65) return empty(q);
            return alias(q, question(q).equals("a.example") ? "b.example" : "a.example", 60);
        }, () -> 0);
        assertNull(resolver.resolveWithCloudflareFallback("a.example"));
        assertEquals("alias_loop", resolver.lastReason());
    }

    @Test public void donorMissingOrMalformedNeverRecursesOrProducesAConfiguration() {
        for (boolean malformed : new boolean[]{true, false}) {
            AtomicInteger donorCalls = new AtomicInteger();
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
                if (question(q).equals(DONOR)) {
                    donorCalls.incrementAndGet(); assertEquals(65, type(q));
                    return malformed ? new byte[]{1, 2} : empty(q);
                }
                return standard(q, 60, 60, 60);
            }, () -> 0);
            assertNull(resolver.resolveWithCloudflareFallback("a.example"));
            assertNull(resolver.resolveWithCloudflareFallback(DONOR));
            assertEquals(1, donorCalls.get());
        }
    }

    @Test public void donorCacheIsReusedAcrossDifferentTargets() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger donorCalls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet();
            if (question(q).equals(DONOR)) donorCalls.incrementAndGet();
            return standard(q, 60, 60, 60);
        }, () -> 0);
        assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
        assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("b.example"));
        assertEquals(7, calls.get()); assertEquals(1, donorCalls.get());
    }

    @Test public void fallbackTtlIsBoundedByNativeAbsenceBothFamiliesAndDonor() {
        for (int shortest : new int[]{0, 1, 2, 3}) {
            AtomicLong now = new AtomicLong();
            AtomicInteger calls = new AtomicInteger();
            int stage = shortest;
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
                calls.incrementAndGet();
                return standard(q, stage == 1 ? 2 : 60, stage == 2 ? 2 : 60, stage == 3 ? 2 : 60);
            }, now::get);
            assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
            long ttl = shortest == 0 ? 10000 : 2000;
            now.set(ttl - 1); resolver.resolveWithCloudflareFallback("a.example");
            assertEquals(4, calls.get());
            now.set(ttl); assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
            assertTrue(calls.get() > 4);
        }
    }

    @Test public void zeroTtlOrUnspecifiedNodataTtlDoesNotCacheBorrowedConfiguration() {
        for (boolean noData : new boolean[]{true, false}) {
            AtomicInteger calls = new AtomicInteger();
            EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
                calls.incrementAndGet();
                return noData && type(q) == 28 ? empty(q) : standard(q, 0, 60, 60);
            }, () -> 0);
            assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
            assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("a.example"));
            assertEquals(6, calls.get());
        }
    }

    @Test public void nativeAddressAndDonorQueriesShareOneSixSecondDeadline() {
        AtomicLong now = new AtomicLong();
        List<Long> budgets = new ArrayList<>();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            budgets.add(t); now.addAndGet(2000);
            assertNotEquals(DONOR, question(q));
            return standard(q, 60, 60, 60);
        }, now::get);
        assertNull(resolver.resolveWithCloudflareFallback("a.example"));
        assertEquals(List.of(6000L, 4000L, 2000L), budgets);
        assertEquals("lookup_timeout", resolver.lastReason());
    }

    @Test public void nativeAddressAndDonorAliasesShareEightWireQueries() {
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet();
            if (type(q) == 1) {
                String name = question(q);
                if (!name.equals("alias4.example")) return alias(q,
                        name.equals("a.example") ? "alias1.example"
                                : "alias" + (Integer.parseInt(name.substring(5, 6)) + 1) + ".example", 60);
            }
            if (question(q).equals(DONOR)) return alias(q, "donor-key.example", 60);
            return standard(q, 60, 60, 60);
        }, () -> 0);
        assertNull(resolver.resolveWithCloudflareFallback("a.example"));
        assertEquals(8, calls.get());
        assertEquals("alias_limit", resolver.lastReason());
    }

    @Test public void fallbackAndPublishedRequestsShareFourInflightSlotsWithoutNestedDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch entered = new CountDownLatch(4), release = new CountDownLatch(1);
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            if (type(q) == 65 && !question(q).equals(DONOR)) {
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            return standard(q, 60, 60, 60);
        }, () -> 0);
        try {
            List<Future<byte[]>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int n = i;
                results.add(pool.submit(() -> resolver.resolveWithCloudflareFallback("host" + n + ".example")));
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertNull(resolver.resolve("extra.example"));
            assertEquals("lookup_busy", resolver.lastReason());
            release.countDown();
            for (Future<byte[]> result : results) assertArrayEquals(CONFIG, result.get(2, TimeUnit.SECONDS));
            assertArrayEquals(CONFIG, resolver.resolveWithCloudflareFallback("extra.example"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test public void invalidTargetNeverMakesAnyQuery() {
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            throw new AssertionError("invalid target must not be queried");
        }, () -> 0);
        for (String host : new String[]{null, "", "127.0.0.1", "::1", "a..example"})
            assertNull(resolver.resolveWithCloudflareFallback(host));
    }

    private static byte[] standard(byte[] query, long aTtl, long aaaaTtl, long donorTtl) throws IOException {
        if (type(query) == 1) return response(query, 1, aTtl, V4);
        if (type(query) == 28) return response(query, 28, aaaaTtl, V6);
        return question(query).equals(DONOR) ? ech(query, donorTtl) : empty(query);
    }

    private static byte[] ech(byte[] query, long ttl) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeShort(1); data.writeByte(0);
        data.writeShort(5); data.writeShort(CONFIG.length); data.write(CONFIG);
        return response(query, 65, ttl, bytes.toByteArray());
    }

    private static byte[] alias(byte[] query, String target, long ttl) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (String label : target.split("\\.")) {
            bytes.write(label.length()); bytes.write(label.getBytes(StandardCharsets.US_ASCII));
        }
        bytes.write(0);
        return response(query, 5, ttl, bytes.toByteArray());
    }

    private static byte[] empty(byte[] query) throws IOException {
        return response(query, type(query), 0);
    }

    private static byte[] response(byte[] query, int type, long ttl, byte[]... payloads) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.write(query, 0, 2); data.writeShort(0x8180);
        data.writeShort(1); data.writeShort(payloads.length); data.writeShort(0); data.writeShort(0);
        data.write(query, 12, query.length - 12);
        for (byte[] payload : payloads) {
            data.writeShort(0xc00c); data.writeShort(type); data.writeShort(1);
            data.writeInt((int) ttl); data.writeShort(payload.length); data.write(payload);
        }
        return bytes.toByteArray();
    }

    private static int type(byte[] query) {
        return (query[query.length - 4] & 255) * 256 + (query[query.length - 3] & 255);
    }

    private static String question(byte[] query) {
        StringBuilder name = new StringBuilder();
        int offset = 12;
        while (query[offset] != 0) {
            int size = query[offset++] & 255;
            if (name.length() > 0) name.append('.');
            name.append(new String(query, offset, size, StandardCharsets.US_ASCII));
            offset += size;
        }
        return name.toString();
    }
}
