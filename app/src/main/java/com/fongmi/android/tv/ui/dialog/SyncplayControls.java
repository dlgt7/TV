package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.syncplay.SyncplayConfig;
import com.fongmi.android.tv.syncplay.SyncplaySession;
import com.fongmi.android.tv.syncplay.SyncplaySettings;

import java.util.function.Consumer;

/** Shared manual switch behavior. No persisted enabled flag and no saved password. */
public final class SyncplayControls {
    private SyncplayControls() { }

    public static boolean enabled(SyncplaySession.Status status) {
        return status.phase != SyncplaySession.Phase.OFFLINE;
    }

    public static String statusText(Context context, SyncplaySession.Status status) {
        return context.getString(switch (status.phase) {
            case OFFLINE -> switch (status.reason) {
                case "tls_unavailable" -> R.string.syncplay_tls_unavailable;
                case "tls_failed" -> R.string.syncplay_tls_failed;
                case "server_error" -> R.string.syncplay_server_error;
                case "no_video", "player_closed", "player_error" -> R.string.syncplay_no_video;
                case "speed_changed" -> R.string.syncplay_speed_changed;
                case "" -> R.string.syncplay_offline;
                default -> R.string.syncplay_disconnected;
            };
            case CONNECTING -> R.string.syncplay_connecting;
            case JOINED -> switch (status.gate) {
                case READY -> R.string.syncplay_synchronizing;
                case WAITING_FOR_ROOM -> R.string.syncplay_waiting;
                case PLAYER_NOT_READY -> R.string.syncplay_not_ready;
                case DIFFERENT_MEDIA -> R.string.syncplay_different_media;
            };
        });
    }

    /** Returns a configuration dialog when input is needed; otherwise returns null. */
    public static AlertDialog toggle(Context context) {
        SyncplaySession session = SyncplaySession.get();
        SyncplaySession.Status status = session.status();
        if (enabled(status)) { session.leave(); return null; }
        if (SyncplaySettings.needsPassword()) return SyncplayDialog.show(context, R.string.syncplay_password_required);
        if ("server_error".equals(status.reason)) return SyncplayDialog.show(context, R.string.syncplay_server_error);
        final SyncplayConfig config;
        try {
            config = new SyncplayConfig(SyncplaySettings.host(), SyncplaySettings.port(), SyncplaySettings.username(),
                    SyncplaySettings.room(), "", SyncplaySettings.tls());
        } catch (IllegalArgumentException invalid) {
            return SyncplayDialog.show(context, R.string.syncplay_invalid);
        }
        try {
            session.join(config);
        } catch (IllegalStateException unavailable) {
            return SyncplayDialog.show(context, R.string.syncplay_no_video);
        }
        new JoinWatch(context, session).start();
        return null;
    }

    /** Password-protected servers prompt for this connection's password after rejection. */
    private static final class JoinWatch implements Consumer<SyncplaySession.Status>, LifecycleEventObserver {
        private final Context context;
        private final SyncplaySession session;
        private final Lifecycle lifecycle;

        JoinWatch(Context context, SyncplaySession session) {
            this.context = context; this.session = session;
            lifecycle = context instanceof LifecycleOwner ? ((LifecycleOwner) context).getLifecycle() : null;
        }

        void start() {
            if (lifecycle != null && lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) return;
            if (lifecycle != null) lifecycle.addObserver(this);
            session.observe(this);
        }

        private void stop() {
            session.removeObserver(this);
            if (lifecycle != null) lifecycle.removeObserver(this);
        }

        @Override public void accept(SyncplaySession.Status status) {
            if (status.phase == SyncplaySession.Phase.CONNECTING) return;
            stop();
            boolean visible = lifecycle == null || lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED);
            boolean closing = context instanceof Activity && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed());
            if (visible && !closing && status.phase == SyncplaySession.Phase.OFFLINE && "server_error".equals(status.reason))
                SyncplayDialog.show(context, R.string.syncplay_server_error);
        }

        @Override public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (event == Lifecycle.Event.ON_DESTROY) stop();
        }
    }
}
