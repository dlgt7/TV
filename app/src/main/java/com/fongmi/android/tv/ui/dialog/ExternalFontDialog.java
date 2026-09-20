package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSubtitleApiBinding;
import com.fongmi.android.tv.player.subtitle.ExternalFont;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Pick an external subtitle font for MPV/libass, or import a new font file. */
public class ExternalFontDialog extends BaseAlertDialog {

    public interface Listener {
        void onFontSelected(@Nullable String path);

        void onFontImportRequested();
    }

    private DialogSubtitleApiBinding binding;

    public static void show(Fragment fragment) {
        new ExternalFontDialog().show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        new ExternalFontDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSubtitleApiBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        List<ExternalFont.Entry> fonts = ExternalFont.getAll();
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.subtitle_font_default));
        labels.add(getString(R.string.subtitle_font_import));
        String current = SubtitleSetting.getFontPath();
        int checked = 0;
        for (int i = 0; i < fonts.size(); i++) {
            ExternalFont.Entry entry = fonts.get(i);
            labels.add(entry.name());
            if (!TextUtils.isEmpty(current) && TextUtils.equals(current, entry.path())) checked = i + 2;
        }
        CharSequence[] items = labels.toArray(new CharSequence[0]);
        return builder()
                .setTitle(R.string.player_subtitle_font)
                .setSingleChoiceItems(items, checked, this::onSelect)
                .setNegativeButton(R.string.dialog_negative, null);
    }

    private void onSelect(DialogInterface dialog, int which) {
        Listener listener = findListener();
        if (which == 0) {
            SubtitleSetting.putFontPath("");
            if (listener != null) listener.onFontSelected(null);
            dismiss();
            return;
        }
        if (which == 1) {
            if (listener != null) listener.onFontImportRequested();
            dismiss();
            return;
        }
        List<ExternalFont.Entry> fonts = ExternalFont.getAll();
        int index = which - 2;
        if (index < 0 || index >= fonts.size()) {
            dismiss();
            return;
        }
        String path = fonts.get(index).path();
        SubtitleSetting.putFontPath(path);
        if (listener != null) listener.onFontSelected(path);
        dismiss();
    }

    @Nullable
    private Listener findListener() {
        Fragment parent = getParentFragment();
        if (parent instanceof Listener) return (Listener) parent;
        FragmentActivity activity = getActivity();
        if (activity instanceof Listener) return (Listener) activity;
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return "ExternalFontDialog";
    }
}
