package com.fongmi.android.tv.test;

import android.app.Activity;
import android.os.Bundle;
import androidx.media3.ui.PlayerView;

/** Instrumentation surface only; never included in release builds. */
public final class CorePlaybackActivity extends Activity {
    public PlayerView view;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        view = new PlayerView(this);
        view.setUseController(false);
        setContentView(view);
    }
}
