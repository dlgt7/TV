package com.fongmi.android.tv.drive;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.CookieJar;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Anonymous metadata checks only. Never fetches files or sends the app's stored credentials. */
public final class DriveLinkChecker {

    private static final Object SHARED = new Object();
    private static final int LIMIT = 128;
    private static final int BODY_LIMIT = 256 * 1024;
    private static final Semaphore NETWORK = new Semaphore(3, true);
    private static final Map<String, Flight> FLIGHTS = new HashMap<>();
    private static final LinkedHashMap<String, Cached> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Set<Call> calls = new HashSet<>();
    private final OkHttpClient client;
    private final Endpoints endpoints;
    private final long timeoutNanos;
    private volatile boolean cancelled;

    public DriveLinkChecker(OkHttpClient client) {
        this(client, new Endpoints(
                "https://drive-h.quark.cn/1/clouddrive/share/sharepage/token",
                "https://api.aliyundrive.com/v2/share_link/get_share_token",
                "https://115cdn.com/webapi/share/snap"), 12000);
    }

    /** Package-private endpoint injection is for local HTTP fixtures, never user-supplied URLs. */
    DriveLinkChecker(OkHttpClient client, Endpoints endpoints, long timeoutMs) {
        OkHttpClient.Builder builder = client.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
                .eventListener(EventListener.NONE)
                .proxyAuthenticator(Authenticator.NONE).cache(null)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS);
        builder.interceptors().clear();
        builder.networkInterceptors().clear();
        this.client = builder.build();
        this.endpoints = endpoints;
        timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMs));
    }

    /** Cancellation is permanent for this checker; use a new instance for another UI action. */
    public void cancel() {
        cancelled = true;
        ArrayList<Call> active;
        synchronized (calls) { active = new ArrayList<>(calls); }
        for (Call call : active) call.cancel();
        synchronized (SHARED) { SHARED.notifyAll(); }
    }

    public DriveCheckResult check(String text) throws IOException {
        checkCancelled();
        long deadline = System.nanoTime() + timeoutNanos;
        DriveLink link = DriveLink.parse(text);
        if (link == null) return new DriveCheckResult(DriveCheckResult.Status.UNSUPPORTED, "", "");
        if (link.provider.equals("115") && link.password.isEmpty()) return result(link, DriveCheckResult.Status.LOCKED);
        String key = key(endpoints.namespace + "\n" + link.provider + "\n" + link.id + "\n" + link.password);
        Flight flight;
        while (true) {
            checkCancelled();
            synchronized (SHARED) {
                long now = System.nanoTime();
                if (now - deadline >= 0) return result(link, DriveCheckResult.Status.UNCERTAIN);
                Cached cached = CACHE.get(key);
                if (cached != null) {
                    if (now - cached.expiresAt < 0) return cached.result;
                    CACHE.remove(key);
                }
                flight = FLIGHTS.get(key);
                if (flight == null) {
                    if (FLIGHTS.size() >= LIMIT) return result(link, DriveCheckResult.Status.UNCERTAIN);
                    flight = new Flight();
                    FLIGHTS.put(key, flight);
                    break;
                }
                while (!flight.done) {
                    checkCancelled();
                    long left = deadline - System.nanoTime();
                    if (left <= 0) return result(link, DriveCheckResult.Status.UNCERTAIN);
                    try { SHARED.wait(Math.max(1, Math.min(100, TimeUnit.NANOSECONDS.toMillis(left)))); }
                    catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                        throw cancelledException();
                    }
                }
                checkCancelled();
                if (flight.result != null) return flight.result;
                // A cancelled owner does not poison other callers of the same link.
            }
        }
        boolean acquired = false;
        DriveCheckResult checked = null;
        try {
            while (!acquired) {
                checkCancelled();
                long left = deadline - System.nanoTime();
                if (left <= 0) return checked = result(link, DriveCheckResult.Status.UNCERTAIN);
                try { acquired = NETWORK.tryAcquire(Math.min(left, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS); }
                catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    throw cancelledException();
                }
            }
            checked = fetch(link, deadline);
            checkCancelled();
            return checked;
        } catch (IOException failure) {
            checkCancelled();
            return checked = result(link, DriveCheckResult.Status.UNCERTAIN);
        } finally {
            if (acquired) NETWORK.release();
            synchronized (SHARED) {
                if (cancelled || Thread.currentThread().isInterrupted()) checked = null;
                flight.result = checked;
                flight.done = true;
                FLIGHTS.remove(key);
                if (checked != null) {
                    long ttl = checked.status == DriveCheckResult.Status.UNCERTAIN ? 10 : 120;
                    CACHE.put(key, new Cached(checked, System.nanoTime() + TimeUnit.SECONDS.toNanos(ttl)));
                    while (CACHE.size() > LIMIT) CACHE.remove(CACHE.keySet().iterator().next());
                }
                SHARED.notifyAll();
            }
        }
    }

    private DriveCheckResult fetch(DriveLink link, long deadline) throws IOException {
        checkCancelled();
        Request.Builder request = new Request.Builder().header("Accept", "application/json")
                .header("Accept-Encoding", "identity").header("User-Agent", "TV/DriveLinkCheck");
        JsonObject payload = new JsonObject();
        if (link.provider.equals("夸克")) {
            payload.addProperty("pwd_id", link.id);
            payload.addProperty("passcode", link.password);
            payload.addProperty("support_visit_limit_private_share", true);
            request.url(endpoints.quark).header("Origin", "https://pan.quark.cn")
                    .header("Referer", "https://pan.quark.cn/")
                    .post(RequestBody.create(payload.toString(), JSON));
        } else if (link.provider.equals("阿里云盘")) {
            payload.addProperty("share_id", link.id);
            payload.addProperty("share_pwd", link.password);
            request.url(endpoints.ali).header("Origin", "https://www.alipan.com")
                    .header("Referer", "https://www.alipan.com/")
                    .post(RequestBody.create(payload.toString(), JSON));
        } else {
            request.url(endpoints.drive115.newBuilder().addQueryParameter("share_code", link.id)
                    .addQueryParameter("receive_code", link.password).addQueryParameter("offset", "0")
                    .addQueryParameter("limit", "1").addQueryParameter("cid", "").build())
                    .header("Referer", "https://115cdn.com/").header("X-Requested-With", "XMLHttpRequest");
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return result(link, DriveCheckResult.Status.UNCERTAIN);
        Call call = client.newCall(request.build());
        call.timeout().timeout(remaining, TimeUnit.NANOSECONDS);
        synchronized (calls) {
            checkCancelled();
            calls.add(call);
        }
        try (Response response = call.execute()) {
            checkCancelled();
            if (response.body() == null || response.body().contentLength() > BODY_LIMIT) return result(link, DriveCheckResult.Status.UNCERTAIN);
            try (InputStream input = response.body().byteStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (bytes.size() + read > BODY_LIMIT || deadline - System.nanoTime() <= 0) return result(link, DriveCheckResult.Status.UNCERTAIN);
                    bytes.write(buffer, 0, read);
                }
                return result(link, DriveResponseClassifier.classify(link.provider, response.code(),
                        new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            }
        } finally {
            synchronized (calls) { calls.remove(call); }
        }
    }

    private void checkCancelled() throws InterruptedIOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw cancelledException();
    }

    private static InterruptedIOException cancelledException() {
        return new InterruptedIOException("Drive link check cancelled");
    }

    private static DriveCheckResult result(DriveLink link, DriveCheckResult.Status status) {
        return new DriveCheckResult(status, link.provider, link.url);
    }

    private static String key(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : hash) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static final class Endpoints {
        final HttpUrl quark, ali, drive115;
        final String namespace;
        Endpoints(String quark, String ali, String drive115) {
            this.quark = HttpUrl.get(quark);
            this.ali = HttpUrl.get(ali);
            this.drive115 = HttpUrl.get(drive115);
            namespace = quark + "\n" + ali + "\n" + drive115;
        }
    }

    private static final class Flight {
        boolean done;
        DriveCheckResult result;
    }

    private static final class Cached {
        final DriveCheckResult result;
        final long expiresAt;
        Cached(DriveCheckResult result, long expiresAt) { this.result = result; this.expiresAt = expiresAt; }
    }
}
