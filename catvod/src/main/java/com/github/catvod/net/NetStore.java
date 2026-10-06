package com.github.catvod.net;

import android.util.AtomicFile;
import com.github.catvod.Init;
import com.github.catvod.utils.Util;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

final class NetStore {
    private static final long MAX_BYTES = 0x1000000L;
    private static final Object LOCK = new Object();
    private final File directory;

    NetStore(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) {
            throw new IllegalArgumentException("Cache requires a site key");
        }
        this.directory = new File(Init.context().getCacheDir(), "spider/" + Util.sha256(siteKey));
    }

    String get(String key) {
        File file = this.file(key);
        Object object = LOCK;
        synchronized (object) {
            if (!file.isFile() && !new File(file.getPath() + ".bak").isFile()) {
                return null;
            }
            try (FileInputStream input = new AtomicFile(file).openRead();){
                String string;
                try (ByteArrayOutputStream output = new ByteArrayOutputStream();){
                    int size;
                    byte[] buffer = new byte[16384];
                    while ((size = input.read(buffer)) != -1) {
                        output.write(buffer, 0, size);
                    }
                    string = output.toString(StandardCharsets.UTF_8.name());
                }
                return string;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    void set(String key, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        File file = this.file(key);
        Object object = LOCK;
        synchronized (object) {
            if ((long)bytes.length > 0x1000000L) {
                this.delete(file);
                return;
            }
            if (!this.directory.isDirectory() && !this.directory.mkdirs()) {
                throw new IllegalStateException("Cannot create cache directory");
            }
            AtomicFile atomic = new AtomicFile(file);
            FileOutputStream output = null;
            try {
                output = atomic.startWrite();
                output.write(bytes);
                atomic.finishWrite(output);
            }
            catch (IOException e) {
                atomic.failWrite(output);
                throw new UncheckedIOException(e);
            }
            this.prune();
        }
    }

    void remove(String key) {
        Object object = LOCK;
        synchronized (object) {
            if (key != null) {
                File file = this.file(key);
                this.delete(file);
                this.delete(new File(file.getPath() + ".bak"));
                this.delete(new File(file.getPath() + ".new"));
            } else {
                File[] files = this.directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        this.delete(file);
                    }
                }
            }
        }
    }

    private void prune() {
        File[] files = this.directory.listFiles((dir, name) -> !name.endsWith(".bak") && !name.endsWith(".new"));
        if (files == null) {
            throw new IllegalStateException("Cannot read cache directory");
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long bytes = 0L;
        for (File file : files) {
            bytes += file.length();
        }
        for (int i = 0; i < files.length && (files.length - i > 64 || bytes > MAX_BYTES); ++i) {
            long size = files[i].length();
            this.delete(files[i]);
            bytes -= size;
        }
    }

    private File file(String key) {
        return new File(this.directory, Util.sha256(key));
    }

    private void delete(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Cannot delete cache file");
        }
    }
}
