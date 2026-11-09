package com.mazehunter.common.model;

/**
 * Immutable 2D grid position coordinate in the maze.
 *
 * @param x horizontal coordinate (0-indexed, column)
 * @param y vertical coordinate (0-indexed, row)
 */
public record GridPosition(int x, int y) {

    public GridPosition translate(Direction direction) {
        if (direction == null) {
            return this;
        }
        return new GridPosition(x + direction.getDx(), y + direction.getDy());
    }

    public int manhattanDistance(GridPosition other) {
        if (other == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    public boolean isAdjacent(GridPosition other) {
        return manhattanDistance(other) == 1;
    }

    public boolean isWithin(int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
}
