package com.fongmi.android.tv.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Bounded metadata requests with fallback on transport AND parsing failures. */
public final class UpdateRepository {
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private final OkHttpClient client;
    private volatile Call call;
    private volatile boolean cancelled;

    public UpdateRepository(OkHttpClient client) {
        this.client = client.newBuilder().connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
                .followSslRedirects(false).build();
    }

    public UpdateManifest manifest(String packageName, String mode, String abi, int installedCode) throws IOException {
        return manifest(UpdateSources.manifests(mode), packageName, mode, abi, installedCode);
    }

    UpdateManifest manifest(List<String> urls, String packageName, String mode, String abi, int installedCode) throws IOException {
        UpdateManifest newest = null;
        IOException failure = new IOException("No update manifest available");
        for (String url : urls) {
            checkCancelled();
            try {
                UpdateManifest result = UpdateManifest.parse(read(url), packageName, mode, abi);
                if (newest == null || newest.code < result.code) newest = result;
                // Do not report 'up to date' from a stale mirror without trying the remaining sources.
                if (result.code > installedCode) return result;
            } catch (IOException e) {
                failure = e;
            }
        }
        checkCancelled();
        if (newest != null) return newest;
        throw failure;
    }

    public UpdatePolicy policy() throws IOException {
        return policy(UpdateSources.policies());
    }

    UpdatePolicy policy(List<String> urls) throws IOException {
        IOException failure = new IOException("No update policy available");
        for (String url : urls) {
            checkCancelled();
            try {
                return UpdatePolicy.parse(read(url));
            } catch (IOException e) {
                failure = e;
            }
        }
        checkCancelled();
        throw failure;
    }

    private String read(String url) throws IOException {
        checkCancelled();
        Call request = client.newCall(new Request.Builder().url(url)
                .header("Accept", "application/json").header("Cache-Control", "no-cache").build());
        call = request;
        if (cancelled) request.cancel();
        try (Response response = request.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Update metadata HTTP " + response.code());
            if (response.body().contentLength() > MAX_JSON_BYTES) throw new IOException("Update metadata too large");
            try (InputStream input = response.body().byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > MAX_JSON_BYTES) throw new IOException("Update metadata too large");
                    output.write(buffer, 0, count);
                }
                checkCancelled();
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            call = null;
        }
    }

    public void cancel() {
        cancelled = true;
        Call pending = call;
        if (pending != null) pending.cancel();
    }

    private void checkCancelled() throws InterruptedIOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Update check cancelled");
    }
}
