package com.fongmi.android.tv.player.subtitle;

import android.graphics.Typeface;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * External font files for MPV/libass subtitle rendering.
 * Fonts live under Path.font() and are referenced from fonts.conf.
 */
public final class ExternalFont {

    private static final String TEMP_FILE_PREFIX = ".external-font-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final String[] EXTENSIONS = {".ttf", ".otf", ".ttc"};

    public static File getDirectory() {
        return Path.font();
    }

    public static List<Entry> getAll() {
        File[] files = listSupportedFiles(getDirectory());
        if (files == null) return List.of();
        return Arrays.stream(files)
                .sorted((first, second) -> first.getName().compareToIgnoreCase(second.getName()))
                .map(ExternalFont::getEntry)
                .filter(item -> item != null)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Nullable
    public static Entry getEntry(File file) {
        if (file == null || !file.isFile()) return null;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String display = dot > 0 ? name.substring(0, dot) : name;
        return new Entry(file.getAbsolutePath(), display.isEmpty() ? name : display);
    }

    @Nullable
    public static String importFrom(android.content.Context context, android.net.Uri uri) {
        if (context == null || uri == null) return null;
        File dir = getDirectory();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return null;
        File temp = null;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            String display = FileUtil.getDisplayName(uri);
            if (!isSupportedName(display)) {
                // Allow octet-stream pickers that drop the real extension.
                display = display + ".ttf";
            }
            temp = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, dir);
            try (FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.interrupted()) throw new InterruptedIOException();
                    total += read;
                    if (total > MAX_FILE_BYTES) return null;
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
            File target = new File(dir, sanitizeName(display));
            if (!temp.renameTo(target)) {
                try {
                    Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    return null;
                }
            }
            temp = null;
            return target.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            if (temp != null && temp.exists()) temp.delete();
        }
    }

    private static File[] listSupportedFiles(File dir) {
        if (dir == null) return null;
        File[] files = dir.listFiles((folder, name) -> new File(folder, name).isFile() && isSupportedName(name));
        return files == null ? null : files;
    }

    private static boolean isSupportedName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) if (lower.endsWith(extension)) return true;
        return false;
    }

    private static String sanitizeName(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_");
        return TextUtils.isEmpty(value) ? ("font-" + Crypto.md5(String.valueOf(System.nanoTime())) + ".ttf") : value;
    }

    public record Entry(String path, String name) {

        public Typeface typeface() {
            try {
                return Typeface.createFromFile(new File(path));
            } catch (Exception e) {
                return Typeface.DEFAULT;
            }
        }
    }
}
