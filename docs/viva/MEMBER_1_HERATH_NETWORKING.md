# Member 1 Defense Guide: Systems, TCP Networking & Concurrency Architecture

**Member Name:** Herath H.M.K.U.A.  
**Registration Number:** EG/2023/5634  
**Role:** Systems, TCP Networking & Concurrency Architecture (Lead Networking)  
**Evaluation Date:** Wednesday, 18th November 2026 (EC5205 Demo & Viva Evaluation)  
**Assigned Module:** `:server` (Package: `com.mazehunter.server.net`) & `:common` (Package: `com.mazehunter.common.protocol`)

---

## 1. Owned Source Code Files & Locations

Have these files open in your IDE tabs before the evaluation starts:

| Component / Class | File Location | Key Purpose |
| :--- | :--- | :--- |
| **`GameServer`** | [GameServer.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/net/GameServer.java) | `ServerSocket` binding, port management (`5555`), master connection acceptance loop, graceful shutdown hooks. |
| **`ClientHandler`** | [ClientHandler.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/net/ClientHandler.java) | Dedicated per-client reader thread (`Runnable`), streaming I/O with `BufferedReader`, line decoding, disconnect detection. |
| **`ConnectionManager`** | [ConnectionManager.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/net/ConnectionManager.java) | Thread-safe active session registry, thread-safe broadcast outbox, player ID allocation (`p-101`). |
| **`ClientSession`** | [ClientSession.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/net/ClientSession.java) | Encapsulates the live `Socket`, `PrintWriter` writer stream, assigned player ID, and thread-safe sending. |
| **`LimitedUtf8LineReader`** | [LimitedUtf8LineReader.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/net/LimitedUtf8LineReader.java) | Protection against Denial-of-Service (DoS) buffer overflow attacks (caps lines at 8 KB). |
| **`ServerMain`** | [ServerMain.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/main/java/com/mazehunter/server/ServerMain.java) | Entrypoint bootstrapping `GameServer` and binding to port `5555`. |
| **`TcpNetworkingIntegrationTest`** | [TcpNetworkingIntegrationTest.java](file:///e:/Projects/Network%20Programming/MazeHunter/server/src/test/java/com/mazehunter/server/net/TcpNetworkingIntegrationTest.java) | Automated integration test verifying concurrent TCP client handshakes and broadcasts. |

---

## 2. Lecture Concept Mapping (EC5205 Syllabus)

Show the lecturer that you built directly upon the lecture exercises:

* **From `Server.java` & `ChatServerT.java`:**
  * Upgraded from single-client blocking echo server to a **multi-client non-blocking broadcast server**.
* **From `Server_Thread.java` / `ThreadHandler.java`:**
  * Implemented the **`Server_Thread` design pattern**: Every incoming client socket from `serverSocket.accept()` is handed to an independent worker thread (`ClientHandler`) implementing `Runnable`.
* **From Basic Sockets to Production Networking:**
  * Enabled `TCP_NODELAY` (disabling Nagle's algorithm) for sub-15ms real-time packet delivery.
  * Added line-delimited JSON message framing (`\n`) with `BufferedReader.readLine()` and auto-flushing `PrintWriter.println()`.
  * Added DoS protection (8 KB line cap) and graceful socket termination to prevent thread leaks.

---

## 3. Your 60-Second Speaking Pitch (When it's your turn)

> *"Good morning sir/madam. I am Herath H.M.K.U.A. (EG/2023/5634), and I was responsible for the **Systems, TCP Networking, and Server-Side Concurrency Architecture** of Maze Hunter.*
>
> *I designed the core networking layer around Java’s `ServerSocket` listening on port 5555. Following the `Server_Thread` pattern from our lectures, our server uses a master acceptor thread that continuously listens for incoming connections. Whenever a new player connects, the server spawns a dedicated `ClientHandler` thread to manage that socket's input stream.*
>
> *Because TCP socket reads (`readLine()`) are blocking I/O operations, dedicating a separate thread per client ensures that network lag or an idle player never stalls the server or freezes other players. I also implemented `ConnectionManager` using thread-safe collections to handle non-blocking state broadcasting, and built `LimitedUtf8LineReader` to cap payload sizes at 8 KB, preventing buffer overflow DoS attacks. When a client drops out, our socket cleanup routines immediately reclaim the thread and socket without thread leaks."*

---

## 4. Key Code Highlights to Show in IDE

### A. The Master Acceptor Loop (`GameServer.java`)
```java
// Open server socket on port 5555
this.serverSocket = new ServerSocket(port);
serverSocket.setReuseAddress(true);

// Master listener thread continuously accepts incoming clients
while (running.get()) {
    try {
        Socket clientSocket = serverSocket.accept();
        clientSocket.setTcpNoDelay(true); // Disable Nagle's algorithm for low latency
        
        // Wrap connection in a session and dispatch to worker thread
        ClientSession session = new ClientSession(generatePlayerId(), clientSocket);
        connectionManager.register(session);
        
        ClientHandler handler = new ClientHandler(session, commandQueue, connectionManager);
        threadPool.execute(handler); // Dedicated thread per client
    } catch (SocketException e) {
        if (!running.get()) break; // Graceful shutdown
    }
}
```

### B. Dedicated Client Reader Thread (`ClientHandler.java`)
```java
public void run() {
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(session.getSocket().getInputStream(), StandardCharsets.UTF_8))) {
        
        String line;
        while (session.isActive() && (line = reader.readLine()) != null) {
            // Validate payload size and parse JSON message
            MessageEnvelope message = MessageSerializer.deserialize(line);
            handleIncomingMessage(message);
        }
    } catch (IOException e) {
        logger.info("Client disconnected: " + session.getPlayerId());
    } finally {
        // Prevent thread leaks: clean up session and notify game engine
        connectionManager.disconnect(session);
    }
}
```

---

## 5. Top 5 Viva Questions & Bulletproof Model Answers

### Q1: Why did you choose TCP instead of UDP for Maze Hunter?
> **Answer:**  
> *"We chose TCP because Maze Hunter relies on strict discrete state transitions—such as `CONNECT`, `WELCOME`, `GAME_START`, `PLAYER_CAUGHT`, and `GAME_OVER`. Dropping a packet in UDP could cause a player to miss the start of the match or a catch notification, leading to inconsistent game states. TCP guarantees reliable, in-order stream delivery. To overcome TCP's default latency buffering, we enabled `socket.setTcpNoDelay(true)`, which disables Nagle’s algorithm and ensures 20 Hz state updates are transmitted immediately."*

### Q2: Why does each client require a dedicated thread (`ClientHandler`)?
> **Answer:**  
> *"TCP socket reads like `BufferedReader.readLine()` are **blocking I/O operations**. If a single thread tried to read from all clients sequentially, the server would block while waiting for data from any client that was slow or idle, freezing all other players. By giving each client a dedicated `ClientHandler` thread, incoming network streams are read concurrently and independently."*

### Q3: How do you prevent thread leaks when a player unexpectedly disconnects or turns off Wi-Fi?
> **Answer:**  
> *"When a client abruptly disconnects, the blocking `readLine()` on that socket either returns `null` (EOF) or throws a `SocketException` ('Connection reset'). Our `ClientHandler` catches these conditions and enters a `finally` block. In the `finally` block, we call `connectionManager.disconnect(session)`, which closes the socket, closes the I/O streams, removes the player from the active roster, and allows the worker thread to terminate cleanly, releasing its resources back to the JVM."*

### Q4: Why is there an 8 KB message size cap in `LimitedUtf8LineReader`?
> **Answer:**  
> *"In line-delimited socket streaming, `BufferedReader.readLine()` reads bytes into memory until it encounters a newline character (`\n`). If a malicious or buggy client continuously streams bytes without ever sending a newline, the buffer would grow unbounded until it exhausted the JVM heap memory, causing an `OutOfMemoryError`. The 8 KB limit rejects oversized lines early and protects the server against memory exhaustion and buffer overflow DoS attacks."*

### Q5: How do you handle thread-safe message broadcasting to multiple players?
> **Answer:**  
> *"In `ConnectionManager`, active client sessions are stored in a thread-safe `ConcurrentHashMap`. When broadcasting a state update, we iterate over the active sessions. Each `ClientSession` synchronizes its outgoing `PrintWriter` writes to ensure that concurrent broadcast calls cannot interleave partial JSON strings into the socket stream."*
