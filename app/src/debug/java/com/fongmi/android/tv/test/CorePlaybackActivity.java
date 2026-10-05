package com.fongmi.android.tv.test;

import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.media3.ui.PlayerView;

/** Instrumentation surface only; never included in release builds. */
public final class CorePlaybackActivity extends ComponentActivity {
    public PlayerView view;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        view = new PlayerView(this);
        view.setUseController(false);
        setContentView(view);
    }
}
