package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.databinding.DialogDanmuAlphaBinding;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DanmuAlphaDialog {

    private final DialogDanmuAlphaBinding binding;
    private final Listener callback;
    private final AlertDialog dialog;

    public interface Listener {

        void setDanmuAlpha(int alpha);
    }

    public static DanmuAlphaDialog create(FragmentActivity activity) {
        return new DanmuAlphaDialog(activity);
    }

    public DanmuAlphaDialog(FragmentActivity activity) {
        this.callback = (Listener) activity;
        this.binding = DialogDanmuAlphaBinding.inflate(LayoutInflater.from(activity));
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
        binding.slider.setValueFrom(10);
        binding.slider.setValueTo(100);
        binding.slider.setStepSize(1);
        binding.slider.setValue((int) (DanmakuSetting.getTransparency() * 100));
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> callback.setDanmuAlpha((int) value));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}