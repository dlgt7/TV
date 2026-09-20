package com.fongmi.android.tv.model;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.playback.vod.VodDataSource;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;

import java.util.List;

public class VideoViewModel extends SiteViewModel implements VodDataSource {

    private final VodPlaybackState playbackState;

    public VideoViewModel() {
        playbackState = new VodPlaybackState();
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        return new VodPlaybackController(host, this, playbackState);
    }

    @Override
    public void playerContent(VodPlayRequest request) {
        playerContent(request.getKey(), request.getFlag(), request.getId());
    }

    @Override
    public void preloadContent(VodPlayRequest request) {
        preloadContent(request.getKey(), request.getFlag(), request.getId());
    }

    @Override
    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        super.searchContent(sites, keyword, quick);
    }

    @Override
    protected void onCleared() {
        playbackState.reset();
        super.onCleared();
    }
}
