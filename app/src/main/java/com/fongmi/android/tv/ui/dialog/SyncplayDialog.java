package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.syncplay.SyncplayConfig;
import com.fongmi.android.tv.syncplay.SyncplaySession;
import com.fongmi.android.tv.syncplay.SyncplaySettings;
import com.fongmi.android.tv.syncplay.SyncplaySynchronizer;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.function.Consumer;

/** Uses standard Material input controls for both touch and remote navigation. */
public final class SyncplayDialog {
    private SyncplayDialog() { }

    public static AlertDialog show(Context context) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View form = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_syncplay, null);
        TextInputEditText host = form.findViewById(R.id.syncplay_host), port = form.findViewById(R.id.syncplay_port);
        TextInputEditText username = form.findViewById(R.id.syncplay_username), room = form.findViewById(R.id.syncplay_room);
        TextInputEditText password = form.findViewById(R.id.syncplay_password);
        MaterialCheckBox tls = form.findViewById(R.id.syncplay_tls);
        TextView status = form.findViewById(R.id.syncplay_status);
        View confirm = form.findViewById(R.id.syncplay_confirm_media);
        host.setText(SyncplaySettings.host()); port.setText(String.valueOf(SyncplaySettings.port()));
        username.setText(SyncplaySettings.username()); room.setText(SyncplaySettings.room()); tls.setChecked(SyncplaySettings.tls());
        SyncplaySession session = SyncplaySession.get();
        AlertDialog dialog = builder.setTitle(R.string.setting_syncplay).setView(form)
                .setPositiveButton(R.string.syncplay_join, null).setNeutralButton(R.string.syncplay_leave, null)
                .setNegativeButton(R.string.syncplay_close, null).show();
        Consumer<SyncplaySession.Status> observer = value -> {
            status.setText(describe(context, value));
            confirm.setVisibility(value.phase == SyncplaySession.Phase.JOINED
                    && value.gate == SyncplaySynchronizer.Gate.DIFFERENT_MEDIA ? View.VISIBLE : View.GONE);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(value.phase != SyncplaySession.Phase.OFFLINE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(value.phase != SyncplaySession.Phase.CONNECTING);
        };
        session.observe(observer);
        dialog.setOnDismissListener(ignored -> session.removeObserver(observer));
        confirm.setOnClickListener(view -> session.confirmSameMedia());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> session.leave());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                SyncplayConfig config = new SyncplayConfig(text(host), Integer.parseInt(text(port)), text(username), text(room), text(password), tls.isChecked());
                SyncplaySettings.save(config); session.join(config);
            } catch (IllegalArgumentException error) {
                status.setText("controlled_room".equals(error.getMessage()) ? R.string.syncplay_controlled_room : R.string.syncplay_invalid);
            } catch (IllegalStateException error) { status.setText(R.string.syncplay_no_video); }
        });
        return dialog;
    }

    private static String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private static String describe(Context context, SyncplaySession.Status status) {
        if (status.phase == SyncplaySession.Phase.OFFLINE) {
            int message = switch (status.reason) {
                case "tls_unavailable" -> R.string.syncplay_tls_unavailable;
                case "tls_failed" -> R.string.syncplay_tls_failed;
                case "speed_changed" -> R.string.syncplay_speed_changed;
                case "no_video", "player_closed", "player_error" -> R.string.syncplay_no_video;
                case "server_error" -> R.string.syncplay_server_error;
                case "" -> R.string.syncplay_offline;
                default -> R.string.syncplay_disconnected;
            };
            return context.getString(message);
        }
        if (status.phase == SyncplaySession.Phase.CONNECTING) return context.getString(R.string.syncplay_connecting);
        int gate = switch (status.gate) {
            case READY -> R.string.syncplay_synchronizing;
            case WAITING_FOR_ROOM -> R.string.syncplay_waiting;
            case PLAYER_NOT_READY -> R.string.syncplay_not_ready;
            case DIFFERENT_MEDIA -> R.string.syncplay_different_media;
        };
        return context.getString(R.string.syncplay_connected, status.room, status.members)
                + "\n" + context.getString(gate) + " · " + context.getString(status.encrypted ? R.string.syncplay_encrypted : R.string.syncplay_plain);
    }
}
