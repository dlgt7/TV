package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

public class AbortableCallFactoryTest {

    private static final Request REQUEST = new Request.Builder().url("https://example.com/video.mp4").build();

    @Test public void foregroundPriorityActuallyInterruptsStalledHttpRead() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            AbortableCallFactory calls = new AbortableCallFactory(client);
            PreloadBudget budget = new PreloadBudget();
            budget.update(true, 0);
            assertNotNull(budget.acquire(PreloadBudget.Owner.NEXT, 0, calls::abort));
            Future<Boolean> read = executor.submit(() -> {
                try (Response response = calls.newCall(new Request.Builder().url(server.url("/slow-video")).build()).execute()) {
                    response.body().bytes();
                    return false;
                } catch (java.io.IOException canceled) {
                    return true;
                }
            });
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            budget.update(false, 1);
            assertTrue("Cancellation must end the socket read, not wait for its 30-second timeout", read.get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }

    @Test
    public void shouldCancelCallsCreatedBeforeAbort() {
        AbortableCallFactory factory = new AbortableCallFactory(new OkHttpClient());
        Call call = factory.newCall(REQUEST);

        assertFalse(call.isCanceled());
        factory.abort();

        assertTrue(call.isCanceled());
    }

    @Test
    public void shouldReturnCanceledCallAfterAbort() {
        AbortableCallFactory factory = new AbortableCallFactory(new OkHttpClient());

        factory.abort();
        Call call = factory.newCall(REQUEST);

        assertTrue(call.isCanceled());
    }

    @Test
    public void shouldNotLetCreationRaceEscapeAbort() throws Exception {
        BlockingCallFactory upstream = new BlockingCallFactory();
        AbortableCallFactory factory = new AbortableCallFactory(upstream);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Call> create = executor.submit(() -> factory.newCall(REQUEST));
            assertTrue(upstream.started.await(1, TimeUnit.SECONDS));
            Future<?> abort = executor.submit(factory::abort);

            upstream.proceed.countDown();
            Call call = create.get(1, TimeUnit.SECONDS);
            abort.get(1, TimeUnit.SECONDS);

            assertTrue(call.isCanceled());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class BlockingCallFactory implements Call.Factory {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);
        private final OkHttpClient client = new OkHttpClient();

        @Override
        public Call newCall(Request request) {
            Call call = client.newCall(request);
            started.countDown();
            try {
                proceed.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return call;
        }
    }
}
