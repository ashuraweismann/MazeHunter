package com.mazehunter.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the 2D maze layout including walls, open corridors, and player spawn positions.
 */
public class MazeGrid {

    private final int width;
    private final int height;
    private final int[][] grid;

    @JsonCreator
    public MazeGrid(
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("grid") int[][] grid
    ) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Maze dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.grid = Objects.requireNonNull(grid, "Grid data cannot be null");
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[][] getGrid() {
        return grid;
    }

    public CellType getCell(int x, int y) {
        if (!isInBounds(x, y)) {
            return CellType.WALL;
        }
        return CellType.fromId(grid[y][x]);
    }

    public CellType getCell(GridPosition pos) {
        if (pos == null) {
            return CellType.WALL;
        }
        return getCell(pos.x(), pos.y());
    }

    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean isInBounds(GridPosition pos) {
        return pos != null && isInBounds(pos.x(), pos.y());
    }

    public boolean isWalkable(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return getCell(x, y).isWalkable();
    }

    public boolean isWalkable(GridPosition pos) {
        return pos != null && isWalkable(pos.x(), pos.y());
    }

    @JsonIgnore
    public GridPosition getHunterSpawn() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == CellType.SPAWN_HUNTER.getId()) {
                    return new GridPosition(x, y);
                }
            }
        }
        // Fallback to top-left open cell
        return new GridPosition(1, height - 2);
    }

    @JsonIgnore
    public List<GridPosition> getRunnerSpawns() {
        List<GridPosition> spawns = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == CellType.SPAWN_RUNNER.getId()) {
                    spawns.add(new GridPosition(x, y));
                }
            }
        }
        if (spawns.isEmpty()) {
            spawns.add(new GridPosition(1, 1));
            spawns.add(new GridPosition(width - 2, 1));
            spawns.add(new GridPosition(width - 2, height - 2));
        }
        return Collections.unmodifiableList(spawns);
    }

    /**
     * Creates a standard balanced 15x15 maze with perimeter walls, corridors,
     * 1 Hunter spawn at (1, 13), and 3 Runner spawns at (1, 1), (13, 1), and (13, 13).
     */
    public static MazeGrid createDefault() {
        int w = 15;
        int h = 15;
        int[][] data = {
                // y = 0 (bottom)
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                // y = 1: Runner spawns at (1,1) and (13,1)
                {1, 3, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 3, 1},
                // y = 2
                {1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1},
                // y = 3
                {1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1},
                // y = 4
                {1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1},
                // y = 5
                {1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1},
                // y = 6
                {1, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1},
                // y = 7: Central crossway
                {1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1},
                // y = 8
                {1, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1},
                // y = 9
                {1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1},
                // y = 10
                {1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1},
                // y = 11
                {1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1},
                // y = 12
                {1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1},
                // y = 13: Hunter spawn at (1,13), Runner spawn at (13,13)
                {1, 2, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 3, 1},
                // y = 14 (top)
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        };
        return new MazeGrid(w, h, data);
    }
}
