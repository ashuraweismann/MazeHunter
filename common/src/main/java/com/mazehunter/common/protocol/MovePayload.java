package com.mazehunter.common.protocol;

import com.mazehunter.common.model.Direction;

import java.util.Objects;

/**
 * Payload sent by client requesting directional movement.
 */
public record MovePayload(Direction direction, long sequence) {
    public MovePayload {
        Objects.requireNonNull(direction, "direction cannot be null");
    }
}
