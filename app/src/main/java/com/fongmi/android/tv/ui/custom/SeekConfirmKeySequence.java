package com.fongmi.android.tv.ui.custom;

/** Keeps a seek confirmation's release out of the player's play/pause shortcut. */
public final class SeekConfirmKeySequence {
    private int keyCode = -1;
    private long downTime;

    public void claim(int keyCode, long downTime) {
        this.keyCode = keyCode;
        this.downTime = downTime;
    }

    public boolean consume(int keyCode, long downTime, boolean released) {
        if (this.keyCode != keyCode) return false;
        if (this.downTime != downTime) {
            // A release may be lost when a window changes. Never swallow a fresh press.
            this.keyCode = -1;
            return false;
        }
        if (released) this.keyCode = -1;
        return true;
    }
}
