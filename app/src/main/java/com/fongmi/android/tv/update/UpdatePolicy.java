package com.fongmi.android.tv.update;

import org.json.JSONObject;

import java.io.IOException;

/** An explicit, expiring exception to the default of never prompting on startup. */
public final class UpdatePolicy {

    private static final UpdatePolicy DISABLED = new UpdatePolicy(false, 0, 0, 0, 0);

    private final boolean enabled;
    private final int code;
    private final int minCode;
    private final int maxCode;
    private final long expiresAt;

    private UpdatePolicy(boolean enabled, int code, int minCode, int maxCode, long expiresAt) {
        this.enabled = enabled;
        this.code = code;
        this.minCode = minCode;
        this.maxCode = maxCode;
        this.expiresAt = expiresAt;
    }

    public static UpdatePolicy parse(String json) throws IOException {
        JSONObject root = UpdateManifest.parseObject(json);
        UpdateManifest.requireLong(root, "schema", 1, 1);
        if (!root.has("popup")) return DISABLED;
        JSONObject popup = UpdateManifest.requireObject(root, "popup");
        Object enabled = popup.opt("enabled");
        if (!(enabled instanceof Boolean)) throw new IOException("Invalid popup enabled flag");
        if (!((Boolean) enabled)) return DISABLED;
        int code = (int) UpdateManifest.requireLong(popup, "code", 1, Integer.MAX_VALUE);
        int minCode = (int) UpdateManifest.requireLong(popup, "minCode", 0, Integer.MAX_VALUE);
        int maxCode = (int) UpdateManifest.requireLong(popup, "maxCode", 0, Integer.MAX_VALUE);
        long expiresAt = UpdateManifest.requireLong(popup, "expiresAt", 1, Long.MAX_VALUE);
        if (minCode != 0 && maxCode != 0 && minCode > maxCode) throw new IOException("Invalid popup version range");
        return new UpdatePolicy(true, code, minCode, maxCode, expiresAt);
    }

    public boolean shouldPrompt(int installedCode, int availableCode, int lastPromptedCode, long nowEpochSeconds) {
        return enabled && code > installedCode && code == availableCode && lastPromptedCode != code &&
                expiresAt > nowEpochSeconds && (minCode == 0 || installedCode >= minCode) &&
                (maxCode == 0 || installedCode <= maxCode);
    }
}
