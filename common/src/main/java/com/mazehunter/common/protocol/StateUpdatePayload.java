package com.mazehunter.common.protocol;

import com.mazehunter.common.model.MatchStatus;

import java.util.List;
import java.util.Objects;

/**
 * Authoritative state snapshot broadcast by server at 15–20 Hz.
 */
public record StateUpdatePayload(
        long tick,
        int remainingSeconds,
        List<PlayerSummary> players,
        MatchStatus matchStatus
) {
    public StateUpdatePayload {
        Objects.requireNonNull(matchStatus, "matchStatus cannot be null");
        if (players == null) {
            players = List.of();
        } else {
            players = List.copyOf(players);
        }
    }
}
