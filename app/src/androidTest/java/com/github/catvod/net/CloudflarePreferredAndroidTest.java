package com.github.catvod.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.catvod.net.ech.EchSettings;
import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Opt-in public-network integration only, isolated to the disposable sourceprobe UID. */
@RunWith(AndroidJUnit4.class)
public final class CloudflarePreferredAndroidTest {
    private static final String PACKAGE = "com.fongmi.android.tv.sourceprobe";
    private static final String PREFERRED_KEY = "cloudflare_preferred_domain";
    private static final String ECH_KEY = "ech_enabled";
    private static final String TRACE = "https://www.cloudflare.com/cdn-cgi/trace";
    private static final String ORIGIN = "www.cloudflare.com";
    private static final String UNAVAILABLE = "cf-priority-unavailable.invalid";

    @Test(timeout = 120_000L)
    public void productionClientPreferredRoutingAndFallback() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        assumeTrue("Public-network probe requires explicit opt-in",
                "true".equalsIgnoreCase(arguments.getString("cf_preferred_live", "false")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Only the disposable sourceprobe package may run this probe", PACKAGE, context.getPackageName());
        assertEquals("The target UID must be sourceprobe", Process.myUid(),
                context.getPackageManager().getApplicationInfo(PACKAGE, 0).uid);
        assertFalse("Live networking must run off main", Looper.myLooper() == Looper.getMainLooper());
        String domain = CloudflarePreferredSettings.normalize(arguments.getString("cf_preferred_domain", "speed.cloudflare.com"));
        assertFalse("A preferred hostname is required for the live phase", domain.isEmpty());

        SharedPreferences preferences = Prefers.getPrefers();
        boolean hadPreferred = preferences.contains(PREFERRED_KEY);
        Object originalPreferred = preferences.getAll().get(PREFERRED_KEY);
        boolean hadEch = preferences.contains(ECH_KEY);
        Object originalEch = preferences.getAll().get(ECH_KEY);
        assertTrue("Unexpected preferred preference type", !hadPreferred || originalPreferred instanceof String);
        assertTrue("Unexpected ECH preference type", !hadEch || originalEch instanceof Boolean);
        JSONArray phases = new JSONArray();
        JSONObject report = new JSONObject().put("status", "RUNNING").put("phases", phases)
                .put("targetHost", ORIGIN).put("preferredDomain", domain)
                .put("echAcceptedVerified", false)
                .put("scope", "Production client routing; trace bodies discarded; TLS ECH acceptance not measured");
        writeReport(context, report);
        boolean passed = false;
        try {
            boolean baseline = runPhase(phases, "baseline", "", false);
            boolean unavailable = runPhase(phases, "unavailable_preferred_fallback", UNAVAILABLE, false);
            boolean preferred = runPhase(phases, "preferred", domain, false);
            boolean ech = runPhase(phases, "preferred_with_ech_enabled", domain, true);
            passed = baseline && unavailable && preferred && ech;
        } finally {
            boolean restored = false;
            try {
                SharedPreferences.Editor edit = preferences.edit();
                if (hadPreferred) edit.putString(PREFERRED_KEY, (String) originalPreferred);
                else edit.remove(PREFERRED_KEY);
                if (hadEch) edit.putBoolean(ECH_KEY, (Boolean) originalEch);
                else edit.remove(ECH_KEY);
                assertTrue("Network preferences must restore", edit.commit());
                OkHttp.echConfigurationChanged();
                assertEquals(hadPreferred, preferences.contains(PREFERRED_KEY));
                assertEquals(originalPreferred, preferences.getAll().get(PREFERRED_KEY));
                assertEquals(hadEch, preferences.contains(ECH_KEY));
                assertEquals(originalEch, preferences.getAll().get(ECH_KEY));
                restored = true;
            } finally {
                report.put("preferencesRestored", restored);
                report.put("status", passed && restored ? "PASS" : "FAIL");
                writeReport(context, report);
            }
        }
        assertTrue("Cloudflare preferred live probe failed; see ech-validation/cloudflare-preferred-live.json", passed);
    }

    private static boolean runPhase(JSONArray phases, String name, String preferred, boolean echEnabled) throws Exception {
        RouteEvents events = new RouteEvents();
        JSONObject phase = new JSONObject().put("name", name).put("echEnabled", echEnabled)
                .put("connections", events.connections).put("preferredConfigured", !preferred.isEmpty());
        phases.put(phase);
        long started = SystemClock.elapsedRealtime();
        try {
            EchSettings.setEnabled(echEnabled);
            CloudflarePreferredSettings.setDomain(preferred);
            OkHttp.echConfigurationChanged();
            OkHttpClient client = OkHttp.client().newBuilder().eventListener(events)
                    .callTimeout(20, TimeUnit.SECONDS).build();
            Request request = new Request.Builder().url(TRACE).header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache").build();
            try (Response response = client.newCall(request).execute()) {
                phase.put("httpStatus", response.code());
                boolean unchanged = ORIGIN.equals(response.request().url().host());
                phase.put("originHostUnchanged", unchanged);
                phase.put("protocol", response.protocol().toString());
                // Count and discard; do not store trace's client IP, location, TLS or request details.
                int bytes = 0;
                byte[] buffer = new byte[2048];
                assertNotNull("Trace response body required", response.body());
                try (InputStream input = response.body().byteStream()) {
                    for (int read; (read = input.read(buffer)) != -1; ) {
                        bytes += read;
                        assertTrue("Trace response exceeded limit", bytes <= 32 * 1024);
                    }
                }
                phase.put("bodyBytes", bytes);
                assertEquals("Public target must be reachable", 200, response.code());
                assertTrue("Preferred routing must preserve origin hostname", unchanged);
                assertTrue("Trace response must not be empty", bytes > 0);
                if (preferred.isEmpty() || UNAVAILABLE.equals(preferred))
                    assertFalse("Disabled or unresolvable preferred domain must use original route", events.preferredUsed);
            }
            phase.put("status", "PASS");
            return true;
        } catch (Throwable failure) {
            // Do not include exception messages: transport errors can contain headers and addresses.
            phase.put("status", "FAIL").put("failureType", failure.getClass().getSimpleName());
            return false;
        } finally {
            phase.put("elapsedMs", SystemClock.elapsedRealtime() - started);
            phase.put("preferredRouteUsed", events.preferredUsed);
            if (!preferred.isEmpty() && !UNAVAILABLE.equals(preferred) && !events.preferredUsed)
                phase.put("routeObservation", "Original route observed; classification, overlapping IPs, proxy policy or fallback may explain this");
        }
    }

    private static void writeReport(Context context, JSONObject report) throws Exception {
        File external = context.getExternalFilesDir(null);
        assertNotNull("Report directory must exist", external);
        File directory = new File(external, "ech-validation");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, "cloudflare-preferred-live.json"))) {
            output.write(report.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class RouteEvents extends EventListener {
        final JSONArray connections = new JSONArray();
        volatile boolean preferredUsed;

        @Override
        public synchronized void connectionAcquired(Call call, Connection connection) {
            String dnsType = connection.route().address().dns().getClass().getName();
            boolean preferred = dnsType.contains("CloudflarePreferredInterceptor$RouteDns");
            preferredUsed |= preferred;
            try {
                connections.put(new JSONObject().put("dnsType", dnsType)
                        .put("socketIp", connection.route().socketAddress().getAddress().getHostAddress())
                        .put("preferredRoute", preferred).put("protocol", connection.protocol().toString()));
            } catch (Exception error) {
                throw new AssertionError("Could not record connection metadata", error);
            }
        }
    }
}
