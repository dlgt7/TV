package com.fongmi.android.tv.diagnostics;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.crawler.diagnostics.DiagnosticLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real Android storage and production NanoHTTPD, with all requests restricted to loopback.
 * The target Context is delegated unchanged except for the recorder's private fixture directory.
 * Existing diagnostic files, source configuration and accounts are never read or changed.
 * The original collection on/off state is restored; transient buffers/download grants are not retained.
 */
@RunWith(AndroidJUnit4.class)
public class DiagnosticIntegrationTest {
    private static final int ARCHIVE_LIMIT = 3 * 1024 * 1024;
    private Context target;
    private Context fixtureContext;
    private File fixtureRoot;
    private File diagnosticDirectory;
    private File unrelatedFixture;
    private boolean originallyEnabled;
    private boolean originallyServing;
    private boolean setupComplete;

    @Before public void isolateRecorderFiles() throws Exception {
        target = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        originallyEnabled = DiagnosticManager.isEnabled();
        originallyServing = Server.get().isRunning();
        fixtureRoot = new File(target.getCacheDir(), "diagnostics-integration-" + UUID.randomUUID());
        assertTrue("Cannot create private diagnostic fixture", fixtureRoot.mkdirs());
        diagnosticDirectory = new File(fixtureRoot, "diagnostics");
        unrelatedFixture = new File(diagnosticDirectory, "unrelated-fixture.txt");
        fixtureContext = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return fixtureRoot; }
        };
        DiagnosticManager.stop();
        setupComplete = true;
    }

    @After public void restoreRecorderState() throws Exception {
        if (!setupComplete) return;
        try {
            DiagnosticManager.stop();
            DiagnosticManager.clear(fixtureContext);
            if (unrelatedFixture.exists()) assertTrue("Cannot remove fixture sentinel", unrelatedFixture.delete());
            if (diagnosticDirectory.exists()) assertTrue("Fixture recorder directory is not empty", diagnosticDirectory.delete());
            assertTrue("Cannot remove fixture root", fixtureRoot.delete());
        } finally {
            try {
                if (!originallyServing && Server.get().isRunning()) {
                    Server.get().stop();
                    long deadline = SystemClock.elapsedRealtime() + 5000;
                    while (Server.get().isRunning() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20);
                    assertFalse("Test-started local server did not stop", Server.get().isRunning());
                }
            } finally {
                if (originallyEnabled) DiagnosticManager.start(target);
                else DiagnosticManager.stop();
                assertEquals("Diagnostic collection state was not restored", originallyEnabled, DiagnosticManager.isEnabled());
            }
        }
    }

    @Test public void disabledRecorderStaysEmptyAndEnabledRecorderExportsIncidentThenClearsOnlyOwnedFiles() throws Exception {
        String ignored = "disabled-fixture-" + UUID.randomUUID();
        assertFalse(DiagnosticManager.isEnabled());
        DiagnosticLog.record("instrumentation", ignored);
        DiagnosticManager.markIncident(ignored);
        assertFalse("Disabled collection must not create recorder files", diagnosticDirectory.exists());
        try {
            DiagnosticManager.exportZip(fixtureContext);
            fail("Disabled recorder unexpectedly exported an archive");
        } catch (IOException expected) {
            // Explicitly disabled; no fixture data should have been retained.
        }

        DiagnosticManager.start(fixtureContext);
        assertTrue(DiagnosticManager.isEnabled());
        String marker = "enabled-fixture-" + UUID.randomUUID();
        DiagnosticLog.record("instrumentation", marker);
        DiagnosticManager.markIncident(marker);
        awaitIncidentSnapshot();
        File archive = DiagnosticManager.exportZip(fixtureContext);
        assertEquals(diagnosticDirectory.getCanonicalFile(), archive.getParentFile().getCanonicalFile());
        ArchiveContents exported;
        try (InputStream input = new java.io.FileInputStream(archive)) {
            exported = inspectFixtureZip(readBounded(input, ARCHIVE_LIMIT));
        }
        assertTrue("Current fixture marker missing from events", exported.events.contains(marker));
        assertFalse("A disabled-period event was retained", exported.events.contains(ignored));
        assertTrue("Incident snapshot missing from export", exported.incidentCount > 0);
        assertTrue("Checksum manifest missing", exported.hasManifest);

        try (FileOutputStream output = new FileOutputStream(unrelatedFixture)) {
            output.write("fixture owned by this test, not the recorder".getBytes(StandardCharsets.UTF_8));
        }
        DiagnosticManager.stop();
        DiagnosticManager.clear(fixtureContext);
        assertTrue("Recorder cleanup removed another owner's file", unrelatedFixture.exists());
        File[] remaining = diagnosticDirectory.listFiles();
        assertNotNull(remaining);
        assertEquals("Recorder left owned snapshots/archive behind", 1, remaining.length);
        assertEquals(unrelatedFixture.getName(), remaining[0].getName());
    }

    @Test public void realLoopbackDownloadRequiresTokenRejectsMutationsAndRevokesOnStop() throws Exception {
        // Proxy.port is -1 until the real server binds; that URI has no parsed host.
        Server.get().start();
        assertTrue("Local diagnostic server did not bind", Server.get().isRunning());
        URI advertised = new URI(Server.get().getAddress(false));
        DiagnosticManager.start(fixtureContext);
        boolean lanAvailable = advertised.getHost() != null && DiagnosticManager.isLocalPeer(advertised.getHost());
        if (!lanAvailable) {
            try {
                DiagnosticManager.getDownloadUrl(fixtureContext);
                fail("An unavailable LAN address unexpectedly produced a download link");
            } catch (IOException expected) {
                // Exercise the real manager's offline refusal before explicitly skipping HTTP assertions.
            }
        }
        assumeTrue("LAN address unavailable: refusal was checked; authenticated HTTP download assertions require a LAN address", lanAvailable);
        String marker = "download-fixture-" + UUID.randomUUID();
        DiagnosticLog.record("instrumentation", marker);
        DiagnosticManager.markIncident(marker);
        String download = DiagnosticManager.getDownloadUrl(fixtureContext);
        URI issued = new URI(download);
        assertNotNull("Production link contains no capability", issued.getRawQuery());
        assertTrue("Production link contains no token", issued.getRawQuery().startsWith("token="));
        URL loopback = loopbackUrl(issued, issued.getRawQuery());
        URL wrong = loopbackUrl(issued, "token=000000000000000000000000000000000000000000000000");
        URL missing = loopbackUrl(issued, null);

        assertStatus(wrong, "GET", 404);
        assertStatus(missing, "GET", 404);
        assertStatus(loopback, "POST", 404);
        HttpURLConnection connection = openLocal(loopback, "GET");
        try {
            assertEquals(200, connection.getResponseCode());
            assertEquals("application/zip", connection.getContentType());
            assertEquals("no-store", connection.getHeaderField("Cache-Control"));
            assertEquals("no-referrer", connection.getHeaderField("Referrer-Policy"));
            String disposition = connection.getHeaderField("Content-Disposition");
            assertNotNull(disposition);
            assertTrue(disposition.startsWith("attachment;"));
            try (InputStream input = connection.getInputStream()) {
                ArchiveContents response = inspectFixtureZip(readBounded(input, ARCHIVE_LIMIT));
                assertTrue("Downloaded archive did not contain this test's marker", response.events.contains(marker));
                assertTrue(response.hasManifest);
            }
        } finally { connection.disconnect(); }

        DiagnosticManager.stop();
        assertStatus(loopback, "GET", 404);
        DiagnosticManager.clear(fixtureContext);
        assertFalse(new File(diagnosticDirectory, "diagnostics.zip").exists());
    }

    private void awaitIncidentSnapshot() {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < deadline) {
            File[] files = diagnosticDirectory.listFiles((directory, name) -> name.startsWith("incident-") && name.endsWith(".log"));
            if (files != null && files.length > 0) return;
            SystemClock.sleep(20);
        }
        fail("Asynchronous incident snapshot was not published");
    }

    private static URL loopbackUrl(URI issued, String query) throws Exception {
        return new URI("http", null, "127.0.0.1", issued.getPort(), issued.getPath(), query, null).toURL();
    }

    private static HttpURLConnection openLocal(URL url, String method) throws Exception {
        assertEquals("This test must never contact an external host", "127.0.0.1", url.getHost());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        return connection;
    }

    private static void assertStatus(URL url, String method, int expected) throws Exception {
        HttpURLConnection connection = openLocal(url, method);
        try { assertEquals("Unexpected local download authorization response", expected, connection.getResponseCode()); }
        finally { connection.disconnect(); }
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maximum) throw new IOException("Fixture response exceeded its bound");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    /** Only fixture events are examined; original diagnostic directories are never opened. */
    private static ArchiveContents inspectFixtureZip(byte[] archive) throws IOException {
        ArchiveContents result = new ArchiveContents();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            int total = 0;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] data = readBounded(zip, ARCHIVE_LIMIT - total);
                total += data.length;
                if ("events.log".equals(entry.getName())) result.events = new String(data, StandardCharsets.UTF_8);
                if ("manifest.txt".equals(entry.getName())) result.hasManifest = true;
                if (entry.getName().startsWith("incidents/incident-")) result.incidentCount++;
                zip.closeEntry(); // Real ZIP CRC failures propagate out of the production response check.
            }
        }
        return result;
    }

    private static final class ArchiveContents {
        String events = "";
        boolean hasManifest;
        int incidentCount;
    }
}
