package com.fongmi.android.tv.sync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Strict TLS, same-endpoint credentials and conditional writes; never an unconditional overwrite. */
public final class WebDavTransport implements SyncCoordinator.Remote {
    private final OkHttpClient client;
    private final HttpUrl url;
    private final String authorization;
    private volatile Call active;
    private volatile boolean canceled;

    public WebDavTransport(String url, String username, String password) {
        this(new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(), url, username, password);
        if (!this.url.isHttps()) throw new IllegalArgumentException("HTTPS_WEBDAV_URL_REQUIRED");
    }
    WebDavTransport(OkHttpClient client, String url, String username, String password) {
        this.client = client.newBuilder().followRedirects(false).followSslRedirects(false).build();
        this.url = HttpUrl.get(url);
        if (!this.url.username().isEmpty() || !this.url.password().isEmpty()) throw new IllegalArgumentException("URL_CREDENTIALS_NOT_ALLOWED");
        authorization = username.isEmpty() && password.isEmpty() ? null : Credentials.basic(username, password, StandardCharsets.UTF_8);
    }
    public void cancel() { canceled = true; Call call = active; if (call != null) call.cancel(); }
    private Request.Builder request() throws IOException {
        return request(url);
    }
    private Request.Builder request(HttpUrl endpoint) throws IOException {
        if (canceled || Thread.currentThread().isInterrupted()) throw new IOException("SYNC_CANCELED");
        Request.Builder builder = new Request.Builder().url(endpoint).header("Cache-Control", "no-cache");
        if (authorization != null) builder.header("Authorization", authorization);
        return builder;
    }
    private Response execute(Request request) throws IOException {
        Call call = client.newCall(request); active = call;
        if (canceled) call.cancel();
        return call.execute();
    }
    private static boolean usableEtag(String value) {
        if (value == null || value.startsWith("W/")) return false;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            for (int index = 1; index < value.length() - 1; index++) {
                char c = value.charAt(index);
                if (c != 0x21 && (c < 0x23 || c > 0x7e)) return false;
            }
            return true;
        }
        // Some WebDAV services return bare revision tokens. Preserve their exact
        // value for If-Match; adding quotes can change the server's comparison.
        // Never accept wildcard, list, whitespace, weak or malformed validators.
        return value.matches("[A-Za-z0-9._~-]+");
    }
    @Override public SyncCoordinator.Fetched get() throws IOException {
        try (Response response = execute(request().get().build())) {
            if (response.code() == 404) return new SyncCoordinator.Fetched(new SyncDocument(), null, false);
            if (response.code() != 200) throw new SyncCoordinator.Failure("HTTP_" + response.code());
            String etag = response.header("ETag");
            if (!usableEtag(etag))
                throw new SyncCoordinator.Failure("SERVER_NO_STRONG_ETAG");
            if (response.body() == null || response.body().contentLength() > SyncDocument.MAX_BYTES)
                throw new SyncCoordinator.Failure("INVALID_REMOTE_DOCUMENT");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = response.body().byteStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1; ) {
                    if (bytes.size() + count > SyncDocument.MAX_BYTES) throw new SyncCoordinator.Failure("REMOTE_DOCUMENT_TOO_LARGE");
                    bytes.write(buffer, 0, count);
                }
            }
            try { return new SyncCoordinator.Fetched(SyncDocument.decode(bytes.toString(StandardCharsets.UTF_8.name())), etag, true); }
            catch (RuntimeException invalid) { throw new SyncCoordinator.Failure("INVALID_REMOTE_DOCUMENT"); }
        } finally { active = null; }
    }
    @Override public boolean put(SyncDocument document, SyncCoordinator.Fetched fetched) throws IOException {
        if (!fetched.exists) return create(document);
        Request.Builder builder = request().put(RequestBody.create(document.encode(), MediaType.get("application/json; charset=utf-8")));
        if (!usableEtag(fetched.etag)) throw new SyncCoordinator.Failure("SERVER_NO_STRONG_ETAG");
        builder.header("If-Match", fetched.etag);
        try (Response response = execute(builder.build())) {
            if (response.code() == 412) return false;
            if (response.code() != 200 && response.code() != 201 && response.code() != 204)
                throw new SyncCoordinator.Failure("HTTP_" + response.code());
            return true;
        } finally { active = null; }
    }

    private boolean create(SyncDocument document) throws IOException {
        HttpUrl temporary = url.newBuilder().setPathSegment(url.pathSize() - 1,
                "tv-sync-upload-" + UUID.randomUUID() + ".json").build();
        String temporaryEtag = null;
        boolean cleanup = false;
        try {
            Request upload = request(temporary).header("If-None-Match", "*")
                    .put(RequestBody.create(document.encode(), MediaType.get("application/json; charset=utf-8"))).build();
            cleanup = true;
            try (Response response = execute(upload)) {
                // A rejected upload may refer to somebody else's object; never delete it.
                if (response.code() == 412) { cleanup = false; return false; }
                if (response.code() != 200 && response.code() != 201 && response.code() != 204)
                    throw new SyncCoordinator.Failure("HTTP_" + response.code());
            }
            try (Response response = execute(request(temporary).get().build())) {
                if (response.code() != 200) throw new SyncCoordinator.Failure("HTTP_" + response.code());
                temporaryEtag = response.header("ETag");
                if (!usableEtag(temporaryEtag)) {
                    temporaryEtag = null;
                    throw new SyncCoordinator.Failure("SERVER_NO_STRONG_ETAG");
                }
            }
            // Some providers ignore If-None-Match on PUT. MOVE with Overwrite:F
            // atomically creates the final document without replacing a racing writer.
            Request move = request(temporary).method("MOVE", null).header("Destination", url.toString())
                    .header("Overwrite", "F").header("If-Match", temporaryEtag).build();
            try (Response response = execute(move)) {
                if (response.code() == 409 || response.code() == 412) return false;
                // 204 denotes replacement, not successful creation; never accept it here.
                if (response.code() != 201) throw new SyncCoordinator.Failure("HTTP_" + response.code());
                return true;
            }
        } finally {
            // Upload or MOVE may have committed before a timeout/cancel. Clean up
            // only our random sibling, never the final document, even on failures.
            if (cleanup) cleanupTemporary(temporary, temporaryEtag);
            active = null;
        }
    }

    private void cleanupTemporary(HttpUrl temporary, String etag) {
        Request.Builder builder = new Request.Builder().url(temporary).delete();
        if (authorization != null) builder.header("Authorization", authorization);
        if (etag != null) builder.header("If-Match", etag);
        // Best effort after cancellation; do not reset the main request's canceled flag.
        OkHttpClient cleanup = client.newBuilder().callTimeout(3, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS).writeTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
        try (Response ignored = cleanup.newCall(builder.build()).execute()) { }
        catch (IOException ignored) { }
    }
}
