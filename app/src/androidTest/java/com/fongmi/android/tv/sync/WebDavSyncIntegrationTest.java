package com.fongmi.android.tv.sync;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.AppDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;

/** Real Room + AtomicFile + HTTP tests. Never opens the application's persistent database. */
@RunWith(AndroidJUnit4.class)
public final class WebDavSyncIntegrationTest {
    private static final String SUBSCRIPTION = "https://subscription.example.test/wex.json";
    private static final String KEY = "fixture@@@episode-series";
    private static final long BASE = 1_700_000_000_000L;
    private Context context;
    private SharedPreferences settings;
    private Map<String, ?> savedSettings;
    private File directory;
    private WebDavSyncSettings.Options options;
    private final List<AppDatabase> databases = new ArrayList<>();
    private DavServer remote;
    private long now = BASE + 10_000;

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Refuse even a debug/preview installation containing user data.
        assertEquals("Only the disposable sourceprobe package is allowed",
                "com.fongmi.android.tv.sourceprobe", context.getPackageName());
        settings = context.getSharedPreferences("webdav-sync", Context.MODE_PRIVATE);
        savedSettings = new HashMap<>(settings.getAll());
        options = new WebDavSyncSettings.Options("https://fixture.example.test/tv-sync.json", "", "",
                false, true, true, false, false);
        WebDavSyncSettings.save(options);
        directory = new File(context.getCacheDir(), "webdav-room-" + UUID.randomUUID());
        assertTrue(directory.mkdirs());
        remote = new DavServer();
    }

    @After public void tearDown() throws Exception {
        for (AppDatabase db : databases) db.close();
        if (remote != null) remote.close();
        if (settings != null && savedSettings != null) {
            SharedPreferences.Editor editor = settings.edit().clear();
            for (Map.Entry<String, ?> entry : savedSettings.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
                else throw new AssertionError("Unexpected SharedPreferences type");
            }
            assertTrue(editor.commit());
        }
        remove(directory);
        if (remote != null) remote.assertHealthy();
    }

    @Test public void mapsSubscriptionsByUrlAndTypeAcrossDifferentLocalIds() throws Exception {
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0);
        config(b.db, "https://other.example.test/feed.json", 0);
        Config sameUrlDifferentType = config(b.db, SUBSCRIPTION, 1);
        Config bc = config(b.db, SUBSCRIPTION, 0);
        assertNotEquals(ac.getId(), bc.getId());
        keep(a.db, ac, KEY); history(a.db, ac, KEY, 41_000);
        sync(a); sync(b);
        assertEquals(bc.getId(), b.db.getKeepDao().find(bc.getId(), KEY).getCid());
        History imported = b.db.getHistoryDao().find(bc.getId(), KEY);
        assertEquals(41_000, imported.getPosition());
        assertEquals("", imported.getEpisodeUrl());
        assertEquals(1f, imported.getSpeed(), 0f);
        assertNull(b.db.getHistoryDao().find(sameUrlDifferentType.getId(), KEY));
        String first = remote.document().encode();
        sync(a); sync(b);
        assertEquals("Unchanged devices do not generate revisions", first, remote.document().encode());
        assertEquals(2, remote.document().entries.size());
        assertFalse(first.contains(SUBSCRIPTION));
        assertFalse(first.contains("\"cid\""));
        assertFalse(first.contains("episodeUrl"));
        assertFalse(first.contains("fixture-media-token"));
    }

    @Test public void offlineDeletionSurvivesJournalReopenAndDoesNotResurrect() throws Exception {
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0), bc = config(b.db, SUBSCRIPTION, 0);
        keep(a.db, ac, KEY); history(a.db, ac, KEY, 41_000);
        sync(a); sync(b);
        a.db.getKeepDao().delete(ac.getId(), KEY); a.db.getHistoryDao().delete(ac.getId(), KEY);
        SyncCoordinator.Remote offline = new SyncCoordinator.Remote() {
            public SyncCoordinator.Fetched get() throws IOException { throw new IOException("fixture-offline"); }
            public boolean put(SyncDocument d, SyncCoordinator.Fetched f) { throw new AssertionError("No PUT while offline"); }
        };
        try { sync(a, a.store, offline); fail("Expected offline failure"); }
        catch (IOException expected) { assertEquals("fixture-offline", expected.getMessage()); }
        SyncMerger.State pending = journal("a").read();
        assertEquals(2, pending.document.entries.size());
        for (SyncDocument.Entry entry : pending.document.entries.values()) assertTrue(entry.deleted);
        // B still has stale records; its no-change upload must not recreate A's deleted rows.
        sync(b); sync(a); sync(b); sync(a);
        assertNull(a.db.getKeepDao().find(ac.getId(), KEY));
        assertNull(b.db.getKeepDao().find(bc.getId(), KEY));
        assertNull(a.db.getHistoryDao().find(ac.getId(), KEY));
        assertNull(b.db.getHistoryDao().find(bc.getId(), KEY));
        for (SyncDocument.Entry entry : remote.document().entries.values()) assertTrue(entry.deleted);
    }

    @Test public void playerProgressWrittenDuringHttpGetWinsOnNextSync() throws Exception {
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0), bc = config(b.db, SUBSCRIPTION, 0);
        history(a.db, ac, KEY, 10_000); sync(a); sync(b);
        progress(b.db, bc, KEY, 30_000); sync(b);
        remote.beforeNextGet.set(() -> progress(a.db, ac, KEY, 55_000));
        SyncCoordinator.Applied first = sync(a);
        assertEquals(1, first.skipped);
        assertEquals(55_000, a.db.getHistoryDao().find(ac.getId(), KEY).getPosition());
        sync(a); sync(b);
        assertEquals(55_000, b.db.getHistoryDao().find(bc.getId(), KEY).getPosition());
    }

    @Test public void fullRoomFingerprintProtectsLocalOnlyFieldsAndLaterImportsPreserveThem() throws Exception {
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0), bc = config(b.db, SUBSCRIPTION, 0);
        history(a.db, ac, KEY, 10_000); sync(a); sync(b);
        progress(b.db, bc, KEY, 30_000); sync(b);
        remote.beforeNextGet.set(() -> {
            History playing = a.db.getHistoryDao().find(ac.getId(), KEY);
            playing.setSpeed(1.75f); playing.setScale(3); playing.setRevSort(true);
            a.db.getHistoryDao().insertOrUpdate(playing);
        });
        assertEquals(1, sync(a).skipped);
        assertEquals(10_000, a.db.getHistoryDao().find(ac.getId(), KEY).getPosition());
        sync(a);
        History result = a.db.getHistoryDao().find(ac.getId(), KEY);
        assertEquals(30_000, result.getPosition());
        assertEquals(1.75f, result.getSpeed(), 0f); assertEquals(3, result.getScale()); assertTrue(result.isRevSort());
        assertEquals("", result.getEpisodeUrl());
    }

    @Test public void playerWriteAfterRoomCommitIsNotAcknowledgedAsSynced() throws Exception {
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0), bc = config(b.db, SUBSCRIPTION, 0);
        history(a.db, ac, KEY, 10_000); sync(a); sync(b);
        progress(b.db, bc, KEY, 30_000); sync(b);
        SyncCoordinator.Store writeImmediatelyAfterCommit = new SyncCoordinator.Store() {
            public SyncCoordinator.Snapshot snapshot() { return a.store.snapshot(); }
            public SyncCoordinator.Applied apply(SyncDocument d, SyncCoordinator.Snapshot before) {
                SyncCoordinator.Applied applied = a.store.apply(d, before);
                progress(a.db, ac, KEY, 65_000);
                return applied;
            }
        };
        sync(a, writeImmediatelyAfterCommit, remote.transport());
        assertEquals(65_000, a.db.getHistoryDao().find(ac.getId(), KEY).getPosition());
        sync(a); sync(b);
        assertEquals(65_000, b.db.getHistoryDao().find(bc.getId(), KEY).getPosition());
    }

    @Test public void primaryKeyCollisionAcrossSubscriptionsNeverOverwritesOrDeletesOtherSubscription() throws Exception {
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0);
        config(b.db, SUBSCRIPTION, 0);
        Config other = config(b.db, "https://other.example.test/wex.json", 0);
        keep(a.db, ac, KEY); history(a.db, ac, KEY, 10_000);
        keep(b.db, other, KEY); history(b.db, other, KEY, 88_000);
        sync(a);
        assertEquals(2, sync(b).skipped);
        assertEquals(other.getId(), b.db.getKeepDao().findAll().get(0).getCid());
        assertEquals(88_000, b.db.getHistoryDao().find(other.getId(), KEY).getPosition());
        a.db.getKeepDao().delete(ac.getId(), KEY); a.db.getHistoryDao().delete(ac.getId(), KEY);
        sync(a); sync(b);
        assertNotNull(b.db.getKeepDao().find(other.getId(), KEY));
        assertEquals(88_000, b.db.getHistoryDao().find(other.getId(), KEY).getPosition());
    }

    @Test public void optionalSubscriptionImportUsesNewIdsAndOmitsCachedConfigAndCredentials() throws Exception {
        options = new WebDavSyncSettings.Options(options.url, "", "", false, true, true, true, false);
        WebDavSyncSettings.save(options);
        Device a = device("a"), b = device("b");
        Config ac = config(a.db, SUBSCRIPTION, 0);
        ac.setJson("private-cached-json"); ac.setHome("device-local-selected-source");
        a.db.getConfigDao().update(ac);
        config(a.db, "https://user:private-password@private.example.test/wex.json", 0);
        config(a.db, "https://private.example.test/wex.json?token=private-token", 0);
        config(b.db, "https://other.example.test/wex.json", 0);
        keep(a.db, ac, KEY); history(a.db, ac, KEY, 21_000);
        sync(a); sync(b);
        Config imported = b.db.getConfigDao().find(SUBSCRIPTION, 0);
        assertNotNull(imported); assertNotEquals(ac.getId(), imported.getId());
        assertNull(imported.getJson()); assertNull(imported.getHome());
        assertNotNull(b.db.getKeepDao().find(imported.getId(), KEY));
        assertEquals(21_000, b.db.getHistoryDao().find(imported.getId(), KEY).getPosition());
        String wire = remote.document().encode();
        assertFalse(wire.contains("private-")); assertFalse(wire.contains("device-local"));
        assertEquals(2, b.db.getConfigDao().findAll().size());
    }

    private Device device(String id) {
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        databases.add(db); return new Device(id, db, new WebDavSyncStore(db, options, () -> true));
    }
    private static final class Device {
        final String id; final AppDatabase db; final WebDavSyncStore store;
        Device(String id, AppDatabase db, WebDavSyncStore store) { this.id = id; this.db = db; this.store = store; }
    }
    private SyncCoordinator.Journal journal(String id) { return WebDavSyncStore.journal(new File(directory, id), "fixture-endpoint"); }
    private SyncCoordinator.Applied sync(Device device) throws IOException { return sync(device, device.store, remote.transport()); }
    private SyncCoordinator.Applied sync(Device device, SyncCoordinator.Store store, SyncCoordinator.Remote transport) throws IOException {
        return SyncCoordinator.sync(store, journal(device.id), transport, options.kinds(), "room-device-" + device.id, ++now);
    }
    private static Config config(AppDatabase db, String url, int type) {
        Config config = new Config(); config.setType(type); config.setUrl(url); config.setName("Fixture subscription"); config.setTime(BASE);
        config.setId(Math.toIntExact(db.getConfigDao().insert(config))); return config;
    }
    private static void keep(AppDatabase db, Config config, String key) {
        Keep keep = new Keep(); keep.setKey(key); keep.setCid(config.getId()); keep.setType(Keep.TYPE_VOD);
        keep.setSiteName("Fixture source"); keep.setVodName("Fixture series"); keep.setCreateTime(BASE);
        keep.setVodPic("https://images.example.test/fixture.jpg"); db.getKeepDao().insertOrUpdate(keep);
    }
    private static void history(AppDatabase db, Config config, String key, long position) {
        History history = new History(); history.setKey(key); history.setCid(config.getId()); history.setVodName("Fixture series");
        history.setVodPic("https://images.example.test/fixture.jpg"); history.setVodFlag("Fixture line"); history.setVodRemarks("Episode 2");
        history.setEpisodeUrl("https://media.example.test/episode.m3u8?token=fixture-media-token");
        history.setDuration(600_000); history.setPosition(position); history.setCreateTime(BASE);
        history.setSpeed(1.25f); history.setScale(2); db.getHistoryDao().insertOrUpdate(history);
    }
    private static void progress(AppDatabase db, Config config, String key, long position) {
        History history = db.getHistoryDao().find(config.getId(), key);
        history.setPosition(position); db.getHistoryDao().insertOrUpdate(history);
    }
    private static void remove(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) remove(child);
        assertTrue("Delete only test-owned temporary files", file.delete());
    }

    /** A real loopback server with strict conditional writes; no production TLS option is changed. */
    private static final class DavServer implements AutoCloseable {
        final ServerSocket socket;
        final Thread worker;
        final AtomicReference<Runnable> beforeNextGet = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final OkHttpClient client = new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build();
        volatile boolean closed;
        private String body;
        private int revision;

        DavServer() throws IOException {
            socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(this::serve, "WebDAV-Room-fixture"); worker.setDaemon(true); worker.start();
        }
        WebDavTransport transport() {
            return new WebDavTransport(client, "http://127.0.0.1:" + socket.getLocalPort() + "/tv-sync.json", "fixture-user", "fixture-password");
        }
        synchronized SyncDocument document() { assertNotNull(body); return SyncDocument.decode(body); }
        void assertHealthy() { if (failure.get() != null) throw new AssertionError("Loopback WebDAV server failed", failure.get()); }
        private void serve() {
            try {
                while (!closed) try (Socket connection = socket.accept()) {
                    connection.setSoTimeout(5000); handle(connection);
                }
            } catch (Throwable error) { if (!closed) failure.compareAndSet(null, error); }
        }
        private void handle(Socket connection) throws IOException {
            InputStream input = connection.getInputStream(); String request = line(input);
            Map<String, String> headers = new HashMap<>();
            for (String line; !(line = line(input)).isEmpty(); ) {
                int colon = line.indexOf(':'); if (colon < 1) throw new IOException("Invalid HTTP header");
                headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
            assertEquals(Credentials.basic("fixture-user", "fixture-password"), headers.get("authorization"));
            assertFalse(headers.containsKey("transfer-encoding"));
            int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            if (length < 0 || length > SyncDocument.MAX_BYTES) throw new IOException("Invalid fixture body size");
            byte[] bytes = new byte[length];
            for (int offset = 0; offset < length; ) { int count = input.read(bytes, offset, length - offset); if (count < 0) throw new IOException("Truncated request"); offset += count; }
            int status; String response = "", etag = null;
            if (request.equals("GET /tv-sync.json HTTP/1.1")) {
                Runnable action = beforeNextGet.getAndSet(null); if (action != null) action.run();
                synchronized (this) { status = body == null ? 404 : 200; if (body != null) { response = body; etag = "\"fixture-" + revision + "\""; } }
            } else if (request.equals("PUT /tv-sync.json HTTP/1.1")) {
                synchronized (this) {
                    assertTrue("Every write must be conditional", headers.containsKey("if-match") || headers.containsKey("if-none-match"));
                    boolean matches = body == null ? "*".equals(headers.get("if-none-match")) : ("\"fixture-" + revision + "\"").equals(headers.get("if-match"));
                    if (!matches) status = 412;
                    else { body = SyncDocument.decode(new String(bytes, StandardCharsets.UTF_8)).encode(); revision++; status = 204; }
                }
            } else throw new IOException("Unexpected fixture request");
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + status + " Fixture\r\nConnection: close\r\nContent-Length: " + payload.length + "\r\n"
                    + (etag == null ? "" : "ETag: " + etag + "\r\n") + "Content-Type: application/json\r\n\r\n";
            OutputStream output = connection.getOutputStream(); output.write(head.getBytes(StandardCharsets.US_ASCII)); output.write(payload); output.flush();
        }
        private static String line(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int value; (value = input.read()) != -1; ) {
                if (value == '\n') return bytes.toString(StandardCharsets.US_ASCII.name()).replace("\r", "");
                if (bytes.size() >= 16384) throw new IOException("Fixture header too long"); bytes.write(value);
            }
            throw new IOException("Unexpected end of HTTP request");
        }
        public void close() throws Exception {
            closed = true; socket.close(); worker.join(6000);
            client.connectionPool().evictAll(); client.dispatcher().executorService().shutdownNow();
            assertFalse("Fixture server must stop", worker.isAlive());
        }
    }
}
