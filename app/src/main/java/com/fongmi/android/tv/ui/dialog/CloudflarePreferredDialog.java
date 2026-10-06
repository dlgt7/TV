package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.CloudflarePreferredSettings;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class CloudflarePreferredDialog {

    private CloudflarePreferredDialog() {
    }

    public static AlertDialog show(Context context, Runnable onSaved) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        TextInputLayout field = new TextInputLayout(builder.getContext());
        TextInputEditText input = new TextInputEditText(field.getContext());
        FrameLayout container = new FrameLayout(builder.getContext());
        String domain = CloudflarePreferredSettings.getDomain();
        field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        field.setHint(context.getString(R.string.setting_cf_preferred_hint));
        input.setSingleLine(true);
        input.setMinHeight(ResUtil.dp2px(56));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(domain);
        input.setSelection(domain.length());
        field.addView(input, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        container.setPadding(ResUtil.dp2px(24), ResUtil.dp2px(8), ResUtil.dp2px(24), 0);
        container.addView(field, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = builder.setTitle(R.string.setting_cf_preferred)
                .setMessage(R.string.setting_cf_preferred_description)
                .setView(container)
                .setPositiveButton(R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                CloudflarePreferredSettings.setDomain(input.getText() == null ? "" : input.getText().toString());
            } catch (IllegalArgumentException e) {
                field.setError(context.getString(R.string.setting_cf_preferred_invalid));
                input.requestFocus();
                return;
            }
            onSaved.run();
            dialog.dismiss();
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
        return dialog;
    }
}
