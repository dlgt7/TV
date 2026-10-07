package com.fongmi.android.tv.player.mpv;

import java.util.Locale;

/** Per-native-player fallback budget. A new file must not revive a failed GPU backend. */
final class MpvVideoOutputRecovery {
    private String openGlDriver;
    private volatile long generation;
    private boolean failed;
    private boolean terminal;

    static boolean isContextFailure(String prefix, String text) {
        if (prefix == null || text == null) return false;
        String source = prefix.toLowerCase(Locale.ROOT), message = text.toLowerCase(Locale.ROOT);
        return (source.equals("vo/gpu") || source.startsWith("vo/gpu-next"))
                && message.contains("failed initializing any suitable gpu context")
                || source.equals("cplayer") && (message.contains("error opening/initializing vo window")
                || message.contains("error opening/initializing the vo window"));
    }

    long generation() { return generation; }
    void beginPlayback() { generation++; failed = false; terminal = false; }
    boolean recordFailure(long observedGeneration) {
        if (terminal || generation != observedGeneration) return false;
        failed = true; return true;
    }
    boolean hasFailure() { return failed; }
    boolean isTerminal() { return terminal; }
    void clearFailure() { failed = false; }
    String openGlDriver() { return openGlDriver; }
    String driver(String configured) { return openGlDriver == null ? configured : openGlDriver; }

    /** Returns null when the last compatible backend has also failed. */
    String next(boolean configuredVulkan, String currentDriver) {
        failed = false;
        generation++;
        if (terminal || "gpu".equals(openGlDriver) || openGlDriver == null && !configuredVulkan && "gpu".equals(currentDriver)) {
            terminal = true; return null;
        }
        openGlDriver = openGlDriver == null && configuredVulkan && "gpu-next".equals(currentDriver)
                ? "gpu-next" : "gpu";
        return openGlDriver;
    }
}
