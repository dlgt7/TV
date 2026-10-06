package com.fongmi.android.tv.syncplay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Cancellable native Syncplay TCP/STARTTLS transport; no public server is selected automatically. */
public final class SyncplayClient implements AutoCloseable {
    public interface Listener extends SyncplayProtocol.Listener {
        void onTransport(boolean encrypted);
        void onDisconnected(String reason);
    }
    private static final Gson JSON = new Gson();
    private static final ExecutorService CLOSER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SyncplayClose"); thread.setDaemon(true); return thread;
    });
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SyncplayNetwork"); thread.setDaemon(true); return thread;
    });
    private final AtomicReference<LocalChange> change = new AtomicReference<>();
    private final AtomicReference<MediaFile> file = new AtomicReference<>();
    private final Listener listener;
    private volatile Socket socket;
    private volatile boolean active;
    private volatile String serverError;

    public SyncplayClient(Listener listener) { this.listener = listener; }

    public void connect(SyncplayConfig config) {
        if (active || worker.isShutdown()) throw new IllegalStateException("Syncplay client already used");
        active = true; worker.execute(() -> run(config));
    }

    public void setFile(String name, double duration) { file.set(new MediaFile(name, duration)); }

    public void localChange(double position, boolean paused, boolean seek) {
        change.updateAndGet(old -> new LocalChange(position, paused, seek || old != null && old.seek));
    }

    private void run(SyncplayConfig config) {
        String reason = "disconnected";
        try {
            Socket raw = new Socket(); socket = raw;
            raw.connect(new InetSocketAddress(config.host, config.port), 5000);
            raw.setTcpNoDelay(true); raw.setSoTimeout(5000);
            InputStream input = raw.getInputStream(); OutputStream output = raw.getOutputStream();
            boolean encrypted = false;
            if (config.requireTls) {
                output.write("{\"TLS\":{\"startTLS\":\"send\"}}\r\n".getBytes(StandardCharsets.UTF_8)); output.flush();
                String frame = readHandshake(input);
                JsonObject answer = JsonParser.parseString(frame).getAsJsonObject();
                if (!answer.has("TLS") || !answer.getAsJsonObject("TLS").has("startTLS")
                        || !"true".equals(answer.getAsJsonObject("TLS").get("startTLS").getAsString()))
                    throw new IOException("tls_unavailable");
                if (!active) return;
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, config.host, config.port, true);
                socket = tls; SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
                tls.setSoTimeout(5000); tls.startHandshake(); input = tls.getInputStream(); output = tls.getOutputStream(); encrypted = true;
            }
            if (!active) return;
            socket.setSoTimeout(250); listener.onTransport(encrypted);
            OutputStream writer = output;
            SyncplayProtocol protocol = new SyncplayProtocol(config, message -> {
                if (!active) throw new IOException("closed");
                writer.write(JSON.toJson(message).getBytes(StandardCharsets.UTF_8)); writer.write(new byte[]{'\r', '\n'}); writer.flush();
            }, new SyncplayProtocol.Listener() {
                @Override public void onHello(String username, String room) { listener.onHello(username, room); }
                @Override public void onState(SyncplayProtocol.RemoteState state) { listener.onState(state); }
                @Override public void onMembers(List<SyncplayProtocol.Member> members, boolean complete) { listener.onMembers(members, complete); }
                @Override public void onError(String message) { serverError = message; active = false; listener.onError(message); }
            }, () -> System.currentTimeMillis() / 1000.0);
            protocol.hello();
            LineBuffer lines = new LineBuffer(); byte[] chunk = new byte[4096]; long received = System.nanoTime();
            MediaFile lastFile = null;
            while (active) {
                MediaFile nextFile = file.get();
                if (protocol.isLogged() && nextFile != null && nextFile != lastFile) { protocol.file(nextFile.name, nextFile.duration); lastFile = nextFile; }
                LocalChange pending = change.getAndSet(null);
                if (pending != null) protocol.localChange(pending.position, pending.paused, pending.seek);
                try {
                    int count = input.read(chunk);
                    if (count < 0) throw new IOException("server_closed");
                    for (int i = 0; i < count; i++) {
                        String line = lines.accept(chunk[i]);
                        if (line != null && !line.trim().isEmpty()) {
                            protocol.receive(line); received = System.nanoTime();
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                    if (System.nanoTime() - received > 12_500_000_000L) throw new IOException("server_timeout");
                }
            }
        } catch (Exception error) {
            reason = error instanceof javax.net.ssl.SSLException ? "tls_failed"
                    : error instanceof SocketTimeoutException ? "connect_timeout"
                    : error.getMessage() == null ? "network_error" : error.getMessage();
        } finally {
            boolean notify = active || serverError != null;
            active = false; closeSocket(socket); socket = null;
            worker.shutdown();
            if (notify) listener.onDisconnected(serverError == null ? reason : "server_error");
        }
    }

    private String readHandshake(InputStream input) throws IOException {
        LineBuffer buffer = new LineBuffer();
        while (active) {
            int value = input.read(); if (value < 0) throw new IOException("server_closed");
            String line = buffer.accept((byte) value); if (line != null && !line.trim().isEmpty()) return line;
        }
        throw new IOException("closed");
    }

    @Override
    public void close() {
        active = false; change.set(null); file.set(null); worker.shutdownNow();
        Socket closing = socket;
        // TLS close_notify can write to the network; never close a TLS socket on the UI thread.
        if (closing != null) CLOSER.execute(() -> closeSocket(closing));
    }

    private static void closeSocket(Socket socket) { if (socket != null) try { socket.close(); } catch (IOException ignored) { } }

    static final class LineBuffer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String accept(byte value) throws IOException {
            if (value != '\n') {
                if (bytes.size() >= 65536) throw new IOException("message_too_large");
                bytes.write(value); return null;
            }
            byte[] line = bytes.toByteArray(); bytes.reset();
            int size = line.length > 0 && line[line.length - 1] == '\r' ? line.length - 1 : line.length;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(line, 0, size)).toString();
        }
    }
    private static final class LocalChange {
        final double position; final boolean paused, seek;
        LocalChange(double position, boolean paused, boolean seek) { this.position = position; this.paused = paused; this.seek = seek; }
    }
    private static final class MediaFile {
        final String name; final double duration;
        MediaFile(String name, double duration) { this.name = name; this.duration = duration; }
    }
}
