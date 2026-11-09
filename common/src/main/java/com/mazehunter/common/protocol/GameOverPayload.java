package com.mazehunter.common.protocol;

import com.mazehunter.common.model.PlayerRole;

import java.util.Objects;

/**
 * Broadcast when the match terminates with a winning role and reason.
 */
public record GameOverPayload(
        PlayerRole winningRole,
        String reason,
        int durationSeconds
) {
    public GameOverPayload {
        Objects.requireNonNull(winningRole, "winningRole cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
