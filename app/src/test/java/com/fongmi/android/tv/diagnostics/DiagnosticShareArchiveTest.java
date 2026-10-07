package com.fongmi.android.tv.diagnostics;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiagnosticShareArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File source(String value) throws Exception {
        File source = new File(temporary.getRoot(), "diagnostics.zip");
        Files.write(source.toPath(), value.getBytes(StandardCharsets.UTF_8));
        return source;
    }

    @Test public void laterExportsNeverChangeAnEarlierSharedPath() throws Exception {
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        File first = owner.create(source("session one bytes"), file -> fail("Nothing should be evicted"));
        File second = owner.create(source("session two bytes"), file -> fail("Nothing should be evicted"));
        assertNotEquals(first, second);
        assertEquals("session one bytes", new String(Files.readAllBytes(first.toPath()), StandardCharsets.UTF_8));
        assertEquals("session two bytes", new String(Files.readAllBytes(second.toPath()), StandardCharsets.UTF_8));
        assertTrue(first.getName().matches("share-[0-9a-f]{32}\\.zip"));
    }

    @Test public void retentionRevokesBeforeDeletionAndRetainsOnlyThreeSnapshots() throws Exception {
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        List<File> revoked = new ArrayList<>();
        List<File> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            File file = owner.create(source("archive " + i), old -> {
                assertTrue("Revoke must precede removal", old.exists());
                revoked.add(old);
            });
            assertTrue(file.setLastModified(1000L + i));
            created.add(file);
        }
        assertEquals(Arrays.asList(created.get(0), created.get(1)), revoked);
        assertEquals(3, new File(temporary.getRoot(), "shares").listFiles().length);
        assertFalse(created.get(0).exists());
        assertFalse(created.get(1).exists());
        assertTrue(created.get(4).exists());
    }

    @Test public void failedRevocationDoesNotPublishExtraArchiveOrLeaveTemporaryBytes() throws Exception {
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        for (int i = 0; i < 3; i++) owner.create(source("prior " + i), ignored -> {});
        assertThrows(IOException.class, () -> owner.create(source("new export"), old -> {
            throw new IOException("fixture revoke failure");
        }));
        File[] files = new File(temporary.getRoot(), "shares").listFiles();
        assertEquals(3, files.length);
        for (File file : files) assertTrue(file.getName().endsWith(".zip"));
    }

    @Test public void oversizedSourceCannotLeavePartialSnapshot() throws Exception {
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        File source = source("initial");
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(source, "rw")) { file.setLength(3L * 1024 * 1024 + 1); }
        assertThrows(IOException.class, () -> owner.create(source, ignored -> {}));
        assertEquals(0, new File(temporary.getRoot(), "shares").listFiles().length);
    }

    @Test public void clearsOnlyOwnedSnapshotsAndOrphanedTemporaries() throws Exception {
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        File first = owner.create(source("fixture"), ignored -> {});
        File unknown = new File(first.getParentFile(), "user-document.zip");
        Files.write(unknown.toPath(), new byte[]{1});
        File orphan = new File(first.getParentFile(), "share-00000000000000000000000000000000.zip.tmp");
        Files.write(orphan.toPath(), new byte[]{2});
        List<File> revoked = new ArrayList<>();
        owner.clear(file -> { assertTrue(file.exists()); revoked.add(file); });
        assertEquals(Arrays.asList(first), revoked);
        assertTrue(unknown.exists());
        assertFalse(orphan.exists());
        assertFalse(first.exists());
        assertTrue(new File(temporary.getRoot(), "diagnostics.zip").exists());
    }

    @Test public void rejectsArbitrarySourceAndSymlinkEscape() throws Exception {
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        File other = new File(temporary.getRoot(), "private.txt");
        Files.write(other.toPath(), new byte[]{4});
        assertThrows(IOException.class, () -> owner.create(other, ignored -> {}));
        File link = new File(temporary.getRoot(), "diagnostics.zip");
        Files.createSymbolicLink(link.toPath(), other.toPath());
        assertThrows(IOException.class, () -> owner.create(link, ignored -> {}));
        assertTrue(other.exists());
    }

    @Test public void rejectsShareDirectorySymlinkWithoutTouchingDestination() throws Exception {
        File external = temporary.newFolder("unrelated");
        File link = new File(temporary.getRoot(), "shares");
        Files.createSymbolicLink(link.toPath(), external.toPath());
        DiagnosticShareArchive owner = new DiagnosticShareArchive(temporary.getRoot());
        assertThrows(IOException.class, () -> owner.create(source("fixture"), ignored -> {}));
        assertThrows(IOException.class, () -> owner.clear(ignored -> {}));
        assertEquals(0, external.listFiles().length);
    }
}
