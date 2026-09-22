package com.fongmi.android.tv.ui.dialog;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogMpvConfBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.player.mpv.MpvConfigFiles;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.QRCode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class MpvConfDialog extends BaseAlertDialog {

    private DialogMpvConfBinding binding;

    public static void show(FragmentActivity activity) {
        new MpvConfDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        setText(MpvConfigFiles.read());
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(4), 200, 0));
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
        binding.text.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateHint(s); }
            @Override public void afterTextChanged(Editable s) { }
        });
        updateHint(binding.text.getText());
    }

    private void setText(String text) {
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    private void updateHint(CharSequence text) {
        List<String> conflicts = MpvConfigFiles.findInterfaceManagedOptions(text);
        binding.hint.setText(conflicts.isEmpty()
                ? getString(com.fongmi.android.tv.R.string.player_mpv_conf_precedence)
                : getString(com.fongmi.android.tv.R.string.player_mpv_conf_conflict, TextUtils.join(", ", conflicts)));
    }

    private void onPositive(View view) {
        if (MpvConfigFiles.write(binding.text.getText().toString())) dismiss();
        else Notify.show(com.fongmi.android.tv.R.string.player_mpv_conf_save_failed);
    }

    private void onNegative(View view) {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        setText(event.text());
        binding.positive.performClick();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }
}
