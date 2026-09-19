package androidx.media3.exoplayer.libass;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * 字体文件解析：从文件名推断字体家族名。
 */
public final class LibassFontFile {

    @Nullable
    public static String getFamilyName(File file) throws IOException {
        if (file == null || !file.exists()) return null;
        return getFamilyName(file.getName());
    }

    @Nullable
    public static String getFamilyName(@Nullable String fileName) {
        if (TextUtils.isEmpty(fileName)) return null;
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return TextUtils.isEmpty(name.trim()) ? null : name.trim();
    }

    private LibassFontFile() {
    }
}
