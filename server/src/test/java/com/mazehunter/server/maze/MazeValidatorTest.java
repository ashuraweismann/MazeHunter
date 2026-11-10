package com.mazehunter.server.maze;

import com.mazehunter.common.model.MazeGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MazeValidatorTest {

    @Test
    void acceptsDefaultMazeWithConnectedSpawnsAndCorridors() {
        assertDoesNotThrow(() -> MazeValidator.validate(MazeGrid.createDefault()));
    }

    @Test
    void rejectsRunnerSpawnWithoutPathToHunter() {
        MazeGrid maze = maze(
                new int[]{1, 1, 1, 1, 1},
                new int[]{1, 2, 1, 1, 1},
                new int[]{1, 1, 1, 1, 1},
                new int[]{1, 1, 1, 3, 1},
                new int[]{1, 1, 1, 1, 1}
        );

        assertThrows(IllegalArgumentException.class, () -> MazeValidator.validate(maze));
    }

    @Test
    void rejectsDisconnectedWalkableCorridorEvenWhenSpawnsAreConnected() {
        MazeGrid maze = maze(
                new int[]{1, 1, 1, 1, 1},
                new int[]{1, 2, 0, 1, 1},
                new int[]{1, 0, 0, 1, 1},
                new int[]{1, 3, 1, 0, 1},
                new int[]{1, 1, 1, 1, 1}
        );

        assertThrows(IllegalArgumentException.class, () -> MazeValidator.validate(maze));
    }

    @Test
    void rejectsMissingSpawnMarkersInsteadOfUsingMazeGridFallbacks() {
        MazeGrid maze = maze(
                new int[]{1, 1, 1, 1, 1},
                new int[]{1, 0, 0, 0, 1},
                new int[]{1, 0, 0, 0, 1},
                new int[]{1, 0, 0, 0, 1},
                new int[]{1, 1, 1, 1, 1}
        );

        assertThrows(IllegalArgumentException.class, () -> MazeValidator.validate(maze));
    }

    private static MazeGrid maze(int[]... cells) {
        return new MazeGrid(cells[0].length, cells.length, cells);
    }
}
