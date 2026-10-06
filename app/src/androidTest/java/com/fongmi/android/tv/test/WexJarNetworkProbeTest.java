package com.fongmi.android.tv.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.api.loader.JarLoader;
import com.github.catvod.bean.Doh;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.ech.EchSettings;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;

/**
 * Explicit live JAR diagnostic, isolated to the sourceprobe package. Stage a private
 * files/jar-network-plan.json and files/jar-network-source.jar first. Each invocation
 * starts one source search; results contain no URLs, headers or returned media titles.
 * Required arguments: jar_probe_site (plan index), jar_probe_doh, jar_probe_ech.
 * Optional jar_probe_capture_body=true saves at most 1 MiB to a separate private file;
 * never publish jar-network-private-body.json.
 * This test never reads the user's VodConfig or existing app databases.
 */
@RunWith(AndroidJUnit4.class)
public final class WexJarNetworkProbeTest {
    private final JSONArray events = new JSONArray();
    private final JSONObject report = new JSONObject();

    @Test public void searchThroughTheRealJarLoader() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Only the disposable sourceprobe package may run this test",
                "com.fongmi.android.tv.sourceprobe", target.getPackageName());
        Bundle args = InstrumentationRegistry.getArguments();
        JSONObject plan = new JSONObject(new String(Files.readAllBytes(
                new File(target.getFilesDir(), "jar-network-plan.json").toPath()),
                StandardCharsets.UTF_8));
        int index = Integer.parseInt(args.getString("jar_probe_site", "0"));
        JSONObject site = plan.getJSONArray("sites").getJSONObject(index);
        File jar = new File(target.getFilesDir(), "jar-network-source.jar");
        assertTrue("Stage the source JAR in this isolated package", jar.isFile());
        assertEquals("Unexpected JAR bytes", plan.getString("jarSha256"), sha256(jar));
        String dohUrl = args.getString("jar_probe_doh", "https://dns.alidns.com/dns-query");
        assertTrue("Use an explicit public HTTPS DoH endpoint",
                dohUrl.equals("https://dns.alidns.com/dns-query")
                        || dohUrl.equals("https://doh.pub/dns-query")
                        || dohUrl.equals(""));
        boolean enabled = Boolean.parseBoolean(args.getString("jar_probe_ech", "true"));
        boolean captureBody = Boolean.parseBoolean(args.getString("jar_probe_capture_body", "false"));
        boolean oldEch = EchSettings.isEnabled();
        Doh oldDoh = OkHttp.dns().getDoh();
        OkHttpClient original = OkHttp.client();
        Field clientField = OkHttp.class.getDeclaredField("client");
        clientField.setAccessible(true);
        OkHttpClient observed = original.newBuilder().connectionPool(new ConnectionPool())
                .eventListenerFactory(call -> new ProbeEvents()).build();
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "jar-network-probe");
            thread.setDaemon(true);
            return thread;
        });
        File output = new File(target.getFilesDir(), "jar-network-result.json");
        File privateBodyFile = new File(target.getFilesDir(), "jar-network-private-body.json");
        report.put("schema", "tv.jar-network-probe.v2");
        report.put("sourceApi", site.getString("api"));
        report.put("jarSha256", plan.getString("jarSha256"));
        report.put("ech", enabled);
        report.put("doh", dohUrl.isEmpty() ? "system" : new java.net.URI(dohUrl).getHost());
        report.put("events", events);
        report.put("privateBodyCaptured", false);
        report.put("dnsOverridesBefore", dnsOverrides());
        report.put("startedAtEpochMs", System.currentTimeMillis());
        int count = 0;
        try {
            if (captureBody && privateBodyFile.exists() && !privateBodyFile.delete())
                throw new IOException("PRIVATE_BODY_RESET_FAILED");
            EchSettings.setEnabled(enabled);
            OkHttp.dns().setDoh(new Doh().name("isolated-probe").url(dohUrl));
            clientField.set(OkHttp.get(), observed);
            Future<String> search = worker.submit(() -> {
                JarLoader loader = new JarLoader();
                String key = site.getString("key");
                Spider spider = loader.getSpider(key, site.getString("api"),
                        site.optString("ext", ""), "file://" + jar.getAbsolutePath());
                if (spider instanceof SpiderNull) throw new IllegalStateException("SOURCE_NOT_LOADED");
                report.put("spiderClass", spider.getClass().getName());
                report.put("dnsOverridesAfterJarLoad", dnsOverrides());
                // This is the application's ordinary first-page search overload.
                return spider.searchContent(plan.optString("keyword", "测试"), false);
            });
            String body = search.get(75, TimeUnit.SECONDS);
            if (captureBody && body != null) {
                if (body.length() > 1024 * 1024) throw new IOException("PRIVATE_BODY_SIZE_LIMIT");
                byte[] privateBody = body.getBytes(StandardCharsets.UTF_8);
                if (privateBody.length > 1024 * 1024) throw new IOException("PRIVATE_BODY_SIZE_LIMIT");
                // Explicit opt-in only. This response may contain sensitive source data; keep it private.
                try (FileOutputStream out = new FileOutputStream(privateBodyFile)) {
                    out.write(privateBody);
                }
                report.put("privateBodyCaptured", true);
            }
            report.put("bodyBytes", body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length);
            if (body != null && !body.isEmpty()) {
                JSONObject document = new JSONObject(body);
                JSONArray list = document.optJSONArray("list");
                count = list == null ? 0 : list.length();
                report.put("unavailableDomainMessage",
                        body.contains("域名") && (body.contains("不可用") || body.contains("均无法")));
            }
            report.put("resultCount", count);
            report.put("status", count > 0 ? "results" : "empty");
        } catch (Throwable failure) {
            report.put("status", "failed");
            report.put("failureTypes", exceptionTypes(failure));
            report.put("failureEchRejected", echRejected(failure));
            // This separate file is private to the disposable probe UID; do not publish it.
            try (java.io.PrintWriter out = new java.io.PrintWriter(
                    new File(target.getFilesDir(), "jar-network-exception.txt"), "UTF-8")) {
                failure.printStackTrace(out);
            }
        } finally {
            observed.dispatcher().cancelAll();
            worker.shutdownNow();
            observed.connectionPool().evictAll();
            report.put("dnsOverridesAtFinish", dnsOverrides());
            clientField.set(OkHttp.get(), original);
            EchSettings.setEnabled(oldEch);
            OkHttp.dns().setDoh(oldDoh);
            report.put("finishedAtEpochMs", System.currentTimeMillis());
            synchronized (events) {
                try (FileOutputStream out = new FileOutputStream(output)) {
                    out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            }
            Log.i("JarNetworkProbe", "Completed status=" + report.optString("status")
                    + " results=" + count + " events=" + events.length());
        }
        assertTrue("No search results; inspect the isolated private jar-network-result.json", count > 0);
    }

    private final class ProbeEvents extends EventListener {
        private long started = SystemClock.elapsedRealtime();

        private void record(Call call, String kind, Object detail) {
            try {
                String host = call.request().url().host();
                // The source scans local proxy ports at startup; retain only its responses.
                if ((host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1"))
                        && !kind.equals("http")) return;
                JSONObject event = new JSONObject().put("kind", kind)
                        .put("host", call.request().url().host())
                        .put("elapsedMs", SystemClock.elapsedRealtime() - started)
                        .put("detail", detail);
                synchronized (events) { if (events.length() < 500) events.put(event); }
            } catch (Exception ignored) { }
        }

        @Override public void callStart(Call call) { record(call, "start", ""); }
        @Override public void dnsEnd(Call call, String domainName, List<InetAddress> addresses) {
            record(call, "dns", addresses.size());
            try {
                JSONArray ips = new JSONArray();
                for (InetAddress address : addresses) ips.put(address.getHostAddress());
                record(call, "dnsAddresses", new JSONObject().put("domain", hostToken(domainName)).put("ips", ips));
            } catch (Exception ignored) { }
        }
        @Override public void connectStart(Call call, InetSocketAddress address, Proxy proxy) {
            record(call, "connectStart", route(address, proxy));
        }
        @Override public void connectFailed(Call call, InetSocketAddress address, Proxy proxy,
                                            Protocol protocol, IOException failure) {
            try {
                JSONObject detail = route(address, proxy).put("failureTypes", exceptionTypes(failure))
                        .put("echRejected", echRejected(failure)).put("dnsOverrides", dnsOverrides());
                if (protocol != null) detail.put("protocol", protocol.toString());
                record(call, "connectFailed", detail);
            } catch (Exception ignored) { }
        }
        @Override public void connectionAcquired(Call call, Connection connection) {
            record(call, "tlsSocket", connection.socket().getClass().getName());
            record(call, "connectedRoute", route(connection.route().socketAddress(), connection.route().proxy()));
        }
        @Override public void responseHeadersEnd(Call call, Response response) {
            record(call, "http", response.code());
        }
        @Override public void callFailed(Call call, IOException failure) {
            record(call, "failure", exceptionTypes(failure));
            try {
                record(call, "failureDiagnostics", new JSONObject().put("echRejected", echRejected(failure))
                        .put("dnsOverrides", dnsOverrides()));
            } catch (Exception ignored) { }
        }
    }

    private static JSONObject route(InetSocketAddress address, Proxy proxy) {
        JSONObject value = new JSONObject();
        try {
            value.put("ip", address.getAddress() == null ? JSONObject.NULL : address.getAddress().getHostAddress())
                    .put("port", address.getPort()).put("unresolved", address.isUnresolved())
                    .put("proxyType", proxy.type().name());
            if (address.isUnresolved()) value.put("host", hostToken(address.getHostString()));
            if (proxy.address() instanceof InetSocketAddress endpoint) {
                value.put("proxyHost", hostToken(endpoint.getHostString())).put("proxyPort", endpoint.getPort());
                if (endpoint.getAddress() != null) value.put("proxyIp", endpoint.getAddress().getHostAddress());
            }
        } catch (Exception ignored) { }
        return value;
    }

    /** Weakly consistent snapshot of the shared override map; never invokes DNS or ECH resolution. */
    private static JSONObject dnsOverrides() {
        JSONObject value = new JSONObject();
        try {
            Field field = OkHttp.dns().getClass().getDeclaredField("map");
            field.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) field.get(OkHttp.dns());
            JSONArray entries = new JSONArray();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entries.length() == 128) break;
                entries.put(new JSONObject().put("host", hostToken(entry.getKey()))
                        .put("target", hostToken(entry.getValue())));
            }
            value.put("count", map.size()).put("entries", entries).put("truncated", map.size() > entries.length());
        } catch (Exception failure) {
            try { value.put("status", failure.getClass().getSimpleName()); } catch (Exception ignored) { }
        }
        return value;
    }

    private static String hostToken(Object value) {
        String text = String.valueOf(value);
        // Keep host/IP overrides, but never expose a malformed mapping containing a URL or payload.
        return text.matches("[A-Za-z0-9._:*\\[\\]-]{1,253}") ? text : "REDACTED_NON_HOST";
    }

    private static boolean echRejected(Throwable failure) {
        return echRejected(failure, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean echRejected(Throwable failure, Set<Throwable> visited) {
        if (failure == null || visited.size() > 32 || !visited.add(failure)) return false;
        String message = failure.getMessage();
        if (failure.getClass().getName().equals("org.conscrypt.EchRejectedException")
                || (message != null && message.contains("ECH_REJECTED"))) return true;
        if (echRejected(failure.getCause(), visited)) return true;
        for (Throwable suppressed : failure.getSuppressed()) if (echRejected(suppressed, visited)) return true;
        return false;
    }

    private static JSONArray exceptionTypes(Throwable failure) {
        Set<String> names = new LinkedHashSet<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(failure, visited, names);
        return new JSONArray(names);
    }

    private static void collect(Throwable failure, Set<Throwable> visited, Set<String> names) {
        if (failure == null || visited.size() > 32 || !visited.add(failure)) return;
        names.add(failure.getClass().getName());
        collect(failure.getCause(), visited, names);
        for (Throwable suppressed : failure.getSuppressed()) collect(suppressed, visited, names);
    }

    private static String sha256(File file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
