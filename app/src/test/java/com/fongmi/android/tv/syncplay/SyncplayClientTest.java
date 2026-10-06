package com.fongmi.android.tv.syncplay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SyncplayClientTest {
    @Test(timeout = 10000)
    public void roomSnapshotRequestSurvivesSerializationOnTheActualSocket() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        AtomicReference<String> clientFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5000);
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    OutputStream output = socket.getOutputStream();
                    assertTrue(JsonParser.parseString(input.readLine()).getAsJsonObject().has("Hello"));
                    output.write(("{\"Hello\":{\"username\":\"tv\",\"room\":{\"name\":\"room\"},\"version\":\"1.7.7\"}}\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    JsonObject request = JsonParser.parseString(input.readLine()).getAsJsonObject();
                    assertTrue("An empty object does not request a room snapshot", request.has("List"));
                    assertTrue(request.get("List").isJsonNull());
                    output.write("{\"List\":{\"room\":{\"tv\":{\"file\":{}}}}}\r\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    assertTrue(complete.await(3, TimeUnit.SECONDS));
                } catch (Throwable error) { serverFailure.set(error); complete.countDown(); }
            }, "Syncplay-wire-test");
            peer.start();
            SyncplayClient client = new SyncplayClient(new SyncplayClient.Listener() {
                @Override public void onTransport(boolean secure) { }
                @Override public void onHello(String username, String room) { }
                @Override public void onState(SyncplayProtocol.RemoteState state) { }
                @Override public void onMembers(List<SyncplayProtocol.Member> members, boolean listed) {
                    if (listed && members.size() == 1) complete.countDown();
                }
                @Override public void onError(String message) { clientFailure.set(message); }
                @Override public void onDisconnected(String reason) { }
            });
            try {
                client.connect(new SyncplayConfig("127.0.0.1", server.getLocalPort(), "tv", "room", "", false));
                assertTrue("The real transport must receive the complete room list", complete.await(6, TimeUnit.SECONDS));
                peer.join(3500);
                assertFalse("Peer did not finish", peer.isAlive());
                if (serverFailure.get() != null) throw new AssertionError(serverFailure.get());
                assertNull(clientFailure.get());
            } finally {
                client.close();
            }
        }
    }
}
