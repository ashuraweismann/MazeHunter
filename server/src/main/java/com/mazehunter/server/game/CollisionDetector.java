package com.mazehunter.server.game;

import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.PlayerRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Detects and records exact-cell Hunter catches. */
public final class CollisionDetector {

    private CollisionDetector() {
    }

    public static List<PlayerState> detectCaughtRunners(Collection<PlayerState> players) {
        Objects.requireNonNull(players, "players");

        List<GridPosition> activeHunterPositions = new ArrayList<>();
        for (PlayerState player : players) {
            Objects.requireNonNull(player, "players must not contain null");
            if (player.getRole() == PlayerRole.HUNTER && player.isActive()) {
                activeHunterPositions.add(player.getPosition());
            }
        }
        if (activeHunterPositions.isEmpty()) {
            return List.of();
        }

        List<PlayerState> caughtRunners = new ArrayList<>();
        for (PlayerState player : players) {
            if (player.getRole() != PlayerRole.RUNNER || !player.isActive()) {
                continue;
            }
            if (activeHunterPositions.contains(player.getPosition())) {
                player.markCaught();
                caughtRunners.add(player);
            }
        }
        return List.copyOf(caughtRunners);
    }
}
