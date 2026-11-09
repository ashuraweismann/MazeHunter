package com.mazehunter.server.net;

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

    public ConnectionManager(int maximumSessions) {
        if (maximumSessions < 1) {
            throw new IllegalArgumentException("maximumSessions must be positive");
        }
        this.maximumSessions = maximumSessions;
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

    /** Removes only the expected session, avoiding deletion of a newer entry by ID. */
    public boolean remove(ClientSession session) {
        if (session == null || session.getPlayerId() == null) {
            return false;
        }
        return sessions.remove(session.getPlayerId(), session);
    }
}
