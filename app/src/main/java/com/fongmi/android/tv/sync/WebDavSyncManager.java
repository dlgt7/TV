package com.fongmi.android.tv.sync;

import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Nonblocking public entry points for settings and application lifecycle hooks. */
public final class WebDavSyncManager {
    public interface Callback { void complete(Result result); }
    public static final class Result {
        public final boolean success;
        public final String error;
        public final int updated, deleted, skipped;
        private Result(boolean success, String error, int updated, int deleted, int skipped) {
            this.success = success; this.error = error; this.updated = updated; this.deleted = deleted; this.skipped = skipped;
        }
    }
    private static final WebDavSyncManager INSTANCE = new WebDavSyncManager();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicLong cancellation = new AtomicLong();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "WebDav-sync"); thread.setDaemon(true); return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile WebDavTransport active;
    private volatile long lastAutomaticAttempt;
    private WebDavSyncManager() { }
    public static WebDavSyncManager get() { return INSTANCE; }
    public boolean isRunning() { return busy.get(); }
    public void cancel() { cancellation.incrementAndGet(); WebDavTransport transport = active; if (transport != null) transport.cancel(); }
    void configurationChanged() { cancel(); lastAutomaticAttempt = 0; }
    public void maybeAutoSync() { maybeAutoSync(false); }
    public void maybeAutoSyncOnBackground() { maybeAutoSync(true); }
    private void maybeAutoSync(boolean background) {
        WebDavSyncSettings.Options options = WebDavSyncSettings.get(); long now = System.currentTimeMillis();
        if (!options.automatic || !options.configured() || (!background && now - WebDavSyncSettings.lastSuccess() < 15 * 60_000L)
                || now - lastAutomaticAttempt < 60_000L || busy.get()) return;
        lastAutomaticAttempt = now; sync(null);
    }
    public void sync(Callback callback) {
        long generation = cancellation.get();
        if (!busy.compareAndSet(false, true)) { deliver(callback, new Result(false, "SYNC_BUSY", 0, 0, 0)); return; }
        worker.execute(() -> {
            Result result;
            try {
                WebDavSyncSettings.Options options = WebDavSyncSettings.get();
                if (!options.configured()) throw new SyncCoordinator.Failure("SYNC_NOT_CONFIGURED");
                if (options.kinds().isEmpty()) throw new SyncCoordinator.Failure("SELECT_SYNC_DATA");
                WebDavTransport transport = new WebDavTransport(options.url, options.username, options.password); active = transport;
                if (generation != cancellation.get()) transport.cancel();
                WebDavSyncStore store = new WebDavSyncStore(AppDatabase.get(), options, () -> generation == cancellation.get());
                File directory = new File(App.get().getFilesDir(), "webdav-sync");
                SyncCoordinator.Applied applied = SyncCoordinator.sync(store, WebDavSyncStore.journal(directory, options.endpointKey()), transport,
                        options.kinds(), WebDavSyncSettings.deviceId(), System.currentTimeMillis());
                WebDavSyncSettings.succeeded(options.endpointKey());
                if (applied.updated + applied.deleted > 0) main.post(() -> { RefreshEvent.keep(); RefreshEvent.history(); });
                result = new Result(true, "", applied.updated, applied.deleted, applied.skipped);
            } catch (SyncCoordinator.Failure failure) {
                result = new Result(false, failure.code, 0, 0, 0);
            } catch (IOException failure) {
                result = new Result(false, "SYNC_NETWORK_OR_STORAGE_ERROR", 0, 0, 0);
            } catch (RuntimeException failure) {
                result = new Result(false, "SYNC_LOCAL_STATE_ERROR", 0, 0, 0);
            } finally { active = null; busy.set(false); }
            deliver(callback, result);
        });
    }
    private void deliver(Callback callback, Result result) { if (callback != null) main.post(() -> callback.complete(result)); }
}
