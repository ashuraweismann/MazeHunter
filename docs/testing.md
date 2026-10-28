# Maze Hunter — Testing Strategy & Verification Guide

## 1. Testing Philosophy

The testing strategy follows a tiered verification methodology to ensure correctness, reliability, and concurrency safety:

```
+-------------------------------------------------------+
|  Manual Acceptance (3 Clients + 1 Server Integration) |
+-------------------------------------------------------+
|  Integration Tests (Headless TCP Network Handshakes)  |
+-------------------------------------------------------+
|  Unit Tests (Common DTOs, Movement, Maze, Collisions)  |
+-------------------------------------------------------+
```

---

## 2. Unit Testing Strategy

All unit tests are executed using **JUnit 5 Jupiter**. Unit tests do not spin up network sockets or graphical rendering loops.

### 2.1 `common` Test Coverage
* **Serialization/Deserialization:** Verify JSON serialization round-trips for every protocol message (`CONNECT`, `WELCOME`, `MOVE`, `STATE_UPDATE`, `PLAYER_CAUGHT`, etc.).
* **Boundary & Envelope Validation:** Ensure unknown properties are handled gracefully and malformed JSON triggers appropriate parsing exceptions.
* **Coordinate Math:** Verify grid boundary math and directional offsets.

### 2.2 `server` Test Coverage
* **Maze Connectivity:** Verify flood-fill/BFS pathfinding ensures open paths between player spawns and no players spawn inside walls.
* **Movement Rules:** Ensure moves into walls are rejected, diagonal inputs are prohibited, and out-of-bounds requests are rejected.
* **Collision Detection:** Verify exact cell overlap logic for Hunter-Runner catch events.
* **Game Lifecycle:** Test timer countdown logic, single-catch state transitions, and Hunter/Runner win condition triggers.

---

## 3. Integration Testing Strategy

Integration tests verify multi-threaded TCP behavior without requiring a graphical window.

### 3.1 Network Handshake & Concurrency
* **Multi-Client Connection Test:** Programmatically launch a test `GameServer` on a dynamic port (`0`), connect 3-4 concurrent TCP client sockets, and verify all clients receive unique IDs and `WELCOME` messages.
* **Ping/Pong Heartbeat:** Ensure low-latency request/response roundtrips across all active connections.
* **Graceful Disconnect:** Disconnect one client abruptly (close socket without `QUIT`), and verify the server remains healthy and active for remaining players.

### 3.2 State Synchronization & Command Queue
* Simulate multiple clients sending simultaneous `MOVE` commands.
* Verify commands are consumed from `ConcurrentLinkedQueue` by the `GameLoop` and produce deterministic `STATE_UPDATE` broadcasts.

---

## 4. Manual Acceptance Testing Procedure

Before merging milestones, perform this 8-step manual test:

1. Launch the server in a terminal:
   ```bash
   ./gradlew :server:run
   ```
2. Launch 3 separate client instances:
   ```bash
   ./gradlew :client:run
   ```
3. Enter player names in each client and join the lobby.
4. Verify all 3 players appear on each client's lobby screen.
5. Toggle ready status on all clients and start the match.
6. Verify exactly 1 player is highlighted as the Hunter and 2 players as Runners.
7. Move players using `W/A/S/D` or Arrow keys:
   * Verify players cannot walk through walls.
   * Verify all clients see consistent player positions in real time.
8. As the Hunter, catch a Runner:
   * Verify the Runner is marked as caught and cannot move.
   * Verify timer expiration or catching all Runners displays the Game Over screen.
   * Return to lobby and ensure a new match can start cleanly.

---

## 5. Running Automated Tests

To run all automated tests across all modules:
```bash
./gradlew test
```

To run tests for a specific module:
```bash
./gradlew :common:test
./gradlew :server:test
./gradlew :client:test
```

Test reports are generated in HTML format under:
`<module>/build/reports/tests/test/index.html`
