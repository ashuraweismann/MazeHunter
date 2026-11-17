# Member 2 Defense Guide: Authoritative Game Engine & Concurrency

**Member Name:** Hansana H.A.J.  
**Registration Number:** EG/2023/5634  
**Role:** Authoritative Game Engine & Simulation (Game Engine Lead)  
**Evaluation Date:** Wednesday, 18th November 2026 (EC5205 Demo & Viva Evaluation)  
**Assigned Module:** `:server` (Package: `com.mazehunter.server.game` & `com.mazehunter.server.maze`) & `:common` (Package: `com.mazehunter.common.model`)

---

## 1. Owned Source Code Files & Locations

Have these files open in your IDE tabs before the evaluation starts:

| Component / Class | File Location | Key Purpose |
| :--- | :--- | :--- |
| **`GameLoop`** | [GameLoop.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/game/GameLoop.java) | Fixed 20 Hz (50ms) simulation loop, monotonic time tracking, command queue draining, state broadcasting. |
| **`MatchManager`** | [MatchManager.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/game/MatchManager.java) | Lifecycle state machine (`LOBBY`, `STARTING`, `IN_PROGRESS`, `GAME_OVER`), random role assignment (Hunter vs Runners). |
| **`ClientCommand`** | [ClientCommand.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/game/ClientCommand.java) | Immutable data record representing incoming player movement intentions from network threads. |
| **`MovementValidator`** | [MovementValidator.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/game/MovementValidator.java) | Rejects diagonal movement, wall collisions (`1`), and out-of-bounds attempts. |
| **`CollisionDetector`** | [CollisionDetector.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/game/CollisionDetector.java) | Precise coordinate overlap detection between the Hunter and active Runners. |
| **`MazeValidator`** | [MazeValidator.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/maze/MazeValidator.java) | Breadth-First Search (BFS) / Flood-Fill graph traversal ensuring open corridor reachability and valid spawns. |
| **`MazeGrid` & `GridPosition`** | [MazeGrid.java](file:///e:/Projects/Network%20Programming/MazeHunter/common/src/main/java/com/mazehunter/common/model/MazeGrid.java) | 2D discrete coordinate matrix representation (`WALL`, `PATH`, `SPAWN`). |
| **`ServerGameLoopIntegrationTest`** | [ServerGameLoopIntegrationTest.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/test/java/com/mazehunter/server/game/ServerGameLoopIntegrationTest.java) | Verifies the single-writer command queue and fixed-rate tick execution. |

---

## 2. Lecture Concept Mapping (EC5205 Syllabus)

Show the lecturer that you applied core Operating Systems & Concurrency theory:

* **Critical Sections & Race Conditions:**
  * If multiple `ClientHandler` threads directly modified player positions, simultaneous writes would corrupt coordinates (coordinate tearing) and cause invalid collision evaluations.
* **The Single-Writer Pattern (Producer-Consumer):**
  * Solved race conditions **without heavy locks** by using a **Single-Writer Pattern**.
  * `ClientHandler` threads act as *Producers*, placing immutable `ClientCommand` objects into a thread-safe `ConcurrentLinkedQueue`.
  * The dedicated `GameLoop` thread is the sole *Consumer/Writer* that mutates player coordinates.
* **Monotonic Timing & Scheduling:**
  * Implemented fixed-rate tick scheduling (20 Hz / 50ms) with drift compensation, preventing lag accumulation and CPU starvation.

---

## 3. Your 60-Second Speaking Pitch (When it's your turn)

> *"Good morning sir/madam. I am Hansana H.A.J. (EG/2023/5634), and I was responsible for the **Authoritative Game Engine, Simulation, and Thread Synchronization Architecture** of Maze Hunter.*
>
> *From an Operating Systems perspective, the central challenge in a multiplayer game is preventing race conditions when multiple client threads submit movements at the same time. Rather than using slow `synchronized` locks that would create severe lock contention, I implemented the **Single-Writer Pattern**.*
>
> *All incoming player movements are pushed into a lock-free `ConcurrentLinkedQueue<ClientCommand>`. My dedicated `GameLoop` thread runs at a fixed 20 Hz tick rate (50ms per tick) as the sole writer. It drains the queue, validates movement against our maze grid boundaries, evaluates Hunter-Runner collisions, and checks win conditions. Furthermore, I built `MazeValidator` using a Breadth-First Search (BFS) graph algorithm to mathematically verify corridor reachability before a match begins. This ensures 100% server authority and guarantees zero race conditions or deadlocks."*

---

## 4. Key Code Highlights to Show in IDE

### A. The Single-Writer Tick Loop (`GameLoop.java`)
```java
public void run() {
    long nextTickTime = System.currentTimeMillis();
    
    while (running.get()) {
        long now = System.currentTimeMillis();
        
        // 1. Drain all player commands from the lock-free concurrent queue
        drainCommandQueue();
        
        // 2. Perform authoritative tick updates (movement, collisions, timer)
        matchManager.tick(TICK_DURATION_MS);
        
        // 3. Generate immutable snapshot and broadcast to all players
        broadcastStateSnapshot();
        
        // 4. Sleep for remaining tick time (20 Hz = 50ms target)
        nextTickTime += TICK_DURATION_MS;
        long sleepTime = nextTickTime - System.currentTimeMillis();
        if (sleepTime > 0) {
            Thread.sleep(sleepTime);
        } else if (sleepTime < -500) {
            nextTickTime = System.currentTimeMillis(); // Reset if severe lag
        }
    }
}
```

### B. BFS Path Connectivity Validation (`MazeValidator.java`)
```java
// Breadth-First Search (BFS) ensures all players have a valid path to each other
Queue<GridPosition> queue = new ArrayDeque<>();
Set<GridPosition> visited = new HashSet<>();

queue.add(hunterSpawn);
visited.add(hunterSpawn);

while (!queue.isEmpty()) {
    GridPosition current = queue.poll();
    for (Direction dir : Direction.values()) {
        GridPosition neighbor = current.step(dir);
        if (grid.isPath(neighbor) && visited.add(neighbor)) {
            queue.add(neighbor);
        }
    }
}

// Assert that every runner spawn was reachable from the hunter spawn
for (GridPosition runnerSpawn : runnerSpawns) {
    if (!visited.contains(runnerSpawn)) {
        throw new IllegalStateException("Unreachable runner spawn detected!");
    }
}
```

---

## 5. Top 5 Viva Questions & Bulletproof Model Answers

### Q1: What is the "Single-Writer Pattern" and why did you use it?
> **Answer:**  
> *"If multiple `ClientHandler` reader threads directly modified player coordinates in memory, we would have severe race conditions and coordinate tearing. The Single-Writer Pattern solves this by decoupling the threads: network reader threads only create and enqueue immutable `ClientCommand` objects into a thread-safe `ConcurrentLinkedQueue`. The single-threaded `GameLoop` is the sole thread that drains the queue and modifies game state. This completely eliminates race conditions without requiring slow synchronization locks."*

### Q2: Why did you use `ConcurrentLinkedQueue` instead of `synchronized(lock)`?
> **Answer:**  
> *"A `synchronized` block causes lock contention: while one thread is writing, all other threads (and potentially the game loop) must block and wait, leading to thread stalling and jitter. `ConcurrentLinkedQueue` is a lock-free, thread-safe FIFO queue based on Michael & Scott's non-blocking algorithm using Compare-And-Swap (CAS) atomic operations. It allows multiple network threads to enqueue commands concurrently without blocking the game loop."*

### Q3: Why must the game logic be strictly "Server-Authoritative"?
> **Answer:**  
> *"In networked games, clients can never be trusted. If clients determined their own positions or calculated collisions locally, a hacked client could walk through walls, teleport, or ignore being caught. In Maze Hunter, clients only send player input intentions (`MOVE UP`). The server checks whether the target cell is a wall, whether the player is alive, evaluates collisions, updates the match timer, and broadcasts the official state back to clients."*

### Q4: How does `GameLoop` maintain a consistent 20 Hz tick rate?
> **Answer:**  
> *"We compute `nextTickTime += 50` at every iteration. After executing the tick logic, we sleep for `(nextTickTime - currentTime)`. If a tick takes slightly longer than expected due to CPU load, `sleepTime` is smaller or negative, allowing the loop to catch up immediately without sleeping. If the lag exceeds 500ms, we reset `nextTickTime` to the current wall-clock time to prevent a 'spiral-of-death' loop."*

### Q5: How does your `MazeValidator` work?
> **Answer:**  
> *"Before allowing a match to begin, `MazeValidator` executes a Breadth-First Search (BFS) graph traversal starting from the Hunter spawn. It expands through all walkable adjacent path cells (`0`) and checks whether all Runner spawns are reachable. If any corridor is blocked by wall tiles (`1`) or a player is spawned in an isolated island, validation throws an exception at server startup, preventing deadlocks during gameplay."*
