package com.fongmi.android.tv.sync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        if (canceled || Thread.currentThread().isInterrupted()) throw new IOException("SYNC_CANCELED");
        Request.Builder builder = new Request.Builder().url(url).header("Cache-Control", "no-cache");
        if (authorization != null) builder.header("Authorization", authorization);
        return builder;
    }
    private Response execute(Request request) throws IOException {
        Call call = client.newCall(request); active = call;
        if (canceled) call.cancel();
        return call.execute();
    }
    @Override public SyncCoordinator.Fetched get() throws IOException {
        try (Response response = execute(request().get().build())) {
            if (response.code() == 404) return new SyncCoordinator.Fetched(new SyncDocument(), null, false);
            if (response.code() != 200) throw new SyncCoordinator.Failure("HTTP_" + response.code());
            String etag = response.header("ETag");
            if (etag == null || etag.startsWith("W/") || !etag.startsWith("\"") || !etag.endsWith("\""))
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
        Request.Builder builder = request().put(RequestBody.create(document.encode(), MediaType.get("application/json; charset=utf-8")));
        if (fetched.exists) {
            if (fetched.etag == null) throw new SyncCoordinator.Failure("SERVER_NO_STRONG_ETAG");
            builder.header("If-Match", fetched.etag);
        } else builder.header("If-None-Match", "*");
        try (Response response = execute(builder.build())) {
            if (response.code() == 412) return false;
            if (response.code() != 200 && response.code() != 201 && response.code() != 204)
                throw new SyncCoordinator.Failure("HTTP_" + response.code());
            return true;
        } finally { active = null; }
    }
}
