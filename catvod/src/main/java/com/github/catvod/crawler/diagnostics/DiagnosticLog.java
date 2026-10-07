package com.github.catvod.crawler.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Small opt-in flight recorder. No Android dependencies and no network or configuration access. */
public final class DiagnosticLog {
    private static final int MAX_LINES = 600;
    private static final int MAX_CHARS = 131072;
    private static final int MAX_LINE = 4096;
    private static final int MAX_SNAPSHOT_BYTES = MAX_CHARS * 4;
    private static final Object LOCK = new Object();
    private static final Object DISK_LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final AtomicLong DROPPED_SNAPSHOTS = new AtomicLong();
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "diagnostic-snapshot");
                thread.setDaemon(true);
                return thread;
            }, (task, executor) -> {
                if (executor.getQueue().poll() != null) DROPPED_SNAPSHOTS.incrementAndGet();
                // Do not recursively re-submit under contention: keep both queue and caller work bounded.
                if (executor.isShutdown() || !executor.getQueue().offer(task)) DROPPED_SNAPSHOTS.incrementAndGet();
            });
    private static volatile boolean enabled;
    private static File directory;
    private static int chars;
    private static long generation;
    private static long sequence;
    private static long droppedLines;

    static { WRITER.allowCoreThreadTimeOut(true); }
    private DiagnosticLog() {}

    public static void start(File privateDirectory) throws IOException {
        synchronized (LOCK) {
            if (enabled) return;
            if ((!privateDirectory.exists() && !privateDirectory.mkdirs()) || !privateDirectory.isDirectory()) {
                throw new IOException("Cannot create diagnostic directory");
            }
            directory = privateDirectory;
            LINES.clear();
            chars = 0;
            droppedLines = 0;
            DROPPED_SNAPSHOTS.set(0);
            generation++;
            enabled = true;
        }
        record("session", "Diagnostic collection started");
    }

    public static void stop() {
        synchronized (LOCK) {
            enabled = false;
            generation++;
            LINES.clear();
            chars = 0;
            WRITER.getQueue().clear();
        }
    }

    public static boolean isEnabled() { return enabled; }

    /** Opaque process-local generation for bridges which buffer partial output between calls. */
    public static long getSessionGeneration() {
        synchronized (LOCK) { return generation; }
    }

    /** Delete only files owned by this recorder; callers must stop collection first. */
    public static void clear(File privateDirectory) throws IOException {
        synchronized (DISK_LOCK) {
            synchronized (LOCK) {
                if (enabled) throw new IOException("Stop diagnostic collection before clearing");
                File[] files = privateDirectory.listFiles((dir, name) -> name.matches("incident-[0-9]+-[0-9]+\\.log")
                        || name.equals("incident.tmp") || name.equals("diagnostics.zip") || name.equals("diagnostics.zip.tmp"));
                if (files == null) return;
                for (File file : files) if (!file.delete() && file.exists()) throw new IOException("Cannot clear diagnostic file");
            }
        }
    }

    public static void record(String source, String event) {
        if (!enabled) return;
        recordInSession(getSessionGeneration(), source, event);
    }

    /** Reject buffered or in-flight output from a previous session, including a restart during redaction. */
    public static void recordInSession(long currentGeneration, String source, String event) {
        if (!enabled) return;
        synchronized (LOCK) { if (!enabled || generation != currentGeneration) return; }
        try {
            String safe = DiagnosticRedactor.redact(event);
            String tag = DiagnosticRedactor.redact(source).replaceAll("[\\r\\n\\t]", " ");
            if (tag.length() > 64) tag = tag.substring(0, 64);
            synchronized (LOCK) {
                if (!enabled || generation != currentGeneration) return;
                for (String line : safe.split("\\r?\\n", -1)) {
                    String entry = System.currentTimeMillis() + " [" + tag + "] " + line;
                    if (entry.length() > MAX_LINE) entry = entry.substring(0, MAX_LINE) + " [truncated]";
                    LINES.add(entry);
                    chars += entry.length();
                    while (LINES.size() > MAX_LINES || chars > MAX_CHARS) {
                        chars -= LINES.removeFirst().length();
                        droppedLines++;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must never break a source or the player.
        }
    }

    public static void record(String source, Throwable error) {
        if (!enabled || error == null) return;
        // Walk a bounded trace instead of letting arbitrary Throwables allocate a huge StringWriter.
        StringBuilder trace = new StringBuilder();
        for (int cause = 0; error != null && cause < 4; cause++, error = error.getCause()) {
            trace.append(error.getClass().getName()).append(": ");
            String message = error.getMessage();
            if (message != null) trace.append(message, 0, Math.min(message.length(), 4096));
            trace.append('\n');
            StackTraceElement[] frames = error.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 32); i++) trace.append("  at ").append(frames[i]).append('\n');
        }
        record(source, trace.toString());
    }

    public static void markIncident(String reason) {
        if (!enabled) return;
        record("incident", reason);
        final Snapshot snapshot = snapshot();
        if (snapshot != null) WRITER.execute(() -> saveQuietly(snapshot));
    }

    /** A bounded synchronous save for an uncaught-exception hook, before delegating to Android. */
    public static void flushIncident(String reason) {
        if (!enabled) return;
        record("incident", reason);
        Snapshot snapshot = snapshot();
        if (snapshot != null) saveQuietly(snapshot);
    }

    private static Snapshot snapshot() {
        synchronized (LOCK) {
            if (!enabled) return null;
            return new Snapshot(directory, generation, ++sequence, new ArrayList<>(LINES), droppedLines, DROPPED_SNAPSHOTS.get());
        }
    }

    private static void saveQuietly(Snapshot snapshot) {
        try {
            synchronized (DISK_LOCK) {
                synchronized (LOCK) { if (!enabled || generation != snapshot.generation) return; }
                File target = new File(snapshot.directory, "incident-" + System.currentTimeMillis() + "-"
                        + String.format(java.util.Locale.ROOT, "%019d", snapshot.sequence) + ".log");
                atomicWrite(target, linesBytes(snapshot.lines));
                File[] files = incidentFiles(snapshot.directory);
                for (int i = 3; i < files.length; i++) files[i].delete();
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    /** Export is explicit and synchronous. Call from a worker, never from the playback/UI thread. */
    public static File exportZip() throws IOException {
        Snapshot snapshot = snapshot();
        if (snapshot == null) throw new IOException("Diagnostic collection is disabled");
        synchronized (DISK_LOCK) {
            File target = new File(snapshot.directory, "diagnostics.zip");
            File temporary = new File(snapshot.directory, "diagnostics.zip.tmp");
            StringBuilder manifest = new StringBuilder("TV diagnostics v1\nTimes: Unix milliseconds (UTC)\nSHA-256 of each uncompressed entry:\n");
            manifest.append("Evicted log lines: ").append(snapshot.droppedLines).append('\n');
            manifest.append("Superseded snapshot requests: ").append(snapshot.droppedSnapshots).append('\n');
            try {
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temporary))) {
                    addEntry(zip, "events.log", linesBytes(snapshot.lines), manifest);
                    File[] incidents = incidentFiles(snapshot.directory);
                    for (int i = 0; i < Math.min(3, incidents.length); i++) {
                        byte[] stored = readBounded(incidents[i], MAX_SNAPSHOT_BYTES);
                        // Redact each bounded line again, including older snapshots from a previous session.
                        List<String> lines = Arrays.asList(new String(stored, StandardCharsets.UTF_8).split("\\n"));
                        addEntry(zip, "incidents/" + incidents[i].getName(), linesBytes(lines), manifest);
                    }
                    zip.putNextEntry(new ZipEntry("manifest.txt"));
                    zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
                // Re-read every entry: detect a truncated/corrupt archive before offering it to a user.
                validateZip(temporary);
                synchronized (LOCK) {
                    if (!enabled || generation != snapshot.generation) throw new IOException("Diagnostic session ended");
                    if (!temporary.renameTo(target)) throw new IOException("Cannot publish diagnostic archive");
                }
                return target;
            } finally { temporary.delete(); }
        }
    }

    private static void validateZip(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                try (java.io.InputStream input = zip.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) crc.update(buffer, 0, count);
                }
                if (crc.getValue() != entry.getCrc()) throw new IOException("Diagnostic archive checksum failed");
            }
        }
    }

    private static byte[] linesBytes(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            String safe = DiagnosticRedactor.redact(line);
            if (text.length() + safe.length() > MAX_CHARS + MAX_LINES) break;
            text.append(safe).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] data, StringBuilder manifest) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            for (byte b : hash) manifest.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            manifest.append("  ").append(name).append('\n');
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    private static File[] incidentFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.matches("incident-[0-9]+-[0-9]+\\.log"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName, Comparator.reverseOrder()));
        return files;
    }

    public static byte[] readBounded(File file, int maxBytes) throws IOException {
        if (file.length() > maxBytes) throw new IOException("Diagnostic file exceeds limit");
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) throw new IOException("Diagnostic file exceeds limit");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void atomicWrite(File target, byte[] bytes) throws IOException {
        File temporary = new File(target.getParentFile(), "incident.tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            if (!temporary.renameTo(target)) throw new IOException("Cannot publish diagnostic snapshot");
        } finally { temporary.delete(); }
    }

    private static final class Snapshot {
        final File directory;
        final long generation;
        final long sequence;
        final List<String> lines;
        final long droppedLines;
        final long droppedSnapshots;
        Snapshot(File directory, long generation, long sequence, List<String> lines, long droppedLines, long droppedSnapshots) {
            this.directory = directory;
            this.generation = generation;
            this.sequence = sequence;
            this.lines = lines;
            this.droppedLines = droppedLines;
            this.droppedSnapshots = droppedSnapshots;
        }
    }
}
