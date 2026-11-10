package com.mazehunter.server.maze;

import com.mazehunter.common.model.CellType;
import com.mazehunter.common.model.Direction;
import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.MazeGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/** Validates maze spawns and connectivity using a breadth-first flood fill. */
public final class MazeValidator {

    private MazeValidator() {
    }

    public static void validate(MazeGrid maze) {
        Objects.requireNonNull(maze, "maze");
        int width = maze.getWidth();
        int height = maze.getHeight();
        int[][] grid = maze.getGrid();
        if (grid.length != height) {
            throw new IllegalArgumentException("Maze row count does not match its height");
        }

        GridPosition hunterSpawn = null;
        List<GridPosition> runnerSpawns = new ArrayList<>();
        int walkableCellCount = 0;
        for (int y = 0; y < height; y++) {
            if (grid[y] == null || grid[y].length != width) {
                throw new IllegalArgumentException("Maze row " + y + " does not match its width");
            }
            for (int x = 0; x < width; x++) {
                int cell = grid[y][x];
                if (cell < CellType.OPEN.getId() || cell > CellType.SPAWN_RUNNER.getId()) {
                    throw new IllegalArgumentException("Maze contains an unknown cell type at (" + x + ", " + y + ")");
                }
                if (CellType.fromId(cell).isWalkable()) {
                    walkableCellCount++;
                }
                if (cell == CellType.SPAWN_HUNTER.getId()) {
                    if (hunterSpawn != null) {
                        throw new IllegalArgumentException("Maze must contain exactly one Hunter spawn");
                    }
                    hunterSpawn = new GridPosition(x, y);
                } else if (cell == CellType.SPAWN_RUNNER.getId()) {
                    runnerSpawns.add(new GridPosition(x, y));
                }
            }
        }

        if (hunterSpawn == null) {
            throw new IllegalArgumentException("Maze must contain a Hunter spawn");
        }
        if (runnerSpawns.isEmpty()) {
            throw new IllegalArgumentException("Maze must contain at least one Runner spawn");
        }

        boolean[][] visited = new boolean[height][width];
        Queue<GridPosition> frontier = new ArrayDeque<>();
        frontier.add(hunterSpawn);
        visited[hunterSpawn.y()][hunterSpawn.x()] = true;
        int reachedCellCount = 0;
        while (!frontier.isEmpty()) {
            GridPosition current = frontier.remove();
            reachedCellCount++;
            for (Direction direction : Direction.values()) {
                GridPosition neighbor = current.translate(direction);
                if (neighbor.isWithin(width, height)
                        && !visited[neighbor.y()][neighbor.x()]
                        && CellType.fromId(grid[neighbor.y()][neighbor.x()]).isWalkable()) {
                    visited[neighbor.y()][neighbor.x()] = true;
                    frontier.add(neighbor);
                }
            }
        }

        for (GridPosition runnerSpawn : runnerSpawns) {
            if (!visited[runnerSpawn.y()][runnerSpawn.x()]) {
                throw new IllegalArgumentException("Runner spawn is not connected to the Hunter: " + runnerSpawn);
            }
        }
        if (reachedCellCount != walkableCellCount) {
            throw new IllegalArgumentException("Maze contains walkable corridors disconnected from the Hunter");
        }
    }
}
