package com.fongmi.android.tv.playback.vod;

import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.TimeUnit;

/** Owns speculative source requests; never touches Source's active extractor session. */
final class NextEpisodePreloader {
    private static final long MAX_AGE_MS = 60_000;
    private final VodPlaybackHost host;
    private ListenableFuture<Result> future;
    private VodPlayRequest request;
    private Result result;
    private long completedAt;
    private int generation;
    private VodPlayRequest lastAttempt;
    private long attemptedAt;

    NextEpisodePreloader(VodPlaybackHost host) {
        this.host = host;
    }

    void prepare(Flag flag, Episode episode, int quality, History history) {
        if (!PreloadSetting.isNextEpisode() || !host.canPreloadNext()) { clear(); return; }
        VodPlayRequest next = VodPlayRequest.create(host.getVodKey(), flag, episode);
        long now = SystemClock.elapsedRealtime();
        if (next.matches(request) && (future != null && !future.isDone() || result != null && now - completedAt <= MAX_AGE_MS)) return;
        if (next.matches(lastAttempt) && now - attemptedAt < 15_000) return;
        clear();
        lastAttempt = next;
        attemptedAt = now;
        request = next;
        int ticket = generation;
        long opening = history == null ? 0 : Math.max(0, history.getOpening());
        future = FluentFuture.from(Task.executor().submit(() -> SiteApi.preloadContent(next.getKey(), next.getFlag(), next.getId())))
                .withTimeout(Constant.TIMEOUT_VOD, TimeUnit.MILLISECONDS, Task.scheduler());
        FluentFuture.from(future).addCallback(Task.callback(value -> App.post(() -> {
            if (ticket != generation || host.isHostFinishing()) return;
            if (!PreloadSetting.isNextEpisode() || !host.canPreloadNext() || !isEligible(value)) {
                clear();
                return;
            }
            value.getUrl().set(quality);
            long start = Math.max(opening, value.hasPosition() ? value.getPosition() : 0);
            if (!host.preloadPlayback(value, start, history, episode)) {
                clear();
                return;
            }
            result = value;
            completedAt = SystemClock.elapsedRealtime();
        }), error -> App.post(() -> {
            if (ticket == generation) clear();
        })), MoreExecutors.directExecutor());
    }

    Result consume(VodPlayRequest target) {
        if (PreloadSetting.isNextEpisode() && request != null && request.matches(target)
                && result != null && SystemClock.elapsedRealtime() - completedAt <= MAX_AGE_MS) {
            Result value = result;
            generation++;
            request = null;
            result = null;
            future = null;
            // The engine transfers the prepared MediaSource to the current player on start.
            return value;
        }
        clear();
        return null;
    }

    static boolean shouldPrepare(long position, long duration, long ending) {
        if (position < 0 || duration <= 0) return false;
        long remaining = duration - position - Math.max(0, ending);
        return remaining > 0 && remaining <= 45_000;
    }

    void expire() {
        if (result != null && SystemClock.elapsedRealtime() - completedAt > MAX_AGE_MS) clear();
    }

    void clear() {
        generation++;
        if (future != null) future.cancel(true);
        future = null;
        request = null;
        result = null;
        host.clearPreload();
    }

    static boolean isEligible(Result result) {
        if (result == null || result.hasMsg() || result.needParse() || result.isUseParse() || result.getDrm() != null) return false;
        String url = result.getRealUrl();
        return url.startsWith("https://") || url.startsWith("http://");
    }
}
