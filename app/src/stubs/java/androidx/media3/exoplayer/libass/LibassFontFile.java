package androidx.media3.exoplayer.libass;

import android.text.TextUtils;
import java.io.File;
import java.io.IOException;

public final class LibassFontFile {

    @androidx.annotation.Nullable
    public static String getFamilyName(File file) throws IOException {
        if (file == null || !file.exists()) return null;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return TextUtils.isEmpty(name) ? null : name;
    }
}