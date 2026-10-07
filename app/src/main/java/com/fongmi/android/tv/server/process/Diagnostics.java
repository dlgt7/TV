package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.diagnostics.DiagnosticManager;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.function.BiFunction;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/** Deliberately no start, stop, clear, export, listing or arbitrary-file HTTP operation. */
public final class Diagnostics implements Process {
    private final BiFunction<String, String, byte[]> reader;

    public Diagnostics() { this(DiagnosticManager::readDownload); }
    Diagnostics(BiFunction<String, String, byte[]> reader) { this.reader = reader; }

    @Override
    public boolean isRequest(IHTTPSession session, String url) { return url.startsWith("/diagnostics"); }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        Response response;
        if (session.getMethod() != NanoHTTPD.Method.GET || !"/diagnostics/download".equals(url)) {
            response = Nano.error(Response.Status.NOT_FOUND, "Not found");
        } else {
            byte[] data = reader.apply(session.getRemoteIpAddress(), session.getParms().get("token"));
            if (data == null) response = Nano.error(Response.Status.NOT_FOUND, "Download unavailable or expired");
            else {
                response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/zip", new ByteArrayInputStream(data), data.length);
                response.addHeader("Content-Disposition", "attachment; filename=\"tv-diagnostics.zip\"");
            }
        }
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Referrer-Policy", "no-referrer");
        response.addHeader("X-Content-Type-Options", "nosniff");
        response.addHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        return response;
    }
}
