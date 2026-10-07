package com.fongmi.android.tv.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DiscoverApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.BrowseExperienceSettings;
import com.fongmi.android.tv.source.PosterSourceResults;
import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.fongmi.android.tv.ui.activity.DiscoverDetailActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.custom.JetStreamPageProgressLayout;
import com.fongmi.android.tv.ui.home.PosterHomeController;
import com.fongmi.android.tv.utils.TmdbNetwork;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;

/** Real native activities and D-pad events, using in-memory posters and a deliberately offline client. */
@RunWith(AndroidJUnit4.class)
public final class NativeBrowseIntegrationTest {
    private static final String[] PREFERENCES = {"browse_poster_home", "browse_search_filter", "browse_detail_sources", "browse_smart_sources"};
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> launched = new ArrayList<>();
    private final Map<String, Object> preferences = new HashMap<>();
    private final AtomicInteger rejectedRequests = new AtomicInteger();
    private Map<DiscoverApi.Row, Object> cache;
    private Map<DiscoverApi.Row, Object> savedCache;
    private OkHttpClient originalClient;
    private OkHttpClient originalTmdbClient;
    private OkHttpClient offlineClient;
    private Object savedSites;
    private boolean replacedSites;
    private boolean prepared;

    @Before public void setup() {
        String packageName = instrumentation.getTargetContext().getPackageName();
        assertTrue("Run only in an isolated validation application", packageName.equals("com.fongmi.android.tv.preview")
                || packageName.equals("com.fongmi.android.tv.sourceprobe"));
        assertEquals("Native browsing is a TV feature", "leanback", BuildConfig.FLAVOR_mode);
        main(() -> {
            Map<String, ?> existing = Prefers.getPrefers().getAll();
            for (String key : PREFERENCES) if (existing.containsKey(key)) preferences.put(key, existing.get(key));
            cache = discoverCache();
            savedCache = new EnumMap<>(DiscoverApi.Row.class);
            savedCache.putAll(cache);
            cache.clear();
            originalClient = OkHttp.client();
            originalTmdbClient = (OkHttpClient) field(TmdbNetwork.class, "client");
            offlineClient = new OkHttpClient.Builder().addInterceptor(chain -> {
                rejectedRequests.incrementAndGet();
                throw new IOException("Intentional offline native browsing fixture");
            }).build();
            setField(OkHttp.get(), "client", offlineClient);
            setField(TmdbNetwork.class, "client", offlineClient);
            BrowseExperienceSettings.restoreOriginal();
            prepared = true;
        });
    }

    @After public void cleanup() {
        if (!prepared) return;
        main(() -> {
            for (int i = launched.size() - 1; i >= 0; i--) {
                Activity activity = launched.get(i);
                if (!activity.isDestroyed()) activity.finish();
            }
        });
        instrumentation.waitForIdleSync();
        main(() -> {
            offlineClient.dispatcher().cancelAll();
            offlineClient.connectionPool().evictAll();
            setField(OkHttp.get(), "client", originalClient);
            setField(TmdbNetwork.class, "client", originalTmdbClient);
            cache.clear();
            cache.putAll(savedCache);
            if (replacedSites) setField(VodConfig.get(), "sites", savedSites);
            SharedPreferences.Editor editor = Prefers.getPrefers().edit();
            for (String key : PREFERENCES) {
                Object value = preferences.get(key);
                if (value instanceof Boolean flag) editor.putBoolean(key, flag);
                else if (value instanceof Integer mode) editor.putInt(key, mode);
                else if (value instanceof String text) editor.putString(key, text);
                else if (value instanceof Long number) editor.putLong(key, number);
                else if (value instanceof Float number) editor.putFloat(key, number);
                else editor.remove(key);
            }
            assertTrue("Restore the exact pre-test settings", editor.commit());
        });
    }

    @Test(timeout = 60000)
    public void homeModeOptInAndRestoreUseTheRealResumePath() {
        HomeActivity original = launch(HomeActivity.class);
        await(() -> original.hasWindowFocus(), "original home window");
        main(() -> {
            assertNull(field(original, "mPosterHome"));
            assertFalse(containsWebView(original.getWindow().getDecorView()));
        });

        HomeActivity wall = changeHomeMode(original, () -> BrowseExperienceSettings.putPosterHomeEnabled(true), true);
        assertNotSame("Changing the mode rebuilds the home surface", original, wall);
        main(() -> {
            assertNotNull(field(wall, "mPosterHome"));
            assertFalse(((JetStreamPageProgressLayout) wall.findViewById(R.id.progressLayout)).isProgress());
            assertFalse(containsWebView(wall.getWindow().getDecorView()));
        });

        HomeActivity restored = changeHomeMode(wall, BrowseExperienceSettings::restoreOriginal, false);
        main(() -> {
            assertNull(field(restored, "mPosterHome"));
            assertFalse(BrowseExperienceSettings.isDetailSourcesEnabled());
            assertFalse(BrowseExperienceSettings.isSmartSourceEnabled());
            assertEquals(0, BrowseExperienceSettings.getSearchFilterMode());
            assertFalse(containsWebView(restored.getWindow().getDecorView()));
        });
    }

    @Test(timeout = 45000)
    public void offlineWallKeepsCategoriesRetryAndNavigationReachable() {
        main(() -> BrowseExperienceSettings.putPosterHomeEnabled(true));
        HomeActivity home = launch(HomeActivity.class);
        await(() -> home.hasWindowFocus() && (int) field(field(home, "mPosterHome"), "pending") == 0,
                "all discovery requests finish with offline errors");
        assertTrue("The unavailable APIs were actually attempted", rejectedRequests.get() > 0);
        main(() -> {
            assertFalse(((JetStreamPageProgressLayout) home.findViewById(R.id.progressLayout)).isProgress());
            assertFalse(containsWebView(home.getWindow().getDecorView()));
            invoke(home, "requestNavFocus");
        });
        View nav = value(() -> home.findViewById(R.id.nav));
        VerticalGridView recycler = value(() -> home.findViewById(R.id.recycler));
        await(nav::hasFocus, "top navigation");
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> recycler.hasFocus() && recycler.getSelectedPosition() == 1,
                "first actionable content is the category row when no poster is available");
        press(KeyEvent.KEYCODE_DPAD_UP);
        await(nav::hasFocus, "UP from categories returns to navigation");

        main(() -> focusRow(home, ((ArrayObjectAdapter) field(home, "mAdapter")).size() - 1));
        await(() -> visibleView(home, R.id.retry) != null && visibleView(home, R.id.retry).hasFocus(), "offline retry button");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> visibleView(home, R.id.more) != null && visibleView(home, R.id.more).hasFocus(), "more recommendations button");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> visibleView(home, R.id.settings) != null && visibleView(home, R.id.settings).hasFocus(), "home settings button");
        press(KeyEvent.KEYCODE_BACK);
        await(nav::hasFocus, "BACK from an offline wall returns to the navigation");
    }

    @Test(timeout = 60000)
    public void fixtureHeroCategoriesAndPostersOpenNativeDetailsWithDpad() {
        main(() -> {
            seedPosters();
            BrowseExperienceSettings.putPosterHomeEnabled(true);
            BrowseExperienceSettings.putDetailSourcesEnabled(true);
        });
        HomeActivity home = launch(HomeActivity.class);
        VerticalGridView recycler = value(() -> home.findViewById(R.id.recycler));
        await(() -> home.hasWindowFocus() && recycler.findViewHolderForAdapterPosition(0) != null,
                "native hero is attached");
        main(() -> invoke(home, "requestNavFocus"));
        await(() -> home.findViewById(R.id.nav).hasFocus(), "navigation before entering the hero");
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> recycler.hasFocus() && recycler.getSelectedPosition() == 0, "hero focus");
        String firstTitle = value(() -> rowText(recycler, 0, R.id.name));
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> !firstTitle.equals(rowText(recycler, 0, R.id.name)), "manual hero advance");
        String selectedTitle = value(() -> rowText(recycler, 0, R.id.name));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        DiscoverDetailActivity detail = awaitActivity(DiscoverDetailActivity.class);
        main(() -> {
            assertEquals(selectedTitle, ((TextView) detail.findViewById(R.id.title)).getText().toString());
            assertFalse(containsWebView(detail.getWindow().getDecorView()));
        });
        press(KeyEvent.KEYCODE_BACK);
        await(() -> home.hasWindowFocus() && recycler.hasFocus(), "return from native detail retains content focus");
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(() -> recycler.getSelectedPosition() == 1 && recycler.hasFocus(), "category row below the hero");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> (int) field(field(home, "mPosterHome"), "category") == 1, "movie category selected with the remote");
        int shelfPosition = value(() -> firstShelfPosition(home));
        main(() -> focusRow(home, shelfPosition));
        // Home first selects the shelf, then transfers focus to a card in a delayed callback.
        // The category still owns focus in between; sending CENTER then cancels that callback.
        await(() -> focusedPosterCard(home, shelfPosition) != null, "the actual clickable poster card receives focus");
        String posterTitle = value(() -> ((TextView) focusedPosterCard(home, shelfPosition).findViewById(R.id.name)).getText().toString());
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        DiscoverDetailActivity fromPoster = awaitActivity(DiscoverDetailActivity.class);
        main(() -> {
            assertEquals(posterTitle, ((TextView) fromPoster.findViewById(R.id.title)).getText().toString());
            assertFalse(containsWebView(fromPoster.getWindow().getDecorView()));
            assertNotNull(field(fromPoster, "sources"));
        });
    }

    @Test(timeout = 60000)
    public void detailSourcePanelIsOptionalAndWorksWithoutMetadataOrConfiguredSources() {
        DiscoverDetailActivity original = launchDetail(false);
        await(() -> original.hasWindowFocus(), "original detail window");
        main(() -> {
            assertNull(field(original, "sources"));
            assertEquals(original.getString(R.string.discover_search_play), ((TextView) original.findViewById(R.id.search)).getText().toString());
            assertEquals(View.GONE, original.findViewById(R.id.sourcePanel).getVisibility());
            original.finish();
            BrowseExperienceSettings.putDetailSourcesEnabled(true);
            savedSites = field(VodConfig.get(), "sites");
            replacedSites = true;
            setField(VodConfig.get(), "sites", new ArrayList<Site>());
        });
        instrumentation.waitForIdleSync();

        DiscoverDetailActivity detail = launchDetail(true);
        await(() -> detail.hasWindowFocus() && (boolean) field(detail, "metadataError"), "unavailable metadata uses poster fallback");
        View panel = value(() -> detail.findViewById(R.id.sourcePanel));
        main(() -> {
            assertEquals("星河旅人", ((TextView) detail.findViewById(R.id.title)).getText().toString());
            assertTrue(detail.findViewById(R.id.search).requestFocus());
        });
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> panel.isShown() && panel.findViewById(R.id.smart).hasFocus(), "native source panel initial focus");
        main(() -> {
            assertEquals(View.GONE, detail.findViewById(R.id.poster).getVisibility());
            assertEquals(detail.getString(R.string.poster_sources_no_sites), ((TextView) panel.findViewById(R.id.status)).getText().toString());
            assertFalse(containsWebView(detail.getWindow().getDecorView()));
        });
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> panel.findViewById(R.id.retry).hasFocus(), "source retry reachable by D-pad");
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        await(() -> panel.findViewById(R.id.smart).hasFocus(), "return to smart source option");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(BrowseExperienceSettings::isSmartSourceEnabled, "smart sorting is separately optional");

        // Feed only transient search results; never install a source or launch its playback code.
        main(() -> {
            Object controller = field(detail, "sources");
            Vod candidate = poster("fixture-source-item", "星河旅人", "movie");
            candidate.setSite(Site.get("native-browse-fixture", "示例播放源"));
            ((PosterSourceResults) field(controller, "results")).add(List.of(candidate));
            invoke(controller, "render");
        });
        RecyclerView candidates = value(() -> panel.findViewById(R.id.results));
        await(() -> candidates.findViewHolderForAdapterPosition(0) != null, "a matched source renders as a native row");
        main(() -> assertTrue(candidates.findViewHolderForAdapterPosition(0).itemView.requestFocus()));
        await(candidates::hasFocus, "source row focus");
        press(KeyEvent.KEYCODE_BACK);
        await(() -> !panel.isShown() && detail.findViewById(R.id.search).hasFocus(), "BACK closes only the panel and restores the primary action");
        main(() -> assertFalse(detail.isFinishing()));
    }

    private HomeActivity changeHomeMode(HomeActivity previous, Runnable change, boolean enabled) {
        CorePlaybackActivity cover = launch(CorePlaybackActivity.class);
        await(cover::hasWindowFocus, "temporary activity pauses home");
        main(() -> { change.run(); cover.finish(); });
        AtomicReference<HomeActivity> replacement = new AtomicReference<>();
        await(() -> {
            for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (candidate instanceof HomeActivity home && home != previous && home.hasWindowFocus()
                        && (field(home, "mPosterHome") != null) == enabled) {
                    replacement.set(home);
                    return true;
                }
            }
            return false;
        }, "home recreates after the mode setting changes");
        launched.add(replacement.get());
        return replacement.get();
    }

    private DiscoverDetailActivity launchDetail(boolean douban) {
        Intent intent = new Intent(instrumentation.getTargetContext(), DiscoverDetailActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (douban) intent.putExtra("doubanItem", poster("douban:900001", "星河旅人", "movie"));
        else intent.putExtra("key", "tmdb:movie:900001");
        intent.putExtra("title", "星河旅人");
        intent.putExtra("year", "2026");
        intent.putExtra("poster", artwork());
        intent.putExtra("backdrop", artwork());
        intent.putExtra("overview", "用于验证遥控器操作的本地影片卡片，元数据网络不可用时仍能选择播放源。");
        DiscoverDetailActivity activity = (DiscoverDetailActivity) instrumentation.startActivitySync(intent);
        launched.add(activity);
        return activity;
    }

    private void seedPosters() {
        List<Vod> movies = List.of(poster("tmdb:movie:900001", "星河旅人", "movie"), poster("tmdb:movie:900003", "山海之间", "movie"));
        List<Vod> series = List.of(poster("tmdb:tv:900002", "长夜微光", "tv"));
        try {
            Constructor<?> constructor = Class.forName(DiscoverApi.class.getName() + "$CacheEntry").getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            for (DiscoverApi.Row row : DiscoverApi.Row.values()) {
                List<Vod> items = row.name().contains("TV") ? series : movies;
                cache.put(row, constructor.newInstance(items));
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private Vod poster(String id, String title, String type) {
        Vod item = new Vod();
        item.setId(id);
        item.setName(title);
        item.setTypeName(type);
        item.setYear("2026");
        item.setPic(artwork());
        item.setBackdrop(artwork());
        item.setContent("用于验证原生海报墙与遥控器操作的本地卡片。");
        return item;
    }

    private String artwork() {
        return "android.resource://" + instrumentation.getTargetContext().getPackageName() + "/" + R.drawable.wallpaper_1;
    }

    private int firstShelfPosition(HomeActivity home) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) field(home, "mAdapter");
        PosterHomeController controller = (PosterHomeController) field(home, "mPosterHome");
        for (int i = adapter.indexOf(R.string.home_recommend) + 1; i < adapter.size() - 1; i++) {
            if (controller.isFocusable(adapter.get(i))) return i;
        }
        throw new AssertionError("Fixture has no focusable poster shelf");
    }

    private static String rowText(RecyclerView recycler, int position, int viewId) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder == null) return "";
        TextView view = holder.itemView.findViewById(viewId);
        return view == null ? "" : view.getText().toString();
    }

    private static View focusedPosterCard(HomeActivity home, int shelfPosition) {
        VerticalGridView page = home.findViewById(R.id.recycler);
        if (!home.hasWindowFocus() || page.getSelectedPosition() != shelfPosition) return null;
        RecyclerView.ViewHolder shelf = page.findViewHolderForAdapterPosition(shelfPosition);
        if (shelf == null) return null;
        HorizontalGridView posters = shelf.itemView.findViewById(R.id.posters);
        if (posters == null || !posters.hasFocus()) return null;
        RecyclerView.ViewHolder card = posters.findViewHolderForAdapterPosition(posters.getSelectedPosition());
        if (card == null || !card.itemView.isShown() || !card.itemView.hasFocus()
                || !card.itemView.isClickable() || !card.itemView.hasOnClickListeners()) return null;
        return card.itemView;
    }

    private static View visibleView(Activity activity, int id) {
        View view = activity.findViewById(id);
        return view != null && view.isShown() ? view : null;
    }

    private static boolean containsWebView(View view) {
        if (view instanceof WebView) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            if (containsWebView(group.getChildAt(i))) return true;
        }
        return false;
    }

    private <T extends Activity> T launch(Class<T> type) {
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
        if (type == HomeActivity.class) flags |= Intent.FLAG_ACTIVITY_CLEAR_TASK;
        T activity = type.cast(instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), type)
                .addFlags(flags)));
        launched.add(activity);
        return activity;
    }

    private <T extends Activity> T awaitActivity(Class<T> type) {
        AtomicReference<T> result = new AtomicReference<>();
        await(() -> {
            for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (type.isInstance(candidate) && candidate.hasWindowFocus()) {
                    result.set(type.cast(candidate));
                    return true;
                }
            }
            return false;
        }, type.getSimpleName() + " becomes visible");
        launched.add(result.get());
        return result.get();
    }

    @SuppressWarnings("unchecked")
    private static Map<DiscoverApi.Row, Object> discoverCache() {
        try {
            Field field = DiscoverApi.class.getDeclaredField("CACHE");
            field.setAccessible(true);
            return (Map<DiscoverApi.Row, Object>) field.get(null);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner instanceof Class<?> ? null : owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            Field field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner instanceof Class<?> ? null : owner, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void invoke(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void focusRow(HomeActivity home, int position) {
        try {
            Method method = HomeActivity.class.getDeclaredMethod("requestRecyclerFocus", int.class);
            method.setAccessible(true);
            method.invoke(home, position);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void press(int keyCode) {
        instrumentation.sendKeyDownUpSync(keyCode);
        instrumentation.waitForIdleSync();
    }

    private void await(BooleanSupplier predicate, String reason) {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (value(predicate::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for " + reason);
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
}
