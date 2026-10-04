package com.fongmi.android.tv.player.subtitle;

import android.net.Uri;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.peerless2012.ass.Ass;

public final class SubtitleFonts {
    private static final int MAX_FONT = 24 * 1024 * 1024;
    private static final int MAX_TOTAL = 32 * 1024 * 1024;
    private final Map<String, byte[]> embedded = new LinkedHashMap<>();
    private int bytes;
    private int version;

    public static File directory() { return new File(App.get().getFilesDir(), "subtitle-fonts"); }

    public synchronized void add(String name, byte[] data) {
        if (name == null || data.length > MAX_FONT || bytes + data.length > MAX_TOTAL || embedded.containsKey(name)) return;
        embedded.put(name, data);
        bytes += data.length;
        version++;
    }

    public synchronized int version() { return version; }
    public synchronized void clear() { embedded.clear(); bytes = 0; version++; }

    public synchronized void apply(Ass ass) {
        ass.clearFont();
        int total = 0;
        for (Map.Entry<String, byte[]> font : embedded.entrySet()) {
            ass.addFont(font.getKey(), font.getValue());
            total += font.getValue().length;
        }
        List<File> files = new ArrayList<>();
        File[] external = directory().listFiles();
        if (external != null) java.util.Collections.addAll(files, external);
        files.add(new File("/system/fonts/Roboto-Regular.ttf"));
        files.add(new File("/system/fonts/NotoSans-Regular.ttf"));
        files.add(new File("/system/fonts/NotoSansCJK-Regular.ttc"));
        for (File file : files) {
            if (!file.isFile() || file.length() > MAX_FONT || total + file.length() > MAX_TOTAL) continue;
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                if (validFont(data)) { ass.addFont(file.getName(), data); total += data.length; }
            } catch (IOException ignored) { }
        }
    }

    static byte[] readFont(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            if (output.size() + read > MAX_FONT) throw new IOException("Font exceeds 24 MiB");
            output.write(chunk, 0, read);
        }
        return output.toByteArray();
    }

    public static synchronized boolean importFont(Uri uri) {
        try (InputStream input = App.get().getContentResolver().openInputStream(uri)) {
            if (input == null) return false;
            byte[] data = readFont(input);
            if (data.length > MAX_FONT || !validFont(data)) return false;
            File dir = directory();
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            long used = 0;
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) used += file.length();
            String name = java.util.UUID.nameUUIDFromBytes(data) + ".ttf";
            File target = new File(dir, name);
            if (target.isFile()) return true;
            if (used + data.length > MAX_TOTAL) return false;
            Files.write(target.toPath(), data);
            return true;
        } catch (IOException | RuntimeException error) { return false; }
    }

    public static boolean validFont(byte[] data) {
        if (data.length < 12) return false;
        int magic = (data[0] & 255) << 24 | (data[1] & 255) << 16 | (data[2] & 255) << 8 | data[3] & 255;
        return magic == 0x00010000 || magic == 0x4F54544F || magic == 0x74746366 || magic == 0x74727565;
    }
}
