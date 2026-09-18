package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingDanmakuBinding;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.DanmakuApiDialog;
import com.fongmi.android.tv.ui.dialog.DanmuAlphaDialog;
import com.fongmi.android.tv.ui.dialog.DanmuLineDialog;
import com.fongmi.android.tv.ui.dialog.DanmuSizeDialog;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingDanmakuActivity extends BaseActivity implements DanmakuListener, DanmuSizeDialog.Listener, DanmuAlphaDialog.Listener, DanmuLineDialog.Listener {

    private ActivitySettingDanmakuBinding mBinding;
    private String[] danmuSpeed;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDanmakuActivity.class));
    }

    private String getApiStatus() {
        return getString(TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl()) ? R.string.none : R.string.yes);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDanmakuBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.danmakuLoad.requestFocus();
        mBinding.danmakuApiText.setText(getApiStatus());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
        mBinding.danmuSizeText.setText(String.valueOf(DanmakuSetting.getTextScale()));
        mBinding.danmuAlphaText.setText(String.valueOf((int) (DanmakuSetting.getTransparency() * 100)));
        mBinding.danmuLineText.setText(String.valueOf(DanmakuSetting.getMaxScrollLines()));
        mBinding.danmuSpeedText.setText((danmuSpeed = ResUtil.getStringArray(R.array.select_danmu_speed))[Setting.getDanmuSpeed()]);
        updateApiVisibility();
    }

    @Override
    protected void initEvent() {
        mBinding.danmakuApi.setOnClickListener(this::onDanmakuApi);
        mBinding.danmakuAuto.setOnClickListener(this::setDanmakuAuto);
        mBinding.danmakuLoad.setOnClickListener(this::setDanmakuLoad);
        mBinding.danmakuSpider.setOnClickListener(this::setDanmakuSpider);
        mBinding.danmuSize.setOnClickListener(this::onDanmuSize);
        mBinding.danmuAlpha.setOnClickListener(this::onDanmuAlpha);
        mBinding.danmuLine.setOnClickListener(this::onDanmuLine);
        mBinding.danmuSpeed.setOnClickListener(this::setDanmuSpeed);
    }

    private void setDanmakuLoad(View view) {
        DanmakuSetting.putLoad(!DanmakuSetting.isLoad());
        mBinding.danmakuLoadText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        updateApiVisibility();
    }

    private void updateApiVisibility() {
        boolean load = DanmakuSetting.isLoad();
        mBinding.danmakuApi.setVisibility(load ? View.VISIBLE : View.GONE);
        updateAutoVisibility();
    }

    private void updateAutoVisibility() {
        boolean show = DanmakuSetting.isLoad() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
        mBinding.danmakuAuto.setVisibility(show ? View.VISIBLE : View.GONE);
        updateSpiderVisibility();
    }

    private void updateSpiderVisibility() {
        boolean show = DanmakuSetting.isLoad() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl()) && DanmakuSetting.isAuto();
        mBinding.danmakuSpider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onDanmakuApi(View view) {
        DanmakuApiDialog.show(this);
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        mBinding.danmakuApiText.setText(getApiStatus());
        updateAutoVisibility();
    }

    private void setDanmakuAuto(View view) {
        DanmakuSetting.putAuto(!DanmakuSetting.isAuto());
        mBinding.danmakuAutoText.setText(Setting.getSwitch(DanmakuSetting.isAuto()));
        updateSpiderVisibility();
    }

    private void setDanmakuSpider(View view) {
        DanmakuSetting.putSpiderFirst(!DanmakuSetting.isSpiderFirst());
        mBinding.danmakuSpiderText.setText(Setting.getSwitch(DanmakuSetting.isSpiderFirst()));
    }

    private void onDanmuSize(View view) {
        DanmuSizeDialog.create(this).show();
    }

    @Override
    public void setDanmuSize(float size) {
        DanmakuSetting.putTextScale(size);
        mBinding.danmuSizeText.setText(String.valueOf(size));
    }

    private void onDanmuAlpha(View view) {
        DanmuAlphaDialog.create(this).show();
    }

    @Override
    public void setDanmuAlpha(int alpha) {
        DanmakuSetting.putTransparency(alpha / 100.0f);
        mBinding.danmuAlphaText.setText(String.valueOf(alpha));
    }

    private void onDanmuLine(View view) {
        DanmuLineDialog.create(this).show();
    }

    @Override
    public void setDanmuLine(int line) {
        DanmakuSetting.putMaxScrollLines(line);
        mBinding.danmuLineText.setText(String.valueOf(line));
    }

    private void setDanmuSpeed(View view) {
        int index = Setting.getDanmuSpeed();
        Setting.putDanmuSpeed(index = index == danmuSpeed.length - 1 ? 0 : ++index);
        mBinding.danmuSpeedText.setText(danmuSpeed[index]);
    }
}
