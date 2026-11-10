package com.mazehunter.server.game;

import com.mazehunter.common.model.Direction;
import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.MatchStatus;
import com.mazehunter.common.model.PlayerRole;
import com.mazehunter.common.protocol.GameOverPayload;
import com.mazehunter.common.protocol.GameStartPayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.PlayerCaughtPayload;
import com.mazehunter.common.protocol.PlayerSummary;
import com.mazehunter.common.protocol.StateUpdatePayload;
import com.mazehunter.server.net.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerGameLoopIntegrationTest {

    private GameServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = new GameServer(0);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Three clients connect, ready up, receive GAME_START, exchange MOVE, and receive synchronized STATE_UPDATE")
    void shouldExecuteMultiplayerMatchFlow() throws Exception {
        int clientCount = 3;
        List<IntegrationTestClient> clients = new ArrayList<>();

        try {
            // 1. Connect 3 clients
            for (int i = 0; i < clientCount; i++) {
                IntegrationTestClient client = new IntegrationTestClient("localhost", port);
                clients.add(client);
                client.send(MessageEnvelope.connect("Player-" + i, "1.0.0"));
                MessageEnvelope welcome = client.waitFor(MessageType.WELCOME, 3000);
                assertNotNull(welcome, "Each client must receive WELCOME");
            }

            assertEquals(clientCount, server.getConnectionManager().getConnectedCount());

            // 2. All 3 clients send READY(true)
            for (IntegrationTestClient client : clients) {
                client.send(MessageEnvelope.ready(true));
            }

            // 3. Verify all 3 clients receive GAME_START
            int hunterCount = 0;
            int runnerCount = 0;
            IntegrationTestClient hunterClient = null;
            IntegrationTestClient runnerClient = null;

            for (IntegrationTestClient client : clients) {
                MessageEnvelope gsEnv = client.waitFor(MessageType.GAME_START, 4000);
                assertNotNull(gsEnv, "Client should receive GAME_START message");
                GameStartPayload startPayload = gsEnv.parsePayload(GameStartPayload.class);
                assertNotNull(startPayload.matchId());

                if (startPayload.assignedRole() == PlayerRole.HUNTER) {
                    hunterCount++;
                    hunterClient = client;
                    assertEquals(new GridPosition(1, 13), new GridPosition(startPayload.spawnX(), startPayload.spawnY()));
                } else if (startPayload.assignedRole() == PlayerRole.RUNNER) {
                    runnerCount++;
                    runnerClient = client;
                }
            }

            assertEquals(1, hunterCount, "Exactly 1 player must be assigned as HUNTER");
            assertEquals(2, runnerCount, "Exactly 2 players must be assigned as RUNNER");
            assertEquals(MatchStatus.IN_PROGRESS, server.getMatchManager().getStatus());

            // 4. Verify clients receive STATE_UPDATE snapshots from 20 Hz game loop
            MessageEnvelope stateEnv = hunterClient.waitFor(MessageType.STATE_UPDATE, 3000);
            assertNotNull(stateEnv, "Hunter client should receive STATE_UPDATE");
            StateUpdatePayload state = stateEnv.parsePayload(StateUpdatePayload.class);
            assertEquals(MatchStatus.IN_PROGRESS, state.matchStatus());
            assertEquals(3, state.players().size());

            // 5. Hunter sends MOVE command (UP from (1, 13) is into wall, DOWN to (1, 12) is open)
            hunterClient.send(MessageEnvelope.move(Direction.DOWN, 1L));

            // Wait for next state update and verify Hunter moved to (1, 12)
            boolean positionUpdated = false;
            for (int attempts = 0; attempts < 10; attempts++) {
                MessageEnvelope updateEnv = hunterClient.waitFor(MessageType.STATE_UPDATE, 1000);
                if (updateEnv != null) {
                    StateUpdatePayload updatedState = updateEnv.parsePayload(StateUpdatePayload.class);
                    for (PlayerSummary p : updatedState.players()) {
                        if (p.role() == PlayerRole.HUNTER && p.x() == 1 && p.y() == 12) {
                            positionUpdated = true;
                            break;
                        }
                    }
                    if (positionUpdated) {
                        break;
                    }
                }
            }
            assertTrue(positionUpdated, "Hunter position should update to (1, 12) after validated MOVE command");

        } finally {
            for (IntegrationTestClient client : clients) {
                client.close();
            }
        }
    }

    @Test
    @DisplayName("Hunter catching all runners should trigger PLAYER_CAUGHT and GAME_OVER with HUNTER victory")
    void shouldTriggerCatchAndGameOver() throws Exception {
        try (IntegrationTestClient c1 = new IntegrationTestClient("localhost", port);
             IntegrationTestClient c2 = new IntegrationTestClient("localhost", port)) {

            c1.send(MessageEnvelope.connect("Alice", "1.0.0"));
            c2.send(MessageEnvelope.connect("Bob", "1.0.0"));

            assertNotNull(c1.waitFor(MessageType.WELCOME, 3000));
            assertNotNull(c2.waitFor(MessageType.WELCOME, 3000));

            c1.send(MessageEnvelope.ready(true));
            c2.send(MessageEnvelope.ready(true));

            MessageEnvelope c1Start = c1.waitFor(MessageType.GAME_START, 3000);
            MessageEnvelope c2Start = c2.waitFor(MessageType.GAME_START, 3000);
            assertNotNull(c1Start);
            assertNotNull(c2Start);

            PlayerRole c1Role = c1Start.parsePayload(GameStartPayload.class).assignedRole();
            IntegrationTestClient hunterClient = (c1Role == PlayerRole.HUNTER) ? c1 : c2;
            IntegrationTestClient runnerClient = (c1Role == PlayerRole.HUNTER) ? c2 : c1;

            // In 2-player match, there is 1 Hunter and 1 Runner.
            // Move Hunter directly onto Runner's position via matchManager to verify catch event and game over
            PlayerState runnerState = null;
            PlayerState hunterState = null;
            for (PlayerState p : server.getMatchManager().getPlayers().values()) {
                if (p.getRole() == PlayerRole.HUNTER) {
                    hunterState = p;
                } else if (p.getRole() == PlayerRole.RUNNER) {
                    runnerState = p;
                }
            }
            assertNotNull(hunterState);
            assertNotNull(runnerState);

            // Set hunter position to runner position
            hunterState.setPosition(runnerState.getPosition());

            // Next tick should trigger catch and game over
            MessageEnvelope caughtEnv = runnerClient.waitFor(MessageType.PLAYER_CAUGHT, 3000);
            assertNotNull(caughtEnv, "Runner should receive PLAYER_CAUGHT");
            PlayerCaughtPayload caughtPayload = caughtEnv.parsePayload(PlayerCaughtPayload.class);
            assertEquals(0, caughtPayload.remainingRunners());

            MessageEnvelope gameOverEnv = hunterClient.waitFor(MessageType.GAME_OVER, 3000);
            assertNotNull(gameOverEnv, "Hunter should receive GAME_OVER");
            GameOverPayload goPayload = gameOverEnv.parsePayload(GameOverPayload.class);
            assertEquals(PlayerRole.HUNTER, goPayload.winningRole());
            assertEquals("ALL_RUNNERS_CAUGHT", goPayload.reason());
        }
    }

    private static class IntegrationTestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        IntegrationTestClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(3000);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(MessageEnvelope envelope) throws IOException {
            String json = MessageSerializer.serialize(envelope);
            writer.write(json);
            writer.write("\n");
            writer.flush();
        }

        MessageEnvelope waitFor(MessageType type, long timeoutMs) throws IOException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    MessageEnvelope env = MessageSerializer.deserialize(line);
                    if (env.type() == type) {
                        return env;
                    }
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
