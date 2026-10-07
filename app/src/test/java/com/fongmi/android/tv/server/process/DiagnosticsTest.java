package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.diagnostics.DiagnosticDownloadGrant;
import com.fongmi.android.tv.diagnostics.DiagnosticManager;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import static org.junit.Assert.*;

public class DiagnosticsTest {
    @Test public void returnsReadOnlyAttachmentOnlyForPrivatePeerAndValidToken() throws Exception {
        DiagnosticDownloadGrant grant = new DiagnosticDownloadGrant();
        String token = grant.issue(new byte[]{1, 2, 3});
        Diagnostics process = new Diagnostics((remote, candidate) -> DiagnosticManager.isLocalPeer(remote) ? grant.read(candidate) : null);
        try (Response response = process.doResponse(session(NanoHTTPD.Method.GET, "192.168.1.3", token), "/diagnostics/download", Collections.emptyMap())) {
            assertEquals(Response.Status.OK, response.getStatus());
            assertArrayEquals(new byte[]{1, 2, 3}, response.getData().readAllBytes());
            assertEquals("application/zip", response.getMimeType());
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
        }
        assertEquals(Response.Status.NOT_FOUND, process.doResponse(session(NanoHTTPD.Method.GET, "8.8.8.8", token), "/diagnostics/download", Collections.emptyMap()).getStatus());
        assertEquals(Response.Status.NOT_FOUND, process.doResponse(session(NanoHTTPD.Method.GET, "192.168.1.3", "wrong"), "/diagnostics/download", Collections.emptyMap()).getStatus());
        grant.revoke();
        assertEquals(Response.Status.NOT_FOUND, process.doResponse(session(NanoHTTPD.Method.GET, "192.168.1.3", token), "/diagnostics/download", Collections.emptyMap()).getStatus());
    }

    @Test public void rejectsMutationsAndArbitraryPathsWithoutReadingArchive() {
        AtomicInteger calls = new AtomicInteger();
        Diagnostics process = new Diagnostics((remote, token) -> { calls.incrementAndGet(); return new byte[]{1}; });
        for (NanoHTTPD.Method method : new NanoHTTPD.Method[]{NanoHTTPD.Method.POST, NanoHTTPD.Method.PUT, NanoHTTPD.Method.DELETE, NanoHTTPD.Method.HEAD}) {
            assertEquals(Response.Status.NOT_FOUND, process.doResponse(session(method, "127.0.0.1", "token"), "/diagnostics/download", Collections.emptyMap()).getStatus());
        }
        for (String path : new String[]{"/diagnostics", "/diagnostics/start", "/diagnostics/download/../private", "/diagnostics/download/extra"}) {
            assertEquals(Response.Status.NOT_FOUND, process.doResponse(session(NanoHTTPD.Method.GET, "127.0.0.1", "token"), path, Collections.emptyMap()).getStatus());
        }
        assertEquals(0, calls.get());
    }

    private static IHTTPSession session(NanoHTTPD.Method method, String remote, String token) {
        return (IHTTPSession) Proxy.newProxyInstance(IHTTPSession.class.getClassLoader(), new Class[]{IHTTPSession.class}, (proxy, invoked, args) -> {
            switch (invoked.getName()) {
                case "getMethod": return method;
                case "getRemoteIpAddress": return remote;
                case "getParms": return Collections.singletonMap("token", token);
                default: throw new AssertionError("Unexpected session access: " + invoked.getName());
            }
        });
    }
}
