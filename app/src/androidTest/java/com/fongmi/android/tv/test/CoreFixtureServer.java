package com.fongmi.android.tv.test;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/** Optional loopback fixture server. Enable with -e core_fixture_root core-fixtures. */
public final class CoreFixtureServer {
    public static final String LOCAL_BASE_URL = "http://127.0.0.1:9982/";
    private static LocalServer server;

    private CoreFixtureServer() {}

    public static synchronized void ensureStarted() {
        String configured = InstrumentationRegistry.getArguments().getString("core_fixture_root");
        if (configured == null || configured.trim().isEmpty()) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            File allowed = new File(context.getFilesDir(), "core-fixtures").getCanonicalFile();
            File root = new File(configured);
            if (!root.isAbsolute()) root = new File(context.getFilesDir(), configured);
            root = root.getCanonicalFile();
            if (!root.equals(allowed)) throw new IOException("core_fixture_root must be " + allowed);
            if (!root.isDirectory()) throw new IOException("Missing fixture directory: " + root);
            if (server != null) return;
            LocalServer starting = new LocalServer(root);
            try {
                starting.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
                server = starting;
            } catch (IOException | RuntimeException e) {
                starting.stop();
                throw e;
            }
        } catch (IOException e) {
            throw new AssertionError("Could not start device-local fixture server", e);
        }
    }

    public static synchronized String baseUrl() {
        return server != null ? LOCAL_BASE_URL : InstrumentationRegistry.getArguments()
                .getString("core_base", "http://10.0.2.2:9980/");
    }

    public static synchronized void stop() {
        if (server == null) return;
        try {
            server.stop();
        } finally {
            server = null;
        }
    }

    private static final class LocalServer extends NanoHTTPD {
        private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");
        private final File root;
        private final String rootPrefix;

        LocalServer(File root) {
            super("127.0.0.1", 9982);
            this.root = root;
            rootPrefix = root.getPath() + File.separator;
        }

        @Override protected boolean useGzipWhenAccepted(Response response) { return false; }

        @Override public Response serve(IHTTPSession session) {
            Response response = serveFixture(session);
            // NanoHTTPD 2.3.1 also tries to read HEAD error bodies; keep their length but send no bytes.
            if (session.getMethod() == Method.HEAD) response.setData(new ByteArrayInputStream(new byte[0]));
            return response;
        }

        private Response serveFixture(IHTTPSession session) {
            if (session.getMethod() != Method.GET && session.getMethod() != Method.HEAD) {
                Response response = text(Response.Status.METHOD_NOT_ALLOWED, "GET or HEAD required");
                response.addHeader("Allow", "GET, HEAD");
                return response;
            }
            try {
                String path = session.getUri();
                while (path.startsWith("/")) path = path.substring(1);
                File file = new File(root, path).getCanonicalFile();
                if (!file.getPath().startsWith(rootPrefix)) return text(Response.Status.FORBIDDEN, "Outside fixture root");
                if (!file.isFile()) return text(Response.Status.NOT_FOUND, "Fixture not found");
                String name = file.getName();
                if ((name.endsWith(".mp4") || name.endsWith(".mkv"))
                        && !name.equals(session.getHeaders().get("x-core-media"))) {
                    return text(Response.Status.FORBIDDEN, "Wrong per-item header");
                }
                long size = file.length();
                long start = 0;
                long end = size - 1;
                String range = session.getMethod() == Method.GET ? session.getHeaders().get("range") : null;
                if (range != null) {
                    Matcher match = RANGE.matcher(range.trim());
                    if (!match.matches() || size == 0) return unsatisfiable(size);
                    try {
                        if (match.group(1).isEmpty()) {
                            if (match.group(2).isEmpty()) return unsatisfiable(size);
                            long suffix = Long.parseLong(match.group(2));
                            if (suffix == 0) return unsatisfiable(size);
                            start = Math.max(0, size - suffix);
                        } else {
                            start = Long.parseLong(match.group(1));
                            if (!match.group(2).isEmpty()) end = Math.min(end, Long.parseLong(match.group(2)));
                        }
                    } catch (NumberFormatException e) {
                        return unsatisfiable(size);
                    }
                    if (start >= size || start > end) return unsatisfiable(size);
                }
                long length = end - start + 1;
                InputStream input;
                if (session.getMethod() == Method.HEAD) input = new ByteArrayInputStream(new byte[0]);
                else {
                    FileInputStream fileInput = new FileInputStream(file);
                    try {
                        fileInput.getChannel().position(start);
                        input = fileInput;
                    } catch (IOException | RuntimeException e) {
                        fileInput.close();
                        throw e;
                    }
                }
                Response response;
                try {
                    response = newFixedLengthResponse(range == null ? Response.Status.OK : Response.Status.PARTIAL_CONTENT,
                            getMimeTypeForFile(name), input, length);
                } catch (RuntimeException e) {
                    input.close();
                    throw e;
                }
                // NanoHTTPD closes the response stream after sending or when a client disconnects.
                response.setGzipEncoding(false);
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader("Cache-Control", "no-store");
                if (range != null) response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
                return response;
            } catch (IOException e) {
                return text(Response.Status.INTERNAL_ERROR, "Could not read fixture");
            }
        }

        private Response unsatisfiable(long size) {
            Response response = text(Response.Status.RANGE_NOT_SATISFIABLE, "Invalid byte range");
            response.addHeader("Content-Range", "bytes */" + size);
            response.addHeader("Accept-Ranges", "bytes");
            return response;
        }

        private Response text(Response.Status status, String message) {
            Response response = newFixedLengthResponse(status, MIME_PLAINTEXT, message);
            response.setGzipEncoding(false);
            return response;
        }
    }
}
