package com.github.catvod.net;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.github.catvod.Init;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

@Keep
public final class Net
implements AutoCloseable {
    private static final int TEXT_TIMEOUT = 15000;
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final Set<String> POST_TYPES = Set.of("json", "form", "form-data");
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Spider WebSocket timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final Cookies cookies;
    private final OkHttpClient client;
    private final Net parent;
    private final Object lock = new Object();
    private final Set<Call> calls = new HashSet<>();
    private final Set<Exchange> sockets = new HashSet<>();
    private final Set<Net> children = new HashSet<>();
    private final NetCache cache;
    private boolean closed;

    public Net() {
        this(null);
    }

    public Net(Cookies cookies) {
        this(OkHttp.client(), cookies);
    }

    public Net(Cookies cookies, String siteKey) {
        this(OkHttp.client(), cookies, null, new NetStore(siteKey));
    }

    Net(OkHttpClient client, Cookies cookies) {
        this(client, cookies, null, null);
    }

    private Net(OkHttpClient client, Cookies cookies, Net parent, NetStore store) {
        this.client = client;
        this.cookies = cookies;
        this.parent = parent;
        this.cache = store == null ? new NetCache() : new NetCache(store);
    }

    public Net session() {
        Object object = this.lock;
        synchronized (object) {
            this.checkOpen();
            Net child = new Net(this.client, new SessionCookies(), this, null);
            this.children.add(child);
            return child;
        }
    }

    public String getCookie(String url) {
        HttpUrl target;
        this.checkOpen();
        HttpUrl httpUrl = target = url == null ? null : HttpUrl.parse((String)url);
        if (this.cookies == null || target == null) {
            return "";
        }
        String value = this.cookies.getCookie(target.toString());
        return value == null ? "" : value;
    }

    public boolean setCookie(String url, String value) {
        HttpUrl target;
        this.checkOpen();
        HttpUrl httpUrl = target = url == null ? null : HttpUrl.parse((String)url);
        if (this.cookies == null || target == null || value == null || Cookie.parse((HttpUrl)target, (String)value) == null) {
            return false;
        }
        return this.cookies.setCookie(target.toString(), value);
    }

    private void checkOpen() {
        Object object = this.lock;
        synchronized (object) {
            if (this.closed) {
                throw new IllegalStateException("Network owner closed");
            }
        }
    }

    public void sleep(long milliseconds) throws InterruptedIOException {
        if (milliseconds < 0L) {
            throw new IllegalArgumentException("Sleep duration must not be negative");
        }
        long started = System.nanoTime();
        Object object = this.lock;
        synchronized (object) {
            while (true) {
                if (this.closed) {
                    throw new InterruptedIOException("Network owner closed");
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Thread interrupted");
                }
                long remaining = milliseconds - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                if (remaining <= 0L) {
                    return;
                }
                try {
                    this.lock.wait(remaining);
                }
                catch (InterruptedException e) {
                    throw Net.interrupted(e);
                }
            }
        }
    }

    public String cached(String key, String options, Callable<String> loader) throws Exception {
        if (loader == null) {
            throw new IllegalArgumentException("Cache loader is required");
        }
        CompletableFuture<String> result = this.cachedAsync(key, options, () -> this.load(loader));
        try {
            return result.get();
        }
        catch (InterruptedException e) {
            throw Net.interrupted(e);
        }
        catch (ExecutionException error) {
            Throwable throwable = error.getCause();
            if (throwable instanceof Exception) {
                Exception cause = (Exception)throwable;
                throw cause;
            }
            throwable = error.getCause();
            if (throwable instanceof Error) {
                Error cause = (Error)throwable;
                throw cause;
            }
            throw new IllegalStateException(error.getCause());
        }
    }

    private static InterruptedIOException interrupted(InterruptedException cause) {
        Thread.currentThread().interrupt();
        InterruptedIOException error = new InterruptedIOException("Thread interrupted");
        error.initCause(cause);
        return error;
    }

    private CompletableFuture<String> load(Callable<String> loader) {
        CompletableFuture<String> loading = new CompletableFuture<>();
        FutureTask<Void> task = new FutureTask<Void>(() -> {
            try {
                loading.complete((String)loader.call());
            }
            catch (Throwable error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                loading.completeExceptionally(error);
            }
            return null;
        });
        loading.whenComplete((value, error) -> {
            if (loading.isCancelled()) {
                task.cancel(true);
            }
        });
        this.client.dispatcher().executorService().execute(task);
        return loading;
    }

    public CompletableFuture<String> cachedAsync(String key, String options, Supplier<CompletableFuture<String>> loader) {
        Object object = this.lock;
        synchronized (object) {
            if (this.closed) {
                return CompletableFuture.failedFuture(new InterruptedIOException("Network owner closed"));
            }
        }
        return this.cache.cached(key, CacheOptions.from((String)options), loader);
    }

    public String peek(String key) {
        this.checkOpen();
        return this.cache.peek(key);
    }

    public String cachedValue(String key, String options) {
        this.checkOpen();
        return this.cache.value(key, CacheOptions.from((String)options));
    }

    public void refresh(String key, String options, Callable<String> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("Cache loader is required");
        }
        CacheOptions config = CacheOptions.from((String)options);
        Object object = this.lock;
        synchronized (object) {
            if (this.closed) {
                SpiderDebug.log(new InterruptedIOException("Network owner closed"));
                return;
            }
        }
        this.cache.cached(key, new CacheOptions(config.ttl, false, config.persist), () -> this.load(loader)).whenComplete((value, error) -> {
            if (error != null) {
                SpiderDebug.log(error);
            }
        });
    }

    public void clearCache(String key) {
        this.cache.clear(key);
    }

    public void clearCache() {
        this.cache.clear(null);
    }

    public String json(String url, String options) {
        String text = this.jsonText(url, options);
        Json.strict(text);
        return text;
    }

    public String jsonText(String url, String options) {
        NetOptions request = NetOptions.from(options);
        if (request.getBuffer() != 0) {
            throw new IllegalArgumentException("JSON requests require text content");
        }
        Result result = this.request(url, request);
        if (result.error != null) {
            throw new IllegalStateException(result.error);
        }
        int code = (Integer)result.code;
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        return result.text(request);
    }

    public String req(String url, String json) {
        return this.json(url, json, false);
    }

    public String get(String url) {
        return this.get(url, null);
    }

    public String get(String url, Map<String, String> headers) {
        return this.text(url, NetOptions.text("GET", null, headers, 15000));
    }

    public String get(String url, int timeout) {
        return this.text(url, NetOptions.text("GET", null, null, timeout));
    }

    public String post(String url, String json) {
        return this.post(url, json, null);
    }

    public String post(String url, String json, Map<String, String> headers) {
        return this.text(url, NetOptions.text("POST", json, headers, 15000));
    }

    private String text(String url, NetOptions options) {
        Result result = this.request(url, options);
        if (result.error != null) {
            throw new IllegalStateException(result.error);
        }
        return result.text(options);
    }

    public String ws(String url, String json) {
        return this.json(url, json, true);
    }

    private String json(String url, String json, boolean webSocket) {
        NetOptions options = new NetOptions();
        try {
            options = NetOptions.from(json);
            return (webSocket ? this.socket(url, options) : this.request(url, options)).json(options);
        }
        catch (Exception e) {
            return Result.failure(e).json(options);
        }
    }

    public Object[] reqParts(String url, String json) {
        return this.parts(url, json, false);
    }

    public Object[] wsParts(String url, String json) {
        return this.parts(url, json, true);
    }

    private Object[] parts(String url, String json, boolean webSocket) {
        NetOptions options = new NetOptions();
        try {
            options = NetOptions.from(json);
            return (webSocket ? this.socket(url, options) : this.request(url, options)).parts(options);
        }
        catch (Exception e) {
            return Result.failure(e).parts(options);
        }
    }

    private Result socket(String url, NetOptions options) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result> result = new AtomicReference<>();
        Exchange exchange = this.openSocket(url, options, value -> {
            result.set(value);
            latch.countDown();
        });
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            if (exchange != null) {
                exchange.cancel();
            }
            Thread.currentThread().interrupt();
            return Result.failure(e);
        }
        return (Result)result.get();
    }

    public Result request(String url, NetOptions options) {
        CompletableFuture<Result> future = this.requestAsync(url, options);
        try {
            return future.get();
        }
        catch (InterruptedException e) {
            future.cancel(true);
            return Result.failure(Net.interrupted(e));
        }
        catch (ExecutionException e) {
            return Result.failure(e.getCause());
        }
    }

    public Result[] batch(String requests) throws IOException {
        return this.batch(requests, 4);
    }

    public Result[] batch(String requests, int limit) throws IOException {
        return this.batch(Json.strict(requests).getAsJsonArray(), limit);
    }

    private Result[] batch(JsonArray requests, int limit) throws IOException {
        if (limit < 1) {
            throw new IllegalArgumentException("Batch limit must be positive");
        }
        for (JsonElement value : requests) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Invalid batch request");
            }
            JsonObject item = value.getAsJsonObject();
            JsonElement url = item.get("url");
            JsonElement options = item.get("options");
            if (url != null && url.isJsonPrimitive() && url.getAsJsonPrimitive().isString() && (options == null || options.isJsonNull() || options.isJsonObject())) continue;
            throw new IllegalArgumentException("Invalid batch request");
        }
        Result[] results = new Result[requests.size()];
        ArrayList<CompletableFuture<Result>> active = new ArrayList<>();
        try {
            for (int start = 0; start < requests.size(); start += active.size()) {
                int index;
                active.clear();
                for (index = start; index < requests.size() && active.size() < limit; ++index) {
                    JsonObject item = requests.get(index).getAsJsonObject();
                    String url = item.get("url").getAsString();
                    active.add(this.requestAsync(url, NetOptions.from(item.get("options") == null ? null : item.get("options").toString())));
                }
                for (index = 0; index < active.size(); ++index) {
                    results[start + index] = (Result)((CompletableFuture)active.get(index)).get();
                }
            }
            Result[] start = results;
            return start;
        }
        catch (InterruptedException e) {
            throw Net.interrupted(e);
        }
        catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        finally {
            for (CompletableFuture completableFuture : active) {
                if (completableFuture.isDone()) continue;
                completableFuture.cancel(true);
            }
        }
    }

    public Object[][] batchParts(String requests, int limit) throws IOException {
        JsonArray items = Json.strict(requests).getAsJsonArray();
        Result[] results = this.batch(items, limit);
        Object[][] parts = new Object[results.length][];
        for (int i = 0; i < parts.length; ++i) {
            JsonElement options = items.get(i).getAsJsonObject().get("options");
            parts[i] = results[i].parts(NetOptions.from(options == null ? null : options.toString()));
        }
        return parts;
    }

    public void download(String url, String json, File file) throws IOException {
        CompletableFuture<String> future = this.downloadAsync(url, json, file);
        try {
            future.get();
        }
        catch (InterruptedException e) {
            future.cancel(true);
            throw Net.interrupted(e);
        }
        catch (ExecutionException e) {
            Throwable throwable = e.getCause();
            if (throwable instanceof IOException) {
                IOException error = (IOException)throwable;
                throw error;
            }
            throw new IOException(e.getCause());
        }
    }

    public CompletableFuture<String> downloadAsync(String url, String json, File file) {
        Call call;
        final File target = file.isAbsolute() ? file : new File(Init.context().getCacheDir(), file.getPath());
        final CompletableFuture<String> future = new CompletableFuture<>();
        try {
            call = this.newCall(url, NetOptions.from(json));
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        future.whenComplete((result, error) -> {
            if (future.isCancelled()) {
                call.cancel();
            }
        });
        call.enqueue(new Callback(){

            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Net.this.remove(call);
                future.completeExceptionally(e);
            }

            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response response2 = response;){
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code());
                    }
                    try (InputStream input = response.body().byteStream();
                         FileOutputStream output = new FileOutputStream(target);){
                        int size;
                        byte[] buffer = new byte[16384];
                        while ((size = input.read(buffer)) != -1) {
                            output.write(buffer, 0, size);
                        }
                    }
                    future.complete(target.getAbsolutePath());
                }
                catch (Exception e) {
                    future.completeExceptionally(e);
                }
                finally {
                    Net.this.remove(call);
                }
            }
        });
        return future;
    }

    public void enqueue(String url, NetOptions options, Consumer<Result> complete) {
        this.requestAsync(url, options).whenComplete((result, error) -> complete.accept(error == null ? result : Result.failure(error)));
    }

    public CompletableFuture<Result> requestAsync(String url, NetOptions options) {
        Call call;
        final CompletableFuture<Result> future = new CompletableFuture<>();
        try {
            call = this.newCall(url, options);
        }
        catch (Exception e) {
            return CompletableFuture.completedFuture(Result.failure(e));
        }
        future.whenComplete((result, error) -> {
            if (future.isCancelled()) {
                call.cancel();
            }
        });
        call.enqueue(new Callback(){

            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Net.this.remove(call);
                future.complete(Result.failure(call, e));
            }

            public void onResponse(@NonNull Call call, @NonNull Response response) {
                Result result;
                try (Response response2 = response;){
                    result = Result.read(response);
                }
                catch (Exception e) {
                    result = Result.failure(call, e);
                }
                finally {
                    Net.this.remove(call);
                }
                future.complete(result);
            }
        });
        return future;
    }

    public void webSocket(String url, NetOptions options, Consumer<Result> complete) {
        this.openSocket(url, options, complete);
    }

    public CompletableFuture<Result> socketAsync(String url, NetOptions options) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        Exchange exchange = this.openSocket(url, options, future::complete);
        future.whenComplete((result, error) -> {
            if (future.isCancelled() && exchange != null) {
                exchange.cancel();
            }
        });
        return future;
    }

    private Call newCall(String url, NetOptions options) throws IOException {
        Request request = this.buildRequest(url, options, false);
        OkHttpClient http = this.builder(options).addNetworkInterceptor(chain -> this.interceptCookies(chain, request, options)).build();
        Call call = http.newCall(request);
        Object object = this.lock;
        synchronized (object) {
            if (this.closed) {
                throw new InterruptedIOException("Network owner closed");
            }
            this.calls.add(call);
        }
        return call;
    }

    private Response interceptCookies(Interceptor.Chain chain, Request request, NetOptions options) throws IOException {
        Request next = this.withCookies(chain.request(), request.url(), request.header("Cookie"), options);
        Response response = chain.proceed(next);
        try {
            this.saveCookies(response, options);
            return response;
        }
        catch (IOException | RuntimeException e) {
            response.close();
            throw e;
        }
    }

    private OkHttpClient.Builder builder(NetOptions options) {
        OkHttpClient.Builder builder = client.newBuilder()
                .connectTimeout(options.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(options.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(options.getTimeout(), TimeUnit.MILLISECONDS)
                .callTimeout(options.getCallTimeout(), TimeUnit.MILLISECONDS)
                .followSslRedirects(options.isRedirect());
        // The shared client chooses a proxy again on every redirect. Native redirects
        // would bypass that interceptor and retain the previous host's proxy policy.
        boolean routedRedirects = builder.interceptors().stream().anyMatch(item -> item instanceof ProxyRedirectInterceptor);
        if (!options.isRedirect()) builder.interceptors().removeIf(item -> item instanceof ProxyRedirectInterceptor);
        return builder.followRedirects(options.isRedirect() && !routedRedirects);
    }

    private Request buildRequest(String url, NetOptions options, boolean socket) {
        Headers headers = Headers.of(options.getHeader());
        Request.Builder request = new Request.Builder().url(url).headers(headers);
        if (options.getData() != null && options.getBody() != null) {
            throw new IllegalArgumentException("data and body are mutually exclusive");
        }
        if (!socket) {
            String method = this.requestMethod(options);
            request.method(method, this.requestBody(method, options, headers));
        }
        return this.withParams(request.build(), options.getParams());
    }

    private String requestMethod(NetOptions options) {
        String method = options.getMethod().toUpperCase(Locale.ROOT);
        if (method.equals("HEADER")) {
            method = "HEAD";
        }
        if (!METHODS.contains(method)) {
            throw new IllegalArgumentException("Unsupported method");
        }
        return method;
    }

    private RequestBody requestBody(String method, NetOptions options, Headers headers) {
        boolean accepts;
        JsonElement data = options.getData();
        String raw = options.getBody();
        String postType = options.getPostType();
        if (!POST_TYPES.contains(postType)) {
            throw new IllegalArgumentException("Unsupported postType");
        }
        boolean required = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
        boolean supplied = data != null || raw != null;
        boolean bl = accepts = !method.equals("GET") && !method.equals("HEAD");
        if (!accepts && supplied) {
            throw new IllegalArgumentException("Method does not accept a body");
        }
        if (data != null && !postType.equals("json") && !data.isJsonObject()) {
            throw new IllegalArgumentException("Form data must be an object");
        }
        if (raw != null && headers.get("Content-Type") == null) {
            throw new IllegalArgumentException("Raw body requires Content-Type");
        }
        if (!accepts || !required && !supplied) {
            return null;
        }
        return this.encodeBody(options, headers.get("Content-Type"));
    }

    private Request withParams(Request request, JsonElement params) {
        if (params == null || params.isJsonNull()) {
            return request;
        }
        if (!params.isJsonObject()) {
            throw new IllegalArgumentException("params must be an object");
        }
        HttpUrl.Builder url = request.url().newBuilder();
        for (Map.Entry<String, JsonElement> entry : params.getAsJsonObject().entrySet()) {
            JsonElement value = (JsonElement)entry.getValue();
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    this.addParam(url, (String)entry.getKey(), item);
                }
                continue;
            }
            this.addParam(url, (String)entry.getKey(), value);
        }
        return request.newBuilder().url(url.build()).build();
    }

    private void addParam(HttpUrl.Builder url, String key, JsonElement value) {
        if (value.isJsonNull()) {
            return;
        }
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Query values must be scalar");
        }
        url.addQueryParameter(key, value.getAsString());
    }

    private RequestBody encodeBody(NetOptions options, String contentType) {
        JsonElement data = options.getData();
        String raw = options.getBody();
        if (data == null) {
            return raw != null && contentType != null ? RequestBody.create((String)raw, (MediaType)MediaType.get((String)contentType)) : RequestBody.create((byte[])new byte[0]);
        }
        return switch (options.getPostType()) {
            case "json" -> RequestBody.create((String)data.toString(), (MediaType)MediaType.get((String)"application/json; charset=utf-8"));
            case "form" -> {
                FormBody.Builder body = new FormBody.Builder();
                NetOptions.toMap(data).forEach((arg_0, arg_1) -> ((FormBody.Builder)body).add(arg_0, arg_1));
                yield body.build();
            }
            case "form-data" -> {
                MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
                NetOptions.toMap(data).forEach((arg_0, arg_1) -> ((MultipartBody.Builder)body).addFormDataPart(arg_0, arg_1));
                yield body.build();
            }
            default -> throw new IllegalArgumentException("Unsupported postType");
        };
    }

    private Request withCookies(Request request, HttpUrl origin, String explicit, NetOptions options) throws IOException {
        if (explicit == null && !this.usesCookies(options)) {
            return request;
        }
        Request.Builder builder = request.newBuilder().removeHeader("Cookie");
        String value = null;
        if (explicit != null && this.sameOrigin(origin, request.url())) {
            value = explicit;
        } else if (this.usesCookies(options)) {
            try {
                value = this.getCookie(request.url().toString());
            }
            catch (RuntimeException e) {
                throw this.cookieFailure(e);
            }
        }
        if (value != null && !value.isEmpty()) {
            builder.header("Cookie", value);
        }
        return builder.build();
    }

    private void saveCookies(Response response, NetOptions options) throws IOException {
        if (!this.usesCookies(options)) {
            return;
        }
        HttpUrl url = response.request().url();
        try {
            for (String value : response.headers("Set-Cookie")) {
                this.setCookie(url.toString(), value);
            }
        }
        catch (RuntimeException e) {
            throw this.cookieFailure(e);
        }
    }

    private IOException cookieFailure(RuntimeException cause) {
        Object object = this.lock;
        synchronized (object) {
            IOException error = this.closed ? new InterruptedIOException("Network owner closed") : new IOException("Cookie provider failed");
            error.initCause(cause);
            return error;
        }
    }

    private boolean usesCookies(NetOptions options) {
        return this.parent != null || options.isCookie();
    }

    private boolean sameOrigin(HttpUrl first, HttpUrl second) {
        return first.scheme().equals(second.scheme()) && first.host().equals(second.host()) && first.port() == second.port();
    }

    private Exchange openSocket(String url, NetOptions options, Consumer<Result> complete) {
        Exchange exchange = null;
        try {
            if (options.getTimeout() <= 0) {
                throw new IllegalArgumentException("WebSocket requires a deadline");
            }
            Request request = this.buildRequest(url, options, true);
            request = this.withCookies(request, request.url(), request.header("Cookie"), options);
            OkHttpClient socket = this.socketClient(options);
            exchange = new Exchange(options, complete);
            Object object = this.lock;
            synchronized (object) {
                if (this.closed) {
                    throw new InterruptedIOException("Network owner closed");
                }
                this.sockets.add(exchange);
            }
            exchange.start(socket, request);
            return exchange;
        }
        catch (Exception e) {
            if (exchange != null) {
                exchange.finish(Result.failure(e));
            } else {
                complete.accept(Result.failure(e));
            }
            return null;
        }
    }

    private OkHttpClient socketClient(NetOptions options) {
        // Keep the application's DNS, ECH socket factory and proxy authentication.
        OkHttpClient.Builder builder = builder(options).followRedirects(false)
                .followSslRedirects(false).retryOnConnectionFailure(false)
                .webSocketCloseTimeout(Math.min(options.getTimeout(), 1000), TimeUnit.MILLISECONDS);
        builder.interceptors().removeIf(item -> item instanceof ProxyRedirectInterceptor);
        return builder.build();
    }

    private void remove(Call call) {
        Object object = this.lock;
        synchronized (object) {
            this.calls.remove(call);
        }
    }

    @Override
    public void close() {
        ArrayList<Net> sessions;
        ArrayList<Exchange> active;
        ArrayList<Call> pending;
        Object object = this.lock;
        synchronized (object) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.lock.notifyAll();
            pending = new ArrayList<>(this.calls);
            active = new ArrayList<>(this.sockets);
            sessions = new ArrayList<>(this.children);
            this.children.clear();
        }
        this.cache.close();
        Cookies cookies = this.cookies;
        if (cookies instanceof SessionCookies) {
            SessionCookies local = (SessionCookies)cookies;
            local.close();
        }
        for (Call call : pending) {
            call.cancel();
        }
        for (Exchange exchange : active) {
            exchange.cancel();
        }
        for (Net session : sessions) {
            session.close();
        }
        if (this.parent != null) {
            object = this.parent.lock;
            synchronized (object) {
                this.parent.children.remove(this);
            }
        }
    }

    @Keep
    public static interface Cookies {
        public String getCookie(String var1);

        public boolean setCookie(String var1, String var2);
    }

    private static final class SessionCookies
    implements Cookies {
        private final List<Cookie> values = new ArrayList<>();
        private boolean closed;

        private SessionCookies() {
        }

        @Override
        public synchronized String getCookie(String url) {
            this.checkOpen();
            HttpUrl target = HttpUrl.get((String)url);
            this.values.removeIf(cookie -> cookie.expiresAt() <= System.currentTimeMillis());
            ArrayList<Cookie> matching = new ArrayList<>();
            for (Cookie cookie2 : this.values) {
                if (!cookie2.matches(target)) continue;
                matching.add(cookie2);
            }
            matching.sort(Comparator.comparingInt((Cookie cookie) -> cookie.path().length()).reversed());
            ArrayList<String> result = new ArrayList<>();
            for (Cookie cookie3 : matching) {
                result.add(cookie3.name() + "=" + cookie3.value());
            }
            return String.join((CharSequence)"; ", result);
        }

        @Override
        public synchronized boolean setCookie(String url, String value) {
            this.checkOpen();
            Cookie cookie = Cookie.parse((HttpUrl)HttpUrl.get((String)url), (String)value);
            if (cookie == null) {
                return false;
            }
            for (int i = 0; i < this.values.size(); ++i) {
                Cookie previous = this.values.get(i);
                if (!previous.name().equals(cookie.name()) || !previous.domain().equals(cookie.domain()) || !previous.path().equals(cookie.path())) continue;
                if (cookie.expiresAt() <= System.currentTimeMillis()) {
                    this.values.remove(i);
                } else {
                    this.values.set(i, cookie);
                }
                return true;
            }
            if (cookie.expiresAt() > System.currentTimeMillis()) {
                this.values.add(cookie);
            }
            return true;
        }

        private synchronized void close() {
            this.closed = true;
            this.values.clear();
        }

        private void checkOpen() {
            if (this.closed) {
                throw new IllegalStateException("Network owner closed");
            }
        }
    }

    public static final class Result {
        private static final Gson GSON = new Gson();
        public final Object code;
        public final Map<String, Object> headers;
        public final byte[] body;
        public final String error;
        private final boolean http;

        private Result(Object code, Map<String, Object> headers, byte[] body, String error, boolean http) {
            this.code = code;
            this.headers = headers;
            this.body = body;
            this.error = error;
            this.http = http;
        }

        public static Result failure(Throwable error) {
            String message = "Network request failed";
            if (error instanceof SSLException) {
                message = "TLS verification failed";
            } else if (Result.isTimeout(error)) {
                message = "Timed out";
            } else if (error instanceof InterruptedIOException || error instanceof InterruptedException || error instanceof CancellationException) {
                message = "Canceled";
            } else if (error instanceof UnknownHostException) {
                message = "Host resolution failed";
            } else if (error instanceof IllegalArgumentException) {
                message = "Invalid request options";
            }
            return Result.failure(error.getClass().getSimpleName() + ": " + message);
        }

        private static Result failure(Call call, Throwable error) {
            return Result.failure(call != null && call.isCanceled() && !Result.isTimeout(error) ? new InterruptedIOException() : error);
        }

        private static boolean isTimeout(Throwable error) {
            return error instanceof SocketTimeoutException || error instanceof InterruptedIOException && "timeout".equals(error.getMessage());
        }

        private static Result failure(String error) {
            return new Result("", new LinkedHashMap<>(), new byte[0], error, false);
        }

        private static Result read(Response response) throws IOException {
            return Result.success(response.code(), response.headers(), response.body().bytes(), true);
        }

        private static Result success(int code, Headers headers, byte[] body, boolean http) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : headers.toMultimap().entrySet()) {
                values.put((String)entry.getKey(), ((List)entry.getValue()).size() == 1 ? ((List)entry.getValue()).get(0) : entry.getValue());
            }
            return new Result(code, values, body, null, http);
        }

        private static Result handshakeFailure(Response response) {
            Result result = Result.success(response.code(), response.headers(), new byte[0], true);
            return new Result(result.code, result.headers, result.body, "WebSocket handshake failed (HTTP " + response.code() + ")", false);
        }

        public String json(NetOptions options) {
            Map<String, Object> result = this.metadata();
            result.put("content", this.content(options));
            return GSON.toJson(result);
        }

        public Object[] parts(NetOptions options) {
            return new Object[]{GSON.toJson(this.metadata()), this.content(options)};
        }

        private Map<String, Object> metadata() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("code", this.code);
            result.put("headers", this.headers);
            if (this.error != null) {
                result.put("error", this.error);
            }
            return result;
        }

        private Object content(NetOptions options) {
            if (this.error != null) {
                return "";
            }
            return switch (options.getBuffer()) {
                case 0 -> this.text(options);
                case 1, 3 -> this.body;
                case 2 -> ByteString.of((byte[])this.body).base64();
                default -> "";
            };
        }

        public String text() {
            return this.text(new NetOptions());
        }

        private String text(NetOptions options) {
            if (!http) return new String(body, Charset.forName(options.getCharset()));
            try (ResponseBody response = ResponseBody.create(body, contentType())) {
                return response.string();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private MediaType contentType() {
            for (Map.Entry<String, Object> entry : this.headers.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase("Content-Type")) continue;
                Object value = entry.getValue();
                if (value instanceof List) {
                    List values = (List)value;
                    value = values.get(values.size() - 1);
                }
                return MediaType.parse((String)String.valueOf(value));
            }
            return null;
        }
    }

    private final class Exchange
    extends WebSocketListener {
        private final NetOptions options;
        private final Consumer<Result> complete;
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile WebSocket socket;
        private volatile ScheduledFuture<?> deadline;
        private volatile Headers headers = new Headers.Builder().build();
        private volatile boolean closing;

        private Exchange(NetOptions options, Consumer<Result> complete) {
            this.options = options;
            this.complete = complete;
        }

        private void start(OkHttpClient client, Request request) {
            WebSocket created;
            if (this.done.get()) {
                return;
            }
            this.deadline = DEADLINES.schedule(() -> {
                this.finish(Result.failure(new SocketTimeoutException()));
                this.cancel();
            }, (long)this.options.getTimeout(), TimeUnit.MILLISECONDS);
            if (this.done.get()) {
                this.deadline.cancel(false);
                return;
            }
            this.socket = created = client.newWebSocket(request, (WebSocketListener)this);
            if (this.done.get() && !this.closing) {
                created.cancel();
            }
        }

        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            String message;
            this.socket = webSocket;
            this.headers = response.headers();
            try {
                Net.this.saveCookies(response, this.options);
                if (this.done.get()) {
                    webSocket.cancel();
                    return;
                }
                message = this.message();
            }
            catch (Exception e) {
                this.finish(Result.failure(e));
                return;
            }
            if (message != null && !webSocket.send(message)) {
                this.finish(Result.failure("IOException: WebSocket send failed"));
            }
        }

        private String message() {
            JsonElement data = this.options.getData();
            if (data == null) {
                return this.options.getBody();
            }
            if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) {
                return data.getAsString();
            }
            return data.toString();
        }

        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            this.finish(Result.success(101, this.headers, text.getBytes(StandardCharsets.UTF_8), false));
        }

        public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
            this.finish(Result.success(101, this.headers, bytes.toByteArray(), false));
        }

        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            this.finish(Result.failure("IOException: WebSocket closed before a message"));
            webSocket.close(code, reason);
        }

        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            this.finish(Result.failure("IOException: WebSocket closed before a message"));
            this.release();
        }

        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable error, @Nullable Response response) {
            this.finish(this.failure(error, response));
            this.release();
        }

        private Result failure(Throwable error, Response response) {
            if (response == null) return Result.failure(error);
            try (response) {
                Net.this.saveCookies(response, options);
                return Result.handshakeFailure(response);
            } catch (Exception e) {
                return Result.failure(e);
            }
        }

        private void cancel() {
            this.finish(Result.failure(new InterruptedIOException()));
            WebSocket current = this.socket;
            if (current != null) {
                current.cancel();
            }
            this.release();
        }

        private void finish(Result result) {
            if (!this.done.compareAndSet(false, true)) {
                return;
            }
            this.closing = result.error == null;
            WebSocket current = this.socket;
            if (current != null) {
                if (this.closing) {
                    current.close(1000, null);
                } else {
                    current.cancel();
                }
            }
            if (!this.closing) {
                this.release();
            }
            this.complete.accept(result);
        }

        private void release() {
            ScheduledFuture<?> timer = this.deadline;
            if (timer != null) {
                timer.cancel(false);
            }
            Object object = Net.this.lock;
            synchronized (object) {
                Net.this.sockets.remove((Object)this);
            }
        }
    }

}
