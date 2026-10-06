package com.github.catvod.utils;

import android.content.SharedPreferences;
import androidx.annotation.Keep;
import com.github.catvod.Init;
import com.github.catvod.utils.Util;
import com.github.catvod.utils.Json;

@Keep
public final class Local {
    private final String name;

    public Local(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) {
            throw new IllegalArgumentException("Local requires a site key");
        }
        this.name = "spider_local_" + Util.sha256(siteKey);
    }

    public String get(String key) {
        return this.getPrefs().getString(this.key(key), null);
    }

    public void set(String key, String value) {
        Json.strict(value);
        this.getPrefs().edit().putString(this.key(key), value).apply();
    }

    public void delete(String key) {
        this.getPrefs().edit().remove(this.key(key)).apply();
    }

    public void clear() {
        this.getPrefs().edit().clear().apply();
    }

    private SharedPreferences getPrefs() {
        return Init.context().getSharedPreferences(this.name, 0);
    }

    private String key(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Local requires a nonempty key");
        }
        return key;
    }
}
