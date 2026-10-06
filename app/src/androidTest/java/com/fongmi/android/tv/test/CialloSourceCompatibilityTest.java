package com.fongmi.android.tv.test;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.api.loader.JarLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Explicit live probe. Uses only the OLD host ABI so this same test can run on the old APK. */
@RunWith(AndroidJUnit4.class)
public final class CialloSourceCompatibilityTest {
    @Test public void sameJarLoadsAndResolvesEpisodesThroughLegacyAbi() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Use the disposable package", "com.fongmi.android.tv.sourceprobe", context.getPackageName());
        File jar = new File(context.getFilesDir(), "ciallo-source.jar");
        assertTrue("Stage the built source JAR", jar.isFile());
        String source = InstrumentationRegistry.getArguments().getString("ciallo_source", "Girigiri");
        assertTrue(source.equals("Girigiri") || source.equals("Sorani"));
        String expected = InstrumentationRegistry.getArguments().getString("ciallo_sha256", "");
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar.toPath())))
            hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        assertEquals("Verify the same exact JAR on both apps", expected, hash.toString());
        File output = new File(context.getFilesDir(), "source-validation");
        assertTrue(output.isDirectory() || output.mkdirs());
        JSONObject report = new JSONObject().put("source", source).put("sourceSha256", hash.toString())
                .put("completed", false);
        JSONArray cases = new JSONArray();
        JarLoader loader = new JarLoader();
        try {
            Spider spider = loader.getSpider("ciallo-probe-" + UUID.randomUUID(), "csp_" + source,
                    "", "file://" + jar.getAbsolutePath());
            assertFalse("Source failed to load", spider instanceof SpiderNull);
            report.put("loaded", true);
            JSONObject home = new JSONObject(spider.homeContent(true));
            assertTrue(home.getJSONArray("class").length() > 1);
            report.put("classes", home.getJSONArray("class").length());
            String category = source.equals("Girigiri") ? "2" : "all";
            JSONObject first = new JSONObject(spider.categoryContent(category, "1", true, new HashMap<>()));
            JSONObject next = new JSONObject(spider.categoryContent(category, "2", true, new HashMap<>()));
            assertEquals(1, first.getInt("page"));
            assertEquals(2, next.getInt("page"));
            assertNotEquals(first.getJSONArray("list").getJSONObject(0).getString("vod_id"),
                    next.getJSONArray("list").getJSONObject(0).getString("vod_id"));
            report.put("categoryPagesDistinct", true);
            String keyword = source.equals("Girigiri") ? "JOJO" : "无职";
            JSONObject search = new JSONObject(spider.searchContent(keyword, false));
            assertTrue(search.getJSONArray("list").length() > 0);
            assertEquals(0, new JSONObject(spider.searchContent("NoSuchCialloAnime20261006", false, "1"))
                    .getJSONArray("list").length());
            report.put("searchResults", search.getJSONArray("list").length()).put("emptySearch", true);
            String id = search.getJSONArray("list").getJSONObject(0).getString("vod_id");
            JSONObject detail = new JSONObject(spider.detailContent(List.of(id))).getJSONArray("list").getJSONObject(0);
            String[] lines = detail.getString("vod_play_from").split("\\$\\$\\$");
            String[] playlists = detail.getString("vod_play_url").split("\\$\\$\\$");
            assertEquals(lines.length, playlists.length);
            String[] episodes = playlists[0].split("#");
            report.put("lines", lines.length).put("episodes", episodes.length);
            for (int index = 0; index < Math.min(2, episodes.length); index++) {
                String[] episode = episodes[index].split("\\$", 2);
                assertEquals(2, episode.length);
                JSONObject play = new JSONObject(spider.playerContent(lines[0], episode[1], List.of()));
                assertEquals(0, play.getInt("parse"));
                assertTrue(play.getString("url").startsWith("https://"));
                cases.put(new JSONObject().put("seriesId", id).put("line", lines[0]).put("episodeIndex", index)
                        .put("url", play.getString("url")).put("parse", 0)
                        .put("headers", play.optJSONObject("header")).put("sourceSha256", hash.toString()));
            }
            report.put("resolvedEpisodes", cases.length()).put("completed", true);
        } finally {
            loader.clear();
            Files.writeString(new File(output, "ciallo-" + source + "-result.json").toPath(), report.toString(2), StandardCharsets.UTF_8);
            Files.writeString(new File(output, "playback-cases.json").toPath(),
                    new JSONObject().put("sourceSha256", hash.toString()).put("cases", cases).toString(), StandardCharsets.UTF_8);
        }
    }
}
