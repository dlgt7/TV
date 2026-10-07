package com.fongmi.android.tv.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticPeerPolicyTest {
    @Test public void acceptsOnlyNumericLocalNetworkPeersWithoutDns() {
        for (String address : new String[]{"127.0.0.1", "192.168.1.20", "10.0.0.8", "172.16.0.1", "169.254.1.1", "::1", "fd01::12", "fe80::abcd"}) {
            assertTrue(address, DiagnosticManager.isLocalPeer(address));
        }
        for (String address : new String[]{"8.8.8.8", "172.15.1.1", "2001:4860:4860::8888", "example.com", "dead.beef", "face", "999.1.1.1", "", "192.168.1.1.attacker.test"}) {
            assertFalse(address, DiagnosticManager.isLocalPeer(address));
        }
        assertFalse(DiagnosticManager.isLocalPeer(null));
    }

    @Test public void rejectsUnavailableOrNonLocalDownloadAddressBeforeIssuingToken() throws Exception {
        assertEquals("http://192.168.1.2:9978", DiagnosticManager.validateDownloadBase("http://192.168.1.2:9978"));
        for (String address : new String[]{"http://:9978", "http://8.8.8.8:9978", "http://example.com:9978", "http://192.168.1.2:-1", "http://u:p@192.168.1.2:9978", "http://192.168.1.2:9978/path"}) {
            try { DiagnosticManager.validateDownloadBase(address); fail(address); }
            catch (java.io.IOException expected) { /* expected */ }
        }
    }
}
