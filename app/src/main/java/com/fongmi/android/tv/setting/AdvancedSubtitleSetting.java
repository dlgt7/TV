package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public final class AdvancedSubtitleSetting {
    private AdvancedSubtitleSetting() {}
    public static boolean ass() { return Prefers.getBoolean("subtitle_libass", true); }
    public static void ass(boolean enabled) { Prefers.put("subtitle_libass", enabled); }
}
