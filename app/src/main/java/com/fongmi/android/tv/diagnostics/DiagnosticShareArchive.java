package com.fongmi.android.tv.diagnostics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/** Immutable FileProvider snapshots: an old shared URI must never expose a later export. */
public final class DiagnosticShareArchive {
    private static final int MAX_ARCHIVES = 3;
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private final File root;
    private final File directory;

    public interface BeforeDelete { void revoke(File file) throws IOException; }

    public DiagnosticShareArchive(File diagnosticDirectory) {
        root = diagnosticDirectory.getAbsoluteFile();
        directory = new File(root, "shares");
    }

    public synchronized File create(File source, BeforeDelete beforeDelete) throws IOException {
        checkDirectory();
        File expected = new File(root, "diagnostics.zip");
        if (!source.getAbsoluteFile().equals(expected) || !source.getCanonicalFile().equals(
                new File(root.getCanonicalFile(), "diagnostics.zip")) || !source.isFile()) {
            throw new IOException("Invalid diagnostic export source");
        }
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create diagnostic share directory");
        File[] abandoned = directory.listFiles((dir, name) -> name.matches("share-[0-9a-f]{32}\\.zip\\.tmp"));
        if (abandoned == null) throw new IOException("Cannot read diagnostic share directory");
        for (File file : abandoned) remove(file, beforeDelete);
        String name = "share-" + UUID.randomUUID().toString().replace("-", "") + ".zip";
        File target = new File(directory, name);
        File temporary = new File(directory, name + ".tmp");
        if (!temporary.createNewFile()) throw new IOException("Diagnostic share name unavailable");
        try {
            if (source.length() > MAX_BYTES) throw new IOException("Diagnostic archive exceeds limit");
            int size = 0;
            try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Diagnostic export cancelled");
                    size += count;
                    if (size > MAX_BYTES) throw new IOException("Diagnostic archive exceeds limit");
                    output.write(buffer, 0, count);
                }
                if (size == 0) throw new IOException("Diagnostic archive is empty");
                output.getFD().sync();
            }
            // Make room before publishing: failed removal cannot grow the retained archive set.
            File[] existing = archives();
            for (int i = 0; i <= existing.length - MAX_ARCHIVES; i++) remove(existing[i], beforeDelete);
            if (target.exists() || !temporary.renameTo(target)) throw new IOException("Cannot publish diagnostic share");
            return target;
        } finally { temporary.delete(); }
    }

    public synchronized void clear(BeforeDelete beforeDelete) throws IOException {
        checkDirectory();
        if (!directory.exists()) return;
        File[] files = directory.listFiles((dir, name) -> ownedName(name));
        if (files == null) throw new IOException("Cannot read diagnostic share directory");
        for (File file : files) remove(file, beforeDelete);
    }

    private File[] archives() throws IOException {
        File[] files = directory.listFiles((dir, name) -> name.matches("share-[0-9a-f]{32}\\.zip"));
        if (files == null) throw new IOException("Cannot read diagnostic share directory");
        for (File file : files) checkOwnedFile(file);
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        return files;
    }

    private void remove(File file, BeforeDelete beforeDelete) throws IOException {
        checkOwnedFile(file);
        if (file.getName().endsWith(".zip")) beforeDelete.revoke(file);
        if (!file.delete() && file.exists()) throw new IOException("Cannot remove diagnostic share");
    }

    private void checkDirectory() throws IOException {
        File parent = root.getParentFile();
        if (parent == null || !root.getCanonicalFile().equals(new File(parent.getCanonicalFile(), root.getName()))) {
            throw new IOException("Invalid diagnostic directory");
        }
        if (!directory.getCanonicalFile().equals(new File(root.getCanonicalFile(), "shares"))) {
            throw new IOException("Invalid diagnostic share directory");
        }
        if (directory.exists() && !directory.isDirectory()) throw new IOException("Invalid diagnostic share directory");
    }

    private void checkOwnedFile(File file) throws IOException {
        if (!ownedName(file.getName()) || !file.isFile() || !file.getCanonicalFile().equals(
                new File(directory.getCanonicalFile(), file.getName()))) throw new IOException("Invalid diagnostic share file");
    }

    private static boolean ownedName(String name) {
        return name.matches("share-[0-9a-f]{32}\\.zip(?:\\.tmp)?");
    }
}
