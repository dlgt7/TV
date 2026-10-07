package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.bean.Doh;
import com.github.catvod.utils.Util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkDns implements Dns {

    private final ConcurrentHashMap<String, String> map;
    private final Runnable configurationChanged;
    private volatile Supplier<Doh> supplier;
    private volatile DnsOverHttps doh;
    private Doh selectedDoh = new Doh();

    public OkDns() {
        this(OkHttp::echConfigurationChanged);
    }

    OkDns(Runnable configurationChanged) {
        this.map = new ConcurrentHashMap<>();
        this.configurationChanged = configurationChanged;
    }

    public synchronized void setDoh(Doh item) {
        applyDoh(item);
        configurationChanged.run();
    }

    private void applyDoh(Doh item) {
        HttpUrl url = HttpUrl.parse(item.getUrl());
        // POST avoids stale shared HTTP GET responses; ECH discovery uses POST as well.
        this.doh = url == null ? null : new DnsOverHttps.Builder().client(new OkHttpClient()).url(url).post(true).bootstrapDnsHosts(item.getHosts()).build();
        this.selectedDoh = Doh.objectFrom(item.toString());
        this.supplier = null;
    }

    public synchronized void setDoh(Supplier<Doh> supplier) {
        this.supplier = supplier;
        configurationChanged.run();
    }

    /** Snapshot of the same DoH selection used for address lookups. */
    public synchronized Doh getDoh() {
        Supplier<Doh> pending = supplier;
        if (pending != null) initDoh(pending);
        return Doh.objectFrom(selectedDoh.toString());
    }

    public void clear() {
        map.clear();
        configurationChanged.run();
    }

    public void addAll(List<String> hosts) {
        map.putAll(hosts.stream().filter(Objects::nonNull).map(host -> host.split("=", 2)).filter(splits -> splits.length == 2).collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim(), (oldHost, newHost) -> newHost)));
    }

    /** Explicit host mappings take priority over optional Cloudflare routing. */
    public boolean hasOverride(String hostname) {
        if (map.containsKey(hostname)) return true;
        for (String pattern : map.keySet()) if (Util.containOrMatch(hostname, pattern)) return true;
        return false;
    }

    private String get(String hostname) {
        String target = map.get(hostname);
        if (target != null) return target;
        for (Map.Entry<String, String> entry : map.entrySet()) if (Util.containOrMatch(hostname, entry.getKey())) return entry.getValue();
        return hostname;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        Supplier<Doh> supplier = this.supplier;
        if (supplier != null) initDoh(supplier);
        return (doh != null ? doh : Dns.SYSTEM).lookup(get(hostname));
    }

    private synchronized void initDoh(Supplier<Doh> supplier) {
        if (supplier != this.supplier) return;
        // Installing the supplier already invalidated the old route/ECH generation.
        // Doing it again here cancels the CF DNS Future currently executing this method,
        // interrupting first-time public-suffix loading and concurrent DNS waiters.
        applyDoh(supplier.get());
    }
}
