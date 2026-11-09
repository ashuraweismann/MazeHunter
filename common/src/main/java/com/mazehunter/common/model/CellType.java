package com.mazehunter.common.model;

/**
 * Types of cells comprising the 2D maze grid.
 */
public enum CellType {
    OPEN(0, true),
    WALL(1, false),
    SPAWN_HUNTER(2, true),
    SPAWN_RUNNER(3, true);

    private final int id;
    private final boolean walkable;

    CellType(int id, boolean walkable) {
        this.id = id;
        this.walkable = walkable;
    }

    public int getId() {
        return id;
    }

    public boolean isWalkable() {
        return walkable;
    }

    public static CellType fromId(int id) {
        for (CellType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return WALL;
    }
}
