package com.mazehunter.server.net;

import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.server.game.GameLoop;
import com.mazehunter.server.game.MatchManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread-safe registry for connected client sessions. */
public final class ConnectionManager {

    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextPlayerNumber = new AtomicInteger(100);
    private final Object registrationLock = new Object();
    private final int maximumSessions;

    private volatile MatchManager matchManager;
    private volatile GameLoop gameLoop;

    public ConnectionManager(int maximumSessions) {
        if (maximumSessions < 1) {
            throw new IllegalArgumentException("maximumSessions must be positive");
        }
        this.maximumSessions = maximumSessions;
    }

    public void setMatchManager(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public void setGameLoop(GameLoop gameLoop) {
        this.gameLoop = gameLoop;
    }

    public GameLoop getGameLoop() {
        return gameLoop;
    }

    /** Registers the session and assigns a unique ID, or returns false when full. */
    public boolean register(ClientSession session, String requestedPlayerName) {
        Objects.requireNonNull(session, "session");
        String playerName = requestedPlayerName == null || requestedPlayerName.isBlank()
                ? "Guest"
                : requestedPlayerName.strip();

        synchronized (registrationLock) {
            if (sessions.size() >= maximumSessions || session.getStatus() != ClientSession.Status.CONNECTED) {
                return false;
            }

            String playerId = "p-" + nextPlayerNumber.incrementAndGet();
            session.activate(playerId, playerName);
            sessions.put(playerId, session);
            if (matchManager != null) {
                matchManager.registerPlayer(playerId, playerName);
            }
            return true;
        }
    }

    public Optional<ClientSession> findSession(String playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public List<ClientSession> getSessions() {
        return List.copyOf(sessions.values());
    }

    public int getConnectedCount() {
        return sessions.size();
    }

    public int getMaximumSessions() {
        return maximumSessions;
    }

    /** Broadcasts an envelope to all currently active sessions. */
    public void broadcast(MessageEnvelope message) {
        if (message == null) {
            return;
        }
        for (ClientSession session : sessions.values()) {
            if (session.isActive()) {
                session.sendMessage(message);
            }
        }
    }

    /** Sends an envelope to a specific active player session. */
    public boolean sendTo(String playerId, MessageEnvelope message) {
        if (playerId == null || message == null) {
            return false;
        }
        ClientSession session = sessions.get(playerId);
        if (session != null && session.isActive()) {
            session.sendMessage(message);
            return true;
        }
        return false;
    }

    /** Removes only the expected session, avoiding deletion of a newer entry by ID. */
    public boolean remove(ClientSession session) {
        if (session == null || session.getPlayerId() == null) {
            return false;
        }
        boolean removed = sessions.remove(session.getPlayerId(), session);
        if (removed && matchManager != null) {
            matchManager.unregisterPlayer(session.getPlayerId());
        }
        return removed;
    }
}
