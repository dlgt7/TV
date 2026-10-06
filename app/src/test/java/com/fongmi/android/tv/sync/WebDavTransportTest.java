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
import okhttp3.mockwebserver.SocketPolicy;

public class WebDavTransportTest {
    private WebDavTransport transport(MockWebServer server) {
        return new WebDavTransport(new OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(), server.url("/tv-sync.json").toString(), "fixture-user", "fixture-password");
    }
    private static SyncCoordinator.Fetched missing() {
        return new SyncCoordinator.Fetched(new SyncDocument(), null, false);
    }
    private static void enqueueStagedCreate(MockWebServer server, int moveStatus) {
        server.enqueue(new MockResponse().setResponseCode(201));
        server.enqueue(new MockResponse().setHeader("ETag", "temporary-revision").setBody(new SyncDocument().encode()));
        server.enqueue(new MockResponse().setResponseCode(moveStatus));
        server.enqueue(new MockResponse().setResponseCode(204));
    }
    private static String assertStagedRequests(MockWebServer server, String expectedBody) throws Exception {
        RecordedRequest upload = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(upload); assertEquals("PUT", upload.getMethod());
        String temporary = upload.getPath();
        assertNotEquals("/tv-sync.json", temporary); assertTrue(temporary.startsWith("/"));
        assertEquals(temporary.lastIndexOf('/'), 0); assertEquals(expectedBody, upload.getBody().readUtf8());
        RecordedRequest read = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(read); assertEquals("GET", read.getMethod()); assertEquals(temporary, read.getPath());
        RecordedRequest move = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(move); assertEquals("MOVE", move.getMethod()); assertEquals(temporary, move.getPath());
        assertEquals(server.url("/tv-sync.json").toString(), move.getHeader("Destination"));
        assertEquals("F", move.getHeader("Overwrite")); assertEquals("temporary-revision", move.getHeader("If-Match"));
        RecordedRequest cleanup = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(cleanup); assertEquals("DELETE", cleanup.getMethod()); assertEquals(temporary, cleanup.getPath());
        assertEquals("temporary-revision", cleanup.getHeader("If-Match"));
        for (RecordedRequest request : new RecordedRequest[]{upload, read, move, cleanup})
            assertEquals(Credentials.basic("fixture-user", "fixture-password"), request.getHeader("Authorization"));
        return temporary;
    }
    @Test public void firstWriteStagesAndMovesWithoutOverwritingTheMissingTarget() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404)); enqueueStagedCreate(server, 201);
            WebDavTransport transport = transport(server); SyncCoordinator.Fetched fetched = transport.get();
            assertFalse(fetched.exists); assertTrue(transport.put(new SyncDocument(), fetched));
            assertEquals("GET", server.takeRequest().getMethod());
            assertStagedRequests(server, new SyncDocument().encode()); assertEquals(5, server.getRequestCount());
        }
    }
    @Test public void competingFirstWriterRefetchesAndMergesInsteadOfOverwritingWinner() throws Exception {
        for (int conflict : new int[]{409, 412}) try (MockWebServer server = new MockWebServer()) {
            SyncDocument.Entry a = history(scope(), "ours", 1, 1, "a"), b = history(scope(), "theirs", 2, 2, "b");
            server.enqueue(new MockResponse().setResponseCode(404)); enqueueStagedCreate(server, conflict);
            server.enqueue(new MockResponse().setHeader("ETag", "winner-revision").setBody(document(b).encode()));
            server.enqueue(new MockResponse().setResponseCode(204));
            SyncCoordinatorTest.Store store = new SyncCoordinatorTest.Store(); store.entries.put(a.id(), a);
            SyncCoordinator.sync(store, new SyncCoordinatorTest.Journal(), transport(server), Set.of("history"), "a", 3);
            assertEquals("GET", server.takeRequest().getMethod());
            // The first upload contains our observed record; compare the later merged PUT instead of timestamp internals.
            RecordedRequest upload = server.takeRequest(); String temporary = upload.getPath(); assertEquals("PUT", upload.getMethod());
            assertNotEquals("/tv-sync.json", temporary); assertEquals(1, SyncDocument.decode(upload.getBody().readUtf8()).entries.size());
            assertEquals(temporary, server.takeRequest().getPath());
            RecordedRequest move = server.takeRequest(); assertEquals("MOVE", move.getMethod()); assertEquals("F", move.getHeader("Overwrite"));
            assertEquals(temporary, server.takeRequest().getPath()); assertEquals("GET", server.takeRequest().getMethod());
            RecordedRequest update = server.takeRequest(); assertEquals("PUT", update.getMethod()); assertEquals("/tv-sync.json", update.getPath());
            assertEquals("winner-revision", update.getHeader("If-Match"));
            assertEquals(2, SyncDocument.decode(update.getBody().readUtf8()).entries.size()); assertEquals(7, server.getRequestCount());
        }
    }
    @Test public void eachCreationAttemptUsesItsOwnTemporaryName() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueStagedCreate(server, 412); enqueueStagedCreate(server, 412);
            WebDavTransport transport = transport(server);
            assertFalse(transport.put(new SyncDocument(), missing())); assertFalse(transport.put(new SyncDocument(), missing()));
            String first = assertStagedRequests(server, new SyncDocument().encode());
            String second = assertStagedRequests(server, new SyncDocument().encode());
            assertNotEquals(first, second); assertEquals(8, server.getRequestCount());
        }
    }
    @Test public void failedOrReplacementMoveNeverFallsBackToFinalPut() throws Exception {
        for (int status : new int[]{204, 400, 403, 405, 500, 501}) try (MockWebServer server = new MockWebServer()) {
            enqueueStagedCreate(server, status);
            assertThrows("Unexpected success for MOVE " + status, IOException.class, () -> transport(server).put(new SyncDocument(), missing()));
            assertStagedRequests(server, new SyncDocument().encode()); assertEquals(4, server.getRequestCount());
        }
    }
    @Test public void moveRedirectNeverSendsAuthorizationOrDestinationToAnotherServer() throws Exception {
        for (int code : new int[]{301, 302, 307, 308}) try (MockWebServer source = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            source.enqueue(new MockResponse().setResponseCode(201));
            source.enqueue(new MockResponse().setHeader("ETag", "temporary-revision").setBody(new SyncDocument().encode()));
            source.enqueue(new MockResponse().setResponseCode(code).setHeader("Location", other.url("/capture")));
            source.enqueue(new MockResponse().setResponseCode(204));
            assertThrows(IOException.class, () -> transport(source).put(new SyncDocument(), missing()));
            assertStagedRequests(source, new SyncDocument().encode()); assertEquals(0, other.getRequestCount());
        }
    }
    @Test public void cleanupFailureDoesNotUndoAConfirmedCreateOrCauseFinalPut() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));
            server.enqueue(new MockResponse().setHeader("ETag", "temporary-revision").setBody(new SyncDocument().encode()));
            server.enqueue(new MockResponse().setResponseCode(201)); server.enqueue(new MockResponse().setResponseCode(500));
            assertTrue(transport(server).put(new SyncDocument(), missing()));
            assertStagedRequests(server, new SyncDocument().encode()); assertEquals(4, server.getRequestCount());
        }
    }
    @Test public void explicitTemporaryPreconditionFailureDoesNotDeleteAnotherResource() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(412));
            assertFalse(transport(server).put(new SyncDocument(), missing()));
            RecordedRequest upload = server.takeRequest(); assertEquals("PUT", upload.getMethod()); assertNotEquals("/tv-sync.json", upload.getPath());
            assertEquals(1, server.getRequestCount());
        }
    }
    @Test public void missingTemporaryValidatorPreventsMoveButCleansOwnUpload() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201)); server.enqueue(new MockResponse().setBody(new SyncDocument().encode()));
            server.enqueue(new MockResponse().setResponseCode(204));
            assertThrows(IOException.class, () -> transport(server).put(new SyncDocument(), missing()));
            String temporary = server.takeRequest().getPath(); assertEquals("GET", server.takeRequest().getMethod());
            RecordedRequest cleanup = server.takeRequest(); assertEquals("DELETE", cleanup.getMethod()); assertEquals(temporary, cleanup.getPath());
            assertNotEquals("/tv-sync.json", cleanup.getPath()); assertEquals(3, server.getRequestCount());
        }
    }
    @Test public void lostUploadResponseCleansOnlyTheAttemptedTemporaryFile() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            server.enqueue(new MockResponse().setResponseCode(204));
            assertThrows(IOException.class, () -> transport(server).put(new SyncDocument(), missing()));
            RecordedRequest upload = server.takeRequest(2, TimeUnit.SECONDS), cleanup = server.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(upload); assertNotNull(cleanup); assertEquals("PUT", upload.getMethod()); assertEquals("DELETE", cleanup.getMethod());
            assertEquals(upload.getPath(), cleanup.getPath()); assertNotEquals("/tv-sync.json", cleanup.getPath()); assertEquals(2, server.getRequestCount());
        }
    }
    @Test public void cancellationDuringStagingPreventsMoveAndStillCleansOwnTemporary() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setResponseCode(201));
            server.enqueue(new MockResponse().setResponseCode(204));
            WebDavTransport transport = transport(server); ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> done = worker.submit(() -> {
                    try { transport.put(new SyncDocument(), missing()); return false; }
                    catch (IOException expected) { return true; }
                });
                RecordedRequest upload = server.takeRequest(2, TimeUnit.SECONDS); assertNotNull(upload); transport.cancel();
                assertTrue(done.get(4, TimeUnit.SECONDS)); RecordedRequest cleanup = server.takeRequest(2, TimeUnit.SECONDS);
                assertNotNull(cleanup); assertEquals("DELETE", cleanup.getMethod()); assertEquals(upload.getPath(), cleanup.getPath());
                assertNotEquals("/tv-sync.json", cleanup.getPath()); assertEquals(2, server.getRequestCount());
            } finally { worker.shutdownNow(); }
        }
    }
    @Test public void actual412RefetchesEtagAndPreservesBothDevicesRecords() throws Exception {
        for (String[] etags : new String[][]{{"\"v1\"", "\"v2\""}, {"revision-1", "revision-2"}}) try (MockWebServer server = new MockWebServer()) {
            SyncDocument.Entry a = history(scope(), "ours", 1, 1, "a"), b = history(scope(), "theirs", 2, 2, "b");
            server.enqueue(new MockResponse().setHeader("ETag", etags[0]).setBody(new SyncDocument().encode()));
            server.enqueue(new MockResponse().setResponseCode(412));
            server.enqueue(new MockResponse().setHeader("ETag", etags[1]).setBody(document(b).encode()));
            server.enqueue(new MockResponse().setResponseCode(204));
            SyncCoordinatorTest.Store store = new SyncCoordinatorTest.Store(); store.entries.put(a.id(), a);
            SyncCoordinator.sync(store, new SyncCoordinatorTest.Journal(), transport(server), Set.of("history"), "a", 3);
            assertEquals("GET", server.takeRequest().getMethod()); assertEquals(etags[0], server.takeRequest().getHeader("If-Match"));
            assertEquals("GET", server.takeRequest().getMethod()); RecordedRequest finalPut = server.takeRequest();
            assertEquals(etags[1], finalPut.getHeader("If-Match")); assertNull(finalPut.getHeader("If-None-Match"));
            assertEquals(2, SyncDocument.decode(finalPut.getBody().readUtf8()).entries.size()); assertEquals(4, server.getRequestCount());
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
    @Test public void quotedStrongEtagIsPreservedInConditionalPut() throws Exception {
        for (String etag : new String[]{"\"v1\"", "\"revision-01_2.3\"", "\"\"", "\"*\"", "\"v1,v2\"", "\"W/revision\""}) {
            assertEtagRoundTripsUnchanged(etag);
        }
    }
    @Test public void unquotedSafeEtagIsSentAsIfMatchWithoutAddingQuotes() throws Exception {
        for (String etag : new String[]{"0123456789abcdefghijkl", "revision-01_2.3~4"}) {
            assertEtagRoundTripsUnchanged(etag);
        }
    }
    private void assertEtagRoundTripsUnchanged(String etag) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("ETag", etag).setBody(new SyncDocument().encode()));
            server.enqueue(new MockResponse().setResponseCode(204));
            WebDavTransport transport = transport(server); SyncCoordinator.Fetched fetched = transport.get();
            assertTrue(fetched.exists); assertEquals(etag, fetched.etag);
            assertTrue(transport.put(new SyncDocument(), fetched));
            assertEquals("GET", server.takeRequest().getMethod()); RecordedRequest put = server.takeRequest();
            assertEquals("PUT", put.getMethod()); assertEquals(etag, put.getHeader("If-Match"));
            assertNull(put.getHeader("If-None-Match")); assertEquals(2, server.getRequestCount());
        }
    }
    @Test public void malformedOrUnsafeEtagFromServerIsRejectedBeforePut() throws Exception {
        for (String etag : new String[]{"", "W/\"weak\"", "w/\"weak\"", "W/revision", "*", "v*1", "v1,v2", "v/1", "v+1", "v1=", "v:1", "v;1", "v\\1",
                "v 1", "v\t1", "\"", "\"v1", "v1\"", "\"v1\",\"v2\"", "\"v1\" \"v2\"", "\"v\t1\""}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setHeader("ETag", etag).setBody(new SyncDocument().encode()));
                SyncCoordinator.Failure failure = assertThrows("Accepted unsafe ETag: " + etag, SyncCoordinator.Failure.class,
                        () -> SyncCoordinator.sync(new SyncCoordinatorTest.Store(), new SyncCoordinatorTest.Journal(), transport(server), Set.of("history"), "a", 1));
                assertEquals("SERVER_NO_STRONG_ETAG", failure.code); assertEquals(1, server.getRequestCount());
            }
        }
    }
    @Test public void invalidFetchedEtagCannotBypassValidationAtPut() throws Exception {
        for (String etag : new String[]{null, "", "W/\"weak\"", "*", "v*1", "v1,v2", "v/1", "v+1", "v1=", "v:1", "v;1", "v\\1", "v 1", "v\t1", " v1", "v1 ",
                "v1\r\nIf-Match: *", "v\u00001", "v\u007f1", "r\u00e9vision", "\"v\u007f1\"", "\"r\u00e9vision\"", "\"", "\"v1\",\"v2\""}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setResponseCode(204));
                WebDavTransport transport = transport(server);
                SyncCoordinator.Fetched fetched = new SyncCoordinator.Fetched(new SyncDocument(), etag, true);
                SyncCoordinator.Failure failure = assertThrows("Accepted unsafe ETag: " + etag, SyncCoordinator.Failure.class,
                        () -> transport.put(new SyncDocument(), fetched));
                assertEquals("SERVER_NO_STRONG_ETAG", failure.code); assertEquals(0, server.getRequestCount());
            }
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
