package com.fongmi.android.tv.syncplay;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;

import com.fongmi.android.tv.player.PlayerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread bridge shared by Exo and MPV's Media3 Player implementations. */
public final class SyncplaySession {
    public enum Phase { OFFLINE, CONNECTING, JOINED }
    public static final class Status {
        public final Phase phase;
        public final SyncplaySynchronizer.Gate gate;
        public final String username, room, filename, reason;
        public final int members;
        public final boolean encrypted;
        Status(Phase phase, SyncplaySynchronizer.Gate gate, String username, String room, String filename,
               String reason, int members, boolean encrypted) {
            this.phase = phase; this.gate = gate; this.username = username; this.room = room;
            this.filename = filename; this.reason = reason; this.members = members; this.encrypted = encrypted;
        }
    }
    private static final SyncplaySession INSTANCE = new SyncplaySession();
    public static SyncplaySession get() { return INSTANCE; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Consumer<Status>> observers = new ArrayList<>();
    private final SyncplaySynchronizer synchronizer = new SyncplaySynchronizer();
    private final SyncplayEchoGuard echoes = new SyncplayEchoGuard();
    private final Runnable tick = this::poll;
    private Object owner;
    private Supplier<PlayerManager> provider;
    private Player player;
    private SyncplayClient client;
    private Phase phase = Phase.OFFLINE;
    private SyncplaySynchronizer.Gate gate = SyncplaySynchronizer.Gate.WAITING_FOR_ROOM;
    private List<SyncplayProtocol.Member> members = Collections.emptyList();
    private String username = "", room = "", filename = "", fileIdentity = "", reason = "";
    private boolean listed, encrypted, sameMediaConfirmed, seededAlone;
    private float originalSpeed = 1;
    private long generation;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            boolean paused = !playWhenReady;
            if (echoes.consumePause(paused, SystemClock.elapsedRealtime())) return;
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) sendLocal(false);
        }
        @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK && reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) return;
            if (echoes.consumeSeek(reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT, SystemClock.elapsedRealtime())) return;
            sendLocal(true);
        }
        @Override public void onPlaybackParametersChanged(PlaybackParameters parameters) {
            if (echoes.consumeSpeed(parameters.speed, SystemClock.elapsedRealtime())) return;
            if (phase != Phase.OFFLINE && Math.abs(parameters.speed - 1) > .001f) disconnect("speed_changed", false);
        }
        @Override public void onMediaItemTransition(MediaItem item, int reason) { updateFile(); }
        @Override public void onPlaybackStateChanged(int state) { updateFile(); }
    };

    private SyncplaySession() { }
    private static void requireMain() { if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Syncplay playback must run on the main thread"); }

    public void attach(Object owner, Supplier<PlayerManager> provider) {
        requireMain();
        if (this.owner != null && this.owner != owner) disconnect("player_changed", true);
        this.owner = owner; this.provider = provider; bindPlayer();
    }

    public void detach(Object owner) {
        requireMain(); if (this.owner != owner) return;
        disconnect("player_closed", true); unbindPlayer(); this.owner = null; provider = null;
    }

    public void join(SyncplayConfig config) {
        requireMain(); bindPlayer();
        if (player == null || player.getCurrentMediaItem() == null || player.isCurrentMediaItemLive()) throw new IllegalStateException("no_video");
        disconnect("", true);
        originalSpeed = player.getPlaybackParameters().speed;
        phase = Phase.CONNECTING; username = config.username; room = config.room; reason = "";
        listed = false; members = Collections.emptyList(); sameMediaConfirmed = false; seededAlone = false; synchronizer.reset();
        generation++; long session = generation;
        client = new SyncplayClient(new SyncplayClient.Listener() {
            @Override public void onTransport(boolean secure) { dispatch(session, () -> { encrypted = secure; notifyObservers(); }); }
            @Override public void onHello(String name, String selectedRoom) {
                dispatch(session, () -> { username = name; room = selectedRoom; phase = Phase.JOINED; updateFile(); notifyObservers(); });
            }
            @Override public void onState(SyncplayProtocol.RemoteState state) {
                long received = SystemClock.elapsedRealtime();
                dispatch(session, () -> apply(state, received));
            }
            @Override public void onMembers(List<SyncplayProtocol.Member> users, boolean complete) {
                dispatch(session, () -> {
                    if (!samePeerMedia(members, users)) sameMediaConfirmed = false;
                    members = users; listed = complete; updateGate(); notifyObservers();
                });
            }
            @Override public void onError(String message) { dispatch(session, () -> { reason = "server_error"; notifyObservers(); }); }
            @Override public void onDisconnected(String error) { dispatch(session, () -> disconnect(error, true)); }
        });
        setSpeed(1); fileIdentity = ""; updateFile(); client.connect(config);
        main.removeCallbacks(tick); main.post(tick); notifyObservers();
    }

    public void leave() { requireMain(); disconnect("", true); }

    public void confirmSameMedia() {
        requireMain(); sameMediaConfirmed = true; synchronizer.reset(); updateGate(); notifyObservers();
    }

    private void disconnect(String reason, boolean restoreSpeed) {
        requireMain(); generation++; main.removeCallbacks(tick);
        SyncplayClient old = client; client = null; if (old != null) old.close();
        Phase previous = phase; phase = Phase.OFFLINE; this.reason = reason;
        if (restoreSpeed && previous != Phase.OFFLINE && player != null) try { setSpeed(originalSpeed); } catch (RuntimeException ignored) { }
        listed = false; sameMediaConfirmed = false; members = Collections.emptyList(); synchronizer.reset();
        echoes.reset();
        notifyObservers();
    }

    private void dispatch(long session, Runnable task) {
        main.post(() -> {
            if (generation != session || phase == Phase.OFFLINE) return;
            try { task.run(); } catch (RuntimeException error) { disconnect("player_error", false); }
        });
    }

    private void bindPlayer() {
        Player next = null;
        try { PlayerManager manager = provider == null ? null : provider.get(); if (manager != null && !manager.isReleased()) next = manager.getPlayer(); }
        catch (RuntimeException ignored) { }
        if (next == player) return;
        unbindPlayer(); player = next;
        if (player != null) {
            player.addListener(playerListener); fileIdentity = ""; synchronizer.reset();
            if (phase != Phase.OFFLINE) setSpeed(1);
            updateFile();
        }
    }

    private void unbindPlayer() {
        if (player != null) try { player.removeListener(playerListener); } catch (RuntimeException ignored) { }
        player = null; echoes.reset();
    }

    private void poll() {
        if (phase == Phase.OFFLINE) return;
        try {
            bindPlayer();
            if (player == null) { disconnect("player_closed", false); return; }
            updateFile(); updateGate(); main.postDelayed(tick, 250);
        } catch (RuntimeException error) { disconnect("player_error", false); }
    }

    private boolean ready() {
        return player != null && player.getPlaybackState() == Player.STATE_READY && !player.isCurrentMediaItemLive()
                && player.isCurrentMediaItemSeekable() && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                && player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
                && player.getDuration() > 0 && player.getDuration() != C.TIME_UNSET;
    }

    private void updateFile() {
        if (player == null || player.getCurrentMediaItem() == null) { updateGate(); return; }
        MediaItem item = player.getCurrentMediaItem(); MediaMetadata metadata = item.mediaMetadata;
        String title = metadata.title == null ? "" : metadata.title.toString();
        String episode = metadata.artist == null ? "" : metadata.artist.toString();
        String name = (title + (episode.isEmpty() || episode.equals(title) ? "" : " " + episode)).trim()
                .replaceAll("[\\p{Cntrl}]+", " ");
        if (name.length() > 250) name = name.substring(0, 250);
        long duration = player.getDuration();
        String identity = item.mediaId + "|" + name + "|" + (duration > 0 ? duration / 1000 : 0);
        if (!identity.equals(fileIdentity)) {
            filename = name; fileIdentity = identity;
            sameMediaConfirmed = false; seededAlone = false; synchronizer.reset(); echoes.reset();
            if (client != null && !filename.isEmpty() && duration > 0) client.setFile(filename, duration / 1000.0);
            updateGate(); notifyObservers();
        }
    }

    private void updateGate() {
        SyncplaySynchronizer.Gate next = SyncplaySynchronizer.gate(listed, ready(), username, filename,
                player == null ? 0 : Math.max(0, player.getDuration() / 1000.0), members, sameMediaConfirmed);
        if (next != gate) {
            gate = next; synchronizer.reset();
            if (phase != Phase.OFFLINE && gate != SyncplaySynchronizer.Gate.READY) setSpeed(1);
            notifyObservers();
        }
    }

    private boolean samePeerMedia(List<SyncplayProtocol.Member> before, List<SyncplayProtocol.Member> after) {
        if (before.size() != after.size()) return false;
        for (SyncplayProtocol.Member old : before) {
            if (old.username.equals(username)) continue;
            boolean found = false;
            for (SyncplayProtocol.Member next : after) {
                if (old.username.equals(next.username) && Objects.equals(old.filename, next.filename)
                        && Double.compare(old.duration, next.duration) == 0) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private void apply(SyncplayProtocol.RemoteState state, long received) {
        bindPlayer(); updateGate();
        if (phase != Phase.JOINED || gate != SyncplaySynchronizer.Gate.READY || player == null) return;
        if (SyncplaySynchronizer.shouldSeedRoom(gate, seededAlone, username, members)) {
            seededAlone = true;
            client.localChange(Math.max(0, player.getCurrentPosition()) / 1000.0, !player.getPlayWhenReady(), true);
            return;
        }
        SyncplaySynchronizer.Correction correction = synchronizer.correction(player.getCurrentPosition(), !player.getPlayWhenReady(),
                player.getDuration(), player.getPlaybackParameters().speed, state, username,
                (SystemClock.elapsedRealtime() - received) / 1000.0);
        long now = SystemClock.elapsedRealtime();
        if (correction.seekMs != null) {
            echoes.beginSeek(now);
            try { player.seekTo(correction.seekMs); } finally { echoes.endSeek(); }
        }
        if (correction.paused != null) { echoes.expectPause(correction.paused, now); player.setPlayWhenReady(!correction.paused); }
        setSpeed(correction.speed);
    }

    private void sendLocal(boolean seek) {
        if (phase != Phase.JOINED || client == null || player == null) return;
        updateGate();
        // Exo enters BUFFERING before dispatching a user's SEEK. The user intent
        // is valid while the known, matching video seeks; READY is required only
        // when applying a remote state, not when publishing this explicit action.
        boolean available = player.getPlaybackState() != Player.STATE_IDLE && !player.isCurrentMediaItemLive()
                && player.isCurrentMediaItemSeekable() && player.getDuration() > 0;
        if (SyncplaySynchronizer.gate(listed, available, username, filename, player.getDuration() / 1000.0,
                members, sameMediaConfirmed) != SyncplaySynchronizer.Gate.READY) return;
        client.localChange(Math.max(0, player.getCurrentPosition()) / 1000.0, !player.getPlayWhenReady(), seek);
    }

    private void setSpeed(float speed) {
        if (player == null || !player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)
                || Math.abs(player.getPlaybackParameters().speed - speed) < .001f) return;
        echoes.expectSpeed(speed, SystemClock.elapsedRealtime());
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
    }

    public Status status() { requireMain(); return new Status(phase, gate, username, room, filename, reason, members.size(), encrypted); }
    public void observe(Consumer<Status> observer) { requireMain(); observers.add(observer); observer.accept(status()); }
    public void removeObserver(Consumer<Status> observer) { requireMain(); observers.remove(observer); }
    private void notifyObservers() { Status value = status(); for (Consumer<Status> observer : new ArrayList<>(observers)) observer.accept(value); }
}
