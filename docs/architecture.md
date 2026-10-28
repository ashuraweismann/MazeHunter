# Maze Hunter — System Architecture

## 1. Overview

Maze Hunter is a multiplayer, real-time client-server maze survival game. The application is built using Java 17 and structured into three distinct modules:

* **`common`**: Shared protocol definitions, message envelopes, JSON serialization/deserialization utilities, coordinate models, and validation logic.
* **`server`**: Standalone, headless authoritative game server using Java TCP sockets, multithreaded client handlers, a single-threaded game loop, collision detection, and periodic state broadcasting.
* **`client`**: Graphical desktop application built with LibGDX (LWJGL3 backend), handling rendering, user keyboard input, and network synchronization.

---

## 2. Architectural Principles

1. **Server Authority:**
   * The server is the single source of truth for all game rules, positions, role assignments, collision detection, timers, and match outcomes.
   * Clients submit *input intentions* (e.g., `MOVE` with direction `UP`). The server validates whether the move is permissible (walkable tile, player active, within bounds) and applies the update.
   * Clients render the world according to the latest authoritative server snapshots.

2. **Concurrency & Thread Safety:**
   * **Client Connection Handlers:** Each connected client has an independent reader worker listening on the TCP socket.
   * **Single-Writer Game Loop:** To prevent race conditions, client reader workers do *not* mutate game state directly. Instead, they enqueue validated `ClientCommand` objects into a thread-safe `ConcurrentLinkedQueue`.
   * **Decoupled Game Loop:** A single dedicated thread runs at a fixed tick rate (e.g., 20 Hz). In each tick, it drains the queue, updates the game simulation, detects collisions, checks timers, and dispatches state updates.
   * **Non-blocking Broadcast:** Outgoing state snapshots are dispatched asynchronously so that a slow or lagging client socket does not stall the server game loop.

3. **Client Thread Isolation:**
   * LibGDX operates on a dedicated rendering thread governed by the LWJGL3 OpenGL context.
   * A background network client thread receives server messages and enqueues them into an incoming message buffer.
   * The LibGDX `render()` method polls this buffer at the start of each frame, ensuring all graphical operations remain strictly on the OpenGL thread.

---

## 3. Module Responsibilities

### 3.1 `common`
* Immutable DTOs/records for all protocol messages.
* Shared grid and coordinate abstractions (`GridPosition`, `Direction`, `PlayerRole`, `MatchStatus`).
* Jackson JSON mapper configured with ISO-8601 timestamps and standard naming.
* Validation logic (direction validation, message size bounds, coordinate limits).

### 3.2 `server`
* `net.GameServer`: Manages the `ServerSocket` listener and accepts incoming connections.
* `net.ClientSession`: Tracks player connection lifecycle, socket streams, and assigned player IDs.
* `net.ClientHandler`: Reads newline-delimited JSON messages from the client socket.
* `game.GameLoop`: Executes the authoritative simulation at fixed intervals.
* `game.GameWorld`: Manages active player entities, roles, and coordinates.
* `game.CollisionDetector`: Computes boundary checks, wall obstructions, and Hunter-Runner catches.
* `maze.Maze`: Represents the 2D grid structure and validates walkable paths between spawns.

### 3.3 `client`
* `ClientLauncher`: Entry point configuring and launching the LWJGL3 application.
* `MazeHunterGame`: LibGDX `Game` instance managing screen transitions.
* `screen.MenuScreen`: Initial connection screen allowing IP/port entry.
* `screen.LobbyScreen`: Shows connected players and ready status.
* `screen.GameScreen`: Renders the maze, player avatars, HUD timer, and role indicators.
* `screen.GameOverScreen`: Displays final match results and allows returning to the lobby.
* `net.NetworkClient`: Manages socket I/O on a background thread.
