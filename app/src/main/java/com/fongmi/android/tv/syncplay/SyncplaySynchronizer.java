package com.fongmi.android.tv.syncplay;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Pure decisions used by both the Android bridge and local interoperability tests. */
public final class SyncplaySynchronizer {
    public enum Gate { READY, WAITING_FOR_ROOM, PLAYER_NOT_READY, DIFFERENT_MEDIA }
    public static final class Correction {
        public final Long seekMs;
        public final Boolean paused;
        public final float speed;
        Correction(Long seekMs, Boolean paused, float speed) { this.seekMs = seekMs; this.paused = paused; this.speed = speed; }
    }
    private boolean initial = true;

    public void reset() { initial = true; }

    public static boolean shouldSeedRoom(Gate gate, boolean seeded, String username, List<SyncplayProtocol.Member> members) {
        if (gate != Gate.READY || seeded) return false;
        for (SyncplayProtocol.Member member : members) if (!member.username.equals(username)) return false;
        return true;
    }

    public static Gate gate(boolean listed, boolean ready, String username, String filename, double duration,
                            List<SyncplayProtocol.Member> members, boolean confirmedSameMedia) {
        if (!ready || filename == null || filename.trim().isEmpty()) return Gate.PLAYER_NOT_READY;
        if (!listed) return Gate.WAITING_FOR_ROOM;
        for (SyncplayProtocol.Member member : members) {
            if (member.username.equals(username)) continue;
            if (member.filename.isEmpty() || member.duration <= 0) return Gate.WAITING_FOR_ROOM;
            if (!confirmedSameMedia && (!normalize(filename).equals(normalize(member.filename))
                    || duration > 0 && Math.abs(duration - member.duration) > 2.5))
                return Gate.DIFFERENT_MEDIA;
        }
        return Gate.READY;
    }

    public Correction correction(long localMs, boolean localPaused, long durationMs, float speed,
                                 SyncplayProtocol.RemoteState remote, String username, double queueDelay) {
        double seconds = remote.position + (remote.paused ? 0 : remote.forwardDelay + Math.max(0, queueDelay));
        long desired = Math.max(0, Math.round(seconds * 1000));
        if (durationMs > 0) desired = Math.min(desired, durationMs);
        long drift = desired - Math.max(0, localMs);
        boolean own = !remote.setBy.isEmpty() && remote.setBy.equals(username);
        Long seek = null;
        if (initial || !own && remote.seek || remote.paused && Math.abs(drift) > 250
                || !own && Math.abs(drift) >= 2500) seek = desired;
        initial = false;
        Boolean paused = localPaused == remote.paused ? null : remote.paused;
        float rate = 1f;
        if (seek == null && !remote.paused && !own) {
            if (drift > 350) rate = 1.03f;
            else if (drift < -350) rate = .97f;
            else if (Math.abs(drift) > 100 && speed >= .97f && speed <= 1.03f) rate = speed;
        }
        return new Correction(seek, paused, rate);
    }

    private static String normalize(String value) {
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
        return text.replaceFirst("\\.(?:mkv|mp4|avi|webm|m4v|flv)$", "").replaceAll("[\\s._-]+", "");
    }
}
