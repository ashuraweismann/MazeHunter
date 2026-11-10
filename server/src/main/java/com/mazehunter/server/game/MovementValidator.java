package com.mazehunter.server.game;

import com.mazehunter.common.model.Direction;
import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.MazeGrid;

import java.util.Objects;

/** Validates one-cell orthogonal movement against the authoritative maze. */
public final class MovementValidator {

    private MovementValidator() {
    }

    public static boolean isValidMove(PlayerState player, Direction direction, MazeGrid maze) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(maze, "maze");
        if (direction == null) {
            return false;
        }
        return isValidMoveTo(player, player.getPosition().translate(direction), maze);
    }

    public static boolean isValidMoveTo(PlayerState player, GridPosition destination, MazeGrid maze) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(maze, "maze");
        return player.isActive()
                && destination != null
                && player.getPosition().isAdjacent(destination)
                && maze.isWalkable(destination);
    }
}
