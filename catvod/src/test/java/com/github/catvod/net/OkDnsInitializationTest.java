package com.github.catvod.net;

import com.github.catvod.bean.Doh;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class OkDnsInitializationTest {
    @Test public void lazySelectionDoesNotCancelTheWorkerThatFirstUsesIt() throws Exception {
        AtomicBoolean workerRunning = new AtomicBoolean();
        AtomicInteger invalidations = new AtomicInteger();
        OkDns dns = new OkDns(() -> {
            invalidations.incrementAndGet();
            // CF preferred routing cancels its running DNS Future during invalidation.
            if (workerRunning.get()) Thread.currentThread().interrupt();
        });
        AtomicInteger selections = new AtomicInteger();
        dns.setDoh(() -> { selections.incrementAndGet(); return choice(""); });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> interrupted = executor.submit(() -> {
                workerRunning.set(true);
                try {
                    assertFalse(dns.lookup("localhost").isEmpty());
                    return Thread.currentThread().isInterrupted();
                } finally { workerRunning.set(false); Thread.interrupted(); }
            });
            assertFalse("Lazy DNS selection must not interrupt its requesting worker", interrupted.get(3, TimeUnit.SECONDS));
            dns.getDoh();
            assertEquals(1, selections.get());
            assertEquals(1, invalidations.get());
        } finally { executor.shutdownNow(); }
    }

    @Test public void explicitChangesStillInvalidateAndReplacePendingSelection() {
        AtomicInteger invalidations = new AtomicInteger();
        AtomicInteger staleSelections = new AtomicInteger();
        OkDns dns = new OkDns(invalidations::incrementAndGet);
        dns.setDoh(() -> { staleSelections.incrementAndGet(); return choice(""); });
        dns.setDoh(choice("https://dns.example.test/dns-query"));
        assertTrue(dns.getDoh().toString().contains("https://dns.example.test/dns-query"));
        assertEquals(0, staleSelections.get());
        assertEquals(2, invalidations.get());
        dns.clear();
        assertEquals(3, invalidations.get());
    }
    // Keep this JVM lifecycle regression independent of Android's TextUtils convenience getter.
    private static Doh choice(String url) {
        return new Doh() {
            @Override public String getUrl() { return url; }
            @Override public String toString() { return "{\"url\":\"" + url + "\"}"; }
        };
    }
}
