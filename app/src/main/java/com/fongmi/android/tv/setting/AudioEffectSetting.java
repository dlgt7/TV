package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public final class AudioEffectSetting {
    private AudioEffectSetting() {}
    private static float clamp(float value, float min, float max) { return Float.isFinite(value) ? Math.clamp(value, min, max) : 0; }
    public static int preset() { return Math.clamp(Prefers.getInt("audio_effect_preset"), 0, 4); }
    public static void preset(int preset) { Prefers.put("audio_effect_preset", Math.clamp(preset, 0, 4)); }
    public static boolean enabled() { return preset() != 0; }
    public static boolean normalize() { return preset() == 2 || preset() == 4 && Prefers.getBoolean("audio_effect_normalize"); }
    public static void normalize(boolean enabled) { Prefers.put("audio_effect_normalize", enabled); preset(4); }
    public static float centerDb() { return preset() == 1 ? 6 : preset() == 4 ? clamp(Prefers.getFloat("audio_effect_center"), 0, 12) : 0; }
    public static void centerDb(float value) { Prefers.put("audio_effect_center", clamp(value, 0, 12)); preset(4); }
    public static float[] bands() {
        return switch (preset()) {
            case 1 -> new float[]{-3, -2, 2, 3, 0};
            case 2 -> new float[]{-3, 0, 0, 0, -2};
            case 3 -> new float[]{3, 1, 0, 1, 2};
            case 4 -> new float[]{band(0), band(1), band(2), band(3), band(4)};
            default -> new float[5];
        };
    }
    private static float band(int index) { return clamp(Prefers.getFloat("audio_effect_band_" + index), -12, 12); }
    public static void band(int index, float value) {
        if (index < 0 || index > 4) return;
        Prefers.put("audio_effect_band_" + index, clamp(value, -12, 12));
        preset(4);
    }
}
