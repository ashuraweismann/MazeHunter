package com.mazehunter.server.game;

import com.mazehunter.common.model.Direction;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.MovePayload;

import java.util.Objects;

/**
 * Immutable command passed from concurrent network reader threads to the authoritative GameLoop.
 */
public record ClientCommand(
        String playerId,
        MessageType type,
        Object payload,
        long timestamp
) {

    public ClientCommand(String playerId, MessageType type, Object payload) {
        this(playerId, type, payload, System.currentTimeMillis());
    }

    public ClientCommand {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
    }

    public Direction direction() {
        if (payload instanceof MovePayload movePayload) {
            return movePayload.direction();
        }
        return null;
    }
}
