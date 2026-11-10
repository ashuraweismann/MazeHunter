package com.mazehunter.server.game;

import com.mazehunter.common.model.Direction;
import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.MazeGrid;
import com.mazehunter.common.model.PlayerRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementValidatorTest {

    private final MazeGrid maze = new MazeGrid(3, 3, new int[][]{
            {0, 0, 0},
            {0, 0, 1},
            {0, 0, 0}
    });

    @Test
    void allowsOnlyOpenOneCellOrthogonalMoves() {
        PlayerState player = playerAt(1, 1);

        assertTrue(MovementValidator.isValidMove(player, Direction.UP, maze));
        assertTrue(MovementValidator.isValidMove(player, Direction.LEFT, maze));
        assertFalse(MovementValidator.isValidMove(player, Direction.RIGHT, maze));
        assertFalse(MovementValidator.isValidMoveTo(player, new GridPosition(2, 2), maze));
    }

    @Test
    void rejectsOutOfBoundsDestinationAndMissingDirection() {
        PlayerState player = playerAt(0, 0);

        assertFalse(MovementValidator.isValidMove(player, Direction.LEFT, maze));
        assertFalse(MovementValidator.isValidMoveTo(player, new GridPosition(-1, 0), maze));
        assertFalse(MovementValidator.isValidMove(player, null, maze));
    }

    @Test
    void rejectsMovementForCaughtOrDisconnectedPlayers() {
        PlayerState caughtPlayer = playerAt(1, 1);
        caughtPlayer.markCaught();
        assertFalse(MovementValidator.isValidMove(caughtPlayer, Direction.UP, maze));

        PlayerState disconnectedPlayer = playerAt(1, 1);
        disconnectedPlayer.setConnectionStatus(PlayerState.ConnectionStatus.DISCONNECTED);
        assertFalse(MovementValidator.isValidMove(disconnectedPlayer, Direction.UP, maze));
    }

    private static PlayerState playerAt(int x, int y) {
        return new PlayerState("p-1", "Player", PlayerRole.RUNNER, new GridPosition(x, y));
    }
}
