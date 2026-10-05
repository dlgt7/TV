package com.fongmi.android.tv.test;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.StatFs;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.ai.subtitle.AiLanguage;
import com.fongmi.android.tv.ai.subtitle.AsrModel;
import com.fongmi.android.tv.ai.subtitle.AsrModelManager;
import com.fongmi.android.tv.ai.subtitle.ModelFile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exercises the production model downloader in the isolated sourceprobe package, without playback. */
@RunWith(AndroidJUnit4.class)
public final class AsrModelDownloadTest {
    private static final long BUDGET_MS = 240_000L;
    private static final Pattern HTTP_STATUS = Pattern.compile("(?i)\\bHTTP\\s*(?:status\\s*)?[:=]?\\s*([1-5][0-9]{2})\\b");
    private final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final AsrModel model = AsrModel.MANDARIN_ZIPFORMER_CTC;
    private final JSONObject report = new JSONObject();
    private final JSONArray progress = new JSONArray();
    private final ConcurrentLinkedQueue<Progress> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<DownloadError> callbackError = new AtomicReference<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private AsrModelManager models;
    private File reportFile;
    private long started;
    private long requiredBytes;
    private boolean timedOut;

    @Test(timeout = 270_000L)
    public void productionDownloaderInstallsVerifiedMandarinModel() throws Exception {
        started = SystemClock.elapsedRealtime();
        long deadline = started + BUDGET_MS - 5000L;
        boolean passed = false;
        assertTrue("Model download test requires the isolated sourceprobe package",
                "com.fongmi.android.tv.sourceprobe".equals(target.getPackageName()));
        try {
            File external = target.getExternalFilesDir(null);
            if (external == null) throw new IllegalStateException("REPORT_DIRECTORY_UNAVAILABLE");
            reportFile = new File(new File(external, "source-validation"), "asr-model-download.json");
            report.put("schema", "tv.asr-model-download.v1");
            report.put("status", "RUNNING");
            report.put("startedAtEpochMs", System.currentTimeMillis());
            report.put("budgetSeconds", 240);
            report.put("completionWaitBudgetSeconds", 235);
            report.put("finalizationReserveSeconds", 5);
            report.put("junitTimeoutSeconds", 270);
            report.put("model", model.folder);
            report.put("modelTotalBytes", model.downloadBytes());
            report.put("progress", progress);
            report.put("progressByteEstimateBasis", "PRODUCTION_PERCENT_TIMES_REQUIRED_BYTES");
            report.put("presentBytesAreFileSizesNotIntegrityProof", true);

            models = new AsrModelManager(target);
            report.put("installedBefore", models.isInstalled(AiLanguage.MANDARIN));
            requiredBytes = models.requiredBytes(AiLanguage.MANDARIN);
            report.put("requiredBytesBefore", requiredBytes);
            report.put("totalRamMb", models.getTotalRamMb());
            report.put("requiredRamMb", model.minRamMb);
            report.put("ramSufficient", models.canRun(AiLanguage.MANDARIN));
            report.put("storageAvailableBytesBefore", availableBytes());
            report.put("storageSufficient", models.hasEnoughStorage(AiLanguage.MANDARIN));
            report.put("filesBefore", fileMetadata());
            writeReport();

            // Uses the production URLs, downloader, size checks, SHA-256 checks and atomic commit.
            models.download(AiLanguage.MANDARIN, new AsrModelManager.Listener() {
                @Override public void onProgress(int percent, String fileName) {
                    pending.add(new Progress(SystemClock.elapsedRealtime() - started,
                            Math.max(0, Math.min(100, percent)), knownFileName(fileName), presentBytes()));
                }

                @Override public void onComplete() {
                    completed.set(true);
                    finished.countDown();
                }

                @Override public void onError(String message) {
                    callbackError.set(classify(message));
                    finished.countDown();
                }
            });

            while (finished.getCount() != 0) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) {
                    timedOut = true;
                    break;
                }
                finished.await(Math.min(1000, remaining), TimeUnit.MILLISECONDS);
                drainProgress();
                report.put("elapsedMs", SystemClock.elapsedRealtime() - started);
                report.put("presentBytes", presentBytes());
                report.put("downloading", models.isDownloading());
                writeReport();
            }
            drainProgress();
            report.put("onComplete", completed.get());
            report.put("onError", callbackError.get() != null);
            report.put("timedOut", timedOut);
            DownloadError error = callbackError.get();
            if (error != null) {
                report.put("errorType", error.type);
                if (error.httpStatus != null) report.put("httpStatus", error.httpStatus);
            }
            // This is the production validation, including the fixed model digests.
            boolean installed = !timedOut && models.isInstalled(AiLanguage.MANDARIN);
            report.put("installedAfter", installed);
            report.put("fixedShaVerificationPassed", installed);
            report.put("cacheHitOnly", requiredBytes == 0);
            report.put("applicationDownloadCompleted", requiredBytes > 0 && completed.get() && installed);
            report.put("filesAfter", fileMetadata());
            report.put("storageAvailableBytesAfter", availableBytes());
            passed = completed.get() && error == null && installed && !timedOut;
            report.put("status", passed ? "PASS" : timedOut ? "LIMITATION" : "FAIL");
            if (timedOut) report.put("errorType", "MODEL_DOWNLOAD_DEADLINE_EXCEEDED");
            else if (completed.get() && !installed) report.put("errorType", "POST_DOWNLOAD_INTEGRITY_FAILURE");
        } catch (Throwable error) {
            passed = false;
            report.put("status", "FAIL");
            report.put("errorType", "TEST_" + error.getClass().getSimpleName());
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            drainProgress();
            stopOwnedExecutor();
            report.put("onComplete", completed.get());
            report.put("onError", callbackError.get() != null);
            report.put("elapsedMs", SystemClock.elapsedRealtime() - started);
            report.put("finishedAtEpochMs", System.currentTimeMillis());
            if (models != null) report.put("workerStillDownloading", models.isDownloading());
            writeReport();
        }
        assertTrue("Production model download/verification did not complete; see source-validation/asr-model-download.json", passed);
    }

    private void drainProgress() throws Exception {
        Progress item;
        while ((item = pending.poll()) != null) {
            // The production listener supplies percent, not transferred byte counts. Keep the
            // calculated estimate distinct from measured temporary/final file sizes.
            if (progress.length() < 512) progress.put(new JSONObject()
                    .put("elapsedMs", item.elapsedMs)
                    .put("percent", item.percent)
                    .put("file", item.file)
                    .put("estimatedDownloadedBytes", requiredBytes * item.percent / 100)
                    .put("totalBytes", requiredBytes)
                    .put("presentBytes", item.presentBytes));
        }
    }

    private long availableBytes() {
        return new StatFs(target.getFilesDir().getAbsolutePath()).getAvailableBytes();
    }

    private long presentBytes() {
        if (models == null) return 0;
        long total = 0;
        File directory = models.modelDir(model);
        for (ModelFile file : model.files) {
            File destination = new File(directory, file.relativePath);
            File partial = new File(directory, file.relativePath + ".part");
            total += partial.isFile() ? partial.length() : destination.isFile() ? destination.length() : 0;
        }
        return total;
    }

    private JSONArray fileMetadata() throws Exception {
        JSONArray files = new JSONArray();
        File directory = models.modelDir(model);
        for (ModelFile file : model.files) {
            File destination = new File(directory, file.relativePath);
            File partial = new File(directory, file.relativePath + ".part");
            files.put(new JSONObject().put("file", file.relativePath).put("expectedBytes", file.size)
                    .put("expectedSha256", file.sha256)
                    .put("finalBytes", destination.isFile() ? destination.length() : 0)
                    .put("partialBytes", partial.isFile() ? partial.length() : 0));
        }
        return files;
    }

    private String knownFileName(String value) {
        for (ModelFile file : model.files) if (file.relativePath.equals(value)) return value;
        return "UNKNOWN_MODEL_FILE";
    }

    private static DownloadError classify(String message) {
        String value = message == null ? "" : message;
        Matcher matcher = HTTP_STATUS.matcher(value);
        Integer status = matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
        String type = status != null ? "MODEL_DOWNLOAD_HTTP_ERROR"
                : value.startsWith("设备内存不足") ? "INSUFFICIENT_RAM"
                : value.startsWith("存储空间不足") ? "INSUFFICIENT_STORAGE"
                : value.startsWith("已有语言包正在下载") ? "DOWNLOAD_ALREADY_RUNNING"
                : value.contains("校验失败") ? "MODEL_INTEGRITY_FAILURE"
                : value.contains("下载已取消") ? "DOWNLOAD_CANCELLED"
                : "MODEL_DOWNLOAD_ERROR";
        return new DownloadError(type, status);
    }

    private void stopOwnedExecutor() throws Exception {
        if (models == null) return;
        try {
            // The manager has no public close/cancel API. Release only this test-created
            // manager's worker, never a shared HTTP client or another app's model manager.
            Field field = AsrModelManager.class.getDeclaredField("downloads");
            field.setAccessible(true);
            ExecutorService executor = (ExecutorService) field.get(models);
            if (timedOut || models.isDownloading() && !completed.get()) executor.shutdownNow();
            else executor.shutdown();
            report.put("ownedExecutorShutdown", true);
        } catch (ReflectiveOperationException error) {
            report.put("ownedExecutorShutdown", false);
            report.put("cleanupErrorType", error.getClass().getSimpleName());
        }
    }

    private void writeReport() throws Exception {
        if (reportFile == null) return;
        File directory = reportFile.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("REPORT_DIRECTORY_UNAVAILABLE");
        File temporary = new File(directory, reportFile.getName() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary)) {
            stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(reportFile)) throw new IllegalStateException("REPORT_REPLACE_FAILED");
    }

    private record Progress(long elapsedMs, int percent, String file, long presentBytes) {}
    private record DownloadError(String type, Integer httpStatus) {}
}
