package com.mazehunter.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MazeGridTest {

    @Test
    @DisplayName("Default maze should be 15x15 with solid perimeter walls")
    void defaultMazeShouldHaveValidDimensionsAndPerimeter() {
        MazeGrid maze = MazeGrid.createDefault();
        assertEquals(15, maze.getWidth());
        assertEquals(15, maze.getHeight());

        // Test all four outer boundaries are walls
        for (int x = 0; x < 15; x++) {
            assertFalse(maze.isWalkable(x, 0), "Bottom perimeter cell must be wall");
            assertFalse(maze.isWalkable(x, 14), "Top perimeter cell must be wall");
        }
        for (int y = 0; y < 15; y++) {
            assertFalse(maze.isWalkable(0, y), "Left perimeter cell must be wall");
            assertFalse(maze.isWalkable(14, y), "Right perimeter cell must be wall");
        }
    }

    @Test
    @DisplayName("Default maze should define Hunter spawn and Runner spawns on walkable cells")
    void defaultMazeShouldDefineValidSpawns() {
        MazeGrid maze = MazeGrid.createDefault();

        GridPosition hunterSpawn = maze.getHunterSpawn();
        assertNotNull(hunterSpawn);
        assertEquals(new GridPosition(1, 13), hunterSpawn);
        assertTrue(maze.isWalkable(hunterSpawn), "Hunter spawn must be walkable");
        assertEquals(CellType.SPAWN_HUNTER, maze.getCell(hunterSpawn));

        List<GridPosition> runnerSpawns = maze.getRunnerSpawns();
        assertFalse(runnerSpawns.isEmpty(), "Runner spawns must not be empty");
        assertTrue(runnerSpawns.contains(new GridPosition(1, 1)));
        assertTrue(runnerSpawns.contains(new GridPosition(13, 1)));
        assertTrue(runnerSpawns.contains(new GridPosition(13, 13)));

        for (GridPosition spawn : runnerSpawns) {
            assertTrue(maze.isWalkable(spawn), "Runner spawn must be walkable: " + spawn);
        }
    }

    @Test
    @DisplayName("Out-of-bounds coordinates should be treated as non-walkable walls")
    void outOfBoundsShouldBeNonWalkable() {
        MazeGrid maze = MazeGrid.createDefault();

        assertFalse(maze.isWalkable(-1, 5));
        assertFalse(maze.isWalkable(5, -1));
        assertFalse(maze.isWalkable(15, 5));
        assertFalse(maze.isWalkable(5, 15));
        assertEquals(CellType.WALL, maze.getCell(-1, -1));
        assertFalse(maze.isInBounds(new GridPosition(-1, 0)));
    }
}
