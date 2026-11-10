package com.mazehunter.server.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mazehunter.common.protocol.ConnectPayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.MovePayload;
import com.mazehunter.common.protocol.PingPayload;
import com.mazehunter.common.protocol.ReadyPayload;
import com.mazehunter.server.game.ClientCommand;
import com.mazehunter.server.game.GameLoop;
import com.mazehunter.server.game.MatchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/** Reads and processes protocol messages for one client connection. */
public final class ClientHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);
    private static final int MAX_MALFORMED_MESSAGES = 3;
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 10_000;

    private final Socket socket;
    private final ConnectionManager connectionManager;
    private final int serverTickRate;

    public ClientHandler(Socket socket, ConnectionManager connectionManager, int serverTickRate) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager");
        if (serverTickRate < 1) {
            throw new IllegalArgumentException("serverTickRate must be positive");
        }
        this.serverTickRate = serverTickRate;
    }

    @Override
    public void run() {
        ClientSession session = null;
        int malformedMessages = 0;
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
            session = new ClientSession(socket);
            boolean connected = false;

            while (!socket.isClosed()) {
                String line = session.readLine();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    sendError(session, "MALFORMED_MESSAGE", "Message line must not be blank.");
                    continue;
                }

                MessageEnvelope envelope;
                try {
                    envelope = MessageSerializer.deserialize(line);
                } catch (JsonProcessingException exception) {
                    sendError(session, "MALFORMED_JSON", "Message is not valid protocol JSON.");
                    malformedMessages++;
                    if (malformedMessages >= MAX_MALFORMED_MESSAGES) {
                        break;
                    }
                    continue;
                }
                malformedMessages = 0;

                if (!connected) {
                    if (envelope.type() != MessageType.CONNECT) {
                        sendError(session, "HANDSHAKE_REQUIRED", "Send CONNECT before other messages.");
                        break;
                    }
                    ConnectPayload connectPayload;
                    try {
                        connectPayload = envelope.parsePayload(ConnectPayload.class);
                    } catch (JsonProcessingException exception) {
                        sendError(session, "INVALID_CONNECT", "CONNECT payload is invalid.");
                        continue;
                    }

                    if (!connectionManager.register(session, connectPayload.playerName())) {
                        sendError(session, "SERVER_FULL", "The server cannot accept another player.");
                        break;
                    }

                    session.send(MessageEnvelope.welcome(
                            session.getPlayerId(), serverTickRate, connectionManager.getMaximumSessions()));
                    connected = true;
                    socket.setSoTimeout(0);
                    LOGGER.info("Client connected: {} ({})", session.getPlayerName(), session.getPlayerId());
                    continue;
                }

                if (envelope.type() == MessageType.PING) {
                    try {
                        PingPayload ping = envelope.parsePayload(PingPayload.class);
                        session.send(MessageEnvelope.pong(ping.pingId()));
                    } catch (JsonProcessingException | RuntimeException exception) {
                        sendError(session, "INVALID_PING", "PING payload is invalid.");
                    }
                } else if (envelope.type() == MessageType.QUIT) {
                    break;
                } else if (envelope.type() == MessageType.CONNECT) {
                    sendError(session, "ALREADY_CONNECTED", "This connection already completed CONNECT.");
                } else if (envelope.type() == MessageType.READY) {
                    try {
                        ReadyPayload readyPayload = envelope.parsePayload(ReadyPayload.class);
                        session.setReady(readyPayload.ready());
                        MatchManager mm = connectionManager.getMatchManager();
                        if (mm != null) {
                            mm.setPlayerReady(session.getPlayerId(), readyPayload.ready());
                        }
                    } catch (JsonProcessingException exception) {
                        sendError(session, "INVALID_READY", "READY payload is invalid.");
                    }
                } else if (envelope.type() == MessageType.MOVE) {
                    try {
                        MovePayload movePayload = envelope.parsePayload(MovePayload.class);
                        GameLoop loop = connectionManager.getGameLoop();
                        if (loop != null) {
                            loop.enqueueCommand(new ClientCommand(session.getPlayerId(), MessageType.MOVE, movePayload));
                        }
                    } catch (JsonProcessingException exception) {
                        sendError(session, "INVALID_MOVE", "MOVE payload is invalid.");
                    }
                } else if (envelope.type() == MessageType.REMATCH) {
                    MatchManager mm = connectionManager.getMatchManager();
                    if (mm != null) {
                        mm.resetToLobby();
                    }
                } else {
                    sendError(session, "UNSUPPORTED_MESSAGE", "Unsupported message type: " + envelope.type());
                }
            }
        } catch (LimitedUtf8LineReader.LineTooLongException exception) {
            sendError(session, "LINE_TOO_LONG", "Protocol messages may not exceed 8,192 bytes.");
            LOGGER.warn("Disconnected client that exceeded the protocol line limit");
        } catch (SocketException exception) {
            if (!socket.isClosed()) {
                LOGGER.debug("Client socket ended: {}", exception.getMessage());
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) {
                LOGGER.debug("Client connection ended: {}", exception.getMessage());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected failure while handling a client connection", exception);
            sendError(session, "INTERNAL_ERROR", "The server could not process the connection.");
        } finally {
            if (session != null) {
                boolean removed = connectionManager.remove(session);
                closeQuietly(session);
                if (removed) {
                    LOGGER.info("Client disconnected: {} ({})", session.getPlayerName(), session.getPlayerId());
                }
            } else {
                closeSocketQuietly(socket);
            }
        }
    }

    private static void sendError(ClientSession session, String code, String message) {
        if (session == null) {
            return;
        }
        try {
            session.send(MessageEnvelope.error(code, message));
        } catch (IOException exception) {
            LOGGER.debug("Could not send protocol error to client: {}", exception.getMessage());
        }
    }

    private static void closeQuietly(ClientSession session) {
        try {
            session.close();
        } catch (IOException exception) {
            LOGGER.debug("Could not close client session cleanly: {}", exception.getMessage());
        }
    }

    private static void closeSocketQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException exception) {
            LOGGER.debug("Could not close client socket cleanly: {}", exception.getMessage());
        }
    }
}
