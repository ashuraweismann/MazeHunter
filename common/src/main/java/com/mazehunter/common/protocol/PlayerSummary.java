package com.mazehunter.common.protocol;

import com.mazehunter.common.model.PlayerRole;

/**
 * Summary DTO representing a player in lobby or in-game state broadcasts.
 */
public record PlayerSummary(
        String id,
        String name,
        boolean ready,
        PlayerRole role,
        int x,
        int y,
        boolean caught
) {
}
