package com.fongmi.android.tv.player;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Learns successful live source lines without persisting source URLs or credentials. */
public final class LineQualityStore {

    private static final String KEY = "live_line_quality_v2";
    private static final int MAX_ENTRIES = 256;
    private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, LineScore>>() {}.getType();

    private LineQualityStore() {
    }

    public static synchronized void recordSuccess(String rawUrl, long openMs) {
        String key = key(rawUrl);
        if (key.isEmpty()) return;
        Map<String, LineScore> map = load();
        LineScore score = map.getOrDefault(key, new LineScore());
        score.ok++;
        score.lastOkAt = System.currentTimeMillis();
        if (openMs > 0) score.openMsAvg = score.openMsAvg <= 0 ? openMs : (score.openMsAvg * 4 + openMs) / 5;
        if (score.fail > 0) score.fail--;
        map.put(key, score);
        save(trim(map));
    }

    public static synchronized void recordFailure(String rawUrl) {
        String key = key(rawUrl);
        if (key.isEmpty()) return;
        Map<String, LineScore> map = load();
        LineScore score = map.getOrDefault(key, new LineScore());
        score.fail = Math.min(1000, score.fail + 1);
        score.lastFailAt = System.currentTimeMillis();
        map.put(key, score);
        save(trim(map));
    }

    public static synchronized int bestIndex(List<String> urls, int fallbackIndex) {
        if (urls == null || urls.isEmpty()) return 0;
        int fallback = Math.clamp(fallbackIndex, 0, urls.size() - 1);
        Map<String, LineScore> map = load();
        long bestScore = Long.MIN_VALUE;
        int bestIndex = fallback;
        boolean found = false;
        for (int i = 0; i < urls.size(); i++) {
            LineScore score = map.get(key(urls.get(i)));
            if (score == null) continue;
            long value = qualityScore(score);
            if (!found || value > bestScore) {
                found = true;
                bestScore = value;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static Map<String, LineScore> load() {
        try {
            String json = Prefers.getString(KEY, "");
            if (TextUtils.isEmpty(json)) return new HashMap<>();
            Map<String, LineScore> map = App.gson().fromJson(json, TYPE);
            return map == null ? new HashMap<>() : map;
        } catch (Throwable e) {
            return new HashMap<>();
        }
    }

    private static void save(Map<String, LineScore> map) {
        Prefers.put(KEY, App.gson().toJson(map));
    }

    private static Map<String, LineScore> trim(Map<String, LineScore> map) {
        while (map.size() > MAX_ENTRIES) {
            String oldest = map.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().lastSeen())).map(Map.Entry::getKey).orElse(null);
            if (oldest == null) break;
            map.remove(oldest);
        }
        return map;
    }

    private static long qualityScore(LineScore score) {
        long base = (long) score.ok * 1000L - (long) score.fail * 2000L;
        long speed = score.openMsAvg > 0 ? Math.max(0, 3000L - score.openMsAvg) : 0;
        return base + speed + (score.lastOkAt >= score.lastFailAt ? 300 : -300);
    }

    private static String key(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) return "";
        String normalized = rawUrl.split("\\$", 2)[0].trim();
        if (normalized.isEmpty()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte value : digest) output.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            return output.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static final class LineScore {
        int ok;
        int fail;
        long openMsAvg;
        long lastOkAt;
        long lastFailAt;

        long lastSeen() {
            return Math.max(lastOkAt, lastFailAt);
        }
    }
}
