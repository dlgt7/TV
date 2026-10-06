package com.github.catvod.net.ech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Call;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Optional ECH discovery. A result is configuration availability, never handshake acceptance. */
public final class EchDnsResolver {
    public static final String DEFAULT_DOH_URL = "https://1.1.1.1/dns-query";
    private static final String CLOUDFLARE_CONFIG_HOST = "crypto.cloudflare.com";
    private static final String FALLBACK_CACHE_PREFIX = "cloudflare:";
    private static final int CACHE_LIMIT = 128;
    private static final int IN_FLIGHT_LIMIT = 4;
    private static final int MAX_QUERIES = 8;
    private static final long MAX_TTL_MS = 300_000;
    private static final long NEGATIVE_TTL_MS = 10_000;
    private static final long LOOKUP_TIMEOUT_MS = 6_000;
    private static final MediaType DNS_MESSAGE = MediaType.get("application/dns-message");

    private final Object lock = new Object();
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, CompletableFuture<Entry>> inFlight = new HashMap<>();
    private final ThreadLocal<String> lastReason = new ThreadLocal<>();
    private final Transport transport;
    private final Clock clock;

    public EchDnsResolver(OkHttpClient bootstrapClient, String dohUrl) {
        this(bootstrapClient, dohUrl, Collections.emptyList());
    }

    /**
     * Configuration is immutable: recreate this resolver when the DoH URL/bootstrap IPs change.
     * Only proxy policy/authentication are copied. TLS, DNS and cookies cannot inherit ECH or
     * source-specific interceptors from the supplied client.
     */
    public EchDnsResolver(OkHttpClient bootstrapClient, String dohUrl,
                          List<InetAddress> bootstrapHosts) {
        this(httpTransport(bootstrapClient, dohUrl, bootstrapHosts),
                () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    EchDnsResolver(Transport transport, Clock clock) {
        this.transport = transport;
        this.clock = clock;
    }

    public byte[] resolve(String hostname) {
        return resolve(hostname, 443);
    }

    public byte[] resolve(String hostname, int port) {
        return resolve(hostname, port, false);
    }

    /**
     * Also permits Cloudflare's public configuration when this DoH resolver reports only
     * Cloudflare addresses for the target. This class supplies configuration bytes only:
     * DNS classification does not prove which destination a proxy actually connects to,
     * change the connection route, or guarantee that the TLS server will accept ECH.
     */
    public byte[] resolveWithCloudflareFallback(String hostname) {
        return resolve(hostname, 443, true);
    }

    private byte[] resolve(String hostname, int port, boolean cloudflareFallback) {
        // Non-default HTTPS ports require a different DNS owner name (_port._https).
        if (port != 443) return result(new Entry(null, "unsupported_port", 0));
        String host;
        try {
            host = canonicalHost(hostname);
        } catch (IllegalArgumentException e) {
            return result(new Entry(null, "invalid_host", 0));
        }
        String key = cloudflareFallback ? FALLBACK_CACHE_PREFIX + host : host;
        CompletableFuture<Entry> flight;
        boolean owner = false;
        synchronized (lock) {
            Entry cached = cache.get(key);
            if (cached != null && cached.expiresAt > clock.nowMillis()) return result(cached);
            cache.remove(key);
            flight = inFlight.get(key);
            if (flight == null) {
                if (inFlight.size() >= IN_FLIGHT_LIMIT)
                    return result(new Entry(null, "lookup_busy", 0));
                flight = new CompletableFuture<>();
                inFlight.put(key, flight);
                owner = true;
            }
        }
        if (!owner) {
            try {
                return result(flight.get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result(new Entry(null, "lookup_interrupted", 0));
            } catch (ExecutionException | TimeoutException e) {
                return result(new Entry(null, "lookup_timeout", 0));
            }
        }
        Entry entry;
        try {
            LookupBudget budget = new LookupBudget();
            entry = cloudflareFallback ? queryWithCloudflareFallback(host, budget)
                    : query(host, budget);
        } catch (QueryFailure e) {
            entry = negative(e.reason);
        } catch (IOException e) {
            entry = negative(Thread.currentThread().isInterrupted()
                    ? "lookup_interrupted" : "doh_io");
        } catch (RuntimeException e) {
            entry = negative("doh_failure");
        } catch (Error e) {
            synchronized (lock) {
                inFlight.remove(key);
                flight.completeExceptionally(e);
            }
            throw e;
        }
        synchronized (lock) {
            cache(key, entry);
            inFlight.remove(key);
            flight.complete(entry);
        }
        return result(entry);
    }

    /** Stable status for the calling thread, with no hostname, payload, URL or credentials. */
    public String lastReason() {
        return lastReason.get();
    }

    private byte[] result(Entry entry) {
        lastReason.set(entry.reason);
        return entry.config == null ? null : entry.config.clone();
    }

    private Entry query(String originalHost, LookupBudget budget) throws IOException {
        long expiresAt = Long.MAX_VALUE;
        Set<String> visited = new HashSet<>();
        String host = originalHost;
        while (true) {
            if (!visited.add(host)) return negative("alias_loop");
            int id = ThreadLocalRandom.current().nextInt(65536);
            byte[] wire = budget.exchange(EchDnsParser.buildQuery(id, host));
            EchDnsParser.Result parsed = EchDnsParser.parse(wire, id, host, 443);
            if (!parsed.hasEch() && !parsed.hasAlias()) {
                Entry absent = negative(parsed.reason);
                return new Entry(null, absent.reason, Math.min(expiresAt, absent.expiresAt));
            }
            expiresAt = Math.min(expiresAt, expiresAt(parsed.ttlSeconds));
            if (parsed.hasEch()) return new Entry(parsed.echConfigList, "ech_config_available", expiresAt);
            host = canonicalHost(parsed.aliasTarget);
        }
    }

    private Entry queryWithCloudflareFallback(String host, LookupBudget budget) throws IOException {
        Entry published = published(host, budget);
        if (published.config != null || host.equals(CLOUDFLARE_CONFIG_HOST)
                || !("no_https_record".equals(published.reason) || "no_ech".equals(published.reason)))
            return published;

        AddressEntry ipv4 = addresses(host, 1, budget);
        AddressEntry ipv6 = addresses(host, 28, budget);
        if (ipv4.addresses.isEmpty() && ipv6.addresses.isEmpty())
            return negative("cloudflare_no_addresses");
        for (byte[] address : ipv4.addresses)
            if (!CloudflareAddressRanges.contains(address)) return negative("cloudflare_non_cf_address");
        for (byte[] address : ipv6.addresses)
            if (!CloudflareAddressRanges.contains(address)) return negative("cloudflare_non_cf_address");

        Entry donor = published(CLOUDFLARE_CONFIG_HOST, budget);
        if (donor.config == null) return donor;
        long expiresAt = Math.min(Math.min(published.expiresAt, donor.expiresAt),
                Math.min(ipv4.expiresAt, ipv6.expiresAt));
        return new Entry(donor.config, "cloudflare_ech_fallback", expiresAt);
    }

    /** Cache native answers without recursively acquiring another in-flight slot or deadline. */
    private Entry published(String host, LookupBudget budget) throws IOException {
        budget.remaining();
        synchronized (lock) {
            Entry cached = cache.get(host);
            if (cached != null && cached.expiresAt > clock.nowMillis()) return cached;
            cache.remove(host);
        }
        Entry entry = query(host, budget);
        synchronized (lock) {
            cache(host, entry);
        }
        return entry;
    }

    private AddressEntry addresses(String originalHost, int type, LookupBudget budget)
            throws IOException {
        String host = originalHost;
        long expiresAt = Long.MAX_VALUE;
        Set<String> visited = new HashSet<>();
        while (true) {
            if (!visited.add(host)) throw new QueryFailure("alias_loop");
            int id = ThreadLocalRandom.current().nextInt(65536);
            byte[] wire = budget.exchange(EchDnsParser.buildQuery(id, host, type));
            EchDnsParser.AddressResult parsed = EchDnsParser.parseAddresses(wire, id, host, type);
            if (parsed.reason != null) throw new QueryFailure(parsed.reason);
            expiresAt = Math.min(expiresAt, expiresAt(parsed.ttlSeconds));
            if (!parsed.hasAlias()) return new AddressEntry(parsed.addresses, expiresAt);
            host = canonicalHost(parsed.aliasTarget);
        }
    }

    private long expiresAt(long ttlSeconds) {
        return clock.nowMillis() + Math.min(MAX_TTL_MS, ttlSeconds * 1000L);
    }

    /** Must be called while holding lock. Both lookup modes share this one bounded cache. */
    private void cache(String key, Entry entry) {
        if (entry.expiresAt <= clock.nowMillis()) return;
        cache.put(key, entry);
        while (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
    }

    private final class LookupBudget {
        private final long deadline = clock.nowMillis() + LOOKUP_TIMEOUT_MS;
        private int queries;

        byte[] exchange(byte[] query) throws IOException {
            long remaining = remaining();
            if (queries >= MAX_QUERIES) throw new QueryFailure("alias_limit");
            queries++;
            byte[] wire = transport.exchange(query, remaining);
            remaining();
            return wire;
        }

        long remaining() throws QueryFailure {
            if (Thread.currentThread().isInterrupted()) throw new QueryFailure("lookup_interrupted");
            long remaining = deadline - clock.nowMillis();
            if (remaining <= 0) throw new QueryFailure("lookup_timeout");
            return remaining;
        }
    }

    private static final class AddressEntry {
        final List<byte[]> addresses;
        final long expiresAt;

        AddressEntry(List<byte[]> addresses, long expiresAt) {
            this.addresses = addresses;
            this.expiresAt = expiresAt;
        }
    }

    private Entry negative(String reason) {
        return new Entry(null, reason == null ? "no_ech_config" : reason,
                clock.nowMillis() + NEGATIVE_TTL_MS);
    }

    private static String canonicalHost(String hostname) {
        if (hostname == null) throw new IllegalArgumentException();
        String host = hostname.trim();
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.indexOf(':') >= 0 || host.matches("[0-9.]+"))
            throw new IllegalArgumentException();
        EchDnsParser.buildQuery(0, host);
        return host;
    }

    private static Transport httpTransport(OkHttpClient supplied, String configuredUrl,
                                            List<InetAddress> bootstrapHosts) {
        if (supplied == null) throw new IllegalArgumentException("bootstrap_client_required");
        String selected = configuredUrl == null || configuredUrl.trim().isEmpty()
                ? DEFAULT_DOH_URL : configuredUrl.trim();
        HttpUrl endpoint = HttpUrl.parse(selected);
        if (endpoint == null || !endpoint.isHttps() || !endpoint.username().isEmpty()
                || !endpoint.password().isEmpty() || endpoint.fragment() != null)
            throw new IllegalArgumentException("invalid_doh_url");
        List<InetAddress> hosts = bootstrapHosts == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(bootstrapHosts));
        if (hosts.contains(null)) throw new IllegalArgumentException("invalid_bootstrap_host");
        // Deliberately do not call supplied.newBuilder(): its TLS may be our ECH factory.
        OkHttpClient bootstrap = new OkHttpClient.Builder()
                .proxy(supplied.proxy()).proxySelector(supplied.proxySelector())
                .proxyAuthenticator(supplied.proxyAuthenticator())
                .dns(name -> !hosts.isEmpty() && endpoint.host().equalsIgnoreCase(name)
                        ? hosts : Dns.SYSTEM.lookup(name))
                .cookieJar(CookieJar.NO_COOKIES)
                .followRedirects(false).followSslRedirects(false)
                .connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS).build();
        return (query, timeoutMs) -> {
            Request request = new Request.Builder().url(endpoint)
                    .header("Accept", "application/dns-message")
                    .post(RequestBody.create(query, DNS_MESSAGE)).build();
            Call call = bootstrap.newCall(request);
            call.timeout().timeout(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
            try (Response response = call.execute()) {
                if (response.code() != 200) throw new QueryFailure("doh_http_status");
                MediaType type = MediaType.parse(response.header("Content-Type", ""));
                if (type == null || !type.type().equalsIgnoreCase("application")
                        || !type.subtype().equalsIgnoreCase("dns-message"))
                    throw new QueryFailure("doh_content_type");
                ResponseBody body = response.body();
                if (body == null || body.contentLength() > 65535)
                    throw new QueryFailure("doh_body_size");
                try (InputStream input = body.byteStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 65535) throw new QueryFailure("doh_body_size");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            }
        };
    }

    interface Transport {
        byte[] exchange(byte[] query, long timeoutMs) throws IOException;
    }

    interface Clock {
        long nowMillis();
    }

    private static final class Entry {
        final byte[] config;
        final String reason;
        final long expiresAt;

        Entry(byte[] config, String reason, long expiresAt) {
            this.config = config == null ? null : config.clone();
            this.reason = reason;
            this.expiresAt = expiresAt;
        }
    }

    private static final class QueryFailure extends IOException {
        final String reason;

        QueryFailure(String reason) {
            super(reason);
            this.reason = reason;
        }
    }
}
