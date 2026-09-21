package com.fongmi.android.tv.debug;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.subtitle.SecondarySubtitleDiagnostics;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.activity.VideoActivity;

/** ADB-driven end-to-end smoke test for the public-API secondary subtitle overlay. */
public final class DualSubtitleSmokeActivity extends Activity implements ServiceConnection {

    public static final String TAG = "DualSubtitleSmoke";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String primarySubtitleUrl;
    private String subtitleUrl;
    private int attempts;
    private boolean bound;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        String videoUrl = getIntent().getStringExtra("videoUrl");
        primarySubtitleUrl = getIntent().getStringExtra("primarySubtitleUrl");
        subtitleUrl = getIntent().getStringExtra("subtitleUrl");
        if (videoUrl == null || subtitleUrl == null) {
            Log.e(TAG, "RESULT passed=false reason=missing_url");
            finish();
            return;
        }
        SecondarySubtitleDiagnostics.reset();
        VideoActivity.start(this, videoUrl);
        Intent service = new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION);
        startService(new Intent(this, PlaybackService.class));
        bound = bindService(service, this, BIND_AUTO_CREATE);
        handler.postDelayed(this::report, 8000L);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        PlaybackService service = ((PlaybackService.LocalBinder) binder).getService();
        handler.postDelayed(() -> applySubtitle(service.player()), 200L);
    }

    private void applySubtitle(PlayerManager player) {
        if (!player.isEmpty()) {
            if (primarySubtitleUrl != null) player.setSub(Sub.from("primary.srt", primarySubtitleUrl));
            player.setSecondarySub(Sub.from("secondary.srt", subtitleUrl));
        } else if (attempts++ < 25) {
            handler.postDelayed(() -> applySubtitle(player), 200L);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    private void report() {
        SecondarySubtitleDiagnostics.Snapshot value = SecondarySubtitleDiagnostics.snapshot();
        boolean passed = value.loaded() > 0 && value.failed() == 0 && value.rendered() > 0;
        Log.i(TAG, "RESULT passed=" + passed + " loaded=" + value.loaded() + " failed=" + value.failed() + " rendered=" + value.rendered());
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (bound) unbindService(this);
        bound = false;
        super.onDestroy();
    }
}
