package com.fongmi.android.tv.ui.dialog;

import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Independent timing adjustment for the public secondary-subtitle overlay. */
public final class SecondarySubtitleOffsetDialog {

    private static final int LIMIT_SECONDS = 600;

    private SecondarySubtitleOffsetDialog() {
    }

    public static void show(FragmentActivity activity, PlayerManager player) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(24);
        root.setPadding(padding, padding, padding, padding / 2);
        TextView value = new TextView(activity);
        value.setTextSize(18);
        SeekBar seek = new SeekBar(activity);
        seek.setMax(LIMIT_SECONDS * 2);
        int current = Math.clamp((int) (player.getSecondarySubtitleOffsetMs() / 1000), -LIMIT_SECONDS, LIMIT_SECONDS);
        seek.setProgress(current + LIMIT_SECONDS);
        setText(value, current);
        root.addView(value);
        root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int seconds = progress - LIMIT_SECONDS;
                setText(value, seconds);
                if (fromUser) player.setSecondarySubtitleOffsetMs(seconds * 1000L);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.subtitle_secondary_offset)
                .setView(root)
                .setPositiveButton(R.string.dialog_positive, null)
                .setNeutralButton(R.string.dialog_reset, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            player.setSecondarySubtitleOffsetMs(0);
            seek.setProgress(LIMIT_SECONDS);
        }));
        dialog.show();
    }

    private static void setText(TextView view, int seconds) {
        view.setText(String.format(Locale.getDefault(), "%+.1f s", seconds * 1.0f));
    }
}
