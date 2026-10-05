package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.subtitle.SubtitleFonts;
import com.fongmi.android.tv.setting.AdvancedSubtitleSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class AdvancedSubtitleDialog extends DialogFragment {
    private PlayerManager player;

    public static void show(FragmentActivity activity, PlayerManager player) {
        AdvancedSubtitleDialog dialog = new AdvancedSubtitleDialog();
        dialog.player = player;
        dialog.show(activity.getSupportFragmentManager(), "advanced-subtitle");
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (player == null || player.isReleased()) return new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.subtitle_advanced).setPositiveButton(android.R.string.ok, null).create();
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(24);
        content.setPadding(pad, pad, pad, pad);
        MaterialSwitch ass = new MaterialSwitch(requireContext());
        ass.setText(R.string.subtitle_ass);
        ass.setChecked(AdvancedSubtitleSetting.ass());
        ass.setOnCheckedChangeListener((button, checked) -> {
            AdvancedSubtitleSetting.ass(checked);
            player.clearPreload();
            player.refreshSubtitles();
        });
        content.addView(ass);
        MaterialButton secondary = new MaterialButton(requireContext());
        secondary.setText(R.string.subtitle_secondary);
        secondary.setOnClickListener(view -> {
            TrackDialog.create().player(player).type(C.TRACK_TYPE_TEXT).secondary(true).show(requireActivity());
            dismiss();
        });
        content.addView(secondary);
        MaterialButton font = new MaterialButton(requireContext());
        font.setText(R.string.subtitle_fonts);
        font.setOnClickListener(view -> chooseFont.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)));
        content.addView(font);
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.subtitle_advanced).setView(content)
                .setPositiveButton(android.R.string.ok, null).create();
    }

    private final ActivityResultLauncher<Intent> chooseFont = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        android.net.Uri uri = result.getData().getData();
        Task.executor().execute(() -> {
            boolean imported = SubtitleFonts.importFont(uri);
            App.post(() -> {
                Notify.show(imported ? R.string.subtitle_font_added : R.string.subtitle_font_invalid);
                if (imported && player != null && !player.isReleased()) player.refreshSubtitles();
            });
        });
    });
}
