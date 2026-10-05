package com.fongmi.android.tv.player.subtitle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded replay window for overlapping Matroska events discarded by SampleQueue. */
final class AssHistory {
    record Packet(long sequence, long timeUs, String text) {}
    private record Key(String id, long timeUs, String text) {}
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PACKETS = 32_000;
    private final Map<String, ArrayDeque<Packet>> tracks = new HashMap<>();
    private final LinkedHashMap<Key, Packet> retained = new LinkedHashMap<>();
    private final int maxBytes;
    private final int maxPackets;
    private int bytes;
    private long nextSequence;

    AssHistory() { this(MAX_BYTES, MAX_PACKETS); }
    AssHistory(int maxBytes, int maxPackets) { this.maxBytes = maxBytes; this.maxPackets = maxPackets; }
    synchronized void register(String id) { tracks.computeIfAbsent(id, ignored -> new ArrayDeque<>()); }
    synchronized boolean contains(String id) { return tracks.containsKey(id); }
    synchronized void add(String id, long timeUs, String text) {
        register(id);
        Key key = new Key(id, timeUs, text);
        if (retained.containsKey(key) || text.length() * 2L > maxBytes) return;
        Packet packet = new Packet(nextSequence++, timeUs, text);
        retained.put(key, packet);
        tracks.get(id).addLast(packet);
        bytes += text.length() * 2;
        // Evict oldest packets instead of stopping subtitles once the limit is reached.
        while (bytes > maxBytes || retained.size() > maxPackets) {
            Key oldest = retained.keySet().iterator().next();
            retained.remove(oldest);
            tracks.get(oldest.id()).removeFirst();
            bytes -= oldest.text().length() * 2;
        }
    }
    synchronized List<Packet> since(String id, long sequence) {
        ArrayDeque<Packet> all = tracks.get(id);
        if (all == null || all.isEmpty() || all.getLast().sequence() < sequence) return List.of();
        ArrayList<Packet> result = new ArrayList<>();
        for (Packet packet : all) if (packet.sequence() >= sequence) result.add(packet);
        return List.copyOf(result);
    }
}
