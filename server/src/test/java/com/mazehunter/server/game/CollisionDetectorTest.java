package com.mazehunter.server.game;

import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.PlayerRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionDetectorTest {

    @Test
    void catchesOnlyActiveRunnersAtTheHuntersExactCell() {
        PlayerState hunter = player("hunter", PlayerRole.HUNTER, 2, 3);
        PlayerState overlappingRunner = player("runner-1", PlayerRole.RUNNER, 2, 3);
        PlayerState nearbyRunner = player("runner-2", PlayerRole.RUNNER, 2, 2);
        PlayerState disconnectedRunner = player("runner-3", PlayerRole.RUNNER, 2, 3);
        disconnectedRunner.setConnectionStatus(PlayerState.ConnectionStatus.DISCONNECTED);

        List<PlayerState> caught = CollisionDetector.detectCaughtRunners(
                List.of(hunter, overlappingRunner, nearbyRunner, disconnectedRunner));

        assertEquals(List.of(overlappingRunner), caught);
        assertTrue(overlappingRunner.isCaught());
        assertFalse(nearbyRunner.isCaught());
        assertFalse(disconnectedRunner.isCaught());
    }

    @Test
    void caughtRunnerStaysCaughtAndCannotMoveAfterDetection() {
        PlayerState hunter = player("hunter", PlayerRole.HUNTER, 1, 1);
        PlayerState runner = player("runner", PlayerRole.RUNNER, 1, 1);

        CollisionDetector.detectCaughtRunners(List.of(hunter, runner));
        assertTrue(runner.isCaught());
        assertFalse(runner.isActive());
        assertThrows(IllegalStateException.class,
                () -> runner.setPosition(new GridPosition(1, 2)));
        assertEquals(new GridPosition(1, 1), runner.getPosition());
        assertTrue(CollisionDetector.detectCaughtRunners(List.of(hunter, runner)).isEmpty());
    }

    @Test
    void doesNotCatchRunnersWhenHunterIsDisconnected() {
        PlayerState hunter = player("hunter", PlayerRole.HUNTER, 1, 1);
        hunter.setConnectionStatus(PlayerState.ConnectionStatus.DISCONNECTED);
        PlayerState runner = player("runner", PlayerRole.RUNNER, 1, 1);

        assertTrue(CollisionDetector.detectCaughtRunners(List.of(hunter, runner)).isEmpty());
        assertFalse(runner.isCaught());
    }

    private static PlayerState player(String id, PlayerRole role, int x, int y) {
        return new PlayerState(id, id, role, new GridPosition(x, y));
    }
}
