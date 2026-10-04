package com.fongmi.android.tv.player.subtitle;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Converts extractor-relative SSA timestamps to the renderer clock without stripping tags. */
public final class AssPacket {
    private static final String DEFAULT_FORMAT = "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text";
    private AssPacket() {}

    public static String header(List<byte[]> initialization) {
        if (initialization.size() < 2) return "";
        String header = new String(initialization.get(1), StandardCharsets.UTF_8);
        return header + "\n[Events]\n" + new String(initialization.get(0), StandardCharsets.UTF_8) + "\n";
    }

    public static String shift(String text, List<byte[]> initialization, long offsetUs) {
        String format = initialization.isEmpty() ? DEFAULT_FORMAT : new String(initialization.get(0), StandardCharsets.UTF_8);
        String[] fields = format.substring(format.indexOf(':') + 1).split(",");
        StringBuilder out = new StringBuilder(text.length() + 64);
        boolean events = !initialization.isEmpty();
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().equalsIgnoreCase("[Events]")) events = true;
            else if (line.startsWith("[") && !line.trim().equalsIgnoreCase("[Events]")) events = false;
            if (events && line.regionMatches(true, 0, "Format:", 0, 7)) fields = line.substring(7).split(",");
            if (line.startsWith("Dialogue:")) {
                String[] values = line.substring(9).trim().split(",", fields.length);
                if (values.length == fields.length) {
                    for (int i = 0; i < fields.length; i++) {
                        if (fields[i].trim().equalsIgnoreCase("Start") || fields[i].trim().equalsIgnoreCase("End")) {
                            long millis = parseTime(values[i]);
                            if (millis >= 0) values[i] = formatTime(Math.max(0, millis + offsetUs / 1000));
                        }
                    }
                    line = "Dialogue: " + String.join(",", values);
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    static long parseTime(String value) {
        try {
            String[] parts = value.trim().split("[:.]");
            if (parts.length != 4) return -1;
            return Long.parseLong(parts[0]) * 3_600_000 + Long.parseLong(parts[1]) * 60_000
                    + Long.parseLong(parts[2]) * 1000 + Long.parseLong((parts[3] + "000").substring(0, 3));
        } catch (NumberFormatException error) { return -1; }
    }

    private static String formatTime(long value) {
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", value / 3_600_000,
                value / 60_000 % 60, value / 1000 % 60, value / 10 % 100);
    }
}
