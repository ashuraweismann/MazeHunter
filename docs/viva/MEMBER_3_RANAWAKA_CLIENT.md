# Member 3 Defense Guide: Desktop Game Client & Thread Isolation

**Member Name:** Ranawaka P.S.  
**Registration Number:** EG/2023/5821  
**Role:** Desktop Game Client & UI Presentation (Client Lead)  
**Evaluation Date:** Wednesday, 18th November 2026 (EC5205 Demo & Viva Evaluation)  
**Assigned Module:** `:client` (Packages: `com.mazehunter.client.net`, `com.mazehunter.client.screen`, `com.mazehunter.client.ui`)

---

## 1. Owned Source Code Files & Locations

Have these files open in your IDE tabs before the evaluation starts:

| Component / Class | File Location | Key Purpose |
| :--- | :--- | :--- |
| **`NetworkClient`** | [NetworkClient.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/net/NetworkClient.java) | Client TCP `Socket` connection, dedicated background reader daemon thread, thread-safe message outbox. |
| **`ClientLauncher`** | [ClientLauncher.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/ClientLauncher.java) | Main entrypoint bootstrapping the LibGDX `Lwjgl3Application` desktop window. |
| **`MazeHunterGame`** | [MazeHunterGame.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/MazeHunterGame.java) | Core LibGDX `Game` controller managing active screen transitions and network client lifecycle. |
| **`MenuScreen`** | [MenuScreen.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/screen/MenuScreen.java) | Server IP, port (`5555`), player name input form, connection validation, and error dialogs. |
| **`LobbyScreen`** | [LobbyScreen.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/screen/LobbyScreen.java) | Real-time player roster list, ready toggle button, and match countdown transition. |
| **`GameScreen`** | [GameScreen.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/screen/GameScreen.java) | 60 FPS 2D rendering loop: dynamic maze grid, Hunter (Red) vs Runner (Blue/Green) avatars, HUD timer. |
| **`GameOverScreen`** | [GameOverScreen.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/main/java/com/mazehunter/client/screen/GameOverScreen.java) | Match result banner ("Hunter Victory" or "Runners Survived!"), rematch request button. |
| **`NetworkClientTest`** | [NetworkClientTest.java](file:///e:/Projects/Network%20Programming/MazeHunter/client/src/test/java/com/mazehunter/client/net/NetworkClientTest.java) | Unit test validating client packet parsing and background thread safety. |

---

## 2. Lecture Concept Mapping (EC5205 Syllabus)

Show the lecturer that you applied core Client-Server & Multi-threading principles:

* **From `Client.java` & `ChatClientT.java`:**
  * Upgraded from console socket reading to a **graphical desktop client with decoupled network threading**.
* **Client-Side Thread Isolation (UI vs. Network Thread):**
  * LibGDX graphics run on the main **OpenGL thread at 60 FPS** (16.6ms per frame).
  * Socket I/O (`BufferedReader.readLine()`) is **blocking** and must NEVER run on the UI thread.
  * Solved this by executing network I/O on a **dedicated background daemon thread** inside `NetworkClient`.
* **Safe Thread Synchronization & Dispatch:**
  * Incoming packets are stored in a thread-safe `ConcurrentLinkedQueue`.
  * Safe dispatch to the OpenGL thread is handled via `Gdx.app.postRunnable()` or queue draining at the beginning of each frame, avoiding OpenGL context violations.

---

## 3. Your 60-Second Speaking Pitch (When it's your turn)

> *"Good morning sir/madam. I am Ranawaka P.S. (EG/2023/5821), and I was responsible for the **Desktop Game Client, UI Presentation, and Client-Side Network Integration** of Maze Hunter.*
>
> *From a Network Programming standpoint, the most critical architectural decision on the client side is **Thread Isolation**. LibGDX runs an OpenGL graphics rendering loop at 60 FPS on the main thread. Because reading from a TCP socket with `readLine()` is a blocking operation, placing network calls directly inside the rendering loop would cause the UI to freeze and stutter whenever network latency occurred.*
>
> *To prevent this, I designed `NetworkClient` to run on an isolated background daemon thread. It establishes the TCP `Socket(host, port)` and continuously listens for incoming server updates. Incoming packets are buffered into a thread-safe `ConcurrentLinkedQueue`. At the start of each frame, our `GameScreen` drains this queue and renders the authoritative player positions, maze walls, and HUD timer. Furthermore, our client remains non-authoritative: keyboard inputs (`W/A/S/D`) are throttled and dispatched to the server outbox, ensuring smooth 60 FPS gameplay with zero UI lag."*

---

## 4. Key Code Highlights to Show in IDE

### A. Thread-Isolated Network Listener (`NetworkClient.java`)
```java
// Connect client socket to server IP and Port
this.socket = new Socket(host, port);
this.socket.setTcpNoDelay(true);

this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

// Spawn background daemon thread for blocking socket reads
Thread listenerThread = new Thread(() -> {
    try {
        String line;
        while (running.get() && (line = reader.readLine()) != null) {
            MessageEnvelope message = MessageSerializer.deserialize(line);
            // Push into thread-safe queue for the OpenGL thread
            incomingQueue.offer(message);
        }
    } catch (IOException e) {
        notifyDisconnect();
    }
}, "NetworkClient-Worker");
listenerThread.setDaemon(true);
listenerThread.start();
```

### B. OpenGL Frame Queue Draining (`GameScreen.java`)
```java
@Override
public void render(float delta) {
    // 1. Drain incoming network queue on the OpenGL thread
    MessageEnvelope message;
    while ((message = networkClient.pollMessage()) != null) {
        handleServerUpdate(message);
    }
    
    // 2. Poll keyboard inputs and dispatch MOVE commands (throttled)
    pollKeyboardInput();
    
    // 3. Clear screen and render authoritative maze, players, and HUD
    Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    
    renderMaze(shapeRenderer);
    renderPlayers(shapeRenderer);
    renderHUD(spriteBatch);
}
```

---

## 5. Top 5 Viva Questions & Bulletproof Model Answers

### Q1: Why can't you put socket `readLine()` directly inside the LibGDX `render()` method?
> **Answer:**  
> *"The LibGDX `render()` method runs on the main OpenGL UI thread at 60 FPS, meaning each frame must complete within ~16.6 milliseconds. Socket reads like `readLine()` are **blocking I/O operations** that wait for data over the network. If `readLine()` were placed inside `render()`, any network delay or packet jitter would freeze the entire UI thread, causing frame drops, frozen animations, and the operating system flagging the application as 'Not Responding'."*

### Q2: How do you safely transfer data from the background network thread to the UI thread?
> **Answer:**  
> *"We use a producer-consumer model with two safe techniques:  
> 1. Incoming packets read by the background thread are placed into a thread-safe `ConcurrentLinkedQueue<MessageEnvelope>`. The `render()` method drains this queue on the OpenGL thread at the start of each frame.  
> 2. For critical UI events (like switching screens upon receiving `GAME_START`), we use `Gdx.app.postRunnable(() -> game.setScreen(...))`. This safely posts the task onto LibGDX's main OpenGL execution thread, avoiding OpenGL context thread violations."*

### Q3: Does the client predict or calculate its own player movements?
> **Answer:**  
> *"No, our client is **strictly non-authoritative**. When the user presses `W/A/S/D` or Arrow keys, the client does not immediately change the avatar's coordinates locally. It only sends a `MOVE` command to the server outbox. The player only moves on screen when the server validates the move against walls and broadcasts the new coordinates in a `STATE_UPDATE` snapshot. This guarantees that the client's screen is always in sync with the server's authoritative state."*

### Q4: How does the client handle a sudden server crash or lost network connection?
> **Answer:**  
> *"When the server closes the connection, `reader.readLine()` on the background thread returns `null` or throws a `SocketException`. The catch block catches this and uses `Gdx.app.postRunnable()` to transition the UI back to `MenuScreen` with a clear error dialog: 'Server connection lost. Please verify host IP and retry.' This prevents uncaught background exceptions from crashing the desktop app."*

### Q5: How do you throttle keyboard input to avoid flooding the TCP socket?
> **Answer:**  
> *"Humans can hold down a key that generates events at hardware repeat rates, and our render loop executes at 60 FPS. If we sent a packet on every single frame, we would flood the TCP socket and saturate the server queue. We enforce an **input throttle interval** (e.g. 100ms between discrete grid steps). If the user holds a key down, a new command is only sent after the throttle duration has elapsed."*
