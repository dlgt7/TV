package com.fongmi.android.tv.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicitly invoked, live-source probe; never loads or persists a VodConfig.
 * Required: -e source_url http://HOST/UNIQUE_ASCII_NAME.py
 * Optional: source_keyword, source_step_seconds (15..90), source_delay_ms (250..2000),
 * source_detail_count (2..6), core_fixture_root.
 *
 * Public results contain counts, hashes and error codes only. Resolved playback URLs,
 * headers and identifiers are written solely to files/source-validation/playback-cases.json.
 * This class resolves episodes; PythonSourcePlaybackTest performs actual playback.
 */
@RunWith(AndroidJUnit4.class)
public final class PythonSourceFunctionalTest {
    private static final String TAG = "PythonSourceProbe";
    private static final String[] RANKS = {"rank_hot", "rank_human_hot", "rank_comic_hot", "rank_ai_hot"};
    private static final String[] THEMES = {"romance", "period", "comeback", "legend", "growth",
            "family", "clan", "cute-kids", "suspense", "thriller", "horror", "supernatural",
            "costume", "fantasy", "wonder", "urban", "youth", "comedy", "sci-fi", "disaster",
            "action-adventure", "war", "variety", "drama"};
    private static final Pattern QUALITY = Pattern.compile("(?i)(\\d{3,4}P|[48]K)");
    private final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Bundle arguments = InstrumentationRegistry.getArguments();
    private final JSONArray steps = new JSONArray();
    private final JSONArray cases = new JSONArray();
    private final LinkedHashMap<String, JSONObject> candidates = new LinkedHashMap<>();
    private final List<JSONObject> details = new ArrayList<>();
    private final Set<String> caseKeys = new LinkedHashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "source-functional-probe");
        thread.setDaemon(true);
        return thread;
    });
    private final JSONObject report = new JSONObject();
    private Spider spider;
    private PythonGuard guard;
    private File sourceCache;
    private File publicReport;
    private File privateCases;
    private String sourceSha256 = "";
    private int failures;
    private int stepSeconds;
    private int delayMs;
    private boolean stalled;
    private boolean ownsSourceCache;

    @Test public void exerciseSourceWithoutChangingConfiguration() throws Exception {
        String sourceUrl = arguments.getString("source_url", "");
        URI source = URI.create(sourceUrl);
        assertTrue("source_url must be an HTTP(S) URL without query, fragment or credentials",
                ("http".equals(source.getScheme()) || "https".equals(source.getScheme()))
                        && source.getHost() != null && source.getRawQuery() == null
                        && source.getFragment() == null && source.getUserInfo() == null);
        String basename = source.getPath().substring(source.getPath().lastIndexOf('/') + 1);
        assertTrue("Use a unique ASCII .py basename", basename.matches("[A-Za-z][A-Za-z0-9_-]{8,100}\\.py"));
        sourceCache = new File(new File(target.getCacheDir(), "py"), basename);
        assertTrue("Temporary source filename already exists; choose a fresh name", !sourceCache.exists());
        ownsSourceCache = true;
        stepSeconds = option("source_step_seconds", 45, 15, 90);
        delayMs = option("source_delay_ms", 350, 250, 2000);
        File external = target.getExternalFilesDir(null);
        assertTrue("External app files directory unavailable", external != null);
        publicReport = new File(new File(external, "source-validation"), "source-functional.json");
        privateCases = new File(new File(target.getFilesDir(), "source-validation"), "playback-cases.json");
        report.put("schema", "tv.python-source-functional.v1");
        report.put("sourceFile", basename);
        report.put("startedAtEpochMs", System.currentTimeMillis());
        report.put("stepDeadlineSeconds", stepSeconds);
        report.put("delayBetweenCallsMs", delayMs);
        report.put("steps", steps);
        Map<String, ?> originalPreferences = new HashMap<>(Prefers.getPrefers().getAll());
        // Replace any prior cases immediately so playback cannot consume stale evidence.
        checkpoint();
        try {
            CoreFixtureServer.ensureStarted();
            Future<?> setup = worker.submit(() -> {
                new Loader();
                guard = new PythonGuard();
                return null;
            });
            setup.get(60, TimeUnit.SECONDS);
            Step loaded = request("source.load", () -> {
                try {
                    spider = new Loader().spider(sourceUrl);
                } finally {
                    if (sourceCache.isFile()) sourceSha256 = sha256(readSource(sourceCache));
                }
                spider.siteKey = "source-probe-" + sourceSha256.substring(0, 12);
                spider.init(target, "");
                return "{\"loaded\":true}";
            });
            if (loaded.document == null || spider == null) {
                failStep(loaded, "SOURCE_NOT_LOADED");
            } else {
                exerciseHomeAndCategories();
                exerciseSearch();
                exerciseDetails();
                exercisePlaybackResolution();
            }
        } catch (Throwable error) {
            Step unexpected = localStep("suite.infrastructure");
            failStep(unexpected, "EXCEPTION_" + rootCause(error).getClass().getSimpleName());
        } finally {
            cleanup();
            Step preferences = localStep("configuration.preserved");
            if (!originalPreferences.equals(Prefers.getPrefers().getAll())) {
                failStep(preferences, "APPLICATION_PREFERENCES_CHANGED");
            }
            report.put("finishedAtEpochMs", System.currentTimeMillis());
            checkpoint();
        }
        Log.i(TAG, "Completed steps=" + steps.length() + " failures=" + failures
                + " playbackCases=" + cases.length() + " report=source-validation/source-functional.json");
        assertEquals("See source-validation/source-functional.json; failed steps", 0, failures);
    }

    private void exerciseHomeAndCategories() throws Exception {
        Step home = request("home", () -> spider.homeContent(true));
        JSONArray classes = home.document == null ? null : home.document.optJSONArray("class");
        if (classes == null || classes.length() == 0) failStep(home, "EMPTY_OR_INVALID_HOME_CLASSES");
        else home.evidence.put("classCount", classes.length());
        Step homeVideo = request("homeVideo", () -> spider.homeVideoContent());
        checkList(homeVideo, false);
        collect(homeVideo.document);

        // Retain the supplied source's known categories even if its catch-all home handler returns empty.
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        if (classes != null) for (int i = 0; i < classes.length(); i++) {
            JSONObject item = classes.optJSONObject(i);
            if (item != null && !item.optString("type_id").isEmpty()) categories.add(item.optString("type_id"));
            else failStep(home, "INVALID_CLASS_ENTRY");
        }
        Set<String> expected = new LinkedHashSet<>(Arrays.asList("rank_home", "real-drama", "comic-drama", "ai-drama"));
        for (String theme : THEMES) expected.add("real-drama|" + theme);
        if (!categories.containsAll(expected)) failStep(home, "ADVERTISED_CATEGORIES_MISSING");
        categories.addAll(expected);

        int index = 0;
        JSONObject rankHome = null;
        for (String category : categories) {
            String label = "category." + (++index);
            Step first = category(label + ".page1", category, 1, new HashMap<>(), false);
            Step second = category(label + ".page2", category, 2, new HashMap<>(), terminal(first));
            checkPagination(first, second, terminal(first));
            if ("rank_home".equals(category)) rankHome = first.document;
        }
        exerciseRanks(rankHome);
        exerciseFilters(home.document == null ? null : home.document.optJSONObject("filters"));
        checkpoint();
    }

    private void exerciseRanks(JSONObject rankHome) throws Exception {
        Step folders = localStep("rank_home.folders");
        JSONArray entries = rankHome == null ? null : rankHome.optJSONArray("list");
        folders.evidence.put("count", entries == null ? 0 : entries.length());
        if (entries == null || entries.length() != 4) failStep(folders, "EXPECTED_FOUR_RANK_FOLDERS");
        Set<String> visited = new LinkedHashSet<>();
        if (entries != null) for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null || !"folder".equals(item.optString("vod_tag")) || item.optString("vod_id").isEmpty()) {
                failStep(folders, "INVALID_RANK_FOLDER");
                continue;
            }
            String id = item.optString("vod_id");
            if (!visited.add(id)) failStep(folders, "DUPLICATE_RANK_FOLDER");
            Step first = category("rank.folder." + (i + 1) + ".page1", id, 1, new HashMap<>(), false);
            Step second = category("rank.folder." + (i + 1) + ".page2", id, 2, new HashMap<>(), terminal(first));
            checkPagination(first, second, terminal(first));
        }
        // Exercise all named routes independently, even when one folder or the home list is broken.
        for (int i = 0; i < RANKS.length; i++) {
            category("rank.direct." + (i + 1), RANKS[i], 1, new HashMap<>(), false);
        }
    }

    private void exerciseFilters(JSONObject filters) throws Exception {
        Step advertised = localStep("filters.advertised");
        advertised.evidence.put("categoryCount", filters == null ? 0 : filters.length());
        if (filters == null || filters.length() == 0) {
            advertised.evidence.put("status", "NOT_APPLICABLE");
            advertised.evidence.put("reason", "SOURCE_ADVERTISES_NO_FILTERS");
            return;
        }
        int categoryIndex = 0;
        java.util.Iterator<String> keys = filters.keys();
        while (keys.hasNext()) {
            String category = keys.next();
            JSONArray groups = filters.optJSONArray(category);
            categoryIndex++;
            if (groups == null) {
                failStep(advertised, "INVALID_FILTER_GROUPS");
                continue;
            }
            HashMap<String, String> defaults = new HashMap<>();
            for (int g = 0; g < groups.length(); g++) {
                JSONObject group = groups.optJSONObject(g);
                JSONArray values = group == null ? null : group.optJSONArray("value");
                if (group != null && values != null && values.optJSONObject(0) != null) {
                    defaults.put(group.optString("key"), values.optJSONObject(0).optString("v"));
                }
            }
            for (int g = 0; g < groups.length(); g++) {
                JSONObject group = groups.optJSONObject(g);
                JSONArray values = group == null ? null : group.optJSONArray("value");
                if (group == null || group.optString("key").isEmpty() || values == null || values.length() == 0) {
                    failStep(advertised, "INVALID_FILTER_OPTIONS");
                    continue;
                }
                for (int v = 0; v < values.length(); v++) {
                    JSONObject value = values.optJSONObject(v);
                    if (value == null || !value.has("v")) {
                        failStep(advertised, "INVALID_FILTER_VALUE");
                        continue;
                    }
                    HashMap<String, String> selected = new HashMap<>(defaults);
                    selected.put(group.optString("key"), value.optString("v"));
                    category("filter." + categoryIndex + "." + (g + 1) + "." + (v + 1),
                            category, 1, selected, false);
                }
            }
        }
    }

    private void exerciseSearch() throws Exception {
        String keyword = arguments.getString("source_keyword", "总裁");
        Step first = request("search.normal.page1", () -> spider.searchContent(keyword, false, "1"));
        checkList(first, false);
        collect(first.document);
        Step second = request("search.normal.page2", () -> spider.searchContent(keyword, false, "2"));
        checkList(second, true);
        collect(second.document);
        checkPagination(first, second, false);
        Step quick = request("search.quick", () -> spider.searchContent(keyword, true, "1"));
        checkList(quick, false);
        Step empty = request("search.empty", () -> spider.searchContent("", false, "1"));
        checkList(empty, true);
        if (count(empty.document) != 0) failStep(empty, "EMPTY_KEYWORD_RETURNED_ITEMS");
        String missing = "ZXQJNORESULT20261005";
        Step noMatch = request("search.noMatch", () -> spider.searchContent(missing, false, "1"));
        checkList(noMatch, true);
        noMatch.evidence.put("queryKind", "unmatched_probe");
        noMatch.evidence.put("returnedSuggestions", noMatch.document == null
                || noMatch.document.optJSONArray("list") == null ? JSONObject.NULL : count(noMatch.document));
        noMatch.evidence.put("exactSearchValidated", false);
        if (!"FAIL".equals(noMatch.evidence.optString("status"))) {
            noMatch.evidence.put("status", "OBSERVATION");
            noMatch.evidence.put("observation", count(noMatch.document) > 0
                    ? "SOURCE_RETURNS_FALLBACK_SUGGESTIONS" : "NO_SUGGESTIONS_FOR_THIS_PROBE");
        }
        String longKeyword = "TVSOURCE_NO_MATCH_" + sourceSha256.substring(0, Math.min(16, sourceSha256.length()));
        Step longQuery = request("search.longQueryBoundary", () -> spider.searchContent(longKeyword, false, "1"));
        checkList(longQuery, true);
        longQuery.evidence.put("queryKind", "long_query_boundary");
        longQuery.evidence.put("queryLength", longKeyword.length());
        longQuery.evidence.put("returnedSuggestions", longQuery.document == null
                || longQuery.document.optJSONArray("list") == null ? JSONObject.NULL : count(longQuery.document));
        longQuery.evidence.put("exactSearchValidated", false);
        longQuery.evidence.put("returnedStructuredResponse", longQuery.document != null);
        JSONObject longNetwork = longQuery.evidence.optJSONObject("network");
        JSONArray longStatuses = longNetwork == null ? null : longNetwork.optJSONArray("httpStatusCodes");
        boolean upstreamRejected = false;
        if (longStatuses != null) for (int i = 0; i < longStatuses.length(); i++) {
            if (longStatuses.optInt(i) >= 400) upstreamRejected = true;
        }
        longQuery.evidence.put("upstreamRejected", upstreamRejected);
        longQuery.evidence.put("observation", upstreamRejected
                ? "UPSTREAM_REJECTED_LONG_QUERY" : "LONG_QUERY_RESPONSE_OBSERVED");
        if (upstreamRejected) failStep(longQuery, "UPSTREAM_REJECTED_LONG_QUERY");
        else if (!"FAIL".equals(longQuery.evidence.optString("status"))) longQuery.evidence.put("status", "OBSERVATION");
        checkpoint();
    }

    private void exerciseDetails() throws Exception {
        List<String> ids = new ArrayList<>(candidates.keySet());
        int wanted = option("source_detail_count", 3, 2, 6);
        Step available = localStep("detail.candidates");
        available.evidence.put("count", ids.size());
        if (ids.size() < 2) failStep(available, "FEWER_THAN_TWO_SERIES");
        // Spread requests across the collected library rather than selecting one contiguous row.
        Set<String> selected = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(wanted, ids.size()); i++) {
            selected.add(ids.get(i * Math.max(0, ids.size() - 1) / Math.max(1, Math.min(wanted, ids.size()) - 1)));
        }
        int index = 0;
        for (String id : selected) {
            Step detail = request("detail.single." + (++index), () -> spider.detailContent(Collections.singletonList(id)));
            checkList(detail, false);
            JSONArray list = detail.document == null ? null : detail.document.optJSONArray("list");
            if (list == null || list.length() == 0) continue;
            JSONObject vod = list.optJSONObject(0);
            if (vod == null || vod.optString("vod_play_from").isEmpty() || vod.optString("vod_play_url").isEmpty()) {
                failStep(detail, "DETAIL_HAS_NO_EPISODES");
                continue;
            }
            String[] lines = vod.optString("vod_play_from").split("\\$\\$\\$", -1);
            String[] streams = vod.optString("vod_play_url").split("\\$\\$\\$", -1);
            detail.evidence.put("lineCount", lines.length);
            if (lines.length != streams.length) failStep(detail, "PLAY_LINE_COUNT_MISMATCH");
            detail.evidence.put("episodeCount", streams[0].split("#", -1).length);
            details.add(vod);
        }
        if (ids.size() >= 2) {
            Step batch = request("detail.multipleIds", () -> spider.detailContent(ids.subList(0, 2)));
            checkList(batch, false);
            if (count(batch.document) != 2) failStep(batch, "BATCH_DETAIL_DID_NOT_RETURN_TWO");
        }
        Step coverage = localStep("detail.coverage");
        coverage.evidence.put("successfulSeries", details.size());
        if (details.size() < 2) failStep(coverage, "TWO_PLAYABLE_SERIES_NOT_FOUND");
        checkpoint();
    }

    private void exercisePlaybackResolution() throws Exception {
        for (int series = 0; series < details.size(); series++) {
            JSONObject vod = details.get(series);
            String[] lines = vod.optString("vod_play_from").split("\\$\\$\\$", -1);
            String[] streams = vod.optString("vod_play_url").split("\\$\\$\\$", -1);
            if (lines.length == 0 || streams.length == 0) continue;
            String[] episodes = streams[0].split("#", -1);
            Set<Integer> positions = new LinkedHashSet<>(Arrays.asList(1, (episodes.length + 1) / 2, episodes.length));
            if (series == 0 && episodes.length > 1) positions.add(2);
            for (int position : positions) {
                resolveEpisode(series, vod, lines[0], episodes, position);
            }
            if (series == 0) {
                Map<String, List<Integer>> sameUrls = new LinkedHashMap<>();
                for (int line = 0; line < Math.min(lines.length, streams.length); line++) {
                    JSONObject resolved = resolveEpisode(series, vod, lines[line], streams[line].split("#", -1), 1);
                    if (resolved != null) sameUrls.computeIfAbsent(sha256(resolved.optString("url")
                            .getBytes(StandardCharsets.UTF_8)), ignored -> new ArrayList<>()).add(line + 1);
                }
                Step quality = localStep("playback.qualityDistinctness");
                quality.evidence.put("advertisedLines", lines.length);
                quality.evidence.put("distinctResolvedUrls", sameUrls.size());
                JSONArray duplicates = new JSONArray();
                for (List<Integer> group : sameUrls.values()) if (group.size() > 1) duplicates.put(new JSONArray(group));
                quality.evidence.put("sameUrlLineGroups", duplicates);
                if (duplicates.length() > 0) failStep(quality, "DIFFERENT_QUALITY_LINES_RESOLVE_IDENTICALLY");
            }
        }
        Step coverage = localStep("playback.resolutionCoverage");
        Set<String> series = new LinkedHashSet<>();
        for (int i = 0; i < cases.length(); i++) series.add(cases.getJSONObject(i).optString("seriesId"));
        coverage.evidence.put("seriesCount", series.size());
        coverage.evidence.put("caseCount", cases.length());
        if (series.size() < 2) failStep(coverage, "FEWER_THAN_TWO_RESOLVED_SERIES");
        checkpoint();
    }

    private JSONObject resolveEpisode(int series, JSONObject vod, String line, String[] episodes, int position) throws Exception {
        String key = vod.optString("vod_id") + "\n" + line + "\n" + position;
        if (caseKeys.contains(key)) {
            for (int i = 0; i < cases.length(); i++) {
                JSONObject item = cases.getJSONObject(i);
                if (item.optString("seriesId").equals(vod.optString("vod_id"))
                        && item.optString("line").equals(line) && item.optInt("episodeIndex") == position) return item;
            }
        }
        String label = "series" + (series + 1) + ".line" + sha256(line.getBytes(StandardCharsets.UTF_8)).substring(0, 8)
                + ".episode" + position;
        if (position < 1 || position > episodes.length || episodes[position - 1].indexOf('$') <= 0) {
            failStep(localStep("resolve." + label), "INVALID_EPISODE_REFERENCE");
            return null;
        }
        String reference = episodes[position - 1].substring(episodes[position - 1].indexOf('$') + 1);
        Step step = request("resolve." + label, () -> spider.playerContent(line, reference, Collections.emptyList()));
        if (step.document == null) return null;
        String url = step.document.optString("url");
        int parse = step.document.optInt("parse", -1);
        JSONObject headers = headerObject(step.document.opt("header"));
        if (url.isEmpty()) {
            failStep(step, "EMPTY_PLAYBACK_URL");
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                failStep(step, "NON_HTTP_PLAYBACK_URL");
                return null;
            }
            step.evidence.put("loopback", "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        } catch (IllegalArgumentException error) {
            failStep(step, "INVALID_PLAYBACK_URL");
            return null;
        }
        if (parse != 0) failStep(step, "UNSUPPORTED_PARSE_REQUIRED");
        if (headers == null) {
            failStep(step, "INVALID_PLAYBACK_HEADERS");
            return null;
        }
        Matcher quality = QUALITY.matcher(line);
        JSONObject item = new JSONObject();
        item.put("label", label);
        item.put("seriesId", vod.optString("vod_id"));
        item.put("episodeIndex", position);
        item.put("episodeCount", episodes.length);
        item.put("line", line);
        item.put("requestedQuality", quality.find() ? quality.group().toUpperCase(java.util.Locale.ROOT) : "");
        item.put("url", url);
        item.put("headers", headers);
        item.put("parse", parse);
        caseKeys.add(key);
        cases.put(item);
        step.evidence.put("headerCount", headers.length());
        step.evidence.put("urlSha256", sha256(url.getBytes(StandardCharsets.UTF_8)));
        step.evidence.put("parse", parse);
        checkpoint();
        return item;
    }

    private Step category(String label, String id, int page, HashMap<String, String> filters, boolean allowEmpty) throws Exception {
        Step result = request(label, () -> spider.categoryContent(id, Integer.toString(page), true, filters));
        result.evidence.put("requestedPage", page);
        checkList(result, allowEmpty);
        if (result.document != null) {
            result.evidence.put("reportedPage", result.document.optInt("page", -1));
            result.evidence.put("reportedPageCount", result.document.optInt("pagecount", -1));
            result.evidence.put("reportedTotal", result.document.optLong("total", -1));
            result.evidence.put("reportedLimit", result.document.optLong("limit", -1));
            if (result.document.optInt("page", page) != page && !allowEmpty) failStep(result, "INCORRECT_PAGE_NUMBER");
        }
        collect(result.document);
        return result;
    }

    private void checkPagination(Step first, Step second, boolean terminal) throws Exception {
        Set<String> before = ids(first.document);
        Set<String> after = ids(second.document);
        Set<String> overlap = new LinkedHashSet<>(after);
        overlap.retainAll(before);
        second.evidence.put("overlapWithFirstPage", overlap.size());
        if (!after.isEmpty() && !before.isEmpty() && after.equals(before)) {
            if (terminal) second.evidence.put("observation", "TERMINAL_PAGE_REPEATS_WHEN_FORCED");
            else failStep(second, "SECOND_PAGE_REPEATS_FIRST_PAGE");
        }
        if (!terminal && first.document != null && first.document.optInt("pagecount", 1) > 1
                && count(second.document) == 0) failStep(second, "EMPTY_ADVERTISED_SECOND_PAGE");
    }

    private boolean terminal(Step step) {
        return step.document != null && step.document.optInt("pagecount", Integer.MAX_VALUE) <= 1;
    }

    private void checkList(Step step, boolean allowEmpty) throws Exception {
        if (step.document == null) return;
        JSONArray list = step.document.optJSONArray("list");
        if (list == null) {
            failStep(step, "MISSING_LIST");
            return;
        }
        step.evidence.put("count", list.length());
        boolean expectedTerminal404 = list.length() == 0 && expectedTerminal404(step);
        if (expectedTerminal404) {
            step.evidence.put("observation", "EXPECTED_TERMINAL_404");
        } else {
            if (list.length() == 0 && !allowEmpty) failStep(step, "UNEXPECTED_EMPTY_LIST");
            if (list.length() == 0 && step.networkErrors > 0) failStep(step, "EMPTY_AFTER_NETWORK_OR_PLUGIN_ERROR");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null || item.optString("vod_id").isEmpty() || item.optString("vod_name").isEmpty()) {
                failStep(step, "INVALID_VOD_ENTRY");
            } else if (!seen.add(item.optString("vod_id"))) {
                failStep(step, "DUPLICATE_ITEM_WITHIN_PAGE");
            }
        }
    }

    private boolean expectedTerminal404(Step step) {
        if (step.document == null || step.errors.length() != 0) return false;
        long requested = step.evidence.optLong("requestedPage", -1);
        long page = integerMetadata(step.document, "page");
        long pageCount = integerMetadata(step.document, "pagecount");
        long total = integerMetadata(step.document, "total");
        long limit = integerMetadata(step.document, "limit");
        if (requested <= pageCount || page != requested || pageCount < 1 || total < 0 || limit < 1) return false;
        long calculatedPages = Math.max(1, total / limit + (total % limit == 0 ? 0 : 1));
        if (calculatedPages != pageCount) return false;
        JSONObject network = step.evidence.optJSONObject("network");
        JSONArray statuses = network == null ? null : network.optJSONArray("httpStatusCodes");
        if (statuses == null || statuses.length() == 0) return false;
        for (int i = 0; i < statuses.length(); i++) if (statuses.optInt(i, -1) != 404) return false;
        // HTTPError is counted as a network error by urllib; do not excuse an additional timeout or DNS failure.
        if (network.optInt("networkErrors", -1) + network.optInt("httpErrors", -1) != statuses.length()) return false;
        JSONArray pluginTypes = network.optJSONArray("pluginErrorTypes");
        if (network.optInt("pluginErrors") > 0 && (pluginTypes == null || pluginTypes.length() == 0)) return false;
        if (pluginTypes != null) for (int i = 0; i < pluginTypes.length(); i++) {
            if (!"_HgOfficialNotFound".equals(pluginTypes.optString(i))) return false;
        }
        return true;
    }

    private static long integerMetadata(JSONObject document, String key) {
        Object value = document.opt(key);
        if (!(value instanceof Number number)) return -1;
        double numeric = number.doubleValue();
        long integer = number.longValue();
        return Double.isFinite(numeric) && numeric == integer && integer >= 0 ? integer : -1;
    }

    private void collect(JSONObject document) {
        JSONArray list = document == null ? null : document.optJSONArray("list");
        if (list == null) return;
        for (int i = 0; i < list.length() && candidates.size() < 1000; i++) {
            JSONObject item = list.optJSONObject(i);
            if (item != null && !"folder".equals(item.optString("vod_tag")) && !item.optString("vod_id").isEmpty()) {
                candidates.putIfAbsent(item.optString("vod_id"), item);
            }
        }
    }

    private Step request(String label, Callable<String> operation) throws Exception {
        Step step = localStep(label);
        if (stalled) {
            failStep(step, "BLOCKED_BY_UNRESPONSIVE_NATIVE_CALL");
            return step;
        }
        SystemClock.sleep(delayMs);
        long started = SystemClock.elapsedRealtime();
        Future<Reply> future = worker.submit(() -> {
            guard.begin(stepSeconds);
            String raw = null;
            Throwable error = null;
            try {
                raw = operation.call();
            } catch (Throwable failure) {
                error = rootCause(failure);
            }
            JSONObject metrics = guard.end();
            return new Reply(raw, error, metrics);
        });
        try {
            Reply reply = future.get(stepSeconds + 15L, TimeUnit.SECONDS);
            step.evidence.put("network", reply.metrics);
            step.networkErrors = reply.metrics.optInt("networkErrors") + reply.metrics.optInt("httpErrors")
                    + reply.metrics.optInt("pluginErrors");
            if (reply.error != null) failStep(step, "EXCEPTION_" + reply.error.getClass().getSimpleName());
            if (reply.raw != null) {
                try {
                    if (reply.raw.length() > 8 * 1024 * 1024) throw new IllegalArgumentException();
                    step.document = new JSONObject(reply.raw);
                } catch (Exception error) {
                    failStep(step, "INVALID_JSON_OBJECT");
                }
            }
        } catch (TimeoutException error) {
            future.cancel(true);
            stalled = true;
            failStep(step, "NATIVE_CALL_EXCEEDED_HARD_DEADLINE");
        } catch (ExecutionException error) {
            failStep(step, "EXCEPTION_" + rootCause(error).getClass().getSimpleName());
        } finally {
            step.evidence.put("elapsedMs", SystemClock.elapsedRealtime() - started);
        }
        checkpoint();
        return step;
    }

    private Step localStep(String label) throws Exception {
        Step step = new Step();
        step.evidence.put("step", label);
        step.evidence.put("status", "PASS");
        step.evidence.put("elapsedMs", 0);
        step.evidence.put("errors", step.errors);
        steps.put(step.evidence);
        return step;
    }

    private void failStep(Step step, String code) throws Exception {
        if (!"FAIL".equals(step.evidence.optString("status"))) failures++;
        step.evidence.put("status", "FAIL");
        for (int i = 0; i < step.errors.length(); i++) if (code.equals(step.errors.optString(i))) return;
        step.errors.put(code);
    }

    private void cleanup() throws Exception {
        Step cleanup = localStep("source.cleanup");
        if (!stalled) {
            try {
                worker.submit(() -> {
                    try {
                        if (spider != null) {
                            if (guard != null) guard.begin(10);
                            try { spider.destroy(); }
                            finally { if (guard != null) guard.end(); }
                        }
                    } finally {
                        if (guard != null) guard.close();
                    }
                    return null;
                }).get(20, TimeUnit.SECONDS);
            } catch (Throwable error) {
                failStep(cleanup, "RUNTIME_CLEANUP_FAILED");
            }
        } else {
            failStep(cleanup, "RUNTIME_STILL_BUSY");
        }
        worker.shutdownNow();
        CoreFixtureServer.stop();
        if (!stalled && ownsSourceCache && sourceCache.exists() && !sourceCache.delete()) {
            failStep(cleanup, "TEMPORARY_SOURCE_DELETE_FAILED");
        }
    }

    private void checkpoint() throws Exception {
        if (publicReport == null || privateCases == null) return;
        report.put("sourceSha256", sourceSha256);
        report.put("failureCount", failures);
        report.put("stepCount", steps.length());
        report.put("playbackCaseCount", cases.length());
        report.put("status", failures == 0 ? "PASS_SO_FAR" : "FAIL");
        if (report.has("finishedAtEpochMs")) report.put("status", failures == 0 ? "PASS" : "FAIL");
        JSONObject playback = new JSONObject();
        playback.put("sourceSha256", sourceSha256);
        playback.put("cases", cases);
        write(privateCases, playback);
        write(publicReport, report);
    }

    private static void write(File file, JSONObject value) throws Exception {
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Report directory unavailable");
        File temporary = new File(directory, file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(value.toString(2).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!temporary.renameTo(file)) throw new IllegalStateException("Could not replace report");
    }

    private int option(String key, int fallback, int minimum, int maximum) {
        try { return Math.max(minimum, Math.min(maximum, Integer.parseInt(arguments.getString(key, Integer.toString(fallback))))); }
        catch (NumberFormatException error) { return fallback; }
    }

    private static JSONObject headerObject(Object value) {
        try {
            JSONObject headers;
            if (value == null || value == JSONObject.NULL) headers = new JSONObject();
            else if (value instanceof JSONObject object) headers = object;
            else if (value instanceof String text) headers = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            else return null;
            java.util.Iterator<String> keys = headers.keys();
            while (keys.hasNext()) if (!(headers.get(keys.next()) instanceof String)) return null;
            return headers;
        } catch (Exception ignored) { }
        return null;
    }

    private static byte[] readSource(File source) throws Exception {
        try (FileInputStream input = new FileInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 16 * 1024 * 1024) throw new IllegalStateException("Source exceeds probe limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static int count(JSONObject document) {
        JSONArray items = document == null ? null : document.optJSONArray("list");
        return items == null ? 0 : items.length();
    }

    private static Set<String> ids(JSONObject document) {
        Set<String> ids = new LinkedHashSet<>();
        JSONArray items = document == null ? null : document.optJSONArray("list");
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && !item.optString("vod_id").isEmpty()) ids.add(item.optString("vod_id"));
        }
        return ids;
    }

    private static String sha256(byte[] value) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(value)) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }

    private static Throwable rootCause(Throwable error) {
        while ((error instanceof ExecutionException || error instanceof InvocationTargetException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static final class Step {
        final JSONObject evidence = new JSONObject();
        final JSONArray errors = new JSONArray();
        JSONObject document;
        int networkErrors;
    }

    private record Reply(String raw, Throwable error, JSONObject metrics) { }

    /** Temporary, worker-thread-scoped limits; production HTTP behavior is restored at teardown. */
    private static final class PythonGuard {
        private final Object builtins;
        private final Object globals;
        private final Method call;

        PythonGuard() throws Exception {
            Class<?> python = Class.forName("com.chaquo.python.Python");
            Object instance = python.getMethod("getInstance").invoke(null);
            builtins = python.getMethod("getModule", String.class).invoke(instance, "builtins");
            call = Class.forName("com.chaquo.python.PyObject").getMethod("callAttr", String.class, Object[].class);
            globals = invoke("dict");
            execute("""
                    import sys, time, json, threading, requests, urllib.request
                    _original_request = requests.sessions.Session.request
                    _original_urlopen = urllib.request.urlopen
                    _thread = None
                    _deadline = 0
                    _metrics = {}
                    class _SourceProbeDeadline(BaseException):
                        pass
                    def _active():
                        return threading.get_ident() == _thread and _deadline > 0
                    def _timeout(value, cap):
                        remaining = max(0.1, _deadline - time.monotonic())
                        try:
                            value = float(value)
                            if value <= 0: value = cap
                        except (TypeError, ValueError):
                            value = cap
                        return min(value, cap, remaining)
                    def _request(self, method, url, *args, **kwargs):
                        active = _active()
                        if active:
                            _metrics['requests'] += 1
                            timeout = kwargs.get('timeout')
                            connect, read = timeout if isinstance(timeout, tuple) and len(timeout) == 2 else (timeout, timeout)
                            kwargs['timeout'] = (_timeout(connect, 6), _timeout(read, 10))
                        try:
                            response = _original_request(self, method, url, *args, **kwargs)
                            if active and response.status_code >= 400:
                                _metrics['httpErrors'] += 1
                                _metrics['httpStatusCodes'].append(response.status_code)
                            return response
                        except BaseException:
                            if active: _metrics['networkErrors'] += 1
                            raise
                    def _urlopen(url, data=None, timeout=None, *args, **kwargs):
                        active = _active()
                        if not active:
                            if timeout is None:
                                return _original_urlopen(url, data, *args, **kwargs)
                            return _original_urlopen(url, data, timeout, *args, **kwargs)
                        _metrics['requests'] += 1
                        try:
                            response = _original_urlopen(url, data, _timeout(timeout, 10), *args, **kwargs)
                            status = getattr(response, 'status', 200)
                            if status >= 400:
                                _metrics['httpErrors'] += 1
                                _metrics['httpStatusCodes'].append(status)
                            return response
                        except BaseException as error:
                            _metrics['networkErrors'] += 1
                            status = getattr(error, 'code', None)
                            if isinstance(status, int): _metrics['httpStatusCodes'].append(status)
                            raise
                    def _trace(frame, event, arg):
                        if time.monotonic() > _deadline:
                            sys.settrace(None)
                            raise _SourceProbeDeadline()
                        if event == 'exception':
                            name = arg[0].__name__
                            if name in ('HongguoPluginError', '_HongguoRetryableRequestError', '_HgOfficialNotFound'):
                                _metrics['pluginErrors'] += 1
                                if name not in _metrics['pluginErrorTypes']:
                                    _metrics['pluginErrorTypes'].append(name)
                        return _trace
                    def _begin(seconds):
                        global _thread, _deadline, _metrics
                        _thread = threading.get_ident()
                        _deadline = time.monotonic() + seconds
                        _metrics = {'requests': 0, 'networkErrors': 0, 'httpErrors': 0,
                                    'pluginErrors': 0, 'pluginErrorTypes': [], 'httpStatusCodes': []}
                        sys.settrace(_trace)
                    def _end():
                        global _deadline
                        sys.settrace(None)
                        _deadline = 0
                        return json.dumps(_metrics)
                    def _close():
                        sys.settrace(None)
                        requests.sessions.Session.request = _original_request
                        urllib.request.urlopen = _original_urlopen
                    requests.sessions.Session.request = _request
                    urllib.request.urlopen = _urlopen
                    """);
        }

        private Object invoke(String name, Object... arguments) throws Exception {
            return call.invoke(builtins, name, arguments);
        }

        private void execute(String source) throws Exception { invoke("exec", source, globals); }
        void begin(int seconds) throws Exception { execute("_begin(" + seconds + ")"); }
        JSONObject end() throws Exception { return new JSONObject(invoke("eval", "_end()", globals).toString()); }
        void close() throws Exception { execute("_close()"); }
    }
}
