package com.fongmi.android.tv.test;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.api.loader.JarLoader;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.crawler.Spider;
import com.fongmi.quickjs.utils.Module;
import com.fongmi.quickjs.utils.QuickLog;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dalvik.system.DexClassLoader;
import fi.iki.elonen.NanoHTTPD;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Opt in with quickjs_legacy=true. Stage exact API/rule bytes under private files/quickjs-regression/.
 * Their configuration addresses and WebDAV credentials are not needed by this test.
 * Init/home may contact the publisher; quickjs_legacy_live=true adds browsing/search/play resolution.
 * quickjs_legacy_jar=true loads the configured main JAR from runtime.jar after SHA-256 verification
 * against private runtime.json. Public reports contain only hashes/counts/error types.
 * quickjs_legacy_original=true reads private scripts.json and preserves original API/rule URLs,
 * replacing only their exact response bodies so relative ES modules retain their original base.
 * Exception traces stay in separate app-private files and must never be published unredacted.
 */
@RunWith(AndroidJUnit4.class)
public final class QuickJsLegacySourcesTest {

    private static final String API_SHA = "67f4f6b460db1ec7ef50585953ce826c5263ca91d72958490ef4702b4e154fe1";
    private static final Map<String, String> RULE_SHA = Map.of(
            "tencent", "da380ca1d7395f36bae82ce239a6a7e9c6fd2367058860d51ea8c2edf81fa739",
            "bili", "c7e65d4c19bb45faacc700993a6c585e38484965e968cc31743bfd9459aa8652");

    private final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Bundle arguments = InstrumentationRegistry.getArguments();
    private final JSONObject report = new JSONObject();
    private final JSONArray sources = new JSONArray();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String stage;

    @Test
    public void configuredLegacySourcesUseTheirExactScripts() throws Exception {
        assumeTrue(Boolean.parseBoolean(arguments.getString("quickjs_legacy", "false")));
        assertEquals("Use the isolated test application", "com.fongmi.android.tv.sourceprobe", target.getPackageName());
        String selected = arguments.getString("quickjs_legacy_source", "all");
        assertTrue(List.of("all", "tencent", "bili").contains(selected));
        boolean live = Boolean.parseBoolean(arguments.getString("quickjs_legacy_live", "false"));
        boolean withJar = Boolean.parseBoolean(arguments.getString("quickjs_legacy_jar", "false"));
        boolean originalUrls = Boolean.parseBoolean(arguments.getString("quickjs_legacy_original", "false"));
        boolean logging = QuickLog.isEnabled();
        QuickLog.putEnabled(false);
        ScriptServer server = new ScriptServer(new File(target.getFilesDir(), "quickjs-regression"));
        JarLoader jarLoader = withJar ? new JarLoader() : null;
        OriginalScripts originals = null;
        int failures = 0;
        try {
            if (originalUrls) originals = new OriginalScripts(server.directory);
            else server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
            Module.get().clear();
            report.put("sources", sources).put("liveCalls", live).put("originalModuleUrls", originalUrls).put("status", "RUNNING");
            DexClassLoader dex = withJar ? runtimeJar(jarLoader, server.directory) : null;
            for (String name : List.of("tencent", "bili")) {
                if (!selected.equals("all") && !selected.equals(name)) continue;
                JSONObject row = new JSONObject().put("source", name);
                sources.put(row);
                Spider spider = null;
                try {
                    stage = name + ".fixtures";
                    File api = new File(server.directory, name + "-api.js");
                    File rule = new File(server.directory, name + "-rule.js");
                    assertTrue("Stage the private API script", api.isFile());
                    assertTrue("Stage the private rule script", rule.isFile());
                    String apiSha = hash(api), ruleSha = hash(rule);
                    assertEquals("API fixture bytes changed", API_SHA, apiSha);
                    assertEquals("Rule fixture bytes changed", RULE_SHA.get(name), ruleSha);
                    row.put("apiSha256", apiSha).put("ruleSha256", ruleSha);
                    String base = "http://127.0.0.1:" + server.getListeningPort() + "/" + name;
                    String apiUrl = originals == null ? base + "/api.js" : originals.url(name, "api");
                    String ruleUrl = originals == null ? base + "/rule.js" : originals.url(name, "rule");
                    spider = new Loader().spider(apiUrl, dex);
                    spider.siteKey = "quickjs-legacy-" + name;
                    Spider current = spider;
                    step(name + ".init", () -> { current.init(target, ruleUrl); return ""; });
                    JSONObject home = new JSONObject(step(name + ".home", () -> current.homeContent(true)));
                    JSONArray classes = home.optJSONArray("class");
                    assertNotNull("Legacy home must return categories", classes);
                    assertTrue("Legacy category list is empty", classes.length() > 0);
                    row.put("categories", classes.length());
                    row.put("homeVideos", count(home));
                    if (live) exercise(name, current, home, row);
                    row.put("status", live ? "PASS_LIVE" : "PASS_INIT_HOME");
                } catch (Throwable failure) {
                    failures++;
                    row.put("status", "FAIL").put("stage", stage);
                    row.put("failureTypes", types(failure));
                    privateTrace(name, failure);
                } finally {
                    if (spider != null) {
                        Spider current = spider;
                        Future<?> cleanup = worker.submit(current::destroy);
                        try { cleanup.get(10, TimeUnit.SECONDS); } catch (Exception ignored) { cleanup.cancel(true); }
                    }
                    write();
                }
            }
            report.put("status", failures == 0 ? "PASS" : "FAIL");
        } catch (Throwable failure) {
            report.put("status", "FAIL").put("stage", stage).put("failureTypes", types(failure));
            privateTrace("setup", failure);
            throw new AssertionError("Inspect the private QuickJS setup failure trace");
        } finally {
            worker.shutdownNow();
            server.stop();
            if (jarLoader != null) jarLoader.clear();
            if (originals != null) originals.close();
            Module.get().clear();
            QuickLog.putEnabled(logging);
            write();
        }
        assertEquals("Inspect private quickjs-legacy-result.json for source/stage/error types", 0, failures);
    }

    private DexClassLoader runtimeJar(JarLoader loader, File directory) throws Exception {
        stage = "runtime.jar";
        File jar = new File(directory, "runtime.jar");
        File metadata = new File(directory, "runtime.json");
        assertTrue("Stage the private runtime JAR", jar.isFile());
        assertTrue("Stage the private runtime metadata", metadata.isFile());
        String expected = new JSONObject(Files.readString(metadata.toPath(), StandardCharsets.UTF_8)).getString("sha256");
        assertTrue("Runtime SHA-256 is invalid", expected.matches("[a-f0-9]{64}"));
        String actual = hash(jar);
        assertEquals("Configured runtime JAR bytes changed", expected, actual);
        report.put("runtimeJarSha256", actual);
        DexClassLoader dex = step("runtime.jar", () -> loader.dex("file://" + jar.getAbsolutePath()));
        assertNotNull("Configured runtime JAR did not load", dex);
        return dex;
    }

    private void exercise(String name, Spider spider, JSONObject home, JSONObject row) throws Exception {
        String category = arguments.getString("quickjs_legacy_category_" + name,
                home.getJSONArray("class").getJSONObject(0).getString("type_id"));
        JSONObject first = new JSONObject(step(name + ".category1", () -> spider.categoryContent(category, "1", true, new HashMap<>())));
        JSONObject second = new JSONObject(step(name + ".category2", () -> spider.categoryContent(category, "2", true, new HashMap<>())));
        row.put("page1Count", count(first)).put("page2Count", count(second));
        assertTrue("Known legacy category returned no videos", count(first) > 0);
        String keyword = arguments.getString("quickjs_legacy_keyword_" + name, name.equals("tencent") ? "三体" : "科学");
        JSONObject search = new JSONObject(step(name + ".search", () -> spider.searchContent(keyword, false, "1")));
        row.put("searchCount", count(search));
        assertTrue("Known legacy search returned no videos", count(search) > 0);
        String id = first.getJSONArray("list").getJSONObject(0).getString("vod_id");
        JSONObject detail = new JSONObject(step(name + ".detail", () -> spider.detailContent(List.of(id))));
        JSONObject vod = detail.getJSONArray("list").getJSONObject(0);
        String[] lines = vod.getString("vod_play_from").split("\\$\\$\\$");
        String[] playlists = vod.getString("vod_play_url").split("\\$\\$\\$");
        String[] episode = playlists[0].split("#")[0].split("\\$", 2);
        assertEquals("Legacy detail needs a playable episode", 2, episode.length);
        JSONObject play = new JSONObject(step(name + ".play", () -> spider.playerContent(lines[0], episode[1], List.of())));
        assertTrue("Legacy player response lacks URL", play.has("url") && !play.isNull("url"));
        row.put("playResolved", true).put("playParse", play.optInt("parse", 0));
    }

    private <T> T step(String name, Callable<T> callable) throws Exception {
        stage = name;
        report.put("stage", stage);
        write();
        Future<T> future = worker.submit(callable);
        try { return future.get(60, TimeUnit.SECONDS); }
        finally { if (!future.isDone()) future.cancel(true); }
    }

    private int count(JSONObject data) {
        JSONArray list = data.optJSONArray("list");
        return list == null ? 0 : list.length();
    }

    private String hash(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return result.toString();
    }

    private JSONArray types(Throwable failure) {
        JSONArray values = new JSONArray();
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) values.put(failure.getClass().getSimpleName());
        return values;
    }

    private void privateTrace(String name, Throwable failure) throws Exception {
        try (PrintWriter output = new PrintWriter(target.openFileOutput("quickjs-legacy-" + name + "-error.private.txt", Context.MODE_PRIVATE))) {
            failure.printStackTrace(output);
        }
    }

    private void write() throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(target.getFilesDir(), "quickjs-legacy-result.json"))) {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Test-only exact response replacement; all dependencies keep the normal shared HTTP path. */
    private final class OriginalScripts implements AutoCloseable {
        private final JSONObject config;
        private final Field clientField;
        private final OkHttpClient original;

        OriginalScripts(File directory) throws Exception {
            stage = "runtime.scripts";
            config = new JSONObject(Files.readString(new File(directory, "scripts.json").toPath(), StandardCharsets.UTF_8));
            Map<String, String> bodies = new HashMap<>();
            for (String name : List.of("tencent", "bili")) {
                for (String kind : List.of("api", "rule")) {
                    File file = new File(directory, name + "-" + kind + ".js");
                    assertEquals("Private script fixture bytes changed", kind.equals("api") ? API_SHA : RULE_SHA.get(name), hash(file));
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    if (kind.equals("api")) content = "console.log=console.info=console.warn=console.error=console.debug=function(){};\n" + content;
                    String key = new Request.Builder().url(url(name, kind)).build().url().toString();
                    bodies.put(key, content);
                }
            }
            original = OkHttp.client();
            OkHttpClient.Builder builder = original.newBuilder();
            builder.interceptors().add(0, chain -> {
                String body = bodies.get(chain.request().url().toString());
                if (body == null) return chain.proceed(chain.request());
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("Private script fixture")
                        .body(ResponseBody.create(body, MediaType.get("application/javascript; charset=utf-8"))).build();
            });
            clientField = OkHttp.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(OkHttp.get(), builder.build());
        }

        String url(String name, String kind) throws Exception { return config.getJSONObject(name).getString(kind); }

        @Override public void close() throws Exception { clientField.set(OkHttp.get(), original); }
    }

    private static final class ScriptServer extends NanoHTTPD {
        final File directory;
        ScriptServer(File directory) { super("127.0.0.1", 0); this.directory = directory; }
        @Override public Response serve(IHTTPSession session) {
            String[] parts = session.getUri().split("/");
            if (parts.length != 3 || !RULE_SHA.containsKey(parts[1]) || !List.of("api.js", "rule.js").contains(parts[2])) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "missing");
            }
            try {
                File file = new File(directory, parts[1] + "-" + parts[2]);
                String script = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (parts[2].equals("api.js")) {
                    // Keep private rule cookies and URLs out of the Android log buffer.
                    script = "console.log=console.info=console.warn=console.error=console.debug=function(){};\n" + script;
                }
                return newFixedLengthResponse(Response.Status.OK, "application/javascript", script);
            } catch (Exception ignored) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "missing fixture");
            }
        }
    }
}
