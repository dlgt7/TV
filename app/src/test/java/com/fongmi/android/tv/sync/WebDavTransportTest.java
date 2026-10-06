package com.fongmi.android.tv.sync;

import static org.junit.Assert.*;
import static com.fongmi.android.tv.sync.SyncMergerTest.*;

import org.junit.Test;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class WebDavTransportTest {
    private WebDavTransport transport(MockWebServer server) {
        return new WebDavTransport(new OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build(), server.url("/tv-sync.json").toString(), "fixture-user", "fixture-password");
    }
    @Test public void firstWriteUsesIfNoneMatchAndDoesNotInterpretMissingFileAsDeletion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404)); server.enqueue(new MockResponse().setResponseCode(201));
            WebDavTransport transport = transport(server); SyncCoordinator.Fetched fetched = transport.get();
            assertFalse(fetched.exists); assertTrue(transport.put(new SyncDocument(), fetched));
            assertEquals("GET", server.takeRequest().getMethod()); RecordedRequest put = server.takeRequest();
            assertEquals("PUT", put.getMethod()); assertEquals("*", put.getHeader("If-None-Match")); assertNull(put.getHeader("If-Match"));
            assertEquals(Credentials.basic("fixture-user", "fixture-password"), put.getHeader("Authorization"));
            assertTrue(SyncDocument.decode(put.getBody().readUtf8()).entries.isEmpty());
        }
    }
    @Test public void actual412RefetchesEtagAndPreservesBothDevicesRecords() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            SyncDocument.Entry a = history(scope(), "ours", 1, 1, "a"), b = history(scope(), "theirs", 2, 2, "b");
            server.enqueue(new MockResponse().setHeader("ETag", "\"v1\"").setBody(new SyncDocument().encode()));
            server.enqueue(new MockResponse().setResponseCode(412));
            server.enqueue(new MockResponse().setHeader("ETag", "\"v2\"").setBody(document(b).encode()));
            server.enqueue(new MockResponse().setResponseCode(204));
            SyncCoordinatorTest.Store store = new SyncCoordinatorTest.Store(); store.entries.put(a.id(), a);
            SyncCoordinator.sync(store, new SyncCoordinatorTest.Journal(), transport(server), Set.of("history"), "a", 3);
            assertEquals("GET", server.takeRequest().getMethod()); assertEquals("\"v1\"", server.takeRequest().getHeader("If-Match"));
            assertEquals("GET", server.takeRequest().getMethod()); RecordedRequest finalPut = server.takeRequest();
            assertEquals("\"v2\"", finalPut.getHeader("If-Match")); assertEquals(2, SyncDocument.decode(finalPut.getBody().readUtf8()).entries.size());
        }
    }
    @Test public void missingOrWeakEtagNeverPermitsUnconditionalPut() throws Exception {
        for (String etag : new String[]{"", "W/\"weak\""}) try (MockWebServer server = new MockWebServer()) {
            MockResponse response = new MockResponse().setBody(new SyncDocument().encode()); if (!etag.isEmpty()) response.setHeader("ETag", etag);
            server.enqueue(response);
            assertThrows(SyncCoordinator.Failure.class, () -> SyncCoordinator.sync(new SyncCoordinatorTest.Store(), new SyncCoordinatorTest.Journal(), transport(server), Set.of("history"), "a", 1));
            assertEquals(1, server.getRequestCount());
        }
    }
    @Test public void unrelatedExistingJsonIsNotOverwritten() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("ETag", "\"config\"").setBody("{\"sites\":[],\"spider\":\"original\"}"));
            assertThrows(SyncCoordinator.Failure.class, () -> SyncCoordinator.sync(new SyncCoordinatorTest.Store(), new SyncCoordinatorTest.Journal(), transport(server), Set.of("history"), "a", 1));
            assertEquals(1, server.getRequestCount());
        }
    }
    @Test public void redirectsNeverForwardWebDavCredentialsToAnotherServer() throws Exception {
        try (MockWebServer source = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            source.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", other.url("/capture")));
            other.enqueue(new MockResponse().setHeader("ETag", "\"other\"").setBody(new SyncDocument().encode()));
            assertThrows(SyncCoordinator.Failure.class, () -> transport(source).get());
            assertNotNull(source.takeRequest().getHeader("Authorization")); assertEquals(0, other.getRequestCount());
        }
    }
    @Test public void oversizedRemoteBodyIsRejectedBeforeReadingAllOfIt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("x").setHeader("Content-Length", SyncDocument.MAX_BYTES + 1).setHeader("ETag", "\"large\""));
            assertThrows(SyncCoordinator.Failure.class, () -> transport(server).get());
        }
    }
    @Test public void cancellationDuringGetDoesNotSendPut() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setHeader("ETag", "\"v1\"").setBody(new SyncDocument().encode()));
            WebDavTransport transport = transport(server); ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> result = worker.submit(() -> {
                    try { SyncCoordinator.sync(new SyncCoordinatorTest.Store(), new SyncCoordinatorTest.Journal(), transport, Set.of("history"), "a", 1); return false; }
                    catch (IOException expected) { return true; }
                });
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); transport.cancel(); assertTrue(result.get(2, TimeUnit.SECONDS));
                assertEquals(1, server.getRequestCount());
            } finally { worker.shutdownNow(); }
        }
    }
    @Test public void publicTransportRejectsCleartextCredentialEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> new WebDavTransport("http://example.test/tv-sync.json", "account", "password"));
    }
}
