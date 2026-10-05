package com.fongmi.android.tv.player.subtitle;

import androidx.media3.common.C;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import java.util.Map;
import java.util.TreeMap;

/** Seek only to PGS acquisition/epoch boundaries, where palette/object state can be rebuilt. */
final class SupSeekMap implements SeekMap {
    private final TreeMap<Long, Long> epochs = new TreeMap<>();
    private long durationUs = C.TIME_UNSET;
    synchronized void add(long timeUs, long position) {
        epochs.put(timeUs, position);
        if (epochs.size() > 64_000) epochs.pollFirstEntry();
    }
    synchronized void finish(long timeUs) { durationUs = timeUs; }
    @Override public boolean isSeekable() { return true; }
    @Override public synchronized long getDurationUs() { return durationUs; }
    @Override public synchronized SeekPoints getSeekPoints(long timeUs) {
        Map.Entry<Long, Long> entry = epochs.floorEntry(timeUs);
        return new SeekPoints(entry == null ? SeekPoint.START : new SeekPoint(entry.getKey(), entry.getValue()));
    }
}
