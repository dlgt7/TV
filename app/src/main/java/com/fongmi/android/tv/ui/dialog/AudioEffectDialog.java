package com.fongmi.android.tv.ui.dialog;

import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.audio.AudioDsp;
import com.fongmi.android.tv.setting.AudioEffectSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textview.MaterialTextView;

public final class AudioEffectDialog {
    private AudioEffectDialog() {}

    public static void show(FragmentActivity activity, PlayerManager player) {
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.audio_effect_title)
                .setSingleChoiceItems(R.array.audio_effect_presets, AudioEffectSetting.preset(), (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 4) customize(activity, player);
                    else {
                        AudioEffectSetting.preset(which);
                        player.refreshAudioEffects();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void customize(FragmentActivity activity, PlayerManager player) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(24);
        content.setPadding(pad, pad, pad, pad);
        MaterialTextView note = new MaterialTextView(activity);
        note.setText(R.string.audio_effect_pcm_note);
        content.addView(note);
        float[] values = AudioEffectSetting.bands();
        Slider[] sliders = new Slider[5];
        for (int i = 0; i < sliders.length; i++) {
            sliders[i] = slider(activity, content, AudioDsp.FREQUENCIES[i] + " Hz", -12, 12, values[i]);
        }
        Slider center = slider(activity, content, activity.getString(R.string.audio_effect_center), 0, 12, AudioEffectSetting.centerDb());
        MaterialSwitch normalize = new MaterialSwitch(activity);
        normalize.setText(R.string.audio_effect_normalize);
        normalize.setChecked(AudioEffectSetting.normalize());
        content.addView(normalize);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.audio_effect_title).setView(scroll)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    for (int i = 0; i < sliders.length; i++) AudioEffectSetting.band(i, sliders[i].getValue());
                    AudioEffectSetting.centerDb(center.getValue());
                    AudioEffectSetting.normalize(normalize.isChecked());
                    player.refreshAudioEffects();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static Slider slider(FragmentActivity activity, LinearLayout parent, String title, float min, float max, float value) {
        MaterialTextView label = new MaterialTextView(activity);
        label.setText(title);
        parent.addView(label);
        Slider slider = new Slider(activity);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(1);
        slider.setValue(Math.round(value));
        slider.setContentDescription(title);
        slider.setLabelFormatter(number -> Math.round(number) + " dB");
        parent.addView(slider);
        return slider;
    }
}
