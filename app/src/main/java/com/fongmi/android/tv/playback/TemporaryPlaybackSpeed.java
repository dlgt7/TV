package com.fongmi.android.tv.playback;

/** Saves the actual speed once per hold; a canceled/repeated gesture cannot replace it. */
public final class TemporaryPlaybackSpeed {
    private Float previous;

    public boolean begin(float speed) {
        if (previous != null) return false;
        previous = speed;
        return true;
    }

    public Float end() {
        Float result = previous;
        previous = null;
        return result;
    }
}
