package com.fongmi.android.tv;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.update.UpdateApkValidator;
import com.fongmi.android.tv.update.UpdateDownloader;
import com.fongmi.android.tv.update.UpdateManifest;
import com.fongmi.android.tv.update.UpdatePolicy;
import com.fongmi.android.tv.update.UpdateRepository;
import com.fongmi.android.tv.update.UpdateSources;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;

/** One user-owned update session. Background checks never download an APK. */
public class Updater implements UpdateListener, DefaultLifecycleObserver {

    private static final String LAST_CHECK = "ota_last_check";
    private static final String LAST_PROMPT = "ota_last_prompt_code";
    private static final long CHECK_INTERVAL = 24 * 60 * 60_000L;
    private static Updater active;

    private static OkHttpClient client() {
        // The content-source client permits invalid TLS certificates. OTA must use platform trust.
        return new OkHttpClient.Builder().dns(OkHttp.dns()).proxySelector(OkHttp.selector()).build();
    }

    private FragmentActivity activity;
    private UpdateDialog dialog;
    private UpdateManifest manifest;
    private UpdateRepository repository;
    private UpdateDownloader downloader;
    private Future<?> task;
    private boolean manual;
    private boolean downloading;
    private volatile boolean closed;

    public static Updater create() {
        return new Updater();
    }

    /** A manual check never changes the background notification preference. */
    public Updater force() {
        manual = true;
        return this;
    }

    public void start(FragmentActivity activity) {
        if (closed || this.activity != null || activity.isFinishing() || activity.isDestroyed()) return;
        if (active != null) {
            if (!manual) return;
            if (active.manual || active.dialog != null) {
                Notify.show(R.string.update_busy);
                return;
            }
            active.finish();
        }
        long now = System.currentTimeMillis();
        long last = Prefers.getLong(LAST_CHECK);
        if (!manual && now >= last && now - last < CHECK_INTERVAL) return;
        if (!manual) Prefers.put(LAST_CHECK, now);
        this.activity = activity;
        active = this;
        activity.getLifecycle().addObserver(this);
        repository = new UpdateRepository(client());
        if (manual) Notify.show(R.string.update_check);
        task = Task.submit(this::check);
    }

    private void check() {
        try {
            UpdateManifest result = repository.manifest(BuildConfig.APPLICATION_ID,
                    BuildConfig.FLAVOR_mode, BuildConfig.FLAVOR_abi, BuildConfig.VERSION_CODE);
            UpdatePolicy policy = manual ? null : repository.policy();
            App.post(() -> checked(result, policy));
        } catch (Exception e) {
            App.post(() -> failed(R.string.update_check_failed));
        }
    }

    private boolean canShow() {
        return !closed && activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                && !activity.getSupportFragmentManager().isStateSaved();
    }

    private void checked(UpdateManifest result, UpdatePolicy policy) {
        if (!canShow()) {
            finish();
            return;
        }
        if (result.code <= BuildConfig.VERSION_CODE) {
            if (manual) Notify.show(R.string.update_latest);
            finish();
            return;
        }
        if (!manual && (policy == null || !policy.shouldPrompt(BuildConfig.VERSION_CODE,
                result.code, Prefers.getInt(LAST_PROMPT), System.currentTimeMillis() / 1000))) {
            finish();
            return;
        }
        manifest = result;
        try {
            dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, result.name))
                    .desc(result.desc).listener(this).show(activity);
            Prefers.put(LAST_PROMPT, result.code);
        } catch (Exception e) {
            failed(R.string.update_check_failed);
        }
    }

    @Override
    public void onConfirm(View view) {
        if (closed || downloading || manifest == null || !canShow()) return;
        downloading = true;
        view.setEnabled(false);
        downloader = new UpdateDownloader(client());
        // Capture immutable values: lifecycle cleanup can clear the Activity while this runs.
        UpdateManifest release = manifest;
        File destination = Path.cache("ota/update-" + release.code + "-" + System.nanoTime() + ".apk");
        UpdateApkValidator validator = new UpdateApkValidator(activity.getApplicationContext(), release.code);
        task = Task.submit(() -> {
            try {
                clearOldDownloads(destination.getParentFile());
                File file = downloader.download(UpdateSources.mirrors(release.asset.url),
                        destination, release.asset.size, release.asset.sha256,
                        validator, percent -> App.post(() -> {
                            if (!closed && dialog != null) dialog.setProgress(percent);
                        }));
                App.post(() -> install(file));
            } catch (Exception e) {
                App.post(() -> failed(R.string.update_download_failed));
            }
        });
    }

    private static void clearOldDownloads(File directory) {
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - CHECK_INTERVAL;
        for (File file : files) {
            // Keep recent completed files available while Android's installer is reading them.
            if (file.isFile() && file.getName().startsWith("update-") && file.lastModified() < cutoff) file.delete();
        }
    }

    private void install(File file) {
        if (!canShow()) {
            finish();
            return;
        }
        finish();
        try {
            FileUtil.openFile(file);
        } catch (Exception e) {
            Notify.show(R.string.update_install_failed);
        }
    }

    private void failed(int message) {
        if (!closed && canShow() && (manual || downloading)) Notify.show(message);
        finish();
    }

    @Override
    public void onCancel(View view) {
        finish();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        finish();
    }

    private void finish() {
        if (closed) return;
        closed = true;
        if (repository != null) repository.cancel();
        if (downloader != null) downloader.cancel();
        if (task != null) task.cancel(true);
        if (activity != null) activity.getLifecycle().removeObserver(this);
        if (dialog != null) dialog.dismissAllowingStateLoss();
        if (active == this) active = null;
        activity = null;
    }
}
