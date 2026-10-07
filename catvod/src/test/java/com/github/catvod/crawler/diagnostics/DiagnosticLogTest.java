package com.github.catvod.crawler.diagnostics;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class DiagnosticLogTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @After public void stop() { DiagnosticLog.stop(); }

    @Test public void remainsDisabledUntilExplicitlyStarted() throws Exception {
        DiagnosticLog.stop();
        DiagnosticLog.record("test", "password=secret");
        DiagnosticLog.markIncident("ignored");
        assertFalse(DiagnosticLog.isEnabled());
        try { DiagnosticLog.exportZip(); fail("disabled export"); }
        catch (java.io.IOException expected) { /* expected */ }
    }

    @Test(timeout = 10000) public void boundsFloodAndPreservesRecentEventsInValidatedArchive() throws Exception {
        DiagnosticLog.start(folder.getRoot());
        for (int i = 0; i < 1200; i++) DiagnosticLog.record("source", "event-" + i + " " + "x".repeat(5000));
        File archive = DiagnosticLog.exportZip();
        try (ZipFile zip = new ZipFile(archive)) {
            String events = read(zip, "events.log");
            assertTrue(events.length() < 132000);
            assertTrue(events.contains("event-1199"));
            assertFalse(events.contains("event-0 "));
            assertTrue(events.contains("[truncated]"));
            assertTrue(read(zip, "manifest.txt").contains(hash(events.getBytes(StandardCharsets.UTF_8)) + "  events.log"));
        }
    }

    @Test public void limitsSnapshotsAndPreservesThemAcrossSessionsWithoutSecrets() throws Exception {
        DiagnosticLog.start(folder.getRoot());
        DiagnosticLog.record("jar", "https://u:p@host.test/a?token=LEAK_ME");
        DiagnosticLog.record("python", "{\"clientSecret\":\"ALSO_SECRET\"}");
        for (int i = 0; i < 8; i++) DiagnosticLog.flushIncident("failure " + i);
        assertEquals(3, folder.getRoot().listFiles((dir, name) -> name.endsWith(".log")).length);
        DiagnosticLog.stop();
        DiagnosticLog.start(folder.getRoot());
        try (ZipFile zip = new ZipFile(DiagnosticLog.exportZip())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                String text = read(zip, entries.nextElement().getName());
                assertFalse(text, text.contains("LEAK_ME"));
                assertFalse(text, text.contains("ALSO_SECRET"));
                count++;
            }
            assertEquals(5, count);
        }
    }

    @Test public void rechecksStoredSnapshotBeforeExport() throws Exception {
        DiagnosticLog.start(folder.getRoot());
        Files.writeString(new File(folder.getRoot(), "incident-1-1.log").toPath(), "Cookie: old=PRIVATE\nrequest https://u:p@host.test/a?unknown=SECRET");
        try (ZipFile zip = new ZipFile(DiagnosticLog.exportZip())) {
            String incident = read(zip, "incidents/incident-1-1.log");
            assertFalse(incident.contains("PRIVATE"));
            assertFalse(incident.contains("SECRET"));
            assertFalse(incident.contains("u:p"));
        }
    }

    @Test public void rejectsOversizeStoredFileRatherThanReadingUnboundedData() throws Exception {
        DiagnosticLog.start(folder.getRoot());
        File poisoned = new File(folder.getRoot(), "incident-1-1.log");
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(poisoned, "rw")) { file.setLength(4 * 1024 * 1024); }
        try { DiagnosticLog.exportZip(); fail("oversized snapshot accepted"); }
        catch (java.io.IOException expected) { assertFalse(new File(folder.getRoot(), "diagnostics.zip.tmp").exists()); }
    }

    @Test public void clearRequiresStoppedSessionAndOnlyDeletesOwnedFiles() throws Exception {
        DiagnosticLog.start(folder.getRoot());
        DiagnosticLog.flushIncident("failure");
        DiagnosticLog.exportZip();
        File unrelated = new File(folder.getRoot(), "unrelated.txt");
        Files.writeString(unrelated.toPath(), "keep");
        try { DiagnosticLog.clear(folder.getRoot()); fail("active clear accepted"); }
        catch (java.io.IOException expected) { /* expected */ }
        DiagnosticLog.stop();
        DiagnosticLog.clear(folder.getRoot());
        assertTrue(unrelated.exists());
        assertEquals(1, folder.getRoot().list().length);
    }

    @Test(timeout = 5000) public void rejectsOldSessionOutputFromThreadsThatResumeAfterStopClearStart() throws Exception {
        DiagnosticLog.start(folder.getRoot());
        long previousSession = DiagnosticLog.getSessionGeneration();
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch restarted = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    restarted.await();
                    DiagnosticLog.recordInSession(previousSession, "python", "stale buffered fixture");
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            threads.add(worker);
            worker.start();
        }
        try {
            assertTrue(ready.await(1, java.util.concurrent.TimeUnit.SECONDS));
            DiagnosticLog.stop();
            assertNotEquals(previousSession, DiagnosticLog.getSessionGeneration());
            DiagnosticLog.clear(folder.getRoot());
            DiagnosticLog.start(folder.getRoot());
            long currentSession = DiagnosticLog.getSessionGeneration();
            assertNotEquals(previousSession, currentSession);
            restarted.countDown();
            for (Thread worker : threads) { worker.join(1000); assertFalse(worker.isAlive()); }
            DiagnosticLog.recordInSession(currentSession, "python", "current session fixture");
            try (ZipFile zip = new ZipFile(DiagnosticLog.exportZip())) {
                String events = read(zip, "events.log");
                assertFalse(events.contains("stale buffered fixture"));
                assertTrue(events.contains("current session fixture"));
            }
        } finally {
            restarted.countDown();
            for (Thread worker : threads) { worker.interrupt(); worker.join(1000); }
        }
    }

    private static String read(ZipFile zip, String name) throws Exception {
        try (java.io.InputStream input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
}
