package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.sync.WebDavSyncManager;
import com.fongmi.android.tv.sync.WebDavSyncSettings;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Material form shared by TV and mobile; all HTTP and database merge work remains off the UI thread. */
public final class WebDavSyncDialog {
    private WebDavSyncDialog() { }
    public static AlertDialog show(Context context, Runnable onChanged) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        Context themed = builder.getContext(); WebDavSyncSettings.Options saved = WebDavSyncSettings.get();
        LinearLayout form = new LinearLayout(themed); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ResUtil.dp2px(24), ResUtil.dp2px(8), ResUtil.dp2px(24), ResUtil.dp2px(8));
        TextInputEditText url = field(themed, form, R.string.webdav_sync_url, saved.url, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextInputEditText user = field(themed, form, R.string.webdav_sync_username, saved.username, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        TextInputEditText password = field(themed, form, R.string.webdav_sync_password, saved.password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        MaterialCheckBox keeps = check(themed, form, R.string.webdav_sync_keeps, saved.keeps);
        MaterialCheckBox history = check(themed, form, R.string.webdav_sync_history, saved.history);
        MaterialCheckBox subscriptions = check(themed, form, R.string.webdav_sync_subscriptions, saved.subscriptions);
        MaterialCheckBox preferences = check(themed, form, R.string.webdav_sync_preferences, saved.preferences);
        MaterialCheckBox automatic = check(themed, form, R.string.webdav_sync_automatic, saved.automatic);
        TextView status = new TextView(themed); status.setText(R.string.webdav_sync_description); form.addView(status);
        LinearProgressIndicator progress = new LinearProgressIndicator(themed); progress.setIndeterminate(true); progress.setVisibility(View.GONE); form.addView(progress);
        ScrollView scroll = new ScrollView(themed); scroll.addView(form);
        AlertDialog dialog = builder.setTitle(R.string.webdav_sync_title).setView(scroll)
                .setPositiveButton(R.string.webdav_sync_now, null).setNeutralButton(R.string.webdav_sync_save, null)
                .setNegativeButton(R.string.dialog_negative, null).show();
        Runnable save = () -> {
            WebDavSyncSettings.Options options = new WebDavSyncSettings.Options(text(url), text(user), text(password), automatic.isChecked(),
                    keeps.isChecked(), history.isChecked(), subscriptions.isChecked(), preferences.isChecked());
            if (!options.fingerprint().equals(WebDavSyncSettings.get().fingerprint())) WebDavSyncManager.get().cancel(); WebDavSyncSettings.save(options); if (onChanged != null) onChanged.run();
        };
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            try { save.run(); dialog.dismiss(); }
            catch (RuntimeException error) { status.setText(R.string.webdav_sync_invalid_settings); }
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                save.run();
                if (!WebDavSyncSettings.get().configured()) { status.setText(R.string.webdav_sync_invalid_settings); return; }
            } catch (RuntimeException error) { status.setText(R.string.webdav_sync_invalid_settings); return; }
            progress.setVisibility(View.VISIBLE); status.setText(R.string.webdav_sync_running);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            WebDavSyncManager.get().sync(result -> {
                if (result.success && onChanged != null) onChanged.run();
                if (!dialog.isShowing()) return;
                progress.setVisibility(View.GONE); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                status.setText(result.success ? context.getString(R.string.webdav_sync_finished, result.updated, result.deleted, result.skipped) : context.getString(errorText(result.error)));
            });
        });
        return dialog;
    }
    private static int errorText(String code) {
        return switch (code) {
            case "HTTP_401", "HTTP_403" -> R.string.webdav_sync_auth_error;
            case "SERVER_NO_STRONG_ETAG" -> R.string.webdav_sync_etag_error;
            case "INVALID_REMOTE_DOCUMENT" -> R.string.webdav_sync_document_error;
            case "REMOTE_CHANGED_RETRY" -> R.string.webdav_sync_conflict_error;
            case "SYNC_BUSY" -> R.string.webdav_sync_busy;
            default -> R.string.webdav_sync_error;
        };
    }
    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private static TextInputEditText field(Context context, LinearLayout parent, int hint, String value, int type) {
        TextInputLayout layout = new TextInputLayout(context); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); layout.setHint(context.getString(hint));
        TextInputEditText input = new TextInputEditText(layout.getContext()); input.setSingleLine(true); input.setInputType(type); input.setText(value);
        layout.addView(input, new TextInputLayout.LayoutParams(-1, -2));
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = ResUtil.dp2px(8); parent.addView(layout, params); return input;
    }
    private static MaterialCheckBox check(Context context, LinearLayout parent, int label, boolean checked) {
        MaterialCheckBox box = new MaterialCheckBox(context); box.setText(label); box.setChecked(checked); parent.addView(box); return box;
    }
}
