package com.github.catvod.net.ech;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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

public class EchDnsResolverTest {
    private static final byte[] CONFIG = {0, 5, (byte) 0xfe, 0x0d, 0, 1, 42};

    @Test public void cacheUsesCanonicalHostAndCopiesReturnedConfig() {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((query, timeout) -> {
            calls.incrementAndGet();
            return answer(query, 60);
        }, now::get);
        byte[] first = resolver.resolve("EXAMPLE.com.");
        assertArrayEquals(CONFIG, first);
        first[0] = 99;
        assertArrayEquals(CONFIG, resolver.resolve("example.com"));
        assertEquals(1, calls.get());
        assertEquals("ech_config_available", resolver.lastReason());
    }

    @Test public void honorsShortTtlAndCapsLongTtlAtFiveMinutes() {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            int count = calls.incrementAndGet();
            return answer(q, count == 1 ? 2 : 86400);
        }, now::get);
        resolver.resolve("a.example");
        now.set(1999); resolver.resolve("a.example");
        assertEquals(1, calls.get());
        now.set(2000); resolver.resolve("a.example");
        assertEquals(2, calls.get());
        now.set(301999); resolver.resolve("a.example");
        assertEquals(2, calls.get());
        now.set(302000); resolver.resolve("a.example");
        assertEquals(3, calls.get());
    }

    @Test public void zeroTtlIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet(); return answer(q, 0);
        }, () -> 0);
        assertArrayEquals(CONFIG, resolver.resolve("a.example"));
        assertArrayEquals(CONFIG, resolver.resolve("a.example"));
        assertEquals(2, calls.get());
    }

    @Test public void negativeCacheExpiresQuicklyAndDiagnosticsAreSanitized() {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet(); throw new IOException("private-url-secret");
        }, now::get);
        assertNull(resolver.resolve("a.example"));
        assertEquals("doh_io", resolver.lastReason());
        now.set(9999); assertNull(resolver.resolve("a.example"));
        assertEquals(1, calls.get());
        now.set(10000); assertNull(resolver.resolve("a.example"));
        assertEquals(2, calls.get());
    }

    @Test public void malformedDnsIsAnOpportunisticMiss() {
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> new byte[]{1, 2}, () -> 0);
        assertNull(resolver.resolve("a.example"));
        assertEquals("message_size", resolver.lastReason());
    }

    @Test public void aliasesUseTheMinimumRemainingTtl() {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet();
            if (question(q).equals("a.example")) return alias(q, "b.example", 3);
            now.addAndGet(1000);
            return answer(q, 100);
        }, now::get);
        assertArrayEquals(CONFIG, resolver.resolve("a.example"));
        now.set(2999); resolver.resolve("a.example");
        assertEquals(2, calls.get());
        now.set(3000); resolver.resolve("a.example");
        assertEquals(4, calls.get());
    }

    @Test public void aliasCycleAndQueryBudgetAreBounded() {
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver cycle = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet();
            return alias(q, question(q).equals("a.example") ? "b.example" : "a.example", 30);
        }, () -> 0);
        assertNull(cycle.resolve("a.example"));
        assertEquals("alias_loop", cycle.lastReason());
        assertEquals(2, calls.get());
        calls.set(0);
        EchDnsResolver many = new EchDnsResolver((q, t) -> alias(q,
                "alias" + calls.incrementAndGet() + ".example", 30), () -> 0);
        assertNull(many.resolve("a.example"));
        assertEquals("alias_limit", many.lastReason());
        assertEquals(8, calls.get());
    }

    @Test public void aliasesShareOneSixSecondDeadline() {
        AtomicLong now = new AtomicLong();
        List<Long> budgets = new ArrayList<>();
        EchDnsResolver resolver = new EchDnsResolver((q, timeout) -> {
            budgets.add(timeout);
            now.addAndGet(4000);
            return alias(q, "next" + budgets.size() + ".example", 30);
        }, now::get);
        assertNull(resolver.resolve("a.example"));
        assertEquals(List.of(6000L, 2000L), budgets);
        assertEquals("lookup_timeout", resolver.lastReason());
    }

    @Test public void separateResolverConfigurationsNeverShareCache() {
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        EchDnsResolver a = new EchDnsResolver((q, t) -> {
            first.incrementAndGet(); return answer(q, 60);
        }, () -> 0);
        EchDnsResolver b = new EchDnsResolver((q, t) -> {
            second.incrementAndGet(); return answer(q, 60);
        }, () -> 0);
        a.resolve("a.example"); b.resolve("a.example");
        assertEquals(1, first.get()); assertEquals(1, second.get());
    }

    @Test public void cacheEvictsOldestOfMoreThan128Hosts() {
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet(); return answer(q, 60);
        }, () -> 0);
        for (int i = 0; i < 129; i++) resolver.resolve("host" + i + ".example");
        resolver.resolve("host128.example");
        assertEquals(129, calls.get());
        resolver.resolve("host0.example");
        assertEquals(130, calls.get());
    }

    @Test public void concurrentSameHostSharesOneLookup() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            calls.incrementAndGet(); entered.countDown(); await(release); return answer(q, 60);
        }, () -> 0);
        try {
            List<Future<byte[]>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> resolver.resolve("a.example")));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            for (int i = 0; i < 5; i++) futures.add(pool.submit(() -> resolver.resolve("a.example")));
            release.countDown();
            for (Future<byte[]> future : futures) assertArrayEquals(CONFIG, future.get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test public void concurrentDistinctLookupsAreLimitedAndCapacityReturns() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch entered = new CountDownLatch(4), release = new CountDownLatch(1);
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            entered.countDown(); await(release); return answer(q, 60);
        }, () -> 0);
        try {
            List<Future<byte[]>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int n = i;
                futures.add(pool.submit(() -> resolver.resolve("host" + n + ".example")));
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertNull(resolver.resolve("extra.example"));
            assertEquals("lookup_busy", resolver.lastReason());
            release.countDown();
            for (Future<byte[]> future : futures) assertArrayEquals(CONFIG, future.get(2, TimeUnit.SECONDS));
            assertArrayEquals(CONFIG, resolver.resolve("extra.example"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test public void invalidHostsAndNonDefaultPortsNeverQuery() {
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            throw new AssertionError("must not query");
        }, () -> 0);
        for (String host : new String[]{null, "", "a..example", "127.0.0.1", "::1", "a/example"})
            assertNull(resolver.resolve(host));
        assertNull(resolver.resolve("a.example", 8443));
        assertEquals("unsupported_port", resolver.lastReason());
    }

    @Test public void fatalTransportErrorDoesNotLeakAnInflightSlot() {
        AtomicInteger calls = new AtomicInteger();
        EchDnsResolver resolver = new EchDnsResolver((q, t) -> {
            if (calls.getAndIncrement() == 0) throw new AssertionError("fixture failure");
            return answer(q, 30);
        }, () -> 0);
        try { resolver.resolve("a.example"); fail(); } catch (AssertionError expected) { }
        assertArrayEquals(CONFIG, resolver.resolve("a.example"));
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    private static byte[] answer(byte[] query, long ttl) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeShort(1); data.writeByte(0);
        data.writeShort(5); data.writeShort(CONFIG.length); data.write(CONFIG);
        return response(query, 65, ttl, bytes.toByteArray());
    }

    private static byte[] alias(byte[] query, String target, long ttl) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (String label : target.split("\\.")) {
            bytes.write(label.length()); bytes.write(label.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        bytes.write(0);
        return response(query, 5, ttl, bytes.toByteArray());
    }

    private static byte[] response(byte[] query, int type, long ttl, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.write(query, 0, 2); data.writeShort(0x8180);
        data.writeShort(1); data.writeShort(1); data.writeShort(0); data.writeShort(0);
        data.write(query, 12, query.length - 12);
        data.writeShort(0xc00c); data.writeShort(type); data.writeShort(1);
        data.writeInt((int) ttl); data.writeShort(payload.length); data.write(payload);
        return bytes.toByteArray();
    }

    private static String question(byte[] query) {
        StringBuilder result = new StringBuilder();
        int offset = 12;
        while (query[offset] != 0) {
            int size = query[offset++] & 255;
            if (result.length() > 0) result.append('.');
            result.append(new String(query, offset, size, java.nio.charset.StandardCharsets.US_ASCII));
            offset += size;
        }
        return result.toString();
    }
}
