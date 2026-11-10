package com.mazehunter.server.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;
import com.mazehunter.server.game.GameLoop;
import com.mazehunter.server.game.MatchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP listener that assigns one handler thread to each accepted client. */
public final class GameServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_MAX_PLAYERS = 4;
    public static final int DEFAULT_TICK_RATE = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(GameServer.class);
    private static final int ACCEPT_BACKLOG = 32;
    private static final long HANDLER_JOIN_MILLIS = 1_000;

    private final int configuredPort;
    private final int serverTickRate;
    private final ConnectionManager connectionManager;
    private final MatchManager matchManager;
    private final GameLoop gameLoop;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<Socket, Thread> clientHandlers = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile Thread shutdownHook;

    public GameServer() {
        this(DEFAULT_PORT);
    }

    public GameServer(int port) {
        this(port, DEFAULT_MAX_PLAYERS, DEFAULT_TICK_RATE);
    }

    public GameServer(int port, int maxPlayers, int serverTickRate) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65,535");
        }
        if (serverTickRate < 1) {
            throw new IllegalArgumentException("serverTickRate must be positive");
        }
        this.configuredPort = port;
        this.connectionManager = new ConnectionManager(maxPlayers);
        this.matchManager = new MatchManager(connectionManager);
        this.gameLoop = new GameLoop(matchManager);
        this.connectionManager.setMatchManager(matchManager);
        this.connectionManager.setGameLoop(gameLoop);
        this.serverTickRate = serverTickRate;
    }

    /** Binds the configured port and starts the background accept loop. */
    public void start() throws IOException {
        synchronized (lifecycleLock) {
            if (running.get()) {
                throw new IllegalStateException("GameServer is already running");
            }
            if (serverSocket != null) {
                throw new IllegalStateException("GameServer cannot be restarted after it has stopped");
            }

            ServerSocket listener = new ServerSocket();
            try {
                listener.setReuseAddress(true);
                listener.bind(new InetSocketAddress(configuredPort), ACCEPT_BACKLOG);
            } catch (IOException exception) {
                try {
                    listener.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
            serverSocket = listener;
            running.set(true);
            installShutdownHook();
            gameLoop.start();

            acceptThread = new Thread(this::acceptConnections, "maze-server-accept");
            acceptThread.setDaemon(false);
            acceptThread.start();
            LOGGER.info("Maze Hunter server listening on port {}", listener.getLocalPort());
        }
    }

    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (!running.get()) {
                    closeSocketQuietly(clientSocket);
                    break;
                }
                if (clientHandlers.size() >= connectionManager.getMaximumSessions()) {
                    rejectFullServer(clientSocket);
                    continue;
                }
                startClientHandler(clientSocket);
            } catch (IOException exception) {
                if (running.get()) {
                    LOGGER.error("TCP accept loop failed", exception);
                }
                break;
            }
        }
        if (running.get()) {
            stopInternal(true);
        }
    }

    private void startClientHandler(Socket clientSocket) {
        Thread handlerThread = new Thread(() -> {
            try {
                new ClientHandler(clientSocket, connectionManager, serverTickRate).run();
            } finally {
                clientHandlers.remove(clientSocket);
            }
        }, "maze-client-" + clientSocket.getRemoteSocketAddress());
        handlerThread.setDaemon(false);
        clientHandlers.put(clientSocket, handlerThread);
        try {
            handlerThread.start();
        } catch (RuntimeException exception) {
            clientHandlers.remove(clientSocket);
            closeSocketQuietly(clientSocket);
            LOGGER.error("Could not start client handler thread", exception);
        }
    }

    private void rejectFullServer(Socket clientSocket) {
        try (Socket socket = clientSocket;
             PrintWriter writer = new PrintWriter(new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)))) {
            String json;
            try {
                json = MessageSerializer.serialize(
                        MessageEnvelope.error("SERVER_FULL", "The server cannot accept another player."));
            } catch (JsonProcessingException exception) {
                LOGGER.error("Could not encode SERVER_FULL response", exception);
                return;
            }
            writer.print(json);
            writer.print('\n');
            writer.flush();
        } catch (IOException exception) {
            LOGGER.debug("Could not reject excess client cleanly: {}", exception.getMessage());
        }
    }

    private void installShutdownHook() {
        Thread hook = new Thread(() -> stopInternal(false), "maze-server-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            shutdownHook = hook;
        } catch (IllegalStateException | SecurityException exception) {
            LOGGER.warn("Could not install JVM shutdown hook: {}", exception.getMessage());
        }
    }

    public void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean removeHook) {
        boolean wasRunning = running.getAndSet(false);
        gameLoop.stop();
        ServerSocket listener = serverSocket;
        if (listener != null) {
            try {
                listener.close();
            } catch (IOException exception) {
                LOGGER.debug("Could not close listening socket cleanly: {}", exception.getMessage());
            }
        }

        for (Socket clientSocket : new ArrayList<>(clientHandlers.keySet())) {
            closeSocketQuietly(clientSocket);
        }

        joinThread(acceptThread, HANDLER_JOIN_MILLIS);
        for (Thread handler : new ArrayList<>(clientHandlers.values())) {
            joinThread(handler, HANDLER_JOIN_MILLIS);
        }

        if (removeHook) {
            removeShutdownHook();
        }
        if (wasRunning) {
            LOGGER.info("Maze Hunter server stopped");
        }
    }

    private void removeShutdownHook() {
        Thread hook = shutdownHook;
        if (hook == null || hook == Thread.currentThread()) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException | SecurityException exception) {
            LOGGER.debug("Could not remove JVM shutdown hook: {}", exception.getMessage());
        }
        shutdownHook = null;
    }

    private static void joinThread(Thread thread, long timeoutMillis) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(timeoutMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeSocketQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException exception) {
            LOGGER.debug("Could not close client socket cleanly: {}", exception.getMessage());
        }
    }

    public void awaitTermination() throws InterruptedException {
        Thread thread = acceptThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.join();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        ServerSocket listener = serverSocket;
        return listener != null && listener.isBound() ? listener.getLocalPort() : configuredPort;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public GameLoop getGameLoop() {
        return gameLoop;
    }

    @Override
    public void close() {
        stop();
    }
}
