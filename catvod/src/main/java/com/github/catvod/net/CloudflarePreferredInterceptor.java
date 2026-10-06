package com.github.catvod.net;

import com.github.catvod.net.ech.CloudflareAddressRanges;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.connection.RealConnection;

/** Optional CF edge routing. Only dial addresses change; origin URL, Host and TLS remain intact. */
final class CloudflarePreferredInterceptor implements Interceptor {
    private static final long LOOKUP_MS = 1500;
    private static final long POSITIVE_MS = 60_000;
    private static final long NEGATIVE_MS = 10_000;
    private static final long COOLDOWN_MS = 60_000;
    private static final int CACHE_LIMIT = 128;
    private final Object lock = new Object();
    private final Dns sharedDns;
    private final Supplier<String> domain;
    private final Predicate<String> overridden;
    private final Predicate<byte[]> cloudflare;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Lookup> lookups = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashMap<String, Long> cooldowns = new LinkedHashMap<>(16, .75f, true);
    // A completed Future can wake its caller before the worker returns to take().
    // Buffer that handoff without growing DNS concurrency or allowing an unbounded backlog.
    private final ThreadPoolExecutor resolver = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "CloudflarePreferredDns");
                thread.setDaemon(true);
                return thread;
            });
    private volatile long generation;

    CloudflarePreferredInterceptor(OkDns dns) {
        this(dns, CloudflarePreferredSettings::getDomain, dns::hasOverride,
                CloudflareAddressRanges::contains, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    /** Package-local injection supports real TLS/connection tests without public routing overrides. */
    CloudflarePreferredInterceptor(Dns sharedDns, Supplier<String> domain,
                                   Predicate<String> overridden, Predicate<byte[]> cloudflare,
                                   LongSupplier clock) {
        this.sharedDns = sharedDns;
        this.domain = domain;
        this.overridden = overridden;
        this.cloudflare = cloudflare;
        this.clock = clock;
        resolver.allowCoreThreadTimeOut(true);
    }

    void clear() {
        synchronized (lock) {
            generation++;
            for (Lookup lookup : lookups.values()) if (lookup.future != null) lookup.future.cancel(true);
            resolver.purge();
            lookups.clear();
            cooldowns.clear();
        }
    }

    /** Capture the exact connection, including one reused from the pool without another DNS call. */
    Interceptor networkInterceptor() {
        return chain -> {
            Attempt attempt = chain.request().tag(Attempt.class);
            if (attempt != null) attempt.connection = chain.connection();
            return chain.proceed(chain.request());
        };
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String preferred;
        try {
            preferred = CloudflarePreferredSettings.normalize(domain.get());
        } catch (IllegalArgumentException ignored) {
            return chain.proceed(original);
        }
        if (preferred.isEmpty() || chain.getDns() != sharedDns
                || !chain.getRetryOnConnectionFailure() || !original.url().isHttps()
                || original.url().port() != 443 || original.body() != null
                || !("GET".equals(original.method()) || "HEAD".equals(original.method()))
                || !isDomain(original.url().host()) || original.url().host().equals(preferred)
                || overridden.test(original.url().host()) || overridden.test(preferred)
                || !direct(chain, original)) return chain.proceed(original);
        long selectedGeneration = generation;
        String key = selectedGeneration + ":" + original.url().host();
        if (cooling(key)) return chain.proceed(original);
        List<InetAddress> originAddresses = resolveAddresses(original.url().host(), selectedGeneration,
                chain.call()::isCanceled);
        if (originAddresses.isEmpty()) return chain.proceed(original);
        List<InetAddress> preferredAddresses = resolveAddresses(preferred, selectedGeneration,
                chain.call()::isCanceled);
        List<InetAddress> candidates = selectAddresses(preferredAddresses, originAddresses);
        // Decide before changing chain settings: non-CF/failed DNS must keep original timeouts/pools.
        if (candidates.isEmpty() || generation != selectedGeneration
                || overridden.test(original.url().host()) || overridden.test(preferred))
            return chain.proceed(original);
        RouteDns routeDns = new RouteDns(original.url().host(), preferred, selectedGeneration, candidates);
        Attempt attempt = new Attempt();
        Request request = original.newBuilder().tag(Attempt.class, attempt).build();
        Chain routed = chain.withDns(routeDns).withConnectTimeout(
                boundedConnectTimeout(chain.connectTimeoutMillis()), TimeUnit.MILLISECONDS);
        Response response;
        try {
            response = routed.proceed(request);
        } catch (IOException failure) {
            if (!usedPreferred(attempt, routeDns) || !canFallback(chain.call(), failure)) throw failure;
            retire(attempt.connection);
            cool(key, selectedGeneration);
            try {
                return chain.proceed(original);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(failure);
                throw fallbackFailure;
            }
        }
        if (!usedPreferred(attempt, routeDns) || !fallbackStatus(response)
                || chain.call().isCanceled() || Thread.currentThread().isInterrupted()) return response;
        retire(attempt.connection);
        response.close();
        cool(key, selectedGeneration);
        if (chain.call().isCanceled() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Canceled before original-route fallback");
        return chain.proceed(original);
    }

    private static int boundedConnectTimeout(int value) {
        return value == 0 ? 2000 : Math.min(value, 2000);
    }

    private static boolean isDomain(String value) {
        return value.indexOf(':') < 0 && !value.matches("[0-9.]+");
    }

    private static boolean direct(Chain chain, Request request) {
        Proxy explicit = chain.getProxy();
        if (explicit != null) return explicit.type() == Proxy.Type.DIRECT;
        ProxySelector selector = chain.getProxySelector();
        if (selector == null) return true;
        try {
            List<Proxy> proxies = selector.select(request.url().uri());
            if (proxies == null || proxies.isEmpty()) return false;
            for (Proxy proxy : proxies) if (proxy == null || proxy.type() != Proxy.Type.DIRECT) return false;
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean canFallback(Call call, IOException failure) {
        return !call.isCanceled() && !Thread.currentThread().isInterrupted()
                && (!(failure instanceof InterruptedIOException) || failure instanceof SocketTimeoutException);
    }

    private static boolean fallbackStatus(Response response) {
        int code = response.code();
        // Respect an origin/edge asking us to wait instead of issuing an immediate second GET.
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !"0".equals(retryAfter.trim())) return false;
        return code == 403 || code == 421 || code >= 500 && code <= 599;
    }

    private static void retire(Connection connection) {
        // OkHttp 5.5 retains an attached same-host connection even when withDns changes.
        // This single pinned internal API prevents its reuse without closing other HTTP/2 streams.
        if (connection instanceof RealConnection real) real.noNewExchanges();
    }

    private static boolean usedPreferred(Attempt attempt, RouteDns routeDns) {
        Connection connection = attempt.connection;
        if (connection != null) {
            Dns actual = connection.route().address().dns();
            return actual instanceof CloudflarePreferredInterceptor.RouteDns used
                    && used.host.equals(routeDns.host) && used.selectedGeneration == routeDns.selectedGeneration
                    && used.preferredAddresses.contains(connection.route().socketAddress().getAddress());
        }
        // TCP/TLS failure occurs before network interceptors can capture a connection.
        return !routeDns.preferredAddresses.isEmpty();
    }

    private boolean cooling(String key) {
        synchronized (lock) {
            Long until = cooldowns.get(key);
            if (until == null) return false;
            if (until > clock.getAsLong()) return true;
            cooldowns.remove(key);
            return false;
        }
    }

    private void cool(String key, long selectedGeneration) {
        synchronized (lock) {
            if (generation != selectedGeneration) return;
            cooldowns.put(key, clock.getAsLong() + COOLDOWN_MS);
            trim(cooldowns);
        }
    }

    private boolean allCloudflare(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) return false;
        for (InetAddress address : addresses)
            if (address == null || !cloudflare.test(address.getAddress())) return false;
        return true;
    }

    private List<InetAddress> resolveAddresses(String host, long selectedGeneration,
                                                BooleanSupplier canceled) throws IOException {
        String key = selectedGeneration + ":" + host;
        Lookup lookup;
        synchronized (lock) {
            if (generation != selectedGeneration) return Collections.emptyList();
            lookup = lookups.get(key);
            if (lookup != null && lookup.result != null) {
                if (lookup.expiresAt > clock.getAsLong()) return lookup.result;
                lookups.remove(key);
                lookup = null;
            }
            if (lookup == null) {
                lookup = new Lookup();
                try {
                    lookup.future = resolver.submit(() -> sharedDns.lookup(host));
                } catch (RejectedExecutionException ignored) {
                    lookup.result = Collections.emptyList();
                    lookup.expiresAt = clock.getAsLong() + NEGATIVE_MS;
                }
                lookups.put(key, lookup);
                trim(lookups);
            }
            if (lookup.result != null) return lookup.result;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOOKUP_MS);
        List<InetAddress> result = Collections.emptyList();
        long lifetime = NEGATIVE_MS;
        try {
            while (true) {
                if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted())
                    throw new InterruptedIOException("Canceled during preferred DNS lookup");
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    List<InetAddress> addresses = lookup.future.get(
                            Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS);
                    // A conclusive non-CF DNS answer is also cached to avoid recurring lookup cost.
                    if (addresses != null && !addresses.isEmpty()) lifetime = POSITIVE_MS;
                    if (allCloudflare(addresses)) result = List.copyOf(addresses);
                    break;
                } catch (TimeoutException ignored) {
                    // Poll cancellation while sharing one bounded lookup with concurrent callers.
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted during preferred DNS lookup");
        } catch (ExecutionException | java.util.concurrent.CancellationException ignored) {
            // Resolver failure affects only the optional preferred route.
        }
        synchronized (lock) {
            if (generation != selectedGeneration) return Collections.emptyList();
            if (lookups.get(key) == lookup) {
                lookup.result = result;
                lookup.expiresAt = clock.getAsLong() + lifetime;
            }
        }
        return result;
    }

    private static void trim(Map<?, ?> entries) {
        while (entries.size() > CACHE_LIMIT) entries.remove(entries.keySet().iterator().next());
    }

    private static final class Attempt {
        volatile Connection connection;
    }

    private static final class Lookup {
        Future<List<InetAddress>> future;
        List<InetAddress> result;
        long expiresAt;
    }

    private static List<InetAddress> selectAddresses(List<InetAddress> preferred,
                                                       List<InetAddress> original) {
        ArrayList<InetAddress> selected = new ArrayList<>(2);
        boolean hasV4 = false;
        boolean hasV6 = false;
        for (InetAddress address : preferred) {
            if (original.contains(address)) continue;
            boolean v4 = address.getAddress().length == 4;
            if (v4 ? hasV4 : hasV6) continue;
            selected.add(address);
            if (v4) hasV4 = true;
            else hasV6 = true;
        }
        return List.copyOf(selected);
    }

    private final class RouteDns implements Dns {
        private final String host;
        private final String preferred;
        private final long selectedGeneration;
        private final List<InetAddress> preferredAddresses;

        RouteDns(String host, String preferred, long selectedGeneration,
                 List<InetAddress> preferredAddresses) {
            this.host = host;
            this.preferred = preferred;
            this.selectedGeneration = selectedGeneration;
            this.preferredAddresses = preferredAddresses;
        }

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            return host.equals(hostname) ? preferredAddresses : sharedDns.lookup(hostname);
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof CloudflarePreferredInterceptor.RouteDns other)) return false;
            return owner() == other.owner() && selectedGeneration == other.selectedGeneration
                    && host.equals(other.host) && preferred.equals(other.preferred)
                    && preferredAddresses.equals(other.preferredAddresses);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(owner()), selectedGeneration, host, preferred,
                    preferredAddresses);
        }

        private CloudflarePreferredInterceptor owner() {
            return CloudflarePreferredInterceptor.this;
        }
    }
}
