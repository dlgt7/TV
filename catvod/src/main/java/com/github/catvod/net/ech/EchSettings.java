package com.github.catvod.net.ech;

import com.github.catvod.utils.Prefers;

public final class EchSettings {

    private static final String KEY_ENABLED = "ech_enabled";

    private EchSettings() {
    }

    public static boolean isEnabled() {
        return Prefers.getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        Prefers.put(KEY_ENABLED, enabled);
    }
}
