package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SpeedDialog extends BaseAlertDialog {

    private static final float[] PRESETS = {0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 3.0f, 5.0f};
    private DialogSpeedBinding binding;

    public static void show(FragmentActivity activity) {
        new SpeedDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.player_speed)
                .setSingleChoiceItems(labels(), selected(), (dialog, which) -> {
                    ((SpeedListener) requireActivity()).setSpeed(PRESETS[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null);
    }

    private int selected() {
        float value = PlayerSetting.getSpeed();
        int best = 0;
        for (int i = 1; i < PRESETS.length; i++) if (Math.abs(PRESETS[i] - value) < Math.abs(PRESETS[best] - value)) best = i;
        return best;
    }

    private String[] labels() {
        String[] labels = new String[PRESETS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = PRESETS[i] + "×";
        return labels;
    }
}
