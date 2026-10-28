# Maze Hunter

**A Real-Time Multiplayer Client-Server Maze Survival Game**  
*Network Programming (EC5205) Coursework Project — University of Ruhuna, Faculty of Engineering (25th Batch)*

---

## 1. Overview

Maze Hunter is a real-time 2–4 player multiplayer maze survival game built in Java 17. One randomly chosen player acts as the **Hunter**, while the remaining players act as **Runners**. The Hunter's objective is to navigate the maze and catch every Runner before the match countdown expires; Runners must coordinate, navigate the maze, avoid the Hunter, and survive until time runs out.

This project is designed to demonstrate key network programming and operating systems concepts:
* **Client-Server Architecture:** Strict server-authoritative simulation, movement validation, and collision detection.
* **TCP Socket Networking:** Multi-client communication using standard Java TCP sockets (`ServerSocket` / `Socket`) with newline-delimited UTF-8 JSON framing.
* **Concurrency & Multi-Threading:** Dedicated per-client reader threads (`Server_Thread` pattern) feeding a thread-safe `ConcurrentLinkedQueue`, decoupled from a single-threaded authoritative `GameLoop`.
* **Thread-Isolated Client Presentation:** Desktop 2D client with clear thread isolation between background network I/O and the graphical render loop.

---

## 2. 3-Member Team Work Division

The project is structured to provide each group member with a balanced, clearly defined scope:

| Member | Role | Core Focus | Key Deliverables |
|---|---|---|---|
| **Member 1** | Systems & Network Infrastructure | TCP Server, Sockets & Concurrency | `GameServer`, `ClientHandler`, `ClientSession`, non-blocking state broadcaster, disconnect recovery |
| **Member 2** | Authoritative Simulation & Engine | Game Loop, Maze & Match Rules | `GameLoop` (20 Hz), `Maze`, `MazeValidator` (BFS path check), `CollisionDetector`, timer & win rules |
| **Member 3** | Desktop Client & UI Presentation | Graphics, Screens & Network Integration | LibGDX screens (Menu, Lobby, Game, GameOver), `NetworkClient` background thread, keyboard input, HUD |

*For complete details, interface contracts, and the 6-week milestone schedule, see [Team Work Division & Collaboration Guide](docs/team_work_division.md).*

---

## 3. Project Structure

```
MazeHunter/
├── .gitignore                     # Excludes lecture materials, build artifacts & IDE caches
├── README.md                      # Project overview, team roles & quick start
├── docs/                          # Technical specifications & guides
│   ├── architecture.md            # System architecture & concurrency model
│   ├── protocol.md                # Network protocol specification & JSON envelopes
│   ├── setup.md                   # Development environment setup instructions
│   ├── testing.md                 # Testing strategy & acceptance verification
│   └── team_work_division.md      # Detailed 3-member task breakdown & contracts
├── common/                        # (Phase 1) Shared DTOs, JSON models & validation
├── server/                        # (Phase 2) Authoritative headless TCP server
└── client/                        # (Phase 3) Graphical desktop game client
```

---

## 4. Technical Documentation

* [Team Work Division & Contracts](docs/team_work_division.md)
* [System Architecture](docs/architecture.md)
* [Network Protocol Specification](docs/protocol.md)
* [Environment Setup](docs/setup.md)
* [Testing Strategy](docs/testing.md)

---

## 5. Development Guidelines & Atomic Commits

1. **Coursework Materials Ignored:** All lecture notes and workshop examples in `Lecture-Material/` are placed in `.gitignore` and excluded from git tracking.
2. **Atomic Commits:** Every commit must be atomic, self-contained, and follow standard conventional commit prefixes:
   * `feat:` new feature implementation
   * `fix:` bug resolution or edge case fix
   * `docs:` documentation updates
   * `test:` unit or integration tests
   * `refactor:` code restructuring without behavior changes
   * `chore:` build script or configuration maintenance
3. **No Direct Pushes to `main`:** Developers work on dedicated feature branches (`feature/m1-networking`, `feature/m2-game-engine`, `feature/m3-client-ui`) and merge via pull request after verification.
4. **Academic Integrity & GitHub Collaborators:** This repository is an official university assignment. Under no circumstances should AI agents, bots, or automated accounts (Antigravity, Claude, Codex, GitHub Copilot, etc.) be invited or added as collaborators on GitHub. All repository contributors and collaborators must strictly be the 3 human student team members.
