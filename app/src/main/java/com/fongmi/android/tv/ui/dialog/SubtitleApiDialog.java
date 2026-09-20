package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSubtitleApiBinding;
import com.fongmi.android.tv.impl.SubtitleListener;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SubtitleApiDialog extends BaseAlertDialog {

    private DialogSubtitleApiBinding binding;

    public static void show(Fragment fragment) {
        new SubtitleApiDialog().show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        new SubtitleApiDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSubtitleApiBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.subtitle_search_api_title).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        String text = SubtitleSetting.getSearchToken();
        if (TextUtils.isEmpty(text)) text = SubtitleSetting.getEffectiveToken();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void initEvent() {
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void onPositive(android.content.DialogInterface dialog, int which) {
        CharSequence text = binding.text.getText();
        String token = text == null ? "" : text.toString().trim();
        SubtitleSetting.putSearchToken(token);
        SubtitleListener listener = findListener();
        if (listener != null) listener.setSubtitleToken(token);
        dismiss();
    }

    private SubtitleListener findListener() {
        Fragment parent = getParentFragment();
        if (parent instanceof SubtitleListener) return (SubtitleListener) parent;
        FragmentActivity activity = getActivity();
        if (activity instanceof SubtitleListener) return (SubtitleListener) activity;
        return null;
    }
}
