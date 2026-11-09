package com.mazehunter.common.protocol;

import java.util.Objects;

/**
 * Broadcast when the Hunter catches a Runner.
 */
public record PlayerCaughtPayload(
        String hunterId,
        String caughtPlayerId,
        int remainingRunners
) {
    public PlayerCaughtPayload {
        Objects.requireNonNull(hunterId, "hunterId cannot be null");
        Objects.requireNonNull(caughtPlayerId, "caughtPlayerId cannot be null");
    }
}
