package com.fongmi.quickjs.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.github.catvod.utils.Prefers;
import com.github.catvod.crawler.diagnostics.DiagnosticLog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickLog {

    private static final String LOG_TAG = "QuickLog";
    private static final List<String> logs = new ArrayList<>();
    private static final int MAX_LOG_SIZE = 3000;
    private static final String ENABLED = "quickjs_log_enabled";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    public static void d(String tag, String message, Object... args) {
        write(Log.DEBUG, "DEBUG", tag, format(message, args), null);
    }

    public static void i(String tag, String message, Object... args) {
        write(Log.INFO, "INFO", tag, format(message, args), null);
    }

    public static void w(String tag, String message, Object... args) {
        write(Log.WARN, "WARN", tag, format(message, args), null);
    }

    public static void e(String tag, String message, Object... args) {
        write(Log.ERROR, "ERROR", tag, format(message, args), null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        write(Log.ERROR, "ERROR", tag, message, throwable);
    }

    public static void clear() {
        synchronized (logs) {
            logs.clear();
        }
    }

    public static boolean isEnabled() {
        return Prefers.getBoolean(ENABLED, true);
    }

    public static void putEnabled(boolean enabled) {
        Prefers.put(ENABLED, enabled);
    }

    public static String exportToFile(Context context) {
        synchronized (logs) {
            if (logs.isEmpty()) return null;
            try {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists() && !downloadDir.mkdirs()) return null;

                String fileName = "quickjs_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
                File logFile = new File(downloadDir, fileName);

                try (FileWriter writer = new FileWriter(logFile)) {
                    writer.write("=== QuickJS 调试日志 ===\n");
                    writer.write("导出时间: " + dateFormat.format(new Date()) + "\n");
                    writer.write("日志条数: " + logs.size() + "\n");
                    writer.write("====================\n\n");
                    for (String log : logs) writer.write(log + "\n");
                }

                return logFile.getAbsolutePath();
            } catch (IOException e) {
                Log.e(LOG_TAG, "导出日志失败: " + e.getMessage(), e);
                return null;
            }
        }
    }

    public static List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public static int getLogCount() {
        synchronized (logs) {
            return logs.size();
        }
    }

    private static void write(int priority, String level, String tag, String message, Throwable throwable) {
        DiagnosticLog.record("quickjs:" + tag, level + ": " + message);
        if (throwable != null) DiagnosticLog.record("quickjs:" + tag, throwable);
        if (!isEnabled()) return;
        synchronized (logs) {
            String logEntry = String.format(Locale.getDefault(), "[%s] %s-%s: %s", dateFormat.format(new Date()), level, tag, message);
            logs.add(logEntry);
            if (throwable != null) addStackTrace(throwable);
            trim();
            if (throwable == null) Log.println(priority, LOG_TAG, logEntry);
            else Log.e(LOG_TAG, logEntry, throwable);
        }
    }

    private static void addStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        for (String line : stringWriter.toString().split("\n")) {
            logs.add(line.replace("\r", ""));
        }
    }

    private static void trim() {
        while (logs.size() > MAX_LOG_SIZE) logs.remove(0);
    }

    private static String format(String message, Object... args) {
        if (args == null || args.length == 0) return message;
        try {
            return String.format(Locale.getDefault(), message, args);
        } catch (Throwable ignored) {
            return message;
        }
    }
}
