package com.fongmi.android.tv.syncplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;

/** Syncplay 1.7.7 JSON protocol. All methods are called on one transport thread. */
public final class SyncplayProtocol {
    public interface Sender { void send(JsonObject message) throws IOException; }
    public interface Listener {
        void onHello(String username, String room);
        void onState(RemoteState state);
        void onMembers(List<Member> members, boolean complete);
        void onError(String message);
    }
    public static final class RemoteState {
        public final double position;
        public final boolean paused;
        public final boolean seek;
        public final String setBy;
        public final double forwardDelay;
        public RemoteState(double position, boolean paused, boolean seek, String setBy, double forwardDelay) {
            this.position = position; this.paused = paused; this.seek = seek; this.setBy = setBy; this.forwardDelay = forwardDelay;
        }
    }
    public static final class Member {
        public final String username, room, filename;
        public final double duration;
        public Member(String username, String room, String filename, double duration) {
            this.username = username; this.room = room; this.filename = filename; this.duration = duration;
        }
    }
    private final SyncplayConfig config;
    private final Sender sender;
    private final Listener listener;
    private final DoubleSupplier clock;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private String username;
    private String room;
    private boolean logged;
    private boolean listed;
    private long clientIgnore;
    private long serverIgnore;
    private double rtt;
    private double averageRtt;
    private RemoteState pendingChange;

    public SyncplayProtocol(SyncplayConfig config, Sender sender, Listener listener, DoubleSupplier clock) {
        this.config = config; this.sender = sender; this.listener = listener; this.clock = clock;
        username = config.username; room = config.room;
    }

    public void hello() throws IOException {
        JsonObject hello = new JsonObject();
        hello.addProperty("username", config.username); hello.add("room", named(config.room));
        hello.addProperty("version", "1.2.255"); hello.addProperty("realversion", "1.7.7");
        if (!config.password.isEmpty()) {
            try {
                StringBuilder hash = new StringBuilder();
                for (byte b : MessageDigest.getInstance("MD5").digest(config.password.getBytes(StandardCharsets.UTF_8)))
                    hash.append(String.format(Locale.ROOT, "%02x", b & 255));
                hello.addProperty("password", hash.toString());
            } catch (Exception error) { throw new IOException("password_hash", error); }
        }
        JsonObject features = new JsonObject();
        features.addProperty("featureList", true); features.addProperty("sharedPlaylists", false);
        features.addProperty("chat", false); features.addProperty("readiness", false);
        features.addProperty("managedRooms", false); features.addProperty("persistentRooms", false);
        features.addProperty("uiMode", "GUI"); hello.add("features", features);
        send("Hello", hello);
    }

    public boolean isLogged() { return logged; }

    public void file(String filename, double duration) throws IOException {
        if (!logged) return;
        JsonObject file = new JsonObject(); file.addProperty("name", filename);
        file.addProperty("duration", Math.max(0, duration)); file.addProperty("size", 0);
        JsonObject set = new JsonObject(); set.add("file", file); send("Set", set); send("List", JsonNull.INSTANCE);
    }

    public void localChange(double position, boolean paused, boolean seek) throws IOException {
        if (!logged || !Double.isFinite(position) || position < 0) return;
        RemoteState change = new RemoteState(position, paused, seek, username, 0);
        if (clientIgnore != 0) {
            pendingChange = new RemoteState(position, paused, seek || pendingChange != null && pendingChange.seek, username, 0);
            return;
        }
        sendChange(change);
    }

    private void sendChange(RemoteState change) throws IOException {
        JsonObject state = new JsonObject(); state.add("playstate", playstate(change.position, change.paused, change.seek));
        state.add("ping", ping(null)); JsonObject ignoring = new JsonObject();
        clientIgnore++; ignoring.addProperty("client", clientIgnore);
        state.add("ignoringOnTheFly", ignoring); send("State", state);
    }

    public void receive(String line) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException();
            JsonObject message = parsed.getAsJsonObject();
            if (message.has("Error")) { listener.onError(text(object(message, "Error"), "message")); return; }
            if (message.has("Hello")) handleHello(object(message, "Hello"));
            if (message.has("List")) handleList(object(message, "List"));
            if (message.has("Set")) handleSet(object(message, "Set"));
            if (message.has("State")) handleState(object(message, "State"));
        } catch (IOException error) { throw error; }
        catch (RuntimeException error) { throw new IOException("invalid_protocol_message", error); }
    }

    private void handleHello(JsonObject hello) throws IOException {
        username = text(hello, "username"); room = text(object(hello, "room"), "name");
        if (username.isEmpty() || room.isEmpty() || text(hello, "version").isEmpty()) throw new IOException("invalid_hello");
        logged = true; listener.onHello(username, room); send("List", JsonNull.INSTANCE);
    }

    private void handleList(JsonObject list) {
        members.clear(); JsonObject users = object(list, room);
        for (Map.Entry<String, JsonElement> entry : users.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            members.put(entry.getKey(), member(entry.getKey(), room, object(entry.getValue().getAsJsonObject(), "file")));
        }
        listed = true; listener.onMembers(new ArrayList<>(members.values()), true);
    }

    private void handleSet(JsonObject settings) {
        JsonObject users = object(settings, "user");
        for (Map.Entry<String, JsonElement> entry : users.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject update = entry.getValue().getAsJsonObject();
            String name = entry.getKey(); String changedRoom = text(object(update, "room"), "name");
            if (object(update, "event").has("left") || !changedRoom.isEmpty() && !changedRoom.equals(room)) members.remove(name);
            else {
                Member previous = members.get(name);
                JsonObject file = object(update, "file");
                members.put(name, update.has("file") || previous == null ? member(name, room, file) : previous);
            }
        }
        if (!users.isEmpty()) listener.onMembers(new ArrayList<>(members.values()), listed);
        String newRoom = text(object(settings, "room"), "name");
        if (!newRoom.isEmpty() && !newRoom.equals(room)) {
            room = newRoom; listed = false; members.clear(); listener.onHello(username, room);
            listener.onMembers(new ArrayList<>(), false);
        }
    }

    private Member member(String name, String room, JsonObject file) {
        return new Member(name, room, text(file, "name"), number(file, "duration", 0));
    }

    private void handleState(JsonObject state) throws IOException {
        JsonObject ignored = object(state, "ignoringOnTheFly");
        if (ignored.has("server")) { serverIgnore = ignored.get("server").getAsLong(); clientIgnore = 0; }
        else if (ignored.has("client") && ignored.get("client").getAsLong() == clientIgnore) clientIgnore = 0;
        JsonObject incomingPing = object(state, "ping");
        double forward = 0;
        if (incomingPing.has("clientLatencyCalculation")) {
            double sample = clock.getAsDouble() - number(incomingPing, "clientLatencyCalculation", clock.getAsDouble());
            if (Double.isFinite(sample) && sample >= 0 && sample <= 10) {
                rtt = sample; averageRtt = averageRtt == 0 ? sample : averageRtt * .85 + sample * .15;
                forward = Math.min(2, averageRtt / 2 + Math.max(0, sample - number(incomingPing, "serverRtt", sample)));
            }
        }
        JsonObject play = object(state, "playstate"); JsonObject reply = new JsonObject();
        if (play.has("position") && play.has("paused") && clientIgnore == 0) {
            double position = number(play, "position", -1);
            if (!Double.isFinite(position) || position < 0) throw new IOException("invalid_position");
            boolean paused = play.get("paused").getAsBoolean();
            listener.onState(new RemoteState(position, paused, bool(play, "doSeek"), text(play, "setBy"), forward));
            // Do not echo a stale local pause while the main thread is applying this message.
            // This also keeps a temporarily gated (different-file/buffering) client from controlling the room.
            reply.add("playstate", playstate(position + (paused ? 0 : forward), paused, false));
        }
        reply.add("ping", ping(incomingPing.get("latencyCalculation")));
        if (serverIgnore != 0 || clientIgnore != 0) {
            JsonObject ignoring = new JsonObject();
            if (serverIgnore != 0) { ignoring.addProperty("server", serverIgnore); serverIgnore = 0; }
            if (clientIgnore != 0) ignoring.addProperty("client", clientIgnore);
            reply.add("ignoringOnTheFly", ignoring);
        }
        send("State", reply);
        if (clientIgnore == 0 && pendingChange != null) { RemoteState latest = pendingChange; pendingChange = null; sendChange(latest); }
    }

    private JsonObject ping(JsonElement latency) {
        JsonObject ping = new JsonObject(); if (latency != null && !latency.isJsonNull()) ping.add("latencyCalculation", latency);
        ping.addProperty("clientLatencyCalculation", clock.getAsDouble()); ping.addProperty("clientRtt", rtt); return ping;
    }
    private static JsonObject playstate(double position, boolean paused, boolean seek) {
        JsonObject state = new JsonObject(); state.addProperty("position", position); state.addProperty("paused", paused);
        if (seek) state.addProperty("doSeek", true); return state;
    }
    private static JsonObject named(String name) { JsonObject object = new JsonObject(); object.addProperty("name", name); return object; }
    private void send(String command, JsonElement value) throws IOException { JsonObject message = new JsonObject(); message.add(command, value); sender.send(message); }
    private static JsonObject object(JsonObject parent, String key) { JsonElement item = parent.get(key); return item != null && item.isJsonObject() ? item.getAsJsonObject() : new JsonObject(); }
    private static String text(JsonObject object, String key) { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? "" : value.getAsString(); }
    private static double number(JsonObject object, String key, double fallback) { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? fallback : value.getAsDouble(); }
    private static boolean bool(JsonObject object, String key) { JsonElement value = object.get(key); return value != null && !value.isJsonNull() && value.getAsBoolean(); }
}
