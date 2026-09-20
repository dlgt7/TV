package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.github.catvod.utils.Prefers;

public class SubtitleSetting {

    public static String getSearchToken() {
        return Prefers.getString("subtitle_search_token", "");
    }

    public static String getEffectiveToken() {
        String userToken = getSearchToken();
        if (!TextUtils.isEmpty(userToken)) return userToken;
        try {
            return VodConfig.get().getConfig().getAssrt();
        } catch (Exception e) {
            return "";
        }
    }

    public static void putSearchToken(String token) {
        Prefers.put("subtitle_search_token", token == null ? "" : token.trim());
    }

    /** Optional custom font path for libass / MPV subtitle rendering. */
    public static String getFontPath() {
        return Prefers.getString("subtitle_font_path", "");
    }

    public static void putFontPath(String path) {
        Prefers.put("subtitle_font_path", path == null ? "" : path);
    }

    public static boolean hasCustomFont() {
        return !TextUtils.isEmpty(getFontPath());
    }
}
