package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.syncplay.SyncplayConfig;
import com.fongmi.android.tv.syncplay.SyncplaySession;
import com.fongmi.android.tv.syncplay.SyncplaySettings;
import com.fongmi.android.tv.syncplay.SyncplaySynchronizer;
import com.github.catvod.utils.Prefers;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.function.Consumer;

/** Uses standard Material input controls for both touch and remote navigation. */
public final class SyncplayDialog {
    private SyncplayDialog() { }

    public static AlertDialog show(Context context) {
        return show(context, 0);
    }

    public static AlertDialog show(Context context, int notice) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View form = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_syncplay, null);
        TextInputEditText host = form.findViewById(R.id.syncplay_host), port = form.findViewById(R.id.syncplay_port);
        TextInputEditText username = form.findViewById(R.id.syncplay_username), room = form.findViewById(R.id.syncplay_room);
        TextInputEditText password = form.findViewById(R.id.syncplay_password);
        MaterialCheckBox tls = form.findViewById(R.id.syncplay_tls);
        TextView status = form.findViewById(R.id.syncplay_status);
        View confirm = form.findViewById(R.id.syncplay_confirm_media);
        host.setText(SyncplaySettings.host()); port.setText(String.valueOf(SyncplaySettings.port()));
        clearPasswordOnEndpointChange(host, port, password);
        ServerSelection servers = new ServerSelection(form, host, port);
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
        if (notice != 0) {
            // Keep required-input notices visible even when the form needs to scroll.
            ((TextView) form.findViewById(R.id.syncplay_description)).setText(notice);
            status.setText(notice);
        }
        dialog.setOnDismissListener(ignored -> session.removeObserver(observer));
        confirm.setOnClickListener(view -> session.confirmSameMedia());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> session.leave());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                SyncplayConfig config = new SyncplayConfig(text(host), Integer.parseInt(text(port)), text(username), text(room), text(password), tls.isChecked());
                servers.saveCustomDraft();
                SyncplaySettings.save(config); session.join(config);
            } catch (IllegalArgumentException error) {
                status.setText("controlled_room".equals(error.getMessage()) ? R.string.syncplay_controlled_room : R.string.syncplay_invalid);
            } catch (IllegalStateException error) { status.setText(R.string.syncplay_no_video); }
        });
        return dialog;
    }

    private static void clearPasswordOnEndpointChange(TextInputEditText host, TextInputEditText port, TextInputEditText password) {
        String[] previous = {text(host).trim(), text(port).trim()};
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                String address = text(host).trim(), number = text(port).trim();
                if (!previous[0].equalsIgnoreCase(address) || !previous[1].equals(number)) password.setText("");
                previous[0] = address; previous[1] = number;
            }
        };
        host.addTextChangedListener(watcher); port.addTextChangedListener(watcher);
    }

    /** Selection changes only this form; drafts are persisted by a validated Join action. */
    private static final class ServerSelection {
        private static final String HOST = "syncplay.pl";
        private static final String CUSTOM_HOST = "syncplay_custom_host";
        private static final String CUSTOM_PORT = "syncplay_custom_port";
        private static final int CUSTOM = 5;
        private final TextInputEditText host, port;
        private final View hostField, portField;
        private final MaterialButton button;
        private final String[] choices = new String[6];
        private String customHost, customPort;
        private int selected = CUSTOM;

        ServerSelection(View form, TextInputEditText host, TextInputEditText port) {
            this.host = host; this.port = port;
            hostField = form.findViewById(R.id.syncplay_host_field);
            portField = form.findViewById(R.id.syncplay_port_field);
            button = form.findViewById(R.id.syncplay_server_choice);
            for (int index = 0; index < CUSTOM; index++) {
                choices[index] = HOST + ":" + (8995 + index);
                if (HOST.equalsIgnoreCase(text(host).trim()) && String.valueOf(8995 + index).equals(text(port))) selected = index;
            }
            choices[CUSTOM] = form.getContext().getString(R.string.syncplay_custom_server);
            customHost = selected == CUSTOM ? text(host) : Prefers.getString(CUSTOM_HOST, "");
            customPort = selected == CUSTOM ? text(port) : Prefers.getString(CUSTOM_PORT, String.valueOf(SyncplayConfig.DEFAULT_PORT));
            render();
            button.setOnClickListener(view -> new MaterialAlertDialogBuilder(button.getContext())
                    .setTitle(R.string.syncplay_sync_server)
                    .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                        if (selected == CUSTOM) { customHost = text(host); customPort = text(port); }
                        selected = which;
                        if (selected == CUSTOM) { host.setText(customHost); port.setText(customPort); }
                        else { host.setText(HOST); port.setText(String.valueOf(8995 + selected)); }
                        render(); dialog.dismiss();
                    })
                    .setNegativeButton(R.string.syncplay_close, null).show());
        }

        private void render() {
            button.setText(button.getContext().getString(R.string.syncplay_selected_server, choices[selected]));
            hostField.setVisibility(selected == CUSTOM ? View.VISIBLE : View.GONE);
            portField.setVisibility(selected == CUSTOM ? View.VISIBLE : View.GONE);
        }

        void saveCustomDraft() {
            if (selected == CUSTOM) { customHost = text(host); customPort = text(port); }
            Prefers.put(CUSTOM_HOST, customHost);
            Prefers.put(CUSTOM_PORT, customPort);
        }
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
