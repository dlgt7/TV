package com.fongmi.android.tv.utils;

public class Github {

    public static final String OWNER = "zyqfork";
    public static final String REPO = "TV";
    public static final String API_LATEST = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    public static final String DOWNLOAD = "https://github.com/" + OWNER + "/" + REPO + "/releases/latest/download";

    public static String getJson() {
        return API_LATEST;
    }

    public static String getApk(String name) {
        return DOWNLOAD + "/" + name + ".apk";
    }

    /**
     * Map a release tag to the historical VERSION_CODE scheme (5.5.6 -> 556).
     * Accepts v5.5.7, 5.5.7, or v5.5.7-source.1 (suffix ignored).
     */
    public static int parseCode(String tag) {
        if (tag == null || tag.isEmpty()) return 0;
        String text = tag.trim();
        if (text.startsWith("v") || text.startsWith("V")) text = text.substring(1);
        text = text.split("-", 2)[0].split("\\+", 2)[0];
        String[] parts = text.split("\\.");
        try {
            int major = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 100 + minor * 10 + patch;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
