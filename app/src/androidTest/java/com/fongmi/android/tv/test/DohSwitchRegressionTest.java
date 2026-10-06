package com.fongmi.android.tv.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.ech.ConscryptEchSocketFactory;
import com.github.catvod.net.ech.EchSettings;
import com.github.catvod.utils.Prefers;

import org.conscrypt.Conscrypt;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSocket;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/** Real Conscrypt close_notify regression; may mutate only the disposable sourceprobe UID. */
@RunWith(AndroidJUnit4.class)
public final class DohSwitchRegressionTest {
    private static final String PACKAGE = "com.fongmi.android.tv.sourceprobe";
    private static final String TRACE = "https://crypto.cloudflare.com/cdn-cgi/trace";
    private static final String ECH_KEY = "ech_enabled";

    @Test(timeout = 90_000L)
    public void mainThreadDohAndEchChangesCloseIdleTlsWithoutCancelingPlayback() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        assertEquals("Only the disposable sourceprobe package may run this test", PACKAGE, context.getPackageName());
        assertEquals("The target UID must be sourceprobe", Process.myUid(),
                context.getPackageManager().getApplicationInfo(PACKAGE, 0).uid);
        assertFalse("Network setup must run off main", Looper.myLooper() == Looper.getMainLooper());

        JSONObject report = new JSONObject().put("status", "RUNNING")
                .put("method", "mainThreadDohAndEchChangesCloseIdleTlsWithoutCancelingPlayback")
                .put("strictMainCalls", 0).put("idleTlsClosed", 0).put("activeResponsesCompleted", 0);
        writeReport(context, report);
        SharedPreferences preferences = Prefers.getPrefers();
        boolean hadEnabled = preferences.contains(ECH_KEY);
        Object originalEnabled = preferences.getAll().get(ECH_KEY);
        assertTrue("Unexpected ECH preference type", !hadEnabled || originalEnabled instanceof Boolean);
        Doh originalDoh = OkHttp.dns().getDoh();
        OkHttp host = OkHttp.get();
        Field clientField = field(OkHttp.class, "client");
        Field playerField = field(OkHttp.class, "player");
        Object originalClient = clientField.get(host);
        Object originalPlayer = playerField.get(host);
        OkHttpClient client = null;
        OkHttpClient player = null;
        Response active = null;
        boolean passed = false;
        String stage = "setup";
        try {
            Conscrypt.checkAvailability();
            EchSettings.setEnabled(true);
            OkHttp.dns().setDoh(tencent());
            drainEvictions();
            OkHttpClient shared = OkHttp.client();
            assertTrue("Production ECH socket factory required", shared.sslSocketFactory() instanceof ConscryptEchSocketFactory);
            SocketEvents idleEvents = new SocketEvents();
            SocketEvents activeEvents = new SocketEvents();
            client = isolatedPool(shared, idleEvents);
            player = isolatedPool(shared, activeEvents);
            clientField.set(host, client);
            playerField.set(host, player);
            assertSame(client, OkHttp.client());
            assertSame(player, OkHttp.player());

            Call playback = null;
            for (int phase = 0; phase < 2; phase++) {
                stage = phase == 0 ? "setDoh" : "echConfigurationChanged";
                if (phase != 0) {
                    OkHttp.dns().setDoh(tencent());
                    drainEvictions();
                }
                // Consume the real response so evictAll must close a pooled Conscrypt socket.
                try (Response warm = client.newCall(request()).execute()) {
                    assertEquals("Trace must be reachable", 200, warm.code());
                    assertTrue("Trace must have a body", consume(warm) > 0);
                }
                Socket idleSocket = requireConscrypt(idleEvents.socket);
                assertFalse("Warm socket must still be open", idleSocket.isClosed());
                assertEquals("A real idle TLS connection is required", 1, client.connectionPool().idleConnectionCount());
                if (playback == null) {
                    playback = player.newCall(request());
                    active = playback.execute();
                    assertEquals(200, active.code());
                    requireConscrypt(activeEvents.socket);
                }
                assertEquals("Keep a response allocated during the change", 1, player.connectionPool().connectionCount());
                assertEquals("Active response must not be idle", 0, player.connectionPool().idleConnectionCount());
                assertNotNull("Warm ECH configuration must exist", field(OkHttp.class, "echResolver").get(host));

                strictMain(instrumentation, phase == 0
                        ? () -> OkHttp.dns().setDoh(new Doh().url(""))
                        : OkHttp::echConfigurationChanged);
                report.put("strictMainCalls", phase + 1);
                assertNull("Configuration must invalidate synchronously", field(OkHttp.class, "echResolver").get(host));
                drainEvictions();
                assertTrue("The idle Conscrypt socket must close", idleSocket.isClosed());
                assertEquals(0, client.connectionPool().connectionCount());
                assertFalse("Playback call must not be canceled", playback.isCanceled());
                assertFalse("Active TLS socket must stay open", activeEvents.socket.isClosed());
                assertEquals(1, player.connectionPool().connectionCount());
                report.put("idleTlsClosed", phase + 1);
            }
            assertTrue("Active response must still finish", consume(active) > 0);
            report.put("activeResponsesCompleted", 1);
            passed = true;
        } catch (Throwable error) {
            // No URLs, origin headers, trace contents, certificates or exception messages in reports.
            report.put("status", "FAIL_" + stage + "_" + error.getClass().getSimpleName());
        } finally {
            boolean restored = false;
            try {
                try {
                    try {
                        if (active != null) active.close();
                        drainEvictions();
                    } finally {
                        try {
                            closeOwned(client);
                        } finally {
                            closeOwned(player);
                        }
                    }
                } finally {
                    try {
                        SharedPreferences.Editor edit = preferences.edit();
                        if (hadEnabled) edit.putBoolean(ECH_KEY, (Boolean) originalEnabled);
                        else edit.remove(ECH_KEY);
                        assertTrue("ECH preference restore must commit", edit.commit());
                    } finally {
                        OkHttp.dns().setDoh(originalDoh);
                        drainEvictions();
                    }
                }
                assertEquals(originalDoh.toString(), OkHttp.dns().getDoh().toString());
                assertEquals(hadEnabled, preferences.contains(ECH_KEY));
                assertEquals(originalEnabled, preferences.getAll().get(ECH_KEY));
                restored = true;
            } catch (Throwable error) {
                report.put("status", "FAIL_restore_" + error.getClass().getSimpleName());
            } finally {
                try {
                    clientField.set(host, originalClient);
                } finally {
                    playerField.set(host, originalPlayer);
                }
            }
            passed &= restored;
            if (passed) report.put("status", "PASS");
            writeReport(context, report);
        }
        assertTrue("DoH/StrictMode regression failed; see ech-validation/doh-switch-regression.json", passed);
    }

    private static Doh tencent() {
        return new Doh().name("Tencent").url("https://doh.pub/dns-query");
    }

    private static OkHttpClient isolatedPool(OkHttpClient shared, EventListener events) {
        return shared.newBuilder().connectionPool(new ConnectionPool()).dispatcher(new Dispatcher())
                .protocols(Collections.singletonList(Protocol.HTTP_1_1)).proxy(Proxy.NO_PROXY)
                .eventListener(events).retryOnConnectionFailure(false)
                .callTimeout(45, TimeUnit.SECONDS).connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).build();
    }

    private static Request request() {
        return new Request.Builder().url(TRACE).header("Accept-Encoding", "identity").build();
    }

    private static Socket requireConscrypt(Socket socket) {
        assertTrue("A real Conscrypt TLS socket is required", socket instanceof SSLSocket
                && Conscrypt.isConscrypt((SSLSocket) socket));
        return socket;
    }

    private static int consume(Response response) throws Exception {
        int total = 0;
        byte[] buffer = new byte[2048];
        try (InputStream input = response.body().byteStream()) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                assertTrue("Trace response exceeded limit", total <= 32 * 1024);
            }
        }
        return total;
    }

    private static void strictMain(Instrumentation instrumentation, Runnable change) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            StrictMode.ThreadPolicy original = StrictMode.getThreadPolicy();
            try {
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                        .detectNetwork().penaltyDeathOnNetwork().build());
                change.run();
            } catch (Throwable error) {
                // Android throws NetworkOnMainThreadException here; capture it without killing the test process.
                failure.set(error);
            } finally {
                StrictMode.setThreadPolicy(original);
            }
        });
        assertNull("Strict main-thread network policy must remain satisfied", failure.get());
    }

    private static void drainEvictions() throws Exception {
        Object evictor = field(OkHttp.class, "poolEvictor").get(OkHttp.get());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field(evictor.getClass(), "executor").get(evictor);
        CountDownLatch reached = new CountDownLatch(1);
        Runnable barrier = reached::countDown;
        long deadline = SystemClock.elapsedRealtime() + 5000;
        try {
            // A full bounded queue discards submissions. Retry the same harmless barrier until acknowledged.
            do {
                executor.execute(barrier);
                if (reached.await(20, TimeUnit.MILLISECONDS)) return;
            } while (SystemClock.elapsedRealtime() < deadline);
            throw new AssertionError("Idle cleanup did not finish");
        } finally {
            // A duplicate barrier must not occupy the one pending slot before the next settings change.
            executor.remove(barrier);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void closeOwned(OkHttpClient client) {
        if (client == null) return;
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdownNow();
    }

    private static void writeReport(Context context, JSONObject report) throws Exception {
        File external = context.getExternalFilesDir(null);
        assertNotNull("Report directory must be available", external);
        File directory = new File(external, "ech-validation");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, "doh-switch-regression.json"))) {
            output.write(report.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class SocketEvents extends EventListener {
        volatile Socket socket;

        @Override
        public void connectionAcquired(Call call, Connection connection) {
            socket = connection.socket();
        }
    }
}
