package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.databinding.DialogDanmuLineBinding;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DanmuLineDialog {

    private final DialogDanmuLineBinding binding;
    private final Listener callback;
    private final AlertDialog dialog;

    public interface Listener {

        void setDanmuLine(int line);
    }

    public static DanmuLineDialog create(FragmentActivity activity) {
        return new DanmuLineDialog(activity);
    }

    public DanmuLineDialog(FragmentActivity activity) {
        this.callback = (Listener) activity;
        this.binding = DialogDanmuLineBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private void initView() {
        binding.slider.setValueFrom(0);
        binding.slider.setValueTo(20);
        binding.slider.setStepSize(1);
        binding.slider.setValue(DanmakuSetting.getMaxScrollLines());
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> callback.setDanmuLine((int) value));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}