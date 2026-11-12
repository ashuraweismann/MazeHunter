package com.mazehunter.client.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous TCP client. The reader and writer workers own all socket I/O;
 * UI code only starts the connection, enqueues messages, and polls received messages.
 */
public final class NetworkClient implements AutoCloseable {

    public enum State {
        NEW,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        FAILED,
        CLOSING,
        CLOSED
    }

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int MAX_MESSAGE_BYTES = 8_192;
    private static final int OUTGOING_QUEUE_CAPACITY = 256;

    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final ConcurrentLinkedQueue<MessageEnvelope> incomingMessages = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<MessageEnvelope> outgoingMessages = new ArrayBlockingQueue<>(OUTGOING_QUEUE_CAPACITY);
    private final Object writerLock = new Object();

    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;
    private volatile Thread readerThread;
    private volatile Thread writerThread;
    private volatile String lastError;
    private volatile boolean closing;
    private volatile boolean connectionEnded;

    /** Starts the connection asynchronously; this method performs no socket I/O. */
    public void connect(String host, int port, String playerName) {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("Host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65,535");
        }
        String name = playerName == null || playerName.isBlank() ? "Guest" : playerName.strip();
        if (!state.compareAndSet(State.NEW, State.CONNECTING)) {
            throw new IllegalStateException("NetworkClient can only connect once");
        }

        readerThread = new Thread(() -> connectAndRead(host.strip(), port, name), "maze-client-network-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void connectAndRead(String host, int port, String playerName) {
        try {
            Socket connectedSocket = new Socket();
            socket = connectedSocket;
            if (closing) {
                return;
            }
            connectedSocket.setTcpNoDelay(true);
            connectedSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            if (closing) {
                return;
            }

            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            reader = new BufferedReader(new InputStreamReader(connectedSocket.getInputStream(), decoder));
            writer = new BufferedWriter(new OutputStreamWriter(
                    connectedSocket.getOutputStream(), StandardCharsets.UTF_8));

            writeEnvelope(MessageEnvelope.connect(playerName, "1.0.0"));
            state.compareAndSet(State.CONNECTING, State.CONNECTED);
            startWriterThread();

            String line;
            while (!closing && (line = readBoundedLine(reader)) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    incomingMessages.offer(MessageSerializer.deserialize(line));
                } catch (JsonProcessingException exception) {
                    throw new IOException("Server sent malformed protocol JSON", exception);
                }
            }

            if (!closing && state.get() != State.FAILED) {
                lastError = "The server closed the connection.";
                state.set(State.DISCONNECTED);
            }
        } catch (IOException | RuntimeException exception) {
            if (!closing && state.get() != State.FAILED) {
                lastError = exception.getMessage() == null ? "Could not connect to the server." : exception.getMessage();
                state.set(State.FAILED);
            }
        } finally {
            connectionEnded = true;
            Thread sender = writerThread;
            if (sender != null) {
                sender.interrupt();
            }
            closeSocketFromWorker();
            if (closing) {
                state.set(State.CLOSED);
            }
        }
    }

    private void startWriterThread() {
        writerThread = new Thread(this::writeLoop, "maze-client-network-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void writeLoop() {
        while (!closing) {
            try {
                MessageEnvelope message = outgoingMessages.take();
                writeEnvelope(message);
            } catch (InterruptedException exception) {
                if (!closing && !connectionEnded) {
                    fail("Network writer was interrupted unexpectedly.");
                }
                closeSocketFromWorker();
                return;
            } catch (IOException exception) {
                if (!closing) {
                    fail(exception.getMessage() == null ? "Could not send a server message." : exception.getMessage());
                }
                closeSocketFromWorker();
                return;
            }
        }
    }

    private void writeEnvelope(MessageEnvelope message) throws IOException {
        String json;
        try {
            json = MessageSerializer.serialize(message);
        } catch (JsonProcessingException exception) {
            throw new IOException("Could not serialize a protocol message", exception);
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new IOException("Outgoing message exceeded the 8 KB protocol limit");
        }
        synchronized (writerLock) {
            BufferedWriter currentWriter = writer;
            if (currentWriter == null) {
                throw new IOException("Client socket is not ready");
            }
            currentWriter.write(json);
            currentWriter.write('\n');
            currentWriter.flush();
        }
    }

    /** Reads one frame while bounding memory use before the newline arrives. */
    private static String readBoundedLine(BufferedReader input) throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = input.read()) != -1) {
            if (character == '\n') {
                if (line.length() > 0 && line.charAt(line.length() - 1) == '\r') {
                    line.setLength(line.length() - 1);
                }
                String value = line.toString();
                if (value.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                    throw new IOException("Server message exceeded the 8 KB protocol limit");
                }
                return value;
            }
            if (line.length() >= MAX_MESSAGE_BYTES) {
                throw new IOException("Server message exceeded the 8 KB protocol limit");
            }
            line.append((char) character);
        }
        if (line.length() == 0) {
            return null;
        }
        String value = line.toString();
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new IOException("Server message exceeded the 8 KB protocol limit");
        }
        return value;
    }

    /** Enqueues a message for the background writer; no socket write occurs here. */
    public boolean send(MessageEnvelope message) {
        Objects.requireNonNull(message, "message");
        return state.get() == State.CONNECTED && outgoingMessages.offer(message);
    }

    public MessageEnvelope pollMessage() {
        return incomingMessages.poll();
    }

    public List<MessageEnvelope> drainMessages() {
        List<MessageEnvelope> messages = new ArrayList<>();
        MessageEnvelope message;
        while ((message = incomingMessages.poll()) != null) {
            messages.add(message);
        }
        return messages;
    }

    public State getState() {
        return state.get();
    }

    public String getLastError() {
        return lastError;
    }

    private void fail(String message) {
        lastError = message;
        state.set(State.FAILED);
    }

    private void closeSocketFromWorker() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // The socket is already unusable; the owning worker is exiting.
            }
        }
    }

    /** Requests shutdown without blocking the LibGDX thread on socket closure. */
    @Override
    public void close() {
        if (closing) {
            return;
        }
        closing = true;
        state.set(State.CLOSING);
        Thread sender = writerThread;
        if (sender != null) {
            sender.interrupt();
        }
        Thread receiver = readerThread;
        if (receiver != null) {
            receiver.interrupt();
        }

        Socket current = socket;
        if (current != null) {
            Thread closer = new Thread(() -> {
                try {
                    current.close();
                } catch (IOException ignored) {
                    // Closing is best effort during shutdown.
                }
                state.set(State.CLOSED);
            }, "maze-client-network-close");
            closer.setDaemon(true);
            closer.start();
        } else {
            state.set(State.CLOSED);
        }
    }
}
