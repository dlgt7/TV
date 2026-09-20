package com.fongmi.android.tv.playback.live;

/** Loads a resolved URL for a live or catch-up playback request. */
public interface LiveDataSource {

    void getUrl(LivePlayRequest request);
}
