package com.fongmi.android.tv.test;

import static org.junit.Assert.*;

import android.os.SystemClock;

import com.fongmi.android.tv.player.media.PlaySpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Raw HTTP evidence; no automatic Range skipping, public URLs, headers or body bytes. */
final class SourceStreamProbe {
    private static final long LIMIT = 64L * 1024 * 1024;

    static void downloadAndCheck(PlaySpec spec, File file, JSONObject report) throws Exception {
        HttpURLConnection connection = open(spec, null);
        long total = 0;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        JSONObject full = new JSONObject();
        report.put("fullGet", full);
        try {
            metadata(connection, full);
            assertEquals("Full download must return HTTP 200", 200, connection.getResponseCode());
            assertTrue("Full source file exceeds 64 MiB diagnostic limit", full.getLong("contentLength") <= LIMIT);
            long deadline = SystemClock.elapsedRealtime() + 120_000;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    assertTrue("Full source file exceeds 64 MiB diagnostic limit", total + read <= LIMIT);
                    assertTrue("Full download exceeded two minutes", SystemClock.elapsedRealtime() < deadline);
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    total += read;
                }
            }
            long expected = full.getLong("contentLength");
            assertTrue("Full source download was empty or incomplete", total > 0 && (expected < 0 || total == expected));
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            full.put("sha256", hash.toString()).put("complete", true);
        } finally {
            full.put("actualBytes", total);
            connection.disconnect();
        }
        JSONArray checks = new JSONArray();
        report.put("rangeChecks", checks);
        boolean allCorrect = true;
        try (RandomAccessFile reference = new RandomAccessFile(file, "r")) {
            long[] starts = {0, 4096, 1024 * 1024, Math.max(0, total - 4096), 1024 * 1024};
            for (int i = 0; i < starts.length; i++) {
                long start = starts[i];
                boolean openEnded = i == starts.length - 1;
                JSONObject check = new JSONObject().put("requestedStart", start)
                        .put("requestKind", openEnded ? "OPEN_ENDED" : "CLOSED").put("prefixOnly", openEnded);
                checks.put(check);
                if (start >= total) {
                    check.put("status", "FILE_TOO_SHORT").put("correct", false);
                    allCorrect = false;
                    continue;
                }
                int length = (int) Math.min(4096, total - start);
                if (!openEnded) check.put("requestedEnd", start + length - 1);
                check.put("comparedEnd", start + length - 1);
                byte[] expected = new byte[length];
                reference.seek(start);
                reference.readFully(expected);
                try { checkRange(spec, start, total, expected, openEnded, check); }
                catch (Exception | AssertionError failure) {
                    check.put("correct", false).put("failureType", failure.getClass().getSimpleName());
                }
                allCorrect &= check.optBoolean("correct");
            }
        }
        report.put("rangeChecksPass", allCorrect);
    }

    private static void checkRange(PlaySpec spec, long start, long fileLength, byte[] expected, boolean openEnded, JSONObject out) throws Exception {
        long end = openEnded ? fileLength - 1 : start + expected.length - 1, count = 0, difference = -1;
        HttpURLConnection connection = open(spec, "bytes=" + start + "-" + (openEnded ? "" : end));
        try {
            metadata(connection, out);
            long deadline = SystemClock.elapsedRealtime() + 60_000;
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer, 0, openEnded ? (int) Math.min(buffer.length, expected.length - count) : buffer.length)) != -1) {
                    for (int i = 0; i < read && difference < 0; i++) {
                        long offset = count + i;
                        if (offset >= expected.length || buffer[i] != expected[(int) offset]) difference = start + offset;
                    }
                    count += read;
                    assertTrue("Range response exceeds diagnostic limit", count <= LIMIT);
                    assertTrue("Range request exceeded one minute", SystemClock.elapsedRealtime() < deadline);
                    if (openEnded && count == expected.length) break;
                }
            }
            if (count < expected.length && difference < 0) difference = start + count;
            boolean bytesMatch = count == expected.length && difference < 0;
            JSONObject range = out.optJSONObject("contentRange");
            boolean headersMatch = out.getInt("status") == 206 && range != null
                    && range.optLong("start", -1) == start && range.optLong("end", -1) == end
                    && range.optLong("total", -1) == fileLength
                    && (out.getLong("contentLength") < 0 || out.getLong("contentLength") == end - start + 1);
            out.put("bytesMatch", bytesMatch).put("headersMatch", headersMatch)
                    .put("correct", bytesMatch && headersMatch);
        } finally {
            out.put("actualBytes", count).put("firstDifferenceOffset", difference);
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(PlaySpec spec, String range) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(spec.getUrl()).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        spec.getHeaders().forEach(connection::setRequestProperty);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (range != null) connection.setRequestProperty("Range", range);
        return connection;
    }

    private static void metadata(HttpURLConnection connection, JSONObject out) throws Exception {
        out.put("status", connection.getResponseCode());
        String length = connection.getHeaderField("Content-Length");
        out.put("contentLength", length != null && length.matches("[0-9]{1,18}") ? Long.parseLong(length) : -1);
        String value = connection.getHeaderField("Content-Range");
        out.put("contentRangePresent", value != null);
        if (value == null) return;
        Matcher range = Pattern.compile("bytes ([0-9]{1,18})-([0-9]{1,18})/([0-9]{1,18}|\\*)").matcher(value);
        if (range.matches()) out.put("contentRange", new JSONObject().put("start", Long.parseLong(range.group(1)))
                .put("end", Long.parseLong(range.group(2)))
                .put("total", "*".equals(range.group(3)) ? -1 : Long.parseLong(range.group(3))));
    }
}
