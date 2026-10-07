package com.github.catvod.crawler;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;
import com.github.catvod.crawler.diagnostics.DiagnosticLog;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    public static void log(Throwable th) {
        DiagnosticLog.record("jar", th);
        if (th != null) th.printStackTrace();
    }

    public static void log(String msg) {
        DiagnosticLog.record("jar", msg);
        if (!TextUtils.isEmpty(msg)) Logger.t(TAG).d(msg);
    }

    public static void log(String tag, String msg, Object... args) {
        if (DiagnosticLog.isEnabled()) {
            String formatted = msg;
            try {
                if (args != null && args.length > 0) formatted = String.format(java.util.Locale.ROOT, msg, args);
            } catch (RuntimeException ignored) {}
            DiagnosticLog.record("jar:" + tag, formatted);
        }
        if (!TextUtils.isEmpty(msg)) Logger.t(tag).d(msg, args);
    }
}
