package com.fongmi.android.tv.update;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpdateRepositoryTest {

    private static final String PACKAGE = "com.fongmi.android.tv";
    private static final int INSTALLED = 555;

    private UpdateRepository repository() {
        return new UpdateRepository(new OkHttpClient.Builder().retryOnConnectionFailure(false).build());
    }

    private JSONObject manifest(int code) throws Exception {
        JSONObject asset = new JSONObject()
                .put("url", "https://github.com/wobuhui666/TV/releases/download/build-123/leanback-arm64_v8a.apk")
                .put("size", 500000000)
                .put("sha256", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        return new JSONObject().put("schema", 1).put("code", code).put("name", "5.5.5+20261007")
                .put("packageName", PACKAGE).put("mode", "leanback")
                .put("assets", new JSONObject().put("arm64_v8a", asset));
    }

    private String enabledPolicy() throws Exception {
        JSONObject popup = new JSONObject().put("enabled", true).put("code", 1000)
                .put("minCode", 0).put("maxCode", 0).put("expiresAt", 2000000000);
        return new JSONObject().put("schema", 1).put("popup", popup).toString();
    }

    private List<String> urls(MockWebServer server, String... paths) {
        List<String> urls = new ArrayList<>();
        for (String path : paths) urls.add(server.url(path).toString());
        return urls;
    }

    private UpdateManifest fetch(UpdateRepository repository, List<String> urls) throws IOException {
        return repository.manifest(urls, PACKAGE, "leanback", "arm64_v8a", INSTALLED);
    }

    @Test
    public void malformedJsonHtmlAndHttpFailureFallBackInOrder() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{broken"));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Proxy login</html>"));
            server.enqueue(new MockResponse().setResponseCode(503).setBody("Unavailable"));
            server.enqueue(new MockResponse().setBody(manifest(1000).toString()));
            assertEquals(1000, fetch(repository(), urls(server, "/bad-json", "/html", "/http-error", "/original")).code);
            for (String path : new String[]{"/bad-json", "/html", "/http-error", "/original"}) {
                RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
                assertNotNull(request);
                assertEquals(path, request.getPath());
                assertEquals("GET", request.getMethod());
                assertEquals("application/json", request.getHeader("Accept"));
                assertEquals("no-cache", request.getHeader("Cache-Control"));
            }
            assertEquals(4, server.getRequestCount());
        }
    }

    @Test
    public void variantOrChecksumValidationFailureAlsoFallsBack() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(manifest(1000).put("mode", "mobile").toString()));
            JSONObject corrupt = manifest(1000);
            corrupt.getJSONObject("assets").getJSONObject("arm64_v8a").put("sha256", "missing");
            server.enqueue(new MockResponse().setBody(corrupt.toString()));
            server.enqueue(new MockResponse().setBody(manifest(1001).toString()));
            assertEquals(1001, fetch(repository(), urls(server, "/wrong-mode", "/bad-hash", "/original")).code);
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    public void stalledHttpBodyTimesOutThenUsesNextSource() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(chain -> chain.withReadTimeout(100, TimeUnit.MILLISECONDS).proceed(chain.request()))
                .build();
        try (MockWebServer slow = new MockWebServer(); MockWebServer healthy = new MockWebServer()) {
            slow.enqueue(new MockResponse().setBody(manifest(1000).toString()).setBodyDelay(500, TimeUnit.MILLISECONDS));
            healthy.enqueue(new MockResponse().setBody(manifest(1001).toString()));
            List<String> sources = List.of(slow.url("/slow").toString(), healthy.url("/original").toString());
            assertEquals(1001, fetch(new UpdateRepository(client), sources).code);
            assertEquals(1, slow.getRequestCount());
            assertEquals(1, healthy.getRequestCount());
        }
    }

    @Test
    public void staleAndEqualVersionsContinueThroughRemainingSources() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(manifest(500).toString()));
            server.enqueue(new MockResponse().setBody(manifest(INSTALLED).toString()));
            server.enqueue(new MockResponse().setBody(manifest(1000).toString()));
            assertEquals(1000, fetch(repository(), urls(server, "/stale", "/equal", "/original")).code);
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    public void allStaleSourcesReturnHighestValidVersionAfterCheckingEverySource() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(manifest(540).toString()));
            server.enqueue(new MockResponse().setBody(manifest(520).toString()));
            server.enqueue(new MockResponse().setResponseCode(500));
            assertEquals(540, fetch(repository(), urls(server, "/newer-stale", "/older-stale", "/failed")).code);
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    public void oversizedContentLengthAndChunkedJsonAreRejectedThenFallBack() throws Exception {
        String oversized = manifest(999).put("desc", "x".repeat(256 * 1024)).toString();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(oversized));
            server.enqueue(new MockResponse().setChunkedBody(oversized, 4096));
            server.enqueue(new MockResponse().setBody(manifest(1000).toString()));
            assertEquals(1000, fetch(repository(), urls(server, "/oversized-length", "/oversized-chunked", "/original")).code);
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    public void allInvalidMetadataOrEmptySourcesReportFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(new MockResponse().setBody("<html>Unavailable</html>"));
            assertThrows(IOException.class, () -> fetch(repository(), urls(server, "/missing", "/html")));
            assertEquals(2, server.getRequestCount());
        }
        assertThrows(IOException.class, () -> fetch(repository(), List.of()));
        assertThrows(IOException.class, () -> repository().policy(List.of()));
    }

    @Test
    public void disabledPolicyNeverFallsBackToAnOlderEnabledPolicy() throws Exception {
        for (String disabled : new String[]{"{\"schema\":1}", "{\"schema\":1,\"popup\":{\"enabled\":false}}"}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setBody(disabled));
                server.enqueue(new MockResponse().setBody(enabledPolicy()));
                UpdatePolicy policy = repository().policy(urls(server, "/disabled", "/old-enabled"));
                assertFalse(policy.shouldPrompt(INSTALLED, 1000, 0, 1000000000));
                assertEquals(1, server.getRequestCount());
            }
        }
    }

    @Test
    public void malformedAndUnavailablePolicySourcesFallBackToValidPolicy() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("<html>Unavailable</html>"));
            server.enqueue(new MockResponse().setResponseCode(503));
            server.enqueue(new MockResponse().setBody(enabledPolicy()));
            UpdatePolicy policy = repository().policy(urls(server, "/html", "/http-error", "/original"));
            assertTrue(policy.shouldPrompt(INSTALLED, 1000, 0, 1000000000));
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    public void cancellationBeforeCheckPreventsAnyRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(manifest(1000).toString()));
            UpdateRepository repository = repository();
            repository.cancel();
            assertThrows(InterruptedIOException.class, () -> fetch(repository, urls(server, "/manifest")));
            assertThrows(InterruptedIOException.class, () -> repository.policy(urls(server, "/policy")));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    public void cancellationDuringRequestStopsImmediatelyWithoutFallback() throws Exception {
        for (boolean policyRequest : new boolean[]{false, true}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setBody(policyRequest ? enabledPolicy() : manifest(1000).toString())
                        .setBodyDelay(1, TimeUnit.SECONDS));
                server.enqueue(new MockResponse().setBody(manifest(1001).toString()));
                UpdateRepository repository = repository();
                List<String> sources = urls(server, "/slow", "/must-not-request");
                ExecutorService worker = Executors.newSingleThreadExecutor();
                try {
                    Future<IOException> result = worker.submit(() -> {
                        try {
                            if (policyRequest) repository.policy(sources);
                            else fetch(repository, sources);
                            return null;
                        } catch (IOException e) {
                            return e;
                        }
                    });
                    assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
                    repository.cancel();
                    assertTrue(result.get(500, TimeUnit.MILLISECONDS) instanceof InterruptedIOException);
                    assertEquals(1, server.getRequestCount());
                } finally {
                    worker.shutdownNow();
                }
            }
        }
    }
}
