package com.fongmi.android.tv.playback.vod;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;

/** Holds a prefetched next-episode play result until the user actually switches. */
public final class VodPreloadCache {

    private VodPlayRequest request;
    private Result result;
    private Episode episode;

    public boolean isPending() {
        return request != null && result == null;
    }

    public boolean hasResult() {
        return request != null && result != null;
    }

    public void begin(VodPlayRequest request, Episode episode) {
        this.request = request;
        this.episode = episode;
        this.result = null;
    }

    public void store(VodPlayRequest request, Result result, Episode episode) {
        this.request = request;
        this.result = result;
        this.episode = episode;
    }

    public void clear() {
        request = null;
        result = null;
        episode = null;
    }

    @Nullable
    public VodPlayRequest getRequest() {
        return request;
    }

    @Nullable
    public Episode getEpisode() {
        return episode;
    }

    @Nullable
    public Result consumeIfMatches(VodPlayRequest request, Episode episode) {
        if (this.request == null || result == null) return null;
        if (episode == null || !this.request.matches(request)) return null;
        if (this.episode != null && episode != this.episode && !episode.getUrl().equals(this.episode.getUrl())) return null;
        Result cached = result;
        clear();
        return cached;
    }

    public boolean matchesRequest(VodPlayRequest other) {
        return request != null && other != null && request.matches(other);
    }

    @Nullable
    public Result getResult() {
        return result;
    }
}
