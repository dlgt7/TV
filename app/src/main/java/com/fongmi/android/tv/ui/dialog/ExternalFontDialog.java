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

import java.util.ArrayList;
import java.util.List;

/**
 * Pick an external subtitle font for MPV/libass and Media3 (Exo), or import a new font file.
 *
 * <p>Font collections (.ttc) expose one row per face so a specific sub-font can be selected; the
 * choice is persisted as path + face index rather than path alone.
 */
public class ExternalFontDialog extends BaseAlertDialog {

    public interface Listener {
        void onFontSelected(@Nullable ExternalFont.Entry entry);

        void onFontImportRequested();
    }

    private DialogSubtitleApiBinding binding;
    private List<ExternalFont.Entry> fonts;

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
        return builder()
                .setTitle(R.string.player_subtitle_font)
                .setSingleChoiceItems(buildLabels(), checkedIndex(), this::onSelect)
                .setNegativeButton(R.string.dialog_negative, null);
    }

    private CharSequence[] buildLabels() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.subtitle_font_default));
        labels.add(getString(R.string.subtitle_font_import));
        for (ExternalFont.Entry entry : fonts()) labels.add(entry.name());
        return labels.toArray(new CharSequence[0]);
    }

    /**
     * Enumerating fonts reads and parses every font file, so the dialog must do it once per screen.
     * {@code getBuilder}, the checked-item lookup and the click handler used to each call
     * {@link ExternalFont#getAll()}, which parsed every file three times on the UI thread.
     */
    private List<ExternalFont.Entry> fonts() {
        if (fonts == null) fonts = ExternalFont.getAll();
        return fonts;
    }

    /** First two rows are "default" and "import", so a match is offset by two. */
    private int checkedIndex() {
        String path = SubtitleSetting.getFontPath();
        if (TextUtils.isEmpty(path)) return 0;
        int face = SubtitleSetting.getFontFaceIndex();
        List<ExternalFont.Entry> entries = fonts();
        for (int i = 0; i < entries.size(); i++) {
            ExternalFont.Entry entry = entries.get(i);
            if (TextUtils.equals(path, entry.path()) && face == entry.faceIndex()) return i + 2;
        }
        return 0;
    }

    private void onSelect(DialogInterface dialog, int which) {
        Listener listener = findListener();
        if (which == 0) {
            SubtitleSetting.putFontSelection(null);
            if (listener != null) listener.onFontSelected(null);
            dismiss();
            return;
        }
        if (which == 1) {
            if (listener != null) listener.onFontImportRequested();
            dismiss();
            return;
        }
        List<ExternalFont.Entry> entries = fonts();
        int index = which - 2;
        if (index < 0 || index >= entries.size()) {
            dismiss();
            return;
        }
        ExternalFont.Entry entry = entries.get(index);
        SubtitleSetting.putFontSelection(entry);
        if (listener != null) listener.onFontSelected(entry);
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
