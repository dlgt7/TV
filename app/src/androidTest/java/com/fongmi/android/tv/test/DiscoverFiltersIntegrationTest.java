package com.fongmi.android.tv.test;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DiscoverFilterOption;
import com.fongmi.android.tv.bean.DiscoverFilterPanel;
import com.fongmi.android.tv.bean.DiscoverListQuery;
import com.fongmi.android.tv.bean.DiscoverQuery;
import com.fongmi.android.tv.bean.DiscoverRequestState;
import com.fongmi.android.tv.bean.DoubanDiscoverQuery;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.activity.DiscoverActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Real activity, real presenters and live APIs. Runs only in the disposable sourceprobe UID. */
@RunWith(AndroidJUnit4.class)
public final class DiscoverFiltersIntegrationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context target = instrumentation.getTargetContext();
    private final JSONObject report = new JSONObject();
    private final JSONArray stages = new JSONArray();
    private DiscoverActivity activity;
    private String stage = "start";

    @Test
    public void realDiscoverFiltersKeepLibrariesAndPlatformStateSeparate() throws Exception {
        assertEquals("Use the isolated test application", "com.fongmi.android.tv.sourceprobe", target.getPackageName());
        try {
            activity = (DiscoverActivity) instrumentation.startActivitySync(new Intent(target, DiscoverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            stage = "initial-douban-movie";
            awaitResults("douban:");
            main(() -> {
                assertTrue(query() instanceof DoubanDiscoverQuery);
                assertEquals(6, panel().getRowCount());
                return null;
            });
            showPanel(false);

            stage = "douban-tv-netflix";
            int firstGeneration = generation();
            choose("FILTER_MEDIA", "tv");
            choose("FILTER_PLATFORM", "Netflix");
            main(() -> {
                assertEquals(7, panel().getRowCount());
                assertTrue(((DoubanDiscoverQuery) query()).buildUrl().queryParameter("tags").contains("电视剧,Netflix"));
                assertFalse(state().accepts(firstGeneration));
                activity.onFilterGroupClick(row("FILTER_PLATFORM"));
                return null;
            });
            awaitResults("douban:");
            showPanel(true);

            stage = "movie-hides-tv-platform";
            int tvGeneration = generation();
            choose("FILTER_MEDIA", "movie");
            main(() -> {
                assertEquals(6, panel().getRowCount());
                assertEquals(-1, panel().getExpandedRow());
                assertEquals("电影", ((DoubanDiscoverQuery) query()).buildUrl().queryParameter("tags"));
                assertEquals("", field("platform"));
                assertFalse(state().accepts(tvGeneration));
                return null;
            });
            awaitResults("douban:");
            showPanel(false);

            stage = "switch-to-tmdb";
            // Leave a real Douban request in flight while switching libraries.
            choose("FILTER_GENRE", "科幻");
            int movieGeneration = generation();
            choose("FILTER_LIBRARY", "tmdb");
            main(() -> {
                assertTrue(query() instanceof DiscoverQuery);
                assertEquals(6, panel().getRowCount());
                assertFalse(state().accepts(movieGeneration));
                assertFalse(panel().getRow(row("FILTER_MEDIA")).stream().anyMatch(option -> "show".equals(option.getValue())));
                return null;
            });
            // A TMDB network error must fail, not count as a successful library switch.
            awaitResults("tmdb:");
            showPanel(false);

            stage = "return-to-douban-show";
            int tmdbGeneration = generation();
            choose("FILTER_LIBRARY", "douban");
            choose("FILTER_MEDIA", "show");
            choose("FILTER_GENRE", "真人秀");
            choose("FILTER_REGION", "华语");
            main(() -> {
                assertTrue(query() instanceof DoubanDiscoverQuery);
                assertEquals("综艺,真人秀,华语", ((DoubanDiscoverQuery) query()).buildUrl().queryParameter("tags"));
                assertFalse(state().accepts(tmdbGeneration));
                return null;
            });
            awaitResults("douban:");
            showPanel(false);
            report.put("passed", true);
        } catch (Throwable failure) {
            report.put("passed", false);
            report.put("failedStage", stage);
            report.put("failureType", failure.getClass().getSimpleName());
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new AssertionError(failure);
        } finally {
            report.put("stages", stages);
            try (FileOutputStream output = new FileOutputStream(new File(target.getFilesDir(), "discover-filters-integration-result.json"))) {
                output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            if (activity != null) main(() -> { activity.finish(); return null; });
        }
    }

    private void choose(String fieldName, String value) throws Exception {
        main(() -> {
            int row = row(fieldName);
            DiscoverFilterOption selected = panel().getRow(row).stream()
                    .filter(option -> value.equals(option.getValue())).findFirst()
                    .orElseThrow(() -> new AssertionError("Missing filter option: " + fieldName + "/" + value));
            int before = state().getGeneration();
            activity.onFilterClick(row, selected);
            assertTrue("Changed filter must create a new request generation", state().getGeneration() > before);
            return null;
        });
    }

    private void awaitResults(String prefix) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 40_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            int count = main(() -> {
                if (!(boolean) field("queryFinished")) return -1;
                assertFalse("Live API returned no usable results in " + stage, state().isEmpty());
                for (Vod item : state().getItems()) assertTrue("Previous library leaked into " + stage, item.getId().startsWith(prefix));
                assertTrue(activity.findViewById(R.id.recycler).isShown());
                return state().size();
            });
            if (count > 0) {
                stages.put(new JSONObject().put("stage", stage).put("resultCount", count).put("generation", generation()));
                return;
            }
            SystemClock.sleep(150);
        }
        fail("Live API timed out in " + stage);
    }

    private void showPanel(boolean expanded) throws Exception {
        int position = main(() -> {
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) field("mAdapter");
            int value = adapter.indexOf(panel());
            assertTrue(value >= 0);
            ((VerticalGridView) activity.findViewById(R.id.recycler)).setSelectedPosition(value);
            return value;
        });
        long deadline = SystemClock.elapsedRealtime() + 8_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (main(() -> {
                RecyclerView recycler = activity.findViewById(R.id.recycler);
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
                if (holder == null || !holder.itemView.isShown()) return false;
                View summary = holder.itemView.findViewById(R.id.summary);
                View options = holder.itemView.findViewById(R.id.optionsContainer);
                if (summary == null || !summary.isShown() || summary.getWidth() <= 0) return false;
                return options != null && options.getVisibility() == (expanded ? View.VISIBLE : View.GONE);
            })) return;
            SystemClock.sleep(100);
        }
        fail("Actual filter presenter was not visible in " + stage);
    }

    private int generation() throws Exception { return main(() -> state().getGeneration()); }
    private DiscoverFilterPanel panel() throws Exception { return (DiscoverFilterPanel) field("filterPanel"); }
    private DiscoverRequestState state() throws Exception { return (DiscoverRequestState) field("requestState"); }
    private int row(String name) throws Exception { return (int) field(name); }
    private Object field(String name) throws Exception {
        Field field = DiscoverActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
    private DiscoverListQuery query() throws Exception {
        Method method = DiscoverActivity.class.getDeclaredMethod("currentQuery", int.class);
        method.setAccessible(true);
        return (DiscoverListQuery) method.invoke(activity, 1);
    }
    private <T> T main(Callable<T> callable) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set(callable.call()); } catch (Throwable error) { failure.set(error); }
        });
        Throwable error = failure.get();
        if (error instanceof Exception exception) throw exception;
        if (error instanceof Error assertion) throw assertion;
        if (error != null) throw new AssertionError(error);
        return result.get();
    }
}
