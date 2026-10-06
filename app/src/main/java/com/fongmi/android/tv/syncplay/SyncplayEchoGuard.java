package com.fongmi.android.tv.syncplay;

/** Expected callbacks belong to one player/media binding and expire without delaying user input. */
final class SyncplayEchoGuard {
    private static final long CALLBACK_WINDOW_MS = 2500;
    private boolean applyingSeek;
    private boolean seekAdjustmentPending;
    private long seekUntil, pauseUntil, speedUntil;
    private Boolean paused;
    private Float speed;

    void reset() {
        applyingSeek = false; seekAdjustmentPending = false; paused = null; speed = null;
    }

    void beginSeek(long now) {
        applyingSeek = true; seekAdjustmentPending = true; seekUntil = now + CALLBACK_WINDOW_MS;
    }

    void endSeek() { applyingSeek = false; }

    boolean consumeSeek(boolean adjustment, long now) {
        // Exo and SimpleBasePlayer dispatch the initial SEEK inside seekTo(). Keep its later
        // SEEK_ADJUSTMENT eligible, but a fresh user SEEK outside that call supersedes it immediately.
        if (applyingSeek) return true;
        boolean expected = adjustment && seekAdjustmentPending && now <= seekUntil;
        seekAdjustmentPending = false;
        return expected;
    }

    void expectPause(boolean value, long now) { paused = value; pauseUntil = now + CALLBACK_WINDOW_MS; }

    boolean consumePause(boolean value, long now) {
        boolean expected = paused != null && paused == value && now <= pauseUntil;
        paused = null;
        return expected;
    }

    void expectSpeed(float value, long now) { speed = value; speedUntil = now + CALLBACK_WINDOW_MS; }

    boolean consumeSpeed(float value, long now) {
        boolean expected = speed != null && Math.abs(speed - value) < .001f && now <= speedUntil;
        speed = null;
        return expected;
    }
}
