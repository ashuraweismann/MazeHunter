# Maze Hunter — Team Work Division & Collaboration Guide

**Course:** EC5205 Operating Systems & Network Programming  
**Faculty:** Faculty of Engineering, University of Ruhuna (25th Batch)  
**Project:** Network Programming Group Project — *Maze Hunter: Real-Time Multiplayer Client-Server Maze Game*  
**Team Size:** 3 Members  
**Target Submission / Deadline:** 14th November 2026  

---

## 1. Executive Summary & Team Structure

Maze Hunter is architected into three strictly decoupled modules (`common`, `server`, and `client`), enabling each group member to take full ownership of a distinct engineering discipline while collaborating via stable interface contracts.

```
+----------------------------------------------------------------------------------------------------+
|                                    SHARED PROTOCOL & DTOS (`common`)                               |
|                                    Owned jointly / Agreed upfront                                  |
+----------------------------------------------------------------------------------------------------+
                                      ▲                                        ▲
                                      │                                        │
           +--------------------------┴-------------+      +-------------------┴-------------------+
           |           MEMBER 1: LEAD NETWORKING    |      |       MEMBER 3: CLIENT & GUI          |
           | - TCP ServerSocket & Multi-threading   |      | - LibGDX 2D Rendering & Screens       |
           | - ClientSession & Connection Manager   |      | - Thread-Isolated NetworkClient       |
           | - Stream Framing & Non-blocking Outbox |      | - Keyboard Input & HUD presentation   |
           +----------------------------------------+      +---------------------------------------+
                                      │
                                      ▼ (ConcurrentLinkedQueue<ClientCommand>)
           +---------------------------------------------------------------------------------------+
           |                               MEMBER 2: GAME ENGINE & SIMULATION                      |
           | - Authoritative Fixed-Tick GameLoop (20 Hz)                                           |
           | - Maze Grid Representation & Path Connectivity Validation                             |
           | - Server-Side Movement Validation & Hunter-Runner Collision Detection                 |
           | - Match State Machine, Roles (Hunter/Runner), Timer & Win Conditions                  |
           +---------------------------------------------------------------------------------------+
```

---

## 2. Detailed Member Work Breakdown

### Member 1: Systems, TCP Networking & Concurrency Architecture

* **Primary Modules & Packages:** `server.net.*`, `common.protocol.*`, `common.dto.*`
* **Core Responsibilities:**
  1. **TCP Socket Server:**
     * Implement `GameServer` using Java's standard `ServerSocket`.
     * Support configurable port binding (default `8080`) and graceful shutdown hooks.
     * Establish multi-threaded client connection acceptance loop.
  2. **Multi-Threaded Client Handling (`Server_Thread` Architecture):**
     * Implement `ClientHandler` (extending `Thread` or implementing `Runnable`) per connected client.
     * Handle incoming TCP streams using `BufferedReader` and `InputStreamReader`.
     * Implement line-delimited message framing and payload length caps (8 KB max).
  3. **Session & Lifecycle Management:**
     * Implement `ClientSession` and `ConnectionManager` to manage player identities, assigned IDs (`p-101`, etc.), and socket streams.
     * Handle abrupt socket disconnects, EOF exceptions, and socket timeout events without crashing the server.
  4. **Thread-Safe Outgoing Broadcast Engine:**
     * Implement non-blocking state dissemination (`broadcast(Message)`, `sendTo(playerId, Message)`).
     * Ensure slow network sockets or stalled client buffers do not block or lag the authoritative server game loop.
  5. **Heartbeat & Diagnostics:**
     * Implement `PING` / `PONG` heartbeat monitoring and protocol violation `ERROR` generation.
* **Lecture Concept Mapping:**
  * Socket Programming (`ServerSocket`, `Socket`).
  * Threads (`Thread`, `Runnable`, `ThreadHandler`, `Server_Thread`).
  * Stream I/O (`BufferedReader`, `PrintWriter`).
* **Viva Defense Preparation:**
  * Explain why a dedicated thread is needed for each incoming client connection.
  * Explain how blocking I/O (`in.readLine()`) behaves and how socket timeouts are configured.
  * Explain how the server avoids thread leaks when clients unexpectedly disconnect.

---

### Member 2: Authoritative Game Engine & Simulation

* **Primary Modules & Packages:** `server.game.*`, `server.maze.*`, `common.model.*`
* **Core Responsibilities:**
  1. **Authoritative Single-Writer Game Loop:**
     * Implement `GameLoop` running on a dedicated thread at a fixed tick rate (e.g., 20 Hz / 50ms per tick).
     * Consume incoming player actions from a thread-safe `ConcurrentLinkedQueue<ClientCommand>` filled by Member 1's handlers.
  2. **Maze Architecture & Path Validation:**
     * Implement `Maze` 2D grid matrix representing walkable paths (`0`) and impassable walls (`1`).
     * Implement `MazeValidator` utilizing Breadth-First Search (BFS) / Flood-Fill to ensure every player spawn has a valid, non-blocked path to other players.
  3. **Server-Side Movement & Collision Validation:**
     * Implement `MovementValidator` enforcing discrete directional grid movements (`UP`, `DOWN`, `LEFT`, `RIGHT`).
     * Prevent diagonal movement, out-of-bounds positions, or wall clipping.
     * Implement `CollisionDetector` to calculate exact coordinate overlap between the Hunter and active Runners.
  4. **Match Lifecycle & Rule Engine:**
     * Implement match states: `LOBBY`, `STARTING`, `IN_PROGRESS`, `GAME_OVER`.
     * Implement server-side random role assignment (exactly 1 Hunter, remaining players as Runners).
     * Implement caught state management: caught Runners are marked inactive and cannot move.
     * Implement monotonic timer countdown and win condition resolution (Hunter catches all Runners VS Timer expires with surviving Runners).
  5. **Snapshot Generation:**
     * Produce immutable `STATE_UPDATE` DTOs each tick for Member 1 to broadcast.
* **Lecture Concept Mapping:**
  * Concurrency and thread synchronization without heavy lock contention.
  * Race condition prevention using the single-writer pattern.
  * Discrete event simulation and monotonic timing.
* **Viva Defense Preparation:**
  * Explain why client-handlers do not directly modify game coordinates (preventing race conditions).
  * Explain how `ConcurrentLinkedQueue` acts as a lock-free synchronization bridge.
  * Explain why game rules (movement, collisions, win conditions) must be authoritative on the server.

---

### Member 3: Desktop Game Client & Network Integration

* **Primary Modules & Packages:** `client.net.*`, `client.screen.*`, `client.render.*`
* **Core Responsibilities:**
  1. **Thread-Isolated Network Client:**
     * Implement `NetworkClient` executing socket operations on a background daemon thread.
     * Manage the client TCP `Socket`, `PrintWriter`, and `BufferedReader`.
     * Push incoming server messages into a thread-safe FIFO buffer (`ConcurrentLinkedQueue<ServerMessage>`) for consumption by the rendering loop.
  2. **Screen Flow & Navigation:**
     * Implement `MenuScreen`: IP address, port, and player name inputs with validation.
     * Implement `LobbyScreen`: Visual roster of connected players, ready toggle checkbox/button, and match countdown status.
     * Implement `GameOverScreen`: Clear announcement banner ("Hunter Victory" or "Runners Survived!"), match summary stats, and "Back to Lobby" button.
  3. **In-Game Rendering (`GameScreen`):**
     * Render the 2D maze layout (walls and pathways) dynamically based on the grid received in `GAME_START`.
     * Render player avatars with clear visual distinction (e.g., Red/Predator icon for Hunter, Blue/Green for Runners).
     * Render local player highlights so the user immediately knows which avatar is theirs.
     * Render the Heads-Up Display (HUD): real-time match countdown timer, role indicator, and alive/caught runner counters.
  4. **Input Handling & Command Dispatch:**
     * Capture keyboard inputs (`W/A/S/D` and Arrow keys).
     * Send `MOVE` commands to the `NetworkClient` outbox.
  5. **Client Resilience & Error Handling:**
     * Display graceful user notifications when server connection drops or times out.
* **Lecture Concept Mapping:**
  * Client Socket connection (`Socket(ipAddress, port)`).
  * Multi-threading on the client side: separating the OpenGL/LibGDX render thread from the blocking network read thread.
* **Viva Defense Preparation:**
  * Explain why network I/O cannot be placed directly inside the game's `render()` frame method (it would freeze/stutter the GUI).
  * Explain how the client receives and displays server authoritative updates without making local movement decisions.

---

## 3. Interface Contracts Between Members

To allow all three members to work simultaneously without blocking one another, the team agrees on these three interfaces:

### Contract 1: Common Message Protocol (Agreed by all 3)
* Defined in `docs/protocol.md` and represented as immutable Java records/DTOs in `common`.
* All communication uses newline-delimited UTF-8 JSON.
* Core messages: `CONNECT`, `WELCOME`, `READY`, `LOBBY_UPDATE`, `GAME_START`, `MOVE`, `STATE_UPDATE`, `PLAYER_CAUGHT`, `GAME_OVER`, `PING`, `PONG`, `ERROR`.

### Contract 2: Networking to Game Engine (Member 1 ➔ Member 2)
```java
// Thread-safe command envelope passed from Member 1's handlers to Member 2's GameLoop
public record ClientCommand(
    String playerId,
    CommandType type,
    Direction direction,
    long timestamp
) {}

// Shared concurrent queue owned by the server runtime:
ConcurrentLinkedQueue<ClientCommand> commandQueue = new ConcurrentLinkedQueue<>();
```

### Contract 3: Game Engine to Networking (Member 2 ➔ Member 1)
```java
// Immutable state snapshot generated by Member 2's GameLoop and handed to Member 1's broadcaster
public record StateSnapshot(
    long tick,
    int remainingSeconds,
    List<PlayerStateDto> players,
    MatchStatus status
) {}

// Broadcaster interface implemented by Member 1:
public interface BroadcastService {
    void broadcast(MessageEnvelope message);
    void sendTo(String playerId, MessageEnvelope message);
}
```

---

## 4. Implementation Phases & Milestones

| Milestone | Target Date | Member 1 (Networking) | Member 2 (Engine) | Member 3 (Client) |
|---|---|---|---|---|
| **M1: Architecture & Protocol Contract** | Week 1 | Design JSON envelopes & socket framing specs | Design coordinate system & grid data models | Design screen state wireframes & input mappings |
| **M2: Core Standalone Modules** | Week 2 | Implement `GameServer`, `ClientHandler`, TCP handshake | Implement `Maze`, `MazeValidator`, and `CollisionDetector` | Implement `MenuScreen`, `LobbyScreen`, and LibGDX launcher |
| **M3: Loop & Thread Isolation** | Week 3 | Implement non-blocking broadcast & session manager | Implement fixed-tick `GameLoop` & command queue | Implement `NetworkClient` thread & message queue |
| **M4: End-to-End Integration** | Week 4 | Connect multi-client sockets to engine loop | Hook authoritative catch and win triggers | Integrate `GameScreen` rendering with live server packets |
| **M5: Testing, Hardening & Acceptance** | Week 5 | Stress test concurrent disconnects & latency | Run automated tests for collisions and timers | Perform 3-client manual acceptance test |
| **M6: Documentation & Viva Defense** | Week 6 | Document socket architecture & thread lifecycle | Document race condition avoidance & game loop | Prepare live demo, slides & viva talking points |

---

## 5. Git Branching & Atomic Commit Guidelines

1. **Branch Naming:**
   * `main` — Production-ready, fully compiling code only.
   * `feature/m1-networking` — Work branch for Member 1.
   * `feature/m2-game-engine` — Work branch for Member 2.
   * `feature/m3-client-ui` — Work branch for Member 3.

2. **Commit Message Standard (Atomic Commits):**
   Every commit should be small, focused, and self-contained:
   * `feat: add ClientHandler thread for TCP client connection`
   * `feat: implement BFS path connectivity in MazeValidator`
   * `feat: create thread-isolated NetworkClient in client module`
   * `test: add unit test for Hunter-Runner collision detection`
   * `docs: update protocol documentation with PLAYER_CAUGHT schema`
   * `fix: handle unexpected socket EOF gracefully in ConnectionManager`

3. **No Direct Pushes to `main`:**
   * Merge feature branches into `main` only after code compiles and all existing tests pass.
