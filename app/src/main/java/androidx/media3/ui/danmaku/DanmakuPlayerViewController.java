package androidx.media3.ui.danmaku;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.ui.widget.DanmakuView;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class DanmakuPlayerViewController implements Player.Listener {

    private static final Pattern D_PATTERN = Pattern.compile("<d[^>]*p=\"([^\"]*)\"[^>]*>([^<]*)</d>");
    private static final int[] SCROLL_TYPES = {BaseDanmaku.TYPE_SCROLL_RL, BaseDanmaku.TYPE_SCROLL_LR};

    private DanmakuView danmakuView;
    private DanmakuContext danmakuContext;
    private PlayerView playerView;
    private Player player;
    private OkHttpClient okHttpClient;
    private DanmakuConfig config;
    private boolean enabled;
    private boolean prepared;
    private volatile boolean released;
    private long timeOffsetMs;

    public DanmakuPlayerViewController() {}

    public void bind(PlayerView playerView) {
        if (this.playerView == playerView && this.danmakuView != null) return;
        unbind();
        if (playerView == null) return;
        this.playerView = playerView;
        Context ctx = playerView.getContext();
        this.danmakuView = new DanmakuView(ctx);
        this.danmakuContext = DanmakuContext.create();
        this.prepared = false;
        this.released = false;
        applyConfig();
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        playerView.addView(danmakuView, lp);
        danmakuView.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    public void setOkHttpClient(OkHttpClient client) {
        this.okHttpClient = client;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (danmakuView != null) danmakuView.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    public void setConfig(DanmakuConfig config) {
        this.config = config;
        this.timeOffsetMs = config == null ? 0L : config.timeOffsetMs;
        applyConfig();
    }

    public void setDataSource(@Nullable Uri uri) {
        if (danmakuView == null || released) return;
        danmakuView.release();
        prepared = false;
        if (uri == null) return;
        final OkHttpClient client = okHttpClient;
        final String uriStr = uri.toString();
        new Thread(() -> {
            String content = fetch(client, uriStr);
            if (released) return;
            Danmakus danmakus = parse(content);
            if (released) return;
            androidx.media3.ui.danmaku.DanmakuPlayerViewController.PostHelper.post(() -> {
                if (danmakuView == null || released) return;
                danmakuView.prepare(new DanmakuParser(danmakus), danmakuContext);
                prepared = true;
                attachPlayer();
                startIfNeeded();
            });
        }, "danmaku-load").start();
    }

    public void sendNow(String text) {
        if (danmakuView == null || !prepared || danmakuContext == null || TextUtils.isEmpty(text)) return;
        BaseDanmaku item = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, danmakuContext);
        if (item == null) return;
        item.text = text;
        item.time = player != null ? player.getCurrentPosition() : 0L;
        item.textSize = 25f * (danmakuContext.getDisplayer().getDensity() - 0.6f);
        item.textColor = Color.WHITE;
        item.textShadowColor = Color.BLACK;
        danmakuView.addDanmaku(item);
    }

    public void close() {
        unbind();
    }

    private void unbind() {
        released = true;
        detachPlayer();
        if (danmakuView != null) {
            danmakuView.release();
            if (playerView != null) playerView.removeView(danmakuView);
        }
        danmakuView = null;
        danmakuContext = null;
        playerView = null;
        prepared = false;
    }

    private void detachPlayer() {
        if (player != null) player.removeListener(this);
        player = null;
    }

    private void attachPlayer() {
        if (playerView == null) return;
        Player p = playerView.getPlayer();
        if (p == player) return;
        detachPlayer();
        player = p;
        if (player != null) player.addListener(this);
    }

    private void startIfNeeded() {
        if (danmakuView == null || !prepared || player == null) return;
        int state = player.getPlaybackState();
        if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
            danmakuView.start(player.getCurrentPosition());
            if (enabled) danmakuView.show();
        }
    }

    private void applyConfig() {
        if (danmakuContext == null || config == null) return;
        try {
            danmakuContext.setDanmakuTransparency(1f - config.transparency);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setScaleTextSize(config.textScale);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setScrollSpeedFactor(1f / Math.max(0.1f, config.scrollAreaRatio));
        } catch (Throwable ignored) {}
        try {
            if (config.styleMode == DanmakuConfig.STYLE_SHADOW) {
                danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_SHADOW, 3f);
            } else if (config.styleMode == DanmakuConfig.STYLE_PROJECTION) {
                danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_PROJECTION, 3f);
            } else if (config.styleMode == DanmakuConfig.STYLE_NONE) {
                danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_DEFAULT, 0f);
            } else {
                danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f);
            }
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setDanmakuMargin(8);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setR2LDanmakuVisibility(config.showScroll);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setL2RDanmakuVisibility(config.showReverse);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setFTDanmakuVisibility(config.showTop);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setFBDanmakuVisibility(config.showBottom);
        } catch (Throwable ignored) {}
        try {
            danmakuContext.setSpecialDanmakuVisibility(config.showSpecial);
        } catch (Throwable ignored) {}
        try {
            java.util.Map<Integer, Integer> maxLines = new java.util.HashMap<>();
            if (config.maxScrollLines > 0) maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, config.maxScrollLines);
            if (config.maxTopLines > 0) maxLines.put(BaseDanmaku.TYPE_FIX_TOP, config.maxTopLines);
            if (config.maxBottomLines > 0) maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, config.maxBottomLines);
            if (!maxLines.isEmpty()) danmakuContext.setMaximumLines(maxLines);
        } catch (Throwable ignored) {}
        try {
            if (config.typeface != null) danmakuContext.setTypeface(config.typeface);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (danmakuView == null || !prepared) return;
        if (isPlaying) {
            if (danmakuView.isPrepared()) danmakuView.resume();
        } else {
            danmakuView.pause();
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (danmakuView == null || !prepared || player == null) return;
        if (playbackState == Player.STATE_READY) {
            if (!danmakuView.isPrepared()) {
                startIfNeeded();
            } else if (player.isPlaying()) {
                danmakuView.start(player.getCurrentPosition());
            }
        } else if (playbackState == Player.STATE_ENDED) {
            danmakuView.stop();
        }
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (danmakuView == null || !prepared || player == null) return;
        if (danmakuView.isPrepared()) danmakuView.seekTo(player.getCurrentPosition());
    }

    private String fetch(@Nullable OkHttpClient client, String uriStr) {
        if (TextUtils.isEmpty(uriStr)) return "";
        if (uriStr.startsWith("http")) {
            OkHttpClient c = client != null ? client : new OkHttpClient();
            try (Response res = c.newCall(new Request.Builder().url(uriStr).build()).execute()) {
                if (!res.isSuccessful()) return "";
                return res.body().string();
            } catch (IOException e) {
                return "";
            }
        }
        if (uriStr.startsWith("file") || uriStr.startsWith("content")) {
            try {
                Uri uri = Uri.parse(uriStr);
                Context ctx = playerView != null ? playerView.getContext() : null;
                if (ctx == null) return "";
                java.io.InputStream is = ctx.getContentResolver().openInputStream(uri);
                if (is == null) return "";
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                is.close();
                return bos.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }
        return uriStr;
    }

    private Danmakus parse(String content) {
        Danmakus result = new Danmakus(IDanmakus.ST_BY_TIME);
        if (TextUtils.isEmpty(content)) return result;
        if (content.trim().startsWith("<") || content.contains("<d ")) {
            parseXml(content, result);
        } else {
            parseJson(content, result);
        }
        return result;
    }

    private void parseXml(String content, Danmakus result) {
        Matcher matcher = D_PATTERN.matcher(content);
        float density = danmakuContext != null && danmakuContext.getDisplayer() != null ? danmakuContext.getDisplayer().getDensity() : 1f;
        while (matcher.find()) {
            String param = matcher.group(1);
            String text = decodeXml(matcher.group(2));
            addDanmaku(result, param, text, density);
        }
    }

    private void parseJson(String content, Danmakus result) {
        float density = danmakuContext != null && danmakuContext.getDisplayer() != null ? danmakuContext.getDisplayer().getDensity() : 1f;
        String trimmed = content.trim();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.opt(i);
                    if (o instanceof JSONArray) {
                        JSONArray item = (JSONArray) o;
                        String param = item.optDouble(0, 0) + "," + item.optInt(1, 1) + "," + item.optDouble(3, 25) + "," + item.optInt(2, 16777215);
                        String text = item.optString(4, "");
                        addDanmaku(result, param, text, density);
                    } else if (o instanceof JSONObject) {
                        JSONObject item = (JSONObject) o;
                        String param = item.optDouble("time", item.optDouble("t", 0)) + "," + item.optInt("type", 1) + "," + item.optDouble("size", item.optDouble("fontSize", 25)) + "," + item.optInt("color", 16777215);
                        String text = item.optString("text", item.optString("content", ""));
                        addDanmaku(result, param, text, density);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void addDanmaku(Danmakus result, String param, String text, float density) {
        if (TextUtils.isEmpty(param) || TextUtils.isEmpty(text) || danmakuContext == null) return;
        String[] values = param.split(",");
        if (values.length < 4) return;
        try {
            int type = Integer.parseInt(values[1].trim());
            long time = (long) (Float.parseFloat(values[0].trim()) * 1000) + timeOffsetMs;
            float size = Float.parseFloat(values[2].trim()) * (density - 0.6f);
            long colorLong = Long.parseLong(values[3].trim());
            int color = (int) ((0x00000000FF000000L | colorLong) & 0x00000000FFFFFFFFL);
            BaseDanmaku item = danmakuContext.mDanmakuFactory.createDanmaku(type, danmakuContext);
            if (item == null) return;
            item.setTime(time);
            item.textSize = size;
            item.textColor = color;
            item.textShadowColor = color <= Color.BLACK ? Color.WHITE : Color.BLACK;
            item.flags = danmakuContext.mGlobalFlagValues;
            item.text = text;
            synchronized (result.obtainSynchronizer()) {
                result.addItem(item);
            }
        } catch (Throwable ignored) {}
    }

    private static String decodeXml(String s) {
        if (s == null) return "";
        if (s.contains("&amp;")) s = s.replace("&amp;", "&");
        if (s.contains("&quot;")) s = s.replace("&quot;", "\"");
        if (s.contains("&gt;")) s = s.replace("&gt;", ">");
        if (s.contains("&lt;")) s = s.replace("&lt;", "<");
        return s;
    }

    private static final class DanmakuParser extends BaseDanmakuParser {
        private final Danmakus source;

        DanmakuParser(Danmakus source) {
            this.source = source;
        }

        @Override
        protected Danmakus parse() {
            if (source != null) return source;
            return new Danmakus(IDanmakus.ST_BY_TIME);
        }
    }

    private static final class PostHelper {
        static void post(Runnable r) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
        }
    }
}