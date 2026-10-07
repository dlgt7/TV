package com.fongmi.android.tv.diagnostics;

import android.content.Context;
import android.os.Build;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.crawler.diagnostics.DiagnosticLog;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

/** Android entry points; diagnostics are disabled on every process start. */
public final class DiagnosticManager {
    private static final DiagnosticDownloadGrant DOWNLOAD = new DiagnosticDownloadGrant();
    private static Thread.UncaughtExceptionHandler previousHandler;
    private static Thread.UncaughtExceptionHandler installedHandler;
    private static long session;

    private DiagnosticManager() {}

    public static synchronized void start(Context context) throws IOException {
        if (isEnabled()) return;
        DiagnosticLog.start(new File(context.getApplicationContext().getFilesDir(), "diagnostics"));
        session++;
        DiagnosticLog.record("device", "app=" + BuildConfig.VERSION_NAME + " android=" + Build.VERSION.SDK_INT
                + " manufacturer=" + Build.MANUFACTURER + " model=" + Build.MODEL);
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        final Thread.UncaughtExceptionHandler delegate = previousHandler;
        installedHandler = (thread, error) -> {
            try {
                DiagnosticLog.record("crash", error);
                DiagnosticLog.flushIncident("Uncaught exception on " + thread.getName());
            } catch (Throwable ignored) {
            } finally {
                if (delegate != null) delegate.uncaughtException(thread, error);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(installedHandler);
    }

    public static synchronized void stop() {
        DOWNLOAD.revoke();
        session++;
        DiagnosticLog.stop();
        if (installedHandler != null && Thread.getDefaultUncaughtExceptionHandler() == installedHandler) {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        }
        installedHandler = null;
        previousHandler = null;
    }

    public static boolean isEnabled() { return DiagnosticLog.isEnabled(); }
    public static void markIncident(String reason) { DiagnosticLog.markIncident(reason); }
    public static File exportZip(Context context) throws IOException { return DiagnosticLog.exportZip(); }
    public static synchronized void clear(Context context) throws IOException {
        if (isEnabled()) throw new IOException("Stop diagnostic collection before clearing");
        DOWNLOAD.revoke();
        DiagnosticLog.clear(new File(context.getApplicationContext().getFilesDir(), "diagnostics"));
    }

    /** Worker-thread operation. The returned LAN URL expires in ten minutes or immediately on stop. */
    public static String getDownloadUrl(Context context) throws IOException {
        final long currentSession;
        synchronized (DiagnosticManager.class) { currentSession = session; }
        File file = exportZip(context);
        byte[] archive = DiagnosticLog.readBounded(file, 3 * 1024 * 1024);
        synchronized (DiagnosticManager.class) {
            if (!isEnabled() || currentSession != session) throw new IOException("Diagnostic session ended");
            Server.get().start();
            if (!Server.get().isRunning()) throw new IOException("Local download server unavailable");
            String address = validateDownloadBase(Server.get().getAddress(false));
            return address + "/diagnostics/download?token=" + DOWNLOAD.issue(archive);
        }
    }

    /** No proxy headers are trusted. Even a valid token is usable only from local/private peers. */
    public static synchronized byte[] readDownload(String remoteAddress, String token) {
        if (!isEnabled() || !isLocalPeer(remoteAddress)) return null;
        return DOWNLOAD.read(token);
    }

    public static boolean isLocalPeer(String address) {
        if (address == null || !(address.indexOf(':') >= 0 ? address.matches("[0-9a-fA-F:.]+")
                : address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return false;
        try {
            InetAddress peer = InetAddress.getByName(address);
            if (peer.isLoopbackAddress() || peer.isSiteLocalAddress() || peer.isLinkLocalAddress()) return true;
            byte[] bytes = peer.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc; // IPv6 ULA.
        } catch (Exception ignored) { return false; }
    }

    static String validateDownloadBase(String address) throws IOException {
        try {
            URI uri = new URI(address);
            if (!"http".equals(uri.getScheme()) || !isLocalPeer(uri.getHost()) || uri.getPort() < 1
                    || uri.getPort() > 65535 || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !uri.getPath().isEmpty()) throw new IOException("No local network address available");
            return address;
        } catch (java.net.URISyntaxException | NullPointerException error) {
            throw new IOException("No local network address available", error);
        }
    }
}
