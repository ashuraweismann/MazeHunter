package com.mazehunter.server;

import com.mazehunter.server.net.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Command-line entry point for the standalone TCP server. */
public final class ServerMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = readPort(args);
        try (GameServer server = new GameServer(port)) {
            server.start();
            LOGGER.info("Press Ctrl+C to stop the server");
            server.awaitTermination();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start Maze Hunter server on port " + port, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static int readPort(String[] args) {
        String configuredPort = args.length > 0
                ? args[0]
                : System.getProperty("maze.server.port", System.getenv().getOrDefault("MAZE_SERVER_PORT", "8080"));
        try {
            int port = Integer.parseInt(configuredPort);
            if (port < 0 || port > 65_535) {
                throw new NumberFormatException("port is outside the valid range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Server port must be an integer from 0 to 65,535", exception);
        }
    }
}
