package com.mazehunter.common.protocol;

import java.util.List;

/**
 * Broadcast sent to all players in the lobby showing player list and readiness.
 */
public record LobbyUpdatePayload(List<PlayerSummary> players, boolean canStart) {
    public LobbyUpdatePayload {
        if (players == null) {
            players = List.of();
        } else {
            players = List.copyOf(players);
        }
    }
}
