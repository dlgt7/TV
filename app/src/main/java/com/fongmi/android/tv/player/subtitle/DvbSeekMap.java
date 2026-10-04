package com.fongmi.android.tv.player.subtitle;

import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Indexes DVB acquisition pages, including earlier display/ancillary dependencies. */
final class DvbSeekMap implements SeekMap {
    static final int MAX_ENTRIES = 4096;
    private final Map<Integer, Track> tracks = new HashMap<>();
    private SeekMap delegate = new SeekMap.Unseekable(androidx.media3.common.C.TIME_UNSET);

    synchronized void delegate(SeekMap delegate) { this.delegate = delegate; }
    synchronized void register(int id, int composition, int ancillary) {
        Track current = tracks.get(id);
        if (current == null || current.composition != composition || current.ancillary != ancillary) {
            tracks.put(id, new Track(composition, ancillary));
        }
    }
    synchronized boolean hasDvb() { return !tracks.isEmpty(); }
    synchronized void reset() {
        for (Track track : tracks.values()) track.dependencies.clear();
    }
    synchronized void packet(int id, long timeUs, long position, byte[] data) {
        Track track = tracks.get(id);
        if (track == null) return;
        boolean acquisition = false;
        for (int at = 0; at + 6 <= data.length && data[at] == 0x0f;) {
            int type = data[at + 1] & 255;
            int page = unsignedShort(data, at + 2);
            int size = unsignedShort(data, at + 4);
            if (at + 6 + size > data.length) return;
            if (page == track.composition && type == 0x10 && size >= 2) {
                acquisition |= (data[at + 7] & 0x0c) != 0;
            }
            // Acquisition pages reset composition objects/CLUTs, but not the display
            // definition or ancillary objects. Include their latest definitions in preroll.
            if (page == track.composition && type == 0x14) track.dependencies.put(-1, position);
            if (page == track.ancillary && page != track.composition) {
                if (type == 0x12 && size >= 1) track.dependencies.put(data[at + 6] & 255, position);
                if (type == 0x13 && size >= 2) track.dependencies.put(256 + unsignedShort(data, at + 6), position);
            }
            at += 6 + size;
        }
        if (!acquisition) return;
        long start = position;
        for (long dependency : track.dependencies.values()) start = Math.min(start, dependency);
        track.pages.put(timeUs, start);
        if (track.pages.size() > MAX_ENTRIES) {
            // Thin the index instead of dropping its beginning. Coarser entries only
            // increase preroll; they must never advance the start past a dependency.
            boolean keep = true;
            var iterator = track.pages.entrySet().iterator();
            while (iterator.hasNext()) {
                iterator.next();
                if (!keep && iterator.hasNext()) iterator.remove();
                keep = !keep;
            }
        }
    }
    @Override public synchronized boolean isSeekable() { return delegate.isSeekable(); }
    @Override public synchronized long getDurationUs() { return delegate.getDurationUs(); }
    @Override public synchronized SeekPoints getSeekPoints(long timeUs) {
        SeekPoints original = delegate.getSeekPoints(timeUs);
        if (tracks.isEmpty() || !delegate.isSeekable()) return original;
        long position = original.first.position;
        long time = original.first.timeUs;
        for (Track track : tracks.values()) {
            Map.Entry<Long, Long> entry = track.pages.floorEntry(timeUs);
            // An unvisited interval is rebuilt from the latest known acquisition.
            // Before the first known acquisition the only safe starting point is zero.
            if (entry == null) return new SeekPoints(SeekPoint.START);
            position = Math.min(position, entry.getValue());
            time = Math.min(time, entry.getKey());
        }
        return new SeekPoints(new SeekPoint(time, position));
    }
    private static int unsignedShort(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }
    private static final class Track {
        final int composition, ancillary;
        final TreeMap<Long, Long> pages = new TreeMap<>();
        final Map<Integer, Long> dependencies = new HashMap<>();
        Track(int composition, int ancillary) { this.composition = composition; this.ancillary = ancillary; }
    }
}
