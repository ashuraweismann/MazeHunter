package com.mazehunter.common.protocol;

import com.mazehunter.common.model.PlayerRole;

import java.util.Objects;

/**
 * Broadcast sent to each player when a match begins, containing the maze layout,
 * assigned role, spawn coordinates, and match duration.
 */
public record GameStartPayload(
        String matchId,
        int mazeWidth,
        int mazeHeight,
        int[][] mazeGrid,
        PlayerRole assignedRole,
        int spawnX,
        int spawnY,
        int durationSeconds
) {
    public GameStartPayload {
        Objects.requireNonNull(matchId, "matchId cannot be null");
        Objects.requireNonNull(assignedRole, "assignedRole cannot be null");
        Objects.requireNonNull(mazeGrid, "mazeGrid cannot be null");
    }
}
