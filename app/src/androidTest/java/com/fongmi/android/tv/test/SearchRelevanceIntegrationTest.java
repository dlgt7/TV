package com.fongmi.android.tv.test;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.BrowseExperienceSettings;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SourceSelectionSetting;
import com.fongmi.android.tv.source.SourceSelectionMode;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import fi.iki.elonen.NanoHTTPD;

import static org.junit.Assert.*;

/** Real search screen, loopback sources and filter dialog; every changed preference is restored. */
@RunWith(AndroidJUnit4.class)
public final class SearchRelevanceIntegrationTest {
    private static final String[] PREFERENCES = {"browse_search_filter", "keyword", "source_selection_mode"};
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> preferences = new HashMap<>();
    private Fixture server;
    private Object savedSites;
    private boolean prepared;
    private CorePlaybackActivity anchor;
    private TextView anchorFocus;
    private CollectActivity activity;

    @Before public void prepareIsolatedSources() throws Exception {
        String packageName = instrumentation.getTargetContext().getPackageName();
        assertTrue("Use an isolated validation application", packageName.equals("com.fongmi.android.tv.preview")
                || packageName.equals("com.fongmi.android.tv.sourceprobe"));
        assertEquals("This verifies the native TV search screen", "leanback", BuildConfig.FLAVOR_mode);
        server = new Fixture("android.resource://" + packageName + "/" + R.drawable.wallpaper_1);
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        main(() -> {
            Map<String, ?> existing = Prefers.getPrefers().getAll();
            for (String key : PREFERENCES) if (existing.containsKey(key)) preferences.put(key, existing.get(key));
            savedSites = field(VodConfig.get(), "sites");
            prepared = true;
            setField(VodConfig.get(), "sites", new ArrayList<>(List.of(site("a"), site("b"))));
            BrowseExperienceSettings.putSearchFilterMode(0);
            SourceSelectionSetting.putMode(SourceSelectionMode.LEGACY);
            Setting.putKeyword("[]");
        });
    }

    @After public void restoreExactSourceAndPreferenceState() {
        try {
            main(() -> {
                if (activity != null && !activity.isDestroyed()) activity.finish();
                if (anchor != null && !anchor.isDestroyed()) anchor.finish();
            });
            instrumentation.waitForIdleSync();
        } finally {
            if (server != null) server.stop();
            if (prepared) main(() -> {
                setField(VodConfig.get(), "sites", savedSites);
                SharedPreferences.Editor editor = Prefers.getPrefers().edit();
                for (String key : PREFERENCES) {
                    Object value = preferences.get(key);
                    if (value instanceof Integer mode) editor.putInt(key, mode);
                    else if (value instanceof String text) editor.putString(key, text);
                    else editor.remove(key);
                }
                assertTrue("Restore the exact search settings and keyword history", editor.commit());
            });
        }
    }

    @Test(timeout = 60_000) public void changingFiltersUsesCachedHttpResultsAndKeepsReturnFocus() throws Exception {
        anchor = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), CorePlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            anchorFocus = new TextView(anchor);
            anchorFocus.setText("Search fixture return focus");
            anchorFocus.setFocusable(true);
            anchorFocus.setFocusableInTouchMode(true);
            anchor.setContentView(anchorFocus);
            assertTrue(anchorFocus.requestFocus());
        });
        await(() -> anchor.hasWindowFocus() && anchorFocus.hasFocus(), "initial host focus");
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(CollectActivity.class.getName(), null, false);
        try {
            main(() -> CollectActivity.start(anchor, "庆余年"));
            activity = (CollectActivity) instrumentation.waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull("Search screen was not launched", activity);
        } finally { instrumentation.removeMonitor(monitor); }

        await(() -> activity.hasWindowFocus() && displayedTitles().size() == 6, "both real HTTP search responses rendered");
        assertEquals(2, server.searchRequests.get());
        assertMode(0, 6, true, true);

        chooseMode(1);
        assertMode(1, 4, false, true);
        chooseMode(2);
        assertMode(2, 2, false, false);
        chooseMode(0);
        assertMode(0, 6, true, true);
        assertEquals("Changing filters must not start another HTTP search", 2, server.searchRequests.get());
        assertEquals("Fixture posters must not trigger fetchPic or pagination", 0, server.unexpectedRequests.get());
        assertNull(server.failure.get());

        // Cancelling the actual selection dialog must return to its invoking control.
        main(() -> assertTrue(activity.findViewById(R.id.relevance).requestFocus()));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoiceVisible(0);
        press(KeyEvent.KEYCODE_BACK);
        await(() -> activity.hasWindowFocus() && activity.findViewById(R.id.relevance).hasFocus(), "filter button after cancelling dialog");
        assertEquals(0, value(BrowseExperienceSettings::getSearchFilterMode).intValue());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> activity.findViewById(R.id.recycler).hasFocus(), "source tabs remain reachable below the filter");
        press(KeyEvent.KEYCODE_DPAD_UP);
        await(() -> activity.findViewById(R.id.relevance).hasFocus(), "UP returns to the filter");
        press(KeyEvent.KEYCODE_BACK);
        await(() -> anchor.hasWindowFocus() && anchorFocus.hasFocus(), "BACK restores the original host focus");
        assertEquals(2, server.searchRequests.get());
    }

    private void assertMode(int mode, int size, boolean hasShort, boolean hasSeason) {
        await(() -> BrowseExperienceSettings.getSearchFilterMode() == mode && displayedTitles().size() == size, "filtered poster rows for mode " + mode);
        main(() -> {
            List<String> titles = displayedTitles();
            assertEquals(hasShort, titles.contains("庆"));
            assertEquals(hasSeason, titles.contains("庆余年 第二季"));
            assertEquals(2, titles.stream().filter("庆余年"::equals).count());
            assertFalse(titles.contains("完美世界"));
            int label = mode == 0 ? R.string.search_relevance_legacy : mode == 1 ? R.string.search_relevance_related : R.string.search_relevance_strict;
            assertEquals(activity.getString(label), ((TextView) activity.findViewById(R.id.relevance)).getText().toString());
        });
        assertEquals(2, server.searchRequests.get());
    }

    private void chooseMode(int mode) throws Exception {
        main(() -> assertTrue(activity.findViewById(R.id.relevance).requestFocus()));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        String text = value(() -> activity.getResources().getStringArray(R.array.search_relevance_modes)[mode]);
        awaitChoiceVisible(mode);
        assertTrue("Click the real dialog choice", clickAccessibleText(text));
        await(() -> activity.hasWindowFocus() && BrowseExperienceSettings.getSearchFilterMode() == mode
                && activity.findViewById(R.id.relevance).hasFocus(), "filter choice applied and focus returned");
    }

    private void awaitChoiceVisible(int mode) {
        String text = value(() -> activity.getResources().getStringArray(R.array.search_relevance_modes)[mode]);
        long until = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < until) {
            if (accessibleChoice(text) != null) return;
            SystemClock.sleep(50);
        }
        fail("Filter dialog choice not visible: " + text);
    }

    private AccessibilityNodeInfo accessibleChoice(String text) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
            if (node.getText() != null && text.contentEquals(node.getText()) && node.isVisibleToUser()) return node;
        }
        return null;
    }

    private boolean clickAccessibleText(String text) {
        AccessibilityNodeInfo node = accessibleChoice(text);
        if (node == null) return false;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) return false;
        // Material's single-choice label can be non-clickable: ListView owns the item click.
        // A real pointer tap on the visible row exercises that path and mixed touch/D-pad input.
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0);
        try {
            instrumentation.sendPointerSync(down);
            SystemClock.sleep(50);
            instrumentation.sendPointerSync(up);
        } finally { down.recycle(); up.recycle(); }
        instrumentation.waitForIdleSync();
        return true;
    }

    /** Inspect the actual ListRow adapter consumed by the displayed poster presenters. */
    private List<String> displayedTitles() {
        Object controller = field(activity, "results");
        Object pages = field(controller, "mPageAdapter");
        if (pages == null) return List.of();
        Object fragment = field(pages, "mAllFragment");
        if (fragment == null) return List.of();
        ArrayObjectAdapter rows = (ArrayObjectAdapter) field(fragment, "mAdapter");
        if (rows == null) return List.of();
        List<String> titles = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) if (rows.get(row) instanceof ListRow list) {
            for (int item = 0; item < list.getAdapter().size(); item++) if (list.getAdapter().get(item) instanceof Vod vod) {
                titles.add(Trans.t2s(false, vod.getName()).replace('馀', '余'));
            }
        }
        return titles;
    }

    private Site site(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("key", "search-ui-fixture-" + server.getListeningPort() + "-" + name);
        json.addProperty("name", "Search fixture " + name);
        json.addProperty("type", 1);
        json.addProperty("searchable", 1);
        json.addProperty("api", "http://127.0.0.1:" + server.getListeningPort() + "/" + name);
        return App.gson().fromJson(json, Site.class);
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void press(int code) {
        instrumentation.sendKeyDownUpSync(code);
        instrumentation.waitForIdleSync();
    }

    private void await(BooleanSupplier predicate, String message) {
        long until = SystemClock.elapsedRealtime() + 7000;
        while (SystemClock.elapsedRealtime() < until) {
            if (value(predicate::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for " + message);
    }

    private void main(Runnable action) { value(() -> { action.run(); return null; }); }

    private <T> T value(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set(action.get()); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("UI operation failed", failure.get());
        return result.get();
    }

    private static final class Fixture extends NanoHTTPD {
        final AtomicInteger searchRequests = new AtomicInteger();
        final AtomicInteger unexpectedRequests = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final String artwork;

        Fixture(String artwork) { super("127.0.0.1", 0); this.artwork = artwork; }

        @Override public Response serve(IHTTPSession session) {
            try {
                if (session.getMethod() != Method.GET || !(session.getUri().equals("/a") || session.getUri().equals("/b"))
                        || session.getParms().containsKey("ids") || session.getParms().containsKey("pg")) {
                    unexpectedRequests.incrementAndGet();
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Unexpected search fixture request");
                }
                if (!"庆余年".equals(Trans.t2s(false, session.getParms().getOrDefault("wd", "")))) throw new IllegalArgumentException("Unexpected search keyword");
                searchRequests.incrementAndGet();
                JSONArray items = new JSONArray();
                String[] names = {"庆", "庆余年", "庆余年 第二季", "完美世界"};
                for (int i = 0; i < names.length; i++) items.put(new JSONObject().put("vod_id", "fixture-" + i)
                        .put("vod_name", names[i]).put("vod_pic", artwork).put("type_name", "电视剧"));
                String body = new JSONObject().put("page", 1).put("pagecount", 1).put("list", items).toString();
                return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body);
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Search fixture failure");
            }
        }
    }
}
