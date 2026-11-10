package com.mazehunter.server.game;

import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.PlayerRole;

import java.util.Objects;

/** Mutable authoritative state for one player in a match. */
public final class PlayerState {

    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED
    }

    private final String playerId;
    private final String name;
    private PlayerRole role;
    private GridPosition position;
    private boolean caught;
    private boolean ready;
    private ConnectionStatus connectionStatus;

    public PlayerState(String playerId, String name, PlayerRole role, GridPosition position) {
        this.playerId = requireText(playerId, "playerId");
        this.name = requireText(name, "name");
        this.role = Objects.requireNonNull(role, "role");
        this.position = Objects.requireNonNull(position, "position");
        this.connectionStatus = ConnectionStatus.CONNECTED;
    }

    public PlayerState(String playerId, String name, GridPosition position) {
        this(playerId, name, PlayerRole.UNASSIGNED, position);
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public PlayerRole getRole() {
        return role;
    }

    public void setRole(PlayerRole role) {
        this.role = Objects.requireNonNull(role, "role");
    }

    public GridPosition getPosition() {
        return position;
    }

    public void setPosition(GridPosition position) {
        GridPosition newPosition = Objects.requireNonNull(position, "position");
        if (caught && !this.position.equals(newPosition)) {
            throw new IllegalStateException("A caught player cannot move");
        }
        this.position = newPosition;
    }

    public boolean isCaught() {
        return caught;
    }

    public void markCaught() {
        caught = true;
    }

    public void setCaught(boolean caught) {
        this.caught = caught;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = Objects.requireNonNull(connectionStatus, "connectionStatus");
    }

    public boolean isConnected() {
        return connectionStatus == ConnectionStatus.CONNECTED;
    }

    public void setConnected(boolean connected) {
        this.connectionStatus = connected ? ConnectionStatus.CONNECTED : ConnectionStatus.DISCONNECTED;
    }

    public boolean isActive() {
        return connectionStatus == ConnectionStatus.CONNECTED && !caught;
    }

    public boolean isActiveRunner() {
        return role == PlayerRole.RUNNER && isActive();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
