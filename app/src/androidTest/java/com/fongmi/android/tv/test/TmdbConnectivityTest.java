package com.fongmi.android.tv.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.api.DiscoverApi;
import com.fongmi.android.tv.bean.DiscoverDetail;
import com.fongmi.android.tv.bean.DiscoverMediaKey;
import com.fongmi.android.tv.bean.DiscoverQuery;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.TmdbNetwork;
import com.github.catvod.utils.Prefers;
import com.github.catvod.net.OkHttp;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Request;
import okhttp3.Response;
import static org.junit.Assert.*;

/** Live opt-in test against the preview's configured endpoint; reports contain no URLs or keys. */
@RunWith(AndroidJUnit4.class)
public final class TmdbConnectivityTest {
    @Test public void configuredTmdbLoadsRealListDetailAndArtwork() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.fongmi.android.tv.preview", context.getPackageName());
        SharedPreferences prefs = Prefers.getPrefers();
        boolean present = prefs.contains("tmdb_route_recovery");
        boolean previous = TmdbNetwork.isRouteRecoveryEnabled();
        Object tag = new Object();
        JSONObject report = new JSONObject().put("passed", false);
        long started = SystemClock.elapsedRealtime();
        try {
            TmdbNetwork.setRouteRecoveryEnabled(true);
            assertSame(OkHttp.client().dispatcher(), OkHttp.trustedClient().dispatcher());
            CountDownLatch listing = new CountDownLatch(1);
            AtomicReference<List<Vod>> items = new AtomicReference<>();
            AtomicReference<Exception> failure = new AtomicReference<>();
            DiscoverApi.fetch(DiscoverQuery.defaults(), BuildConfig.TMDB_API_KEY, tag, new DiscoverApi.QueryListener() {
                public void onSuccess(List<Vod> values, int page, int total) { items.set(values); listing.countDown(); }
                public void onError(Exception e) { failure.set(e); listing.countDown(); }
            });
            assertTrue("TMDB list timed out", listing.await(25, TimeUnit.SECONDS));
            if (failure.get() != null) fail("TMDB request failed: " + failure.get().getClass().getSimpleName());
            assertNotNull(items.get()); assertFalse(items.get().isEmpty());
            report.put("listCount", items.get().size());
            DiscoverMediaKey key = DiscoverMediaKey.parse(items.get().get(0).getId());
            assertNotNull(key);
            CountDownLatch details = new CountDownLatch(1);
            AtomicReference<DiscoverDetail> detail = new AtomicReference<>();
            DiscoverApi.fetchDetail(key, BuildConfig.TMDB_API_KEY, tag, new DiscoverApi.DetailListener() {
                public void onSuccess(DiscoverDetail value) { detail.set(value); details.countDown(); }
                public void onError(Exception e) { failure.set(e); details.countDown(); }
            });
            assertTrue("TMDB detail timed out", details.await(25, TimeUnit.SECONDS));
            if (failure.get() != null) fail("TMDB request failed: " + failure.get().getClass().getSimpleName());
            assertNotNull(detail.get()); assertFalse(detail.get().getTitle().isEmpty());
            report.put("detailLoaded", true);
            try (Response response = TmdbNetwork.newCall(new Request.Builder().url(detail.get().getPoster()).tag(tag).build()).execute()) {
                assertEquals(200, response.code());
                assertNotNull(response.body());
                assertTrue(response.header("Content-Type", "").startsWith("image/"));
                int bytes = response.body().bytes().length;
                assertTrue(bytes > 100);
                report.put("posterBytes", bytes);
            }
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("errorType", failure.getClass().getSimpleName());
            throw new AssertionError("TMDB network verification failed: " + failure.getClass().getSimpleName());
        } finally {
            DiscoverApi.cancel(tag);
            TmdbNetwork.setRouteRecoveryEnabled(previous);
            if (!present) prefs.edit().remove("tmdb_route_recovery").commit();
            report.put("settingRestored", present == prefs.contains("tmdb_route_recovery") && previous == TmdbNetwork.isRouteRecoveryEnabled());
            report.put("elapsedMs", SystemClock.elapsedRealtime() - started);
            try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "tmdb-connectivity-result.json"))) {
                output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
