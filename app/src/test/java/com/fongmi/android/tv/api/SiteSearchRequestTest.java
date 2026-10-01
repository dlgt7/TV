package com.fongmi.android.tv.api;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SiteSearchRequestTest {

    private final OkHttpClient client = new OkHttpClient();

    @Test
    public void shouldCancelOnlyCallsOwnedBySearch() {
        SiteApi.SearchRequest request = new SiteApi.SearchRequest();
        Call search = newCall();
        Call unrelated = newCall();
        request.attach(search);

        request.cancel();

        assertTrue(search.isCanceled());
        assertFalse(unrelated.isCanceled());
    }

    @Test
    public void shouldRejectAndCancelCallAttachedAfterSearchStops() {
        SiteApi.SearchRequest request = new SiteApi.SearchRequest();
        Call call = newCall();
        request.cancel();

        assertThrows(CancellationException.class, () -> request.attach(call));

        assertTrue(call.isCanceled());
        assertThrows(CancellationException.class, request::checkCancelled);
    }

    @Test
    public void shouldForgetCompletedCalls() {
        SiteApi.SearchRequest request = new SiteApi.SearchRequest();
        Call call = newCall();
        request.attach(call);
        request.detach(call);

        request.cancel();

        assertFalse(call.isCanceled());
    }

    @Test(timeout = 10_000)
    public void shouldUnblockAnActiveHttpCallWhenSearchStops() throws Exception {
        SiteApi.SearchRequest request = new SiteApi.SearchRequest();
        MockWebServer server = new MockWebServer();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.start();
        try {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            Call call = client.newCall(new Request.Builder().url(server.url("/search")).build());
            request.attach(call);
            Future<String> response = executor.submit(() -> {
                try (Response result = call.execute()) {
                    return result.body().string();
                } finally {
                    request.detach(call);
                }
            });
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));

            request.cancel();

            ExecutionException error = assertThrows(ExecutionException.class, () -> response.get(2, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof IOException);
            assertTrue(call.isCanceled());
        } finally {
            request.cancel();
            executor.shutdownNow();
            server.shutdown();
        }
    }

    private Call newCall() {
        return client.newCall(new Request.Builder().url("https://example.com/search").build());
    }
}
