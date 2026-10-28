# Maze Hunter — Agent Development Guide & Team Charter

## 1. Project Identity

* **Project Name:** Maze Hunter  
* **Project Title:** Maze Hunter — A Real-Time Multiplayer Client-Server Maze Survival Game  
* **Course:** EC5205 Operating Systems & Network Programming (University of Ruhuna, 25th Batch)  
* **Team Size:** 3 Members  
* **Primary Objective:** Build a robust, real-time multiplayer game demonstrating TCP sockets, multi-threading, concurrency, synchronization, and server-authoritative game logic.
* **Strict Rule on Prototype:** The prototype in `MazeHunter-v0.0` is for architectural reference only. **Never copy code directly from the prototype**. All code in this repository must be developed modularly according to the 3-member team task breakdown.

---

## 2. 3-Member Team Allocation

The workload is partitioned across three core tracks as specified in [`docs/team_work_division.md`](docs/team_work_division.md):

* **Member 1 (Lead Systems & Network Infrastructure):**
  * TCP `ServerSocket` and multi-threaded client listeners (`ClientHandler` / `Server_Thread`).
  * Session lifecycle (`ClientSession`, `ConnectionManager`) and disconnect recovery.
  * Thread-safe non-blocking state broadcasting engine.
  * Line-delimited JSON protocol envelopes and stream framing (`BufferedReader` / `PrintWriter`).

* **Member 2 (Authoritative Game Engine & Simulation):**
  * Authoritative fixed-tick `GameLoop` (20 Hz) consuming actions from `ConcurrentLinkedQueue<ClientCommand>`.
  * 2D Maze grid data structure and BFS flood-fill path connectivity validator (`MazeValidator`).
  * Server-side movement validator, boundary enforcement, and Hunter-Runner collision detector (`CollisionDetector`).
  * Match state machine (`LOBBY` ➔ `COUNTDOWN` ➔ `IN_PROGRESS` ➔ `GAME_OVER`), random Hunter assignment, countdown timer, and win conditions.

* **Member 3 (Desktop Client & UI Presentation):**
  * LibGDX desktop application and screen management (`MenuScreen`, `LobbyScreen`, `GameScreen`, `GameOverScreen`).
  * Dedicated thread-isolated `NetworkClient` background worker bridging socket I/O to the OpenGL render loop.
  * User keyboard input processing and `MOVE` command generation.
  * HUD rendering: maze visualization, distinct player avatars, live countdown timer, and game status indicators.

---

## 3. Atomic Commit Policy

All version control operations must be conducted in an **atomic** manner:
1. Each commit must represent a single, well-defined, logical unit of change.
2. Never bundle unrelated changes (e.g. documentation updates + build script tweaks) into one commit.
3. Use Conventional Commits formatting:
   * `feat: ...` for new features or capabilities.
   * `fix: ...` for bug fixes and patches.
   * `docs: ...` for documentation additions or changes.
   * `test: ...` for unit or integration test suites.
   * `refactor: ...` for code refactoring with no behavioral change.
   * `chore: ...` for build scripts, dependencies, or `.gitignore` changes.
4. Ensure the codebase remains in a buildable/valid state at every commit point.

---

## 4. Architectural Rules

1. **Server Authority:**
   * The server is the sole source of truth.
   * Clients submit *input intentions*; the server validates and applies them.
   * Clients never decide collisions, wall traversals, or victory states.

2. **Concurrency & Thread Safety:**
   * Incoming client reader threads must **not** mutate game state directly.
   * All mutations occur sequentially on the single-threaded `GameLoop` by draining the thread-safe `ConcurrentLinkedQueue<ClientCommand>`.
   * Outgoing socket writes must be non-blocking with respect to the `GameLoop`.

3. **Client Thread Isolation:**
   * The client GUI/OpenGL thread must never block on network socket operations.
   * Network I/O runs on a dedicated background worker thread communicating via thread-safe message queues.

---

## 5. Development Phases

* **Phase 0:** Setup, architecture, protocol specification, and team work division documentation. *(Current Phase)*
* **Phase 1:** Shared protocol DTOs and JSON serialization (`common` module).
* **Phase 2:** Headless TCP server core and multi-client socket acceptance (`server.net`).
* **Phase 3:** Authoritative maze simulation, movement validation, and game loop (`server.game`).
* **Phase 4:** Desktop client screens, input handling, and thread-isolated network client (`client`).
* **Phase 5:** End-to-end multi-client integration, stress testing, and acceptance verification.
* **Phase 6:** Final polish, code commenting, and coursework viva defense preparation.
