package com.github.catvod.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class IdleConnectionEvictorTest {

    @Test
    public void blockedCleanupDoesNotBlockCallerAndRapidChangesAreCoalesced() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger passes = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        IdleConnectionEvictor evictor = new IdleConnectionEvictor(() -> {
            worker.set(Thread.currentThread());
            if (passes.incrementAndGet() == 1) {
                started.countDown();
                await(release);
            }
        });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            caller.submit(evictor::request).get(2, TimeUnit.SECONDS);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), worker.get());
            // These all finish while the first socket cleanup is still blocked.
            caller.submit(() -> {
                for (int i = 0; i < 1000; i++) evictor.request();
            }).get(2, TimeUnit.SECONDS);
            assertEquals(1, passes.get());
        } finally {
            release.countDown();
            caller.shutdownNow();
            finish(evictor);
        }
        assertEquals(2, passes.get());
    }

    @Test
    public void onePoolFailureDoesNotSkipOtherPoolOrLaterChanges() throws Exception {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IdleConnectionEvictor evictor = new IdleConnectionEvictor(() -> {
            if (first.incrementAndGet() == 1) {
                failed.countDown();
                await(release);
                throw new IllegalStateException("Simulated socket close failure");
            }
        }, second::incrementAndGet);
        try {
            evictor.request();
            assertTrue(failed.await(2, TimeUnit.SECONDS));
            evictor.request();
        } finally {
            release.countDown();
            finish(evictor);
        }
        assertEquals(2, first.get());
        assertEquals(2, second.get());
    }

    @Test
    public void idleSocketClosesInBackgroundAndActivePlaybackContinues() throws Exception {
        TrackingSocketFactory sockets = new TrackingSocketFactory();
        OkHttpClient client = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).socketFactory(sockets).build();
        OkHttpClient player = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).socketFactory(sockets).build();
        IdleConnectionEvictor evictor = new IdleConnectionEvictor(
                () -> client.connectionPool().evictAll(), () -> player.connectionPool().evictAll());
        try (StreamingServer server = new StreamingServer()) {
            Call playback = player.newCall(new Request.Builder().url(server.url()).build());
            try (Response active = playback.execute()) {
                try (Response idle = client.newCall(new Request.Builder().url(server.url()).build()).execute()) {
                    assertEquals("idle", idle.body().string());
                }
                assertEquals(1, client.connectionPool().idleConnectionCount());
                assertEquals(1, player.connectionPool().connectionCount());
                assertEquals(0, player.connectionPool().idleConnectionCount());
                TrackingSocket activeSocket = sockets.created.get(0);
                TrackingSocket idleSocket = sockets.created.get(1);
                evictor.request();
                assertTrue(idleSocket.closed.await(2, TimeUnit.SECONDS));
                finish(evictor);
                assertNotSame(Thread.currentThread(), idleSocket.closingThread);
                assertEquals(0, client.connectionPool().connectionCount());
                assertEquals(1, player.connectionPool().connectionCount());
                assertFalse(activeSocket.isClosed());
                assertFalse(playback.isCanceled());
                server.releaseBody.countDown();
                assertEquals("live", active.body().string());
                server.completed.get(2, TimeUnit.SECONDS);
            }
        } finally {
            finish(evictor);
            client.connectionPool().evictAll();
            player.connectionPool().evictAll();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test release");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void finish(IdleConnectionEvictor evictor) throws Exception {
        // Drain the real worker to assert completed behavior without timing sleeps or test hooks.
        Field field = IdleConnectionEvictor.class.getDeclaredField("executor");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(evictor);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static final class TrackingSocket extends Socket {
        final CountDownLatch closed = new CountDownLatch(1);
        volatile Thread closingThread;

        @Override
        public synchronized void close() throws IOException {
            closingThread = Thread.currentThread();
            super.close();
            closed.countDown();
        }
    }

    private static final class TrackingSocketFactory extends SocketFactory {
        final List<TrackingSocket> created = new CopyOnWriteArrayList<>();

        @Override
        public Socket createSocket() {
            TrackingSocket socket = new TrackingSocket();
            created.add(socket);
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(local, localPort));
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
            Socket socket = createSocket();
            socket.bind(new InetSocketAddress(local, localPort));
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }
    }

    private static final class StreamingServer implements AutoCloseable {
        final CountDownLatch releaseBody = new CountDownLatch(1);
        final ServerSocket server;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> completed;

        StreamingServer() throws IOException {
            server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(3000);
            completed = executor.submit(() -> {
                try (Socket active = server.accept()) {
                    readRequest(active);
                    write(active, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n");
                    try (Socket idle = server.accept()) {
                        readRequest(idle);
                        write(idle, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nidle");
                        await(releaseBody);
                        write(active, "live");
                    }
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
        }

        String url() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/stream";
        }

        private static void readRequest(Socket socket) throws IOException {
            socket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) { }
        }

        private static void write(Socket socket, String response) throws IOException {
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }

        @Override
        public void close() throws IOException {
            releaseBody.countDown();
            server.close();
            executor.shutdownNow();
        }
    }
}
