package com.fongmi.android.tv.service;

import android.app.Activity;
import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ui.activity.AirPlayCastActivity;
import com.fongmi.android.tv.ui.activity.CastActivity;

/** Mutual exclusion between AirPlay UI and DLNA CastActivity. */
public final class CastConflict {

    private CastConflict() {
    }

    /** AirPlay session became active: stop DLNA cast UI / shared player. */
    public static void yieldToAirPlay() {
        App.post(() -> {
            PlaybackService.requestSuspend(App.get());
            finishIf(CastActivity.class);
        });
    }

    /** DLNA cast is about to play: stop AirPlay local A/V and close its UI. */
    public static void yieldToDlna(Context context) {
        App.post(() -> {
            finishIf(AirPlayCastActivity.class);
            stopAirPlaySession(context.getApplicationContext());
        });
    }

    private static void finishIf(Class<? extends Activity> type) {
        Activity current = App.activity();
        if (current != null && type.isInstance(current) && !current.isFinishing()) {
            current.finish();
        }
    }

    private static void stopAirPlaySession(Context app) {
        // Only yield the local A/V session to DLNA. ACTION_STOP_SERVER would unregister
        // AirPlay/NSD and the TV vanishes from the Apple picker after one DLNA cast.
        AirPlayServer.stopLocalSession(app, "dlna");
    }
}
