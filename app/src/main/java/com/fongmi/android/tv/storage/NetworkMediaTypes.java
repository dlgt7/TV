package com.fongmi.android.tv.storage;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** File-type gate for network browse: only hand playable media to VideoActivity. */
public final class NetworkMediaTypes {

    private static final Set<String> VIDEO = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mts", "flv",
            "wmv", "mpg", "mpeg", "3gp", "ogv", "vob", "f4v", "asf", "rm", "rmvb", "divx"
    ));

    private static final Set<String> AUDIO = new HashSet<>(Arrays.asList(
            "mp3", "flac", "aac", "m4a", "ogg", "oga", "wav", "wma", "opus", "ape", "ac3", "dts", "mka"
    ));

    private static final Set<String> STREAM = new HashSet<>(Arrays.asList(
            "m3u8", "m3u", "mpd", "strm", "pls"
    ));

    private static final Set<String> SUBTITLE = new HashSet<>(Arrays.asList(
            "srt", "ass", "ssa", "vtt", "sub"
    ));

    private NetworkMediaTypes() {
    }

    public static String extensionOf(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    public static boolean isPlayable(String name) {
        String ext = extensionOf(name);
        return VIDEO.contains(ext) || AUDIO.contains(ext) || STREAM.contains(ext);
    }

    public static boolean isSubtitle(String name) {
        return SUBTITLE.contains(extensionOf(name));
    }

    /** Human-readable list for the unsupported-file toast. */
    public static String supportedLabel() {
        return "视频/音频/流媒体，如 mp4 mkv ts m3u8 mp3 flac";
    }
}
