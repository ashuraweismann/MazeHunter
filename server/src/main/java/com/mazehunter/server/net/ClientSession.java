package com.mazehunter.server.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the streams and lifecycle for one accepted client socket. */
public final class ClientSession implements AutoCloseable {

    public static final int MAX_MESSAGE_BYTES = 8_192;

    public enum Status {
        CONNECTED,
        ACTIVE,
        CLOSING,
        CLOSED
    }

    private final Socket socket;
    private final BufferedReader reader;
    private final LimitedUtf8LineReader lineReader;
    private final PrintWriter writer;
    private final Object writerLock = new Object();
    private final AtomicReference<Status> status = new AtomicReference<>(Status.CONNECTED);

    private volatile String playerId;
    private volatile String playerName;

    public ClientSession(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), decoder));
        this.lineReader = new LimitedUtf8LineReader(reader, MAX_MESSAGE_BYTES);
        this.writer = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
    }

    public String readLine() throws IOException {
        return lineReader.readLine();
    }

    public void send(MessageEnvelope message) throws IOException {
        Objects.requireNonNull(message, "message");
        String json;
        try {
            json = MessageSerializer.serialize(message);
        } catch (JsonProcessingException exception) {
            throw new IOException("Could not serialize protocol message", exception);
        }

        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new IOException("Outgoing protocol message exceeds the 8 KB limit");
        }

        synchronized (writerLock) {
            if (status.get() == Status.CLOSING || status.get() == Status.CLOSED) {
                throw new IOException("Client session is closed");
            }
            writer.print(json);
            writer.print('\n');
            writer.flush();
            if (writer.checkError()) {
                throw new IOException("Failed to write to client socket");
            }
        }
    }

    void activate(String assignedPlayerId, String assignedPlayerName) {
        Objects.requireNonNull(assignedPlayerId, "assignedPlayerId");
        Objects.requireNonNull(assignedPlayerName, "assignedPlayerName");
        if (!status.compareAndSet(Status.CONNECTED, Status.ACTIVE)) {
            throw new IllegalStateException("Only a new connection can become active");
        }
        playerId = assignedPlayerId;
        playerName = assignedPlayerName;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Status getStatus() {
        return status.get();
    }

    public boolean isActive() {
        return status.get() == Status.ACTIVE;
    }

    @Override
    public void close() throws IOException {
        boolean beganClosing = status.compareAndSet(Status.CONNECTED, Status.CLOSING)
                || status.compareAndSet(Status.ACTIVE, Status.CLOSING);
        if (!beganClosing) {
            return;
        }
        try {
            socket.close();
        } finally {
            status.set(Status.CLOSED);
        }
    }
}
