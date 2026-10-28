# Maze Hunter — Development Environment Setup

## 1. Prerequisites

Before setting up Maze Hunter, ensure your machine satisfies the following requirements:

* **Operating System:** Windows 10/11, macOS, or Linux.
* **Java Development Kit:** JDK 17 (LTS), 64-bit (OpenJDK, Eclipse Adoptium Temurin, or Amazon Corretto).
  * Verify via terminal:
    ```bash
    java -version
    javac -version
    ```
    Ensure the output indicates version `17.x`.
* **Gradle:** A pre-installed Gradle is **not** required when the Gradle Wrapper (`gradlew` and `gradlew.bat`) is configured.
* **Git:** Version 2.30 or newer.

---

## 2. Cloning & Initializing the Project

Clone the repository to your local machine:
```bash
git clone <repository-url>
cd MazeHunter
```

Verify that the Gradle wrapper operates correctly:
* **Windows:**
  ```cmd
  .\gradlew.bat --version
  ```
* **macOS / Linux:**
  ```bash
  chmod +x gradlew
  ./gradlew --version
  ```

---

## 3. Building the Project

To compile all modules (`common`, `server`, `client`) and execute automated unit tests:
```bash
./gradlew build
```
*(On Windows, use `.\gradlew.bat build`)*

To assemble binaries without running tests:
```bash
./gradlew assemble
```

---

## 4. Running the Applications

### 4.1 Starting the Dedicated Server
Run the headless TCP server:
```bash
./gradlew :server:run
```
By default, the server listens for TCP client connections on port `8080`.

### 4.2 Starting a Game Client
Open a separate terminal window and launch the LibGDX desktop client:
```bash
./gradlew :client:run
```

To run multiple concurrent clients for local multiplayer testing, open multiple terminal windows and run `./gradlew :client:run` in each.

---

## 5. IDE Setup (IntelliJ IDEA / VS Code / Eclipse)

### IntelliJ IDEA
1. Open IntelliJ IDEA.
2. Select **File -> Open...** and choose the `MazeHunter` root directory.
3. Choose **Open as Gradle Project**.
4. In **Settings / Preferences -> Build, Execution, Deployment -> Build Tools -> Gradle**:
   * Set **Gradle JVM** to Project SDK (Java 17).
   * Ensure **Build and run using** is set to `Gradle`.

### Visual Studio Code
1. Install the **Extension Pack for Java**.
2. Open the `MazeHunter` folder.
3. VS Code will detect the Gradle workspace and configure language servers automatically.
