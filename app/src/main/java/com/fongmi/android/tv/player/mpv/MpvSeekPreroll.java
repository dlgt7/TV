package com.fongmi.android.tv.player.mpv;

import java.util.Locale;

/** Per-file HLS demux preroll; the requested/displayed seek time never changes. */
final class MpvSeekPreroll {
    // FFmpeg HLS can start decoding after the requested timestamp. Decode from an
    // earlier segment while mpv's exact seek still discards frames up to the target.
    static final double HLS_SECONDS = 5;
    private Double previous;
    private double applied;

    double apply(double current) {
        if (!Double.isFinite(current)) return current;
        double target = Math.max(current, HLS_SECONDS);
        if (Double.compare(current, target) == 0) return current;
        if (previous == null || Double.compare(current, applied) != 0) previous = current;
        applied = target;
        return target;
    }

    double restore(double current) {
        // A runtime user/config change wins over the temporary value we installed.
        double target = previous != null && Double.compare(current, applied) == 0 ? previous : current;
        previous = null;
        return target;
    }

    static boolean isHls(String mimeType, String path, String demuxerFormat) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (mime.equals("application/x-mpegurl") || mime.equals("application/vnd.apple.mpegurl")) return true;
        if (path != null && path.toLowerCase(Locale.ROOT).endsWith(".m3u8")) return true;
        if (demuxerFormat != null) for (String format : demuxerFormat.toLowerCase(Locale.ROOT).split(",")) {
            if (format.trim().equals("hls") || format.trim().equals("applehttp")) return true;
        }
        return false;
    }
}
