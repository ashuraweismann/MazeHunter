# Maze Hunter — Application Network Protocol Specification

## 1. Transport & Framing

* **Transport:** TCP (Stream-oriented, reliable, ordered delivery).
* **Data Format:** JSON (JavaScript Object Notation), encoded in UTF-8.
* **Framing Mechanism:** Newline-delimited JSON (`\n`).
  * Every complete message sent over the TCP socket must terminate with a newline character (`\n`).
  * Sockets must be read line-by-line using buffered readers (e.g., `BufferedReader.readLine()`).
* **Message Envelope:**
  Every message conforms to the following envelope structure:
  ```json
  {
    "type": "<MESSAGE_TYPE>",
    "timestamp": <EPOCH_MILLIS_LONG>,
    "payload": { ... }
  }
  ```
* **Security & Limits:**
  * Maximum line length is capped at 8,192 bytes (8 KB).
  * Payloads exceeding 8 KB or non-conforming JSON are rejected with an `ERROR` message or connection termination.

---

## 2. Protocol Message Catalog

### 2.1 Connection & Lobby Messages

#### `CONNECT` (Client -> Server)
Sent immediately upon socket connection to establish the player's presence.
```json
{
  "type": "CONNECT",
  "timestamp": 1729310000000,
  "payload": {
    "playerName": "Alice",
    "clientVersion": "1.0.0"
  }
}
```

#### `WELCOME` (Server -> Client)
Sent by the server to confirm successful connection and assign identity.
```json
{
  "type": "WELCOME",
  "timestamp": 1729310000050,
  "payload": {
    "assignedPlayerId": "p-101",
    "serverTickRate": 20,
    "maxPlayers": 4
  }
}
```

#### `READY` (Client -> Server)
Sent by a player in the lobby to toggle their ready status.
```json
{
  "type": "READY",
  "timestamp": 1729310001000,
  "payload": {
    "ready": true
  }
}
```

#### `LOBBY_UPDATE` (Server -> Client)
Broadcast to all connected clients when a player joins, leaves, or changes ready status.
```json
{
  "type": "LOBBY_UPDATE",
  "timestamp": 1729310001050,
  "payload": {
    "players": [
      { "id": "p-101", "name": "Alice", "ready": true },
      { "id": "p-102", "name": "Bob", "ready": false }
    ],
    "canStart": false
  }
}
```

---

### 2.2 Match Lifecycle Messages

#### `GAME_START` (Server -> Client)
Broadcast when all players are ready (or match starts). Informs clients of the maze layout, their role, and spawn position.
```json
{
  "type": "GAME_START",
  "timestamp": 1729310005000,
  "payload": {
    "matchId": "m-5501",
    "mazeWidth": 21,
    "mazeHeight": 21,
    "mazeGrid": [
      [1, 1, 1, 1, 1],
      [1, 0, 0, 0, 1],
      [1, 0, 1, 0, 1],
      [1, 0, 0, 0, 1],
      [1, 1, 1, 1, 1]
    ],
    "assignedRole": "HUNTER",
    "spawnX": 1,
    "spawnY": 1,
    "matchDurationSeconds": 60
  }
}
```

#### `MOVE` (Client -> Server)
Sent by a client when a directional input is triggered.
```json
{
  "type": "MOVE",
  "timestamp": 1729310006000,
  "payload": {
    "direction": "UP",
    "sequence": 1
  }
}
```
*Valid `direction` values:* `"UP"`, `"DOWN"`, `"LEFT"`, `"RIGHT"`.

#### `STATE_UPDATE` (Server -> Client)
Periodic authoritative state broadcast sent by the server game loop at 15–20 Hz.
```json
{
  "type": "STATE_UPDATE",
  "timestamp": 1729310006050,
  "payload": {
    "tick": 120,
    "remainingSeconds": 54,
    "players": [
      {
        "id": "p-101",
        "name": "Alice",
        "role": "HUNTER",
        "x": 2,
        "y": 1,
        "caught": false
      },
      {
        "id": "p-102",
        "name": "Bob",
        "role": "RUNNER",
        "x": 3,
        "y": 3,
        "caught": false
      }
    ],
    "matchStatus": "IN_PROGRESS"
  }
}
```

#### `PLAYER_CAUGHT` (Server -> Client)
Broadcast when the Hunter successfully catches a Runner.
```json
{
  "type": "PLAYER_CAUGHT",
  "timestamp": 1729310012000,
  "payload": {
    "hunterId": "p-101",
    "caughtPlayerId": "p-102",
    "remainingRunners": 0
  }
}
```

#### `GAME_OVER` (Server -> Client)
Broadcast when the match concludes.
```json
{
  "type": "GAME_OVER",
  "timestamp": 1729310012100,
  "payload": {
    "winningRole": "HUNTER",
    "reason": "ALL_RUNNERS_CAUGHT",
    "durationSeconds": 18
  }
}
```
*Valid `winningRole` values:* `"HUNTER"`, `"RUNNER"`.  
*Valid `reason` values:* `"ALL_RUNNERS_CAUGHT"`, `"TIME_EXPIRED"`, `"INSUFFICIENT_PLAYERS"`.

---

### 2.3 Diagnostic & Health Messages

#### `PING` (Client -> Server) / `PONG` (Server -> Client)
Used for heartbeat and latency measurement.
```json
{
  "type": "PING",
  "timestamp": 1729310000000,
  "payload": { "pingId": 1 }
}
```
```json
{
  "type": "PONG",
  "timestamp": 1729310000010,
  "payload": { "pingId": 1 }
}
```

#### `ERROR` (Server -> Client)
Sent to inform a client of a protocol violation, bad state, or rejection.
```json
{
  "type": "ERROR",
  "timestamp": 1729310000020,
  "payload": {
    "code": "MALFORMED_MESSAGE",
    "message": "Field 'direction' is required for MOVE command."
  }
}
```
