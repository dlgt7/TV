package com.github.catvod.net;

import com.github.catvod.net.ech.CloudflareAddressRanges;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Same-host recovery for explicitly selected HTTPS metadata endpoints, never a mirror rewrite. */
public final class CloudflareRouteFallback implements Interceptor {
    private static final long MEMORY_MS = 60_000;
    private final BooleanSupplier enabled;
    private final Predicate<HttpUrl> selected;
    private final Predicate<String> overridden;
    private final Predicate<byte[]> cloudflare;
    private final List<InetAddress> edges;
    private volatile long generation;
    private final Map<String, Long> healthy = new LinkedHashMap<>();
    private final ThreadPoolExecutor resolver = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "MetadataRouteDns");
                thread.setDaemon(true);
                return thread;
            });

    public CloudflareRouteFallback(BooleanSupplier enabled, Predicate<HttpUrl> selected,
                                   Predicate<String> overridden) {
        this(enabled, selected, overridden, CloudflareAddressRanges::contains, defaultEdges());
    }

    CloudflareRouteFallback(BooleanSupplier enabled, Predicate<HttpUrl> selected,
                            Predicate<String> overridden, Predicate<byte[]> cloudflare,
                            List<InetAddress> edges) {
        this.enabled = enabled;
        this.selected = selected;
        this.overridden = overridden;
        this.cloudflare = cloudflare;
        this.edges = List.copyOf(edges);
        resolver.allowCoreThreadTimeOut(true);
    }

    @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();
        if (!eligible(chain, request)) return chain.proceed(request);
        long selectedGeneration = generation;
        boolean remembered = remembered(host);
        if (remembered) {
            try {
                Response response = edge(chain, request);
                if (response.isSuccessful()) return response;
                forget(host);
                return response; // HTTP responses are authoritative; do not replay throttled requests.
            } catch (IOException failure) {
                forget(host);
                if (!retryable(chain, failure)) throw failure;
            }
        }
        try {
            return chain.proceed(request);
        } catch (IOException failure) {
            if (remembered || !retryable(chain, failure) || !eligible(chain, request)
                    || !allCloudflare(chain.getDns(), host)) throw failure;
            if (chain.call().isCanceled()) throw failure;
            try {
                Response response = edge(chain, request);
                if (response.isSuccessful() && enabled.getAsBoolean()) remember(host, selectedGeneration);
                return response;
            } catch (IOException recoveryFailure) {
                recoveryFailure.addSuppressed(failure);
                throw recoveryFailure;
            }
        }
    }

    private Response edge(Chain chain, Request request) throws IOException {
        String host = request.url().host();
        Dns original = chain.getDns();
        // The original URL, SNI, hostname verification, ECH, certificate policy and headers remain intact.
        return chain.withDns(new EdgeDns(host, original, edges))
                .withConnectTimeout(chain.connectTimeoutMillis() == 0 ? 2500 : Math.min(2500, chain.connectTimeoutMillis()), TimeUnit.MILLISECONDS)
                .proceed(request);
    }

    private boolean eligible(Chain chain, Request request) {
        if (!enabled.getAsBoolean() || !selected.test(request.url()) || !request.url().isHttps()
                || request.url().port() != 443 || request.body() != null
                || !("GET".equals(request.method()) || "HEAD".equals(request.method()))
                || overridden.test(request.url().host()) || !chain.getRetryOnConnectionFailure()) return false;
        if (chain.getProxy() != null) return chain.getProxy().type() == Proxy.Type.DIRECT;
        try {
            if (chain.getProxySelector() == null) return true;
            List<Proxy> proxies = chain.getProxySelector().select(request.url().uri());
            if (proxies == null || proxies.isEmpty()) return false;
            for (Proxy proxy : proxies) if (proxy == null || proxy.type() != Proxy.Type.DIRECT) return false;
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean allCloudflare(Dns dns, String host) {
        Future<List<InetAddress>> lookup = null;
        try {
            lookup = resolver.submit(() -> dns.lookup(host));
            List<InetAddress> addresses = lookup.get(1200, TimeUnit.MILLISECONDS);
            if (addresses.isEmpty()) return false;
            for (InetAddress address : addresses) if (!cloudflare.test(address.getAddress())) return false;
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (lookup != null) lookup.cancel(true);
            resolver.purge();
        }
    }

    private static boolean retryable(Chain chain, IOException failure) {
        return !chain.call().isCanceled() && !Thread.currentThread().isInterrupted()
                && !(failure instanceof SSLHandshakeException) && !(failure instanceof SSLPeerUnverifiedException)
                && (!(failure instanceof InterruptedIOException) || failure instanceof SocketTimeoutException);
    }

    public synchronized void clear() { generation++; healthy.clear(); }

    private synchronized boolean remembered(String host) {
        Long until = healthy.get(host);
        if (until != null && until > TimeUnit.NANOSECONDS.toMillis(System.nanoTime())) return true;
        healthy.remove(host);
        return false;
    }

    private synchronized void remember(String host, long selectedGeneration) {
        if (selectedGeneration != generation) return;
        healthy.put(host, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + MEMORY_MS);
        while (healthy.size() > 8) healthy.remove(healthy.keySet().iterator().next());
    }

    private synchronized void forget(String host) { healthy.remove(host); }

    private static final class EdgeDns implements Dns {
        private final String host;
        private final Dns original;
        private final List<InetAddress> edges;

        EdgeDns(String host, Dns original, List<InetAddress> edges) {
            this.host = host;
            this.original = original;
            this.edges = edges;
        }

        @Override public List<InetAddress> lookup(String name) throws java.net.UnknownHostException {
            return host.equals(name) ? edges : original.lookup(name);
        }

        @Override public boolean equals(Object value) {
            return value instanceof EdgeDns other && host.equals(other.host) && edges.equals(other.edges);
        }

        @Override public int hashCode() { return 31 * host.hashCode() + edges.hashCode(); }
    }

    private static List<InetAddress> defaultEdges() {
        // Cloudflare anycast edge addresses. No external resolver/service receives metadata credentials.
        try {
            List<InetAddress> result = new ArrayList<>();
            result.add(InetAddress.getByAddress(new byte[]{104, 16, 0, 1}));
            result.add(InetAddress.getByAddress(new byte[]{104, 17, 0, 1}));
            return result;
        } catch (java.net.UnknownHostException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
