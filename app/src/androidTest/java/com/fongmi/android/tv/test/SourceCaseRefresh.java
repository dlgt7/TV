package com.fongmi.android.tv.test;

import android.content.Context;
import android.os.Looper;

import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Keeps one real Spider alive while private playback cases are refreshed immediately before use. */
final class SourceCaseRefresh implements AutoCloseable {
    private final Context target;
    private final String sourceUrl;
    private final File sourceCache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "source-case-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private Spider spider;
    private String sourceSha;
    private boolean initialized;
    private boolean ownsCache;
    private boolean stalled;
    private boolean closed;

    SourceCaseRefresh(Context target, String sourceUrl) {
        this.target = target;
        this.sourceUrl = sourceUrl;
        try {
            URI uri = URI.create(sourceUrl);
            String path = uri.getRawPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawQuery() != null || uri.getFragment() != null
                    || uri.getUserInfo() != null || !name.matches("[A-Za-z][A-Za-z0-9_-]{8,100}\\.py")) {
                throw new IllegalArgumentException();
            }
            sourceCache = new File(new File(target.getCacheDir(), "py"), name);
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("SOURCE_REFRESH_INVALID_SOURCE_URL");
        }
    }

    /** Call from the instrumentation test thread, not the UI thread. Never logs private fields. */
    synchronized String refresh(JSONObject item) throws Exception {
        if (closed || stalled) throw new Exception("SOURCE_REFRESH_UNAVAILABLE");
        if (Looper.myLooper() == Looper.getMainLooper()) throw new Exception("SOURCE_REFRESH_REQUIRES_WORKER_CALLER");
        try {
            String series = item.getString("seriesId"), line = item.getString("line");
            int episode = item.getInt("episodeIndex");
            JSONObject refreshed = invoke(() -> resolve(series, line, episode), 60);
            for (String key : new String[]{"url", "headers", "parse", "episodeCount", "sourceSha256"}) {
                item.put(key, refreshed.get(key));
            }
            return refreshed.getString("sourceSha256");
        } catch (Exception ignored) {
            throw new Exception(stalled ? "SOURCE_REFRESH_TIMEOUT_OR_INTERRUPTED" : "SOURCE_REFRESH_FAILED");
        }
    }

    private JSONObject resolve(String series, String line, int episode) throws Exception {
        ensureLoaded();
        JSONObject detail = new JSONObject(spider.detailContent(Collections.singletonList(series)));
        JSONArray list = detail.getJSONArray("list");
        if (list.length() != 1) throw new Exception();
        JSONObject vod = list.getJSONObject(0);
        String[] lines = vod.getString("vod_play_from").split("\\$\\$\\$", -1);
        String[] streams = vod.getString("vod_play_url").split("\\$\\$\\$", -1);
        if (lines.length != streams.length) throw new Exception();
        int selected = -1;
        for (int i = 0; i < lines.length; i++) if (line.equals(lines[i])) { selected = i; break; }
        if (selected < 0) throw new Exception();
        String[] episodes = streams[selected].split("#", -1);
        if (episode < 1 || episode > episodes.length) throw new Exception();
        String entry = episodes[episode - 1];
        int separator = entry.indexOf('$');
        if (separator <= 0 || separator == entry.length() - 1) throw new Exception();
        JSONObject play = new JSONObject(spider.playerContent(line, entry.substring(separator + 1), Collections.emptyList()));
        String url = play.getString("url");
        URI uri = URI.create(url);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) throw new Exception();
        int parse = Boolean.FALSE.equals(play.opt("parse")) ? 0 : play.optInt("parse", -1);
        if (parse != 0) throw new Exception();
        Object raw = play.opt("header");
        JSONObject headers = raw == null || raw == JSONObject.NULL ? new JSONObject()
                : raw instanceof JSONObject object ? object
                : raw instanceof String text ? (text.isEmpty() ? new JSONObject() : new JSONObject(text)) : null;
        if (headers == null) throw new Exception();
        for (Iterator<String> keys = headers.keys(); keys.hasNext();) if (!(headers.get(keys.next()) instanceof String)) throw new Exception();
        return new JSONObject().put("url", url).put("headers", headers).put("parse", parse)
                .put("episodeCount", episodes.length).put("sourceSha256", sourceSha);
    }

    private void ensureLoaded() throws Exception {
        if (initialized) return;
        if (ownsCache) throw new Exception();
        File directory = sourceCache.getParentFile();
        if ((!directory.isDirectory() && !directory.mkdirs()) || !sourceCache.createNewFile()) throw new Exception();
        ownsCache = true;
        spider = new Loader().spider(sourceUrl);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(sourceCache)) {
            byte[] bytes = new byte[8192];
            int count, total = 0;
            while ((count = input.read(bytes)) != -1) {
                if ((total += count) > 8 * 1024 * 1024) throw new Exception();
                digest.update(bytes, 0, count);
            }
            if (total == 0) throw new Exception();
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        sourceSha = hash.toString();
        spider.siteKey = "source-refresh-" + sourceSha.substring(0, 12);
        spider.init(target, "");
        initialized = true;
    }

    private <T> T invoke(Callable<T> action, int seconds) throws Exception {
        Future<T> future = worker.submit(action);
        try { return future.get(seconds, TimeUnit.SECONDS); }
        catch (InterruptedException failure) { stalled = true; future.cancel(true); Thread.currentThread().interrupt(); throw new Exception(); }
        catch (TimeoutException failure) { stalled = true; future.cancel(true); throw new Exception(); }
        catch (ExecutionException failure) { throw new Exception("SOURCE_REFRESH_WORKER_FAILED"); }
    }

    /** The source's destroy releases its Python session; no Java service or loopback server is stopped. */
    @Override public synchronized void close() throws Exception {
        if (closed) return;
        closed = true;
        try {
            if (stalled) throw new Exception("SOURCE_REFRESH_WORKER_STILL_BUSY");
            invoke(() -> { if (spider != null) spider.destroy(); return null; }, 10);
        } finally {
            worker.shutdownNow();
            if (!stalled && ownsCache && sourceCache.exists() && !sourceCache.delete()) throw new Exception("SOURCE_REFRESH_CACHE_CLEANUP_FAILED");
        }
    }
}
