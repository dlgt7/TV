package com.fongmi.android.tv.diagnostics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.function.LongSupplier;

/** A single expiring capability, created only by a local user action. Uses monotonic time. */
public final class DiagnosticDownloadGrant {
    public static final long LIFETIME_MILLIS = 10 * 60 * 1000;
    private final LongSupplier now;
    private byte[] token;
    private byte[] archive;
    private long expires;

    public DiagnosticDownloadGrant() { this(() -> System.nanoTime() / 1_000_000); }
    DiagnosticDownloadGrant(LongSupplier now) { this.now = now; }

    public synchronized String issue(byte[] archive) {
        if (archive == null || archive.length == 0 || archive.length > 3 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid diagnostic archive");
        }
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        StringBuilder value = new StringBuilder();
        for (byte b : random) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        token = value.toString().getBytes(StandardCharsets.US_ASCII);
        this.archive = archive.clone();
        expires = now.getAsLong() + LIFETIME_MILLIS;
        return value.toString();
    }

    public synchronized byte[] read(String candidate) {
        if (token == null) return null;
        if (now.getAsLong() >= expires) { revoke(); return null; }
        if (candidate == null || candidate.length() != 48 || !MessageDigest.isEqual(token, candidate.getBytes(StandardCharsets.US_ASCII))) return null;
        return archive.clone();
    }

    public synchronized void revoke() { token = null; archive = null; expires = 0; }
}
