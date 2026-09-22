package com.fongmi.android.tv.storage;

import android.text.TextUtils;

import java.net.IDN;

final class NetworkPathPolicy {

    private static final int MAX_HOST = 253;
    private static final int MAX_PATH = 4096;

    private NetworkPathPolicy() {
    }

    static boolean validHost(String host) {
        if (TextUtils.isEmpty(host) || host.length() > MAX_HOST || containsControl(host)) return false;
        String value = host.trim();
        if (!value.equals(host) || value.contains("://") || value.contains("/") || value.contains("\\")
                || value.contains("@") || value.contains("?") || value.contains("#")) return false;
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        if (value.contains(":")) return value.matches("[0-9a-fA-F:]+") && !value.equals(":");
        try {
            String ascii = IDN.toASCII(value);
            return !ascii.isEmpty() && ascii.length() <= MAX_HOST && !ascii.startsWith(".") && !ascii.endsWith(".")
                    && !ascii.contains("..") && ascii.matches("[A-Za-z0-9._-]+");
        } catch (Exception e) {
            return false;
        }
    }

    static String cleanRelative(String path) {
        if (path == null) return "";
        if (path.length() > MAX_PATH || containsControl(path) || path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid network path");
        }
        String value = path;
        while (value.startsWith("/")) value = value.substring(1);
        StringBuilder result = new StringBuilder();
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty()) continue;
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Path traversal is not allowed");
            if (result.length() > 0) result.append('/');
            result.append(segment);
        }
        return result.toString();
    }

    static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x1f || c == 0x7f) return true;
        }
        return false;
    }
}
