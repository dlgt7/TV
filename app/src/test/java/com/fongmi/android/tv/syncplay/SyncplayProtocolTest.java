package com.fongmi.android.tv.syncplay;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SyncplayProtocolTest {
    private final List<JsonObject> sent = new ArrayList<>();
    private final List<SyncplayProtocol.RemoteState> states = new ArrayList<>();
    private List<SyncplayProtocol.Member> members = new ArrayList<>();
    private boolean complete;
    private final SyncplayProtocol protocol = new SyncplayProtocol(
            new SyncplayConfig("localhost", 8999, "TV", "private-room", "secret", false),
            sent::add, new SyncplayProtocol.Listener() {
                @Override public void onHello(String username, String room) { }
                @Override public void onState(SyncplayProtocol.RemoteState state) { states.add(state); }
                @Override public void onMembers(List<SyncplayProtocol.Member> values, boolean listed) { members = values; complete = listed; }
                @Override public void onError(String message) { }
            }, () -> 1000.0);

    private void hello() throws Exception {
        protocol.receive("{\"Hello\":{\"username\":\"TV\",\"room\":{\"name\":\"private-room\"},\"version\":\"1.2.255\",\"realversion\":\"1.7.7\"}}");
        sent.clear();
    }
    private JsonObject state() { return sent.get(sent.size() - 1).getAsJsonObject("State"); }

    @Test public void announcesRealCompatibleVersionAndHashesServerPassword() throws Exception {
        protocol.hello(); JsonObject hello = sent.get(0).getAsJsonObject("Hello");
        assertEquals("1.2.255", hello.get("version").getAsString());
        assertEquals("1.7.7", hello.get("realversion").getAsString());
        assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", hello.get("password").getAsString());
        assertFalse(hello.toString().contains("secret"));
        assertFalse(hello.getAsJsonObject("features").get("sharedPlaylists").getAsBoolean());
        assertFalse(protocol.isLogged());
    }

    @Test public void rejectsIncompleteHelloAndInvalidPositions() throws Exception {
        assertThrows(IOException.class, () -> protocol.receive("{\"Hello\":{\"username\":\"TV\"}}"));
        hello();
        assertThrows(IOException.class, () -> protocol.receive("{\"State\":{\"playstate\":{\"position\":-2,\"paused\":true}}}"));
        assertThrows(IOException.class, () -> protocol.receive("[]"));
    }

    @Test public void sendsOnlyFilenameDurationAndUnknownSizeWithoutMediaUrl() throws Exception {
        hello(); protocol.file("节目 第01集", 1234.5);
        JsonObject file = sent.get(0).getAsJsonObject("Set").getAsJsonObject("file");
        assertEquals(3, file.size()); assertFalse(file.has("path")); assertFalse(file.has("url"));
        assertEquals(1234.5, file.get("duration").getAsDouble(), .001);
        assertTrue(sent.get(1).has("List"));
    }

    @Test public void acknowledgesServerForcedStateAndEchoesAuthoritativePause() throws Exception {
        hello(); protocol.localChange(10, false, true); sent.clear();
        protocol.receive("{\"State\":{\"playstate\":{\"position\":20,\"paused\":true,\"doSeek\":true,\"setBy\":\"Other\"},\"ping\":{\"latencyCalculation\":123},\"ignoringOnTheFly\":{\"server\":7}}}");
        assertEquals(1, states.size()); assertTrue(states.get(0).paused);
        assertEquals(7, state().getAsJsonObject("ignoringOnTheFly").get("server").getAsInt());
        assertFalse(state().getAsJsonObject("ignoringOnTheFly").has("client"));
        assertEquals(123, state().getAsJsonObject("ping").get("latencyCalculation").getAsInt());
        assertTrue(state().getAsJsonObject("playstate").get("paused").getAsBoolean());
    }

    @Test public void suppressesStaleStateUntilAckAndKeepsLatestLocalSeek() throws Exception {
        hello(); protocol.localChange(10, true, true); protocol.localChange(25, false, true);
        assertEquals(1, sent.size());
        protocol.receive("{\"State\":{\"playstate\":{\"position\":0,\"paused\":false},\"ignoringOnTheFly\":{\"client\":9}}}");
        assertTrue(states.isEmpty()); assertFalse(state().has("playstate"));
        protocol.receive("{\"State\":{\"playstate\":{\"position\":10,\"paused\":true},\"ignoringOnTheFly\":{\"client\":1}}}");
        assertEquals(1, states.size());
        assertEquals(25, state().getAsJsonObject("playstate").get("position").getAsInt());
        assertTrue(state().getAsJsonObject("playstate").get("doSeek").getAsBoolean());
    }

    @Test public void tracksOnlyCurrentRoomAndRemovesDepartedMember() throws Exception {
        hello(); protocol.receive("{\"List\":{\"private-room\":{\"TV\":{\"file\":{}},\"Other\":{\"file\":{\"name\":\"Movie\",\"duration\":120}}},\"another-room\":{\"Stranger\":{\"file\":{\"name\":\"Secret\"}}}}}");
        assertTrue(complete); assertEquals(2, members.size());
        assertEquals("Movie", members.get(1).filename);
        protocol.receive("{\"Set\":{\"user\":{\"Other\":{\"event\":{\"left\":true}}}}}");
        assertEquals(1, members.size()); assertEquals("TV", members.get(0).username);
    }

    @Test public void framedUtf8SurvivesPacketSplitsAndRejectsUnboundedOrInvalidInput() throws Exception {
        SyncplayClient.LineBuffer line = new SyncplayClient.LineBuffer();
        String text = "{\"name\":\"一起看\"}"; String received = null;
        for (byte value : (text + "\r\n").getBytes(StandardCharsets.UTF_8)) { String result = line.accept(value); if (result != null) received = result; }
        assertEquals(text, received);
        line.accept((byte) 0xff); assertThrows(IOException.class, () -> line.accept((byte) '\n'));
        SyncplayClient.LineBuffer oversized = new SyncplayClient.LineBuffer();
        for (int i = 0; i < 65536; i++) oversized.accept((byte) 'a');
        assertThrows(IOException.class, () -> oversized.accept((byte) 'a'));
    }

    @Test public void validatesManualConnectionSettingsWithoutSupplyingAnyPublicRoom() {
        assertThrows(IllegalArgumentException.class, () -> new SyncplayConfig("", 8999, "TV", "room", "", true));
        assertThrows(IllegalArgumentException.class, () -> new SyncplayConfig("https://example.com", 8999, "TV", "room", "", true));
        assertThrows(IllegalArgumentException.class, () -> new SyncplayConfig("example.com:8999", 8999, "TV", "room", "", true));
        assertThrows(IllegalArgumentException.class, () -> new SyncplayConfig("localhost", 70000, "TV", "room", "", false));
        assertThrows(IllegalArgumentException.class, () -> new SyncplayConfig("localhost", 8999, "TV", "+controlled:abc", "", false));
        assertEquals("::1", new SyncplayConfig("[::1]", 8999, "TV", "room", "", true).host);
    }
}
