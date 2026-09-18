package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.databinding.DialogDanmuSizeBinding;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DanmuSizeDialog {

    private final DialogDanmuSizeBinding binding;
    private final Listener callback;
    private final AlertDialog dialog;

    public interface Listener {

        void setDanmuSize(float size);
    }

    public static DanmuSizeDialog create(FragmentActivity activity) {
        return new DanmuSizeDialog(activity);
    }

    public DanmuSizeDialog(FragmentActivity activity) {
        this.callback = (Listener) activity;
        this.binding = DialogDanmuSizeBinding.inflate(LayoutInflater.from(activity));
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
        binding.slider.setValueFrom(0.5f);
        binding.slider.setValueTo(3.0f);
        binding.slider.setStepSize(0.1f);
        binding.slider.setValue(DanmakuSetting.getTextScale());
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> callback.setDanmuSize((float) (Math.round(value * 100.0) / 100.0)));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}