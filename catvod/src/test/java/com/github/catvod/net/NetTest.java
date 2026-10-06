package com.github.catvod.net;

import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;

public class NetTest {

    @Test
    public void requestEncodesRepeatedQueriesAndReturnsHttpErrorBody() throws Exception {
        AtomicReference<Request> seen = new AtomicReference<>();
        try (Server server = new Server(request -> {
            seen.set(request);
            return new Reply(403, "denied");
        }); Net net = new Net(new OkHttpClient(), null)) {
            JsonObject result = Json.strict(net.req(server.url("/list"), "{\"params\":{\"q\":\"a b&中\",\"page\":[1,2]}}" )).getAsJsonObject();
            assertEquals(403, result.get("code").getAsInt());
            assertEquals("denied", result.get("content").getAsString());
            assertEquals("/list?q=a%20b%26%E4%B8%AD&page=1&page=2", seen.get().path);
            assertEquals("denied", net.get(server.url("/list")));
            assertThrows(IllegalStateException.class, () -> net.json(server.url("/list"), "{}"));
        }
    }

    @Test
    public void encodesPostBodyAndValidatesJsonWithoutChangingLegacyTextResponses() throws Exception {
        AtomicReference<Request> seen = new AtomicReference<>();
        try (Server server = new Server(request -> {
            seen.set(request);
            return new Reply(200, "{\"ok\":true}");
        }); Net net = new Net(new OkHttpClient(), null)) {
            assertEquals("{\"ok\":true}", net.post(server.url("/"), "{\"input\":1}"));
            assertEquals("POST", seen.get().method);
            assertEquals("{\"input\":1}", new String(seen.get().body, StandardCharsets.UTF_8));
            assertTrue(seen.get().headers.get("content-type").startsWith("application/json"));
            net.req(server.url("/"), "{\"method\":\"post\",\"postType\":\"form\",\"data\":{\"q\":\"a b&中\"}}");
            assertEquals("q=a+b%26%E4%B8%AD", new String(seen.get().body, StandardCharsets.UTF_8));
            assertTrue(Json.strict(net.json(server.url("/"), "{}")).getAsJsonObject().get("ok").getAsBoolean());
            assertTrue(Json.strict(net.req(server.url("/"), "{\"method\":\"post\",\"data\":{},\"body\":\"raw\"}")).getAsJsonObject().has("error"));
        }
        try (Server server = new Server(request -> new Reply(200, "<html>login</html>")); Net net = new Net(new OkHttpClient(), null)) {
            assertEquals("<html>login</html>", net.get(server.url("/")));
            assertThrows(RuntimeException.class, () -> net.json(server.url("/"), "{}"));
        }
    }

    @Test
    public void noRedirectDisablesTheSharedProxyRedirectInterceptor() throws Exception {
        AtomicInteger targets = new AtomicInteger();
        OkProxySelector selector = new OkProxySelector();
        OkHttpClient client = new OkHttpClient.Builder().proxySelector(selector)
                .addInterceptor(new ProxyRedirectInterceptor(selector)).followRedirects(false).build();
        try (Server server = new Server(request -> {
            if (request.path.equals("/start")) return new Reply(302, "redirect").header("Location", "/target");
            targets.incrementAndGet();
            return new Reply(200, "target");
        }); Net net = new Net(client, null)) {
            assertEquals(302, Json.strict(net.req(server.url("/start"), "{\"redirect\":0}")).getAsJsonObject().get("code").getAsInt());
            assertEquals(0, targets.get());
            assertEquals("target", net.get(server.url("/start")));
            assertEquals(1, targets.get());
        } finally {
            selector.clear();
        }
    }

    @Test
    public void sessionsKeepCookiesIsolatedAndCannotPersistCache() throws Exception {
        try (Server server = new Server(request -> request.path.equals("/set")
                ? new Reply(200, "saved").header("Set-Cookie", "sid=one; Path=/")
                : new Reply(200, request.headers.getOrDefault("cookie", "none")));
             Net owner = new Net(new OkHttpClient(), null);
             Net one = owner.session(); Net two = owner.session()) {
            one.get(server.url("/set"));
            assertEquals("sid=one", one.get(server.url("/read")));
            assertEquals("none", two.get(server.url("/read")));
            assertEquals("none", owner.get(server.url("/read")));
            assertThrows(Exception.class, () -> one.cached("key", "{\"ttl\":10,\"persist\":true}", () -> "1"));
        }
    }

    @Test
    public void binaryResultsBatchOrderAndDownloadPreserveBytes() throws Exception {
        byte[] bytes = new byte[]{0, 1, (byte) 128, (byte) 255};
        File output = File.createTempFile("spider-net", ".bin");
        try (Server server = new Server(request -> new Reply(200, bytes)); Net net = new Net(new OkHttpClient(), null)) {
            assertArrayEquals(bytes, (byte[]) net.reqParts(server.url("/"), "{\"buffer\":3}")[1]);
            assertEquals(Base64.getEncoder().encodeToString(bytes), Json.strict(net.req(server.url("/"), "{\"buffer\":2}")).getAsJsonObject().get("content").getAsString());
            net.download(server.url("/"), "{}", output);
            assertArrayEquals(bytes, Files.readAllBytes(output.toPath()));
        } finally {
            output.delete();
        }
        try (Server server = new Server(request -> new Reply(200, request.path)); Net net = new Net(new OkHttpClient(), null)) {
            String input = "[{\"url\":\"" + server.url("/one") + "\"},{\"url\":\"" + server.url("/two") + "\"}]";
            Net.Result[] values = net.batch(input, 2);
            assertEquals("/one", values[0].text());
            assertEquals("/two", values[1].text());
        }
    }

    @Test
    public void closeCancelsOnlyItsOwnCallsAndWakesSleepers() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OkHttpClient shared = new OkHttpClient();
        try (Server server = new Server(request -> {
            if (request.path.equals("/slow")) {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return new Reply(200, "ok");
        }); Net owner = new Net(shared, null); Net other = new Net(shared, null)) {
            CompletableFuture<Net.Result> pending = owner.requestAsync(server.url("/slow"), new NetOptions());
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            CompletableFuture<Boolean> sleeping = CompletableFuture.supplyAsync(() -> {
                try { owner.sleep(30000); return false; } catch (IOException expected) { return true; }
            });
            owner.close();
            assertNotNull(pending.get(3, TimeUnit.SECONDS).error);
            assertTrue(sleeping.get(3, TimeUnit.SECONDS));
            assertEquals("ok", other.get(server.url("/fast")));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void websocketKeepsSharedTlsDnsProxyAndDisablesRedirectInterceptor() throws Exception {
        OkProxySelector selector = new OkProxySelector();
        OkHttpClient shared = new OkHttpClient.Builder().proxySelector(selector)
                .addInterceptor(new ProxyRedirectInterceptor(selector)).build();
        try (Net net = new Net(shared, null)) {
            Method method = Net.class.getDeclaredMethod("socketClient", NetOptions.class);
            method.setAccessible(true);
            OkHttpClient socket = (OkHttpClient) method.invoke(net, new NetOptions());
            assertSame(shared.sslSocketFactory(), socket.sslSocketFactory());
            assertSame(shared.x509TrustManager(), socket.x509TrustManager());
            assertSame(shared.hostnameVerifier(), socket.hostnameVerifier());
            assertSame(shared.dns(), socket.dns());
            assertSame(shared.proxySelector(), socket.proxySelector());
            assertSame(shared.proxyAuthenticator(), socket.proxyAuthenticator());
            assertFalse(socket.followRedirects());
            assertFalse(socket.interceptors().stream().anyMatch(item -> item instanceof ProxyRedirectInterceptor));
        } finally {
            selector.clear();
        }
    }

    @Test
    public void websocketReturnsFirstMessage() throws Exception {
        try (Server server = new Server(request -> {
            String key = request.headers.get("sec-websocket-key");
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            return new Reply(101, new byte[]{(byte) 0x81, 2, 'o', 'k'})
                    .header("Upgrade", "websocket").header("Connection", "Upgrade").header("Sec-WebSocket-Accept", accept);
        }); Net net = new Net(new OkHttpClient(), null)) {
            JsonObject result = Json.strict(net.ws(server.url("/").replace("http:", "ws:"), "{\"timeout\":2000}")).getAsJsonObject();
            assertEquals(result.toString(), 101, result.get("code").getAsInt());
            assertEquals("ok", result.get("content").getAsString());
        }
    }

    private interface Handler {
        Reply respond(Request request) throws Exception;
    }

    private static final class Request {
        String method;
        String path;
        final Map<String, String> headers = new HashMap<>();
        byte[] body;
    }

    private static final class Reply {
        final int status;
        final byte[] body;
        final Map<String, String> headers = new HashMap<>();

        Reply(int status, String body) { this(status, body.getBytes(StandardCharsets.UTF_8)); }
        Reply(int status, byte[] body) { this.status = status; this.body = body; }
        Reply header(String key, String value) { headers.put(key, value); return this; }
    }

    private static final class Server implements AutoCloseable {
        final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final ExecutorService workers = Executors.newCachedThreadPool();

        Server(Handler handler) throws IOException {
            workers.execute(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket socket = listener.accept();
                        workers.execute(() -> serve(socket, handler));
                    } catch (IOException ignored) {
                        return;
                    }
                }
            });
        }

        String url(String path) { return "http://127.0.0.1:" + listener.getLocalPort() + path; }

        private void serve(Socket socket, Handler handler) {
            try (socket) {
                socket.setSoTimeout(5000);
                InputStream input = new BufferedInputStream(socket.getInputStream());
                String[] first = line(input).split(" ");
                Request request = new Request();
                request.method = first[0];
                request.path = first[1];
                for (String header; !(header = line(input)).isEmpty();) {
                    int colon = header.indexOf(':');
                    request.headers.put(header.substring(0, colon).toLowerCase(java.util.Locale.ROOT), header.substring(colon + 1).trim());
                }
                request.body = input.readNBytes(Integer.parseInt(request.headers.getOrDefault("content-length", "0")));
                Reply reply = handler.respond(request);
                OutputStream output = socket.getOutputStream();
                StringBuilder response = new StringBuilder("HTTP/1.1 " + reply.status + " Test\r\n");
                reply.headers.forEach((key, value) -> response.append(key).append(": ").append(value).append("\r\n"));
                if (reply.status != 101) response.append("Connection: close\r\nContent-Length: ").append(reply.body.length).append("\r\n");
                response.append("\r\n");
                output.write(response.toString().getBytes(StandardCharsets.US_ASCII));
                output.write(reply.body);
                output.flush();
                if (reply.status == 101) input.read();
            } catch (Exception ignored) {
                // A canceled request can close its socket while the fixture writes.
            }
        }

        private String line(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1 && value != '\n') if (value != '\r') line.write(value);
            if (value == -1) throw new IOException("Unexpected end of request");
            return line.toString(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            listener.close();
            workers.shutdownNow();
        }
    }
}
