package com.fongmi.android.tv.diagnostics;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class DiagnosticDownloadGrantTest {
    @Test public void deniesMissingWrongExpiredAndRevokedTokens() {
        AtomicLong time = new AtomicLong(100);
        DiagnosticDownloadGrant grant = new DiagnosticDownloadGrant(time::get);
        assertNull(grant.read("anything"));
        String token = grant.issue(new byte[]{1, 2, 3});
        assertEquals(48, token.length());
        assertNull(grant.read(null));
        assertNull(grant.read("0".repeat(48)));
        assertArrayEquals(new byte[]{1, 2, 3}, grant.read(token));
        time.addAndGet(DiagnosticDownloadGrant.LIFETIME_MILLIS);
        assertNull(grant.read(token));
        token = grant.issue(new byte[]{4});
        grant.revoke();
        assertNull(grant.read(token));
    }

    @Test public void reissueRevokesEarlierUrlAndDoesNotExposeMutableArchive() {
        DiagnosticDownloadGrant grant = new DiagnosticDownloadGrant(() -> 0L);
        byte[] archive = {1, 2};
        String old = grant.issue(archive);
        archive[0] = 9;
        byte[] read = grant.read(old);
        assertEquals(1, read[0]);
        read[0] = 8;
        assertEquals(1, grant.read(old)[0]);
        String current = grant.issue(new byte[]{3});
        assertNotEquals(old, current);
        assertNull(grant.read(old));
        assertArrayEquals(new byte[]{3}, grant.read(current));
    }
}
