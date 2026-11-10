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
import com.mazehunter.common.protocol.WelcomePayload;
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
import java.util.concurrent.TimeUnit;

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

            // UP from (1, 13) is into a wall and must not change the authoritative position.
            hunterClient.send(MessageEnvelope.move(Direction.UP, 1L));
            hunterClient.send(MessageEnvelope.ping(91L));
            MessageEnvelope pong = hunterClient.waitFor(MessageType.PONG, 3000);
            assertNotNull(pong, "MOVE must be handled before the following PING response");
            MessageEnvelope wallUpdate = hunterClient.waitFor(MessageType.STATE_UPDATE, 3000);
            assertNotNull(wallUpdate);
            StateUpdatePayload wallState = wallUpdate.parsePayload(StateUpdatePayload.class);
            PlayerSummary hunterAfterWallMove = wallState.players().stream()
                    .filter(player -> player.role() == PlayerRole.HUNTER)
                    .findFirst()
                    .orElseThrow();
            assertEquals(new GridPosition(1, 13), new GridPosition(hunterAfterWallMove.x(), hunterAfterWallMove.y()),
                    "Hunter must not move into a wall");

            hunterClient.send(MessageEnvelope.move(Direction.DOWN, 2L));

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
    @DisplayName("Flooding MOVE commands over the per-player limit is safely throttled")
    void shouldThrottleFloodedMoveCommands() throws Exception {
        try (IntegrationTestClient c1 = connect("Flood-1");
             IntegrationTestClient c2 = connect("Flood-2")) {
            c1.send(MessageEnvelope.ready(true));
            c2.send(MessageEnvelope.ready(true));
            assertNotNull(c1.waitFor(MessageType.GAME_START, 3000));
            assertNotNull(c2.waitFor(MessageType.GAME_START, 3000));

            long droppedBeforeFlood = server.getGameLoop().getDroppedCommandCount();
            c1.sendBurst(MessageEnvelope.move(Direction.DOWN, 1L), 100);
            c1.send(MessageEnvelope.ping(92L));
            assertNotNull(c1.waitFor(MessageType.PONG, 3000),
                    "PING response should follow processing of the complete command burst");

            long droppedByFlood = server.getGameLoop().getDroppedCommandCount() - droppedBeforeFlood;
            assertTrue(droppedByFlood > 0,
                    "A 100-command burst should exceed the 30 commands/second allowance; dropped "
                            + droppedByFlood);
            assertTrue(server.getGameLoop().getQueueSize() <= GameLoop.MAX_QUEUE_CAPACITY);
            assertTrue(server.isRunning(), "Flooding must not stop the server");
        }
    }

    @Test
    @DisplayName("An abrupt in-match disconnect is removed and broadcast to the remaining clients")
    void shouldBroadcastRemovalOfDisconnectedPlayerDuringMatch() throws Exception {
        List<IntegrationTestClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                clients.add(connect("Disconnect-" + i));
            }
            for (IntegrationTestClient client : clients) {
                client.send(MessageEnvelope.ready(true));
            }

            List<IntegrationTestClient> runners = new ArrayList<>();
            for (IntegrationTestClient client : clients) {
                MessageEnvelope start = client.waitFor(MessageType.GAME_START, 3000);
                assertNotNull(start);
                if (start.parsePayload(GameStartPayload.class).assignedRole() == PlayerRole.RUNNER) {
                    runners.add(client);
                }
            }
            assertEquals(2, runners.size());

            IntegrationTestClient disconnected = runners.get(0);
            String disconnectedId = disconnected.playerId();
            disconnected.close();

            IntegrationTestClient observer = clients.stream()
                    .filter(client -> client != disconnected)
                    .findFirst()
                    .orElseThrow();
            boolean removalBroadcast = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline && !removalBroadcast) {
                MessageEnvelope update = observer.waitFor(MessageType.STATE_UPDATE, 1000);
                if (update != null) {
                    StateUpdatePayload state = update.parsePayload(StateUpdatePayload.class);
                    removalBroadcast = state.players().stream()
                            .noneMatch(player -> disconnectedId.equals(player.id()));
                }
            }

            assertTrue(removalBroadcast, "Remaining clients should receive state without the disconnected player");
            assertEquals(2, server.getMatchManager().getConnectedPlayerCount());
            assertEquals(MatchStatus.IN_PROGRESS, server.getMatchManager().getStatus());
        } finally {
            clients.forEach(IntegrationTestClient::closeQuietly);
        }
    }

    @Test
    @DisplayName("Returning from game over resets the lobby and permits a second match")
    void shouldResetAndStartConsecutiveMatches() throws Exception {
        try (IntegrationTestClient c1 = connect("Rematch-1");
             IntegrationTestClient c2 = connect("Rematch-2")) {
            c1.send(MessageEnvelope.ready(true));
            c2.send(MessageEnvelope.ready(true));
            MessageEnvelope firstStart1 = c1.waitFor(MessageType.GAME_START, 3000);
            MessageEnvelope firstStart2 = c2.waitFor(MessageType.GAME_START, 3000);
            assertNotNull(firstStart1);
            assertNotNull(firstStart2);
            GameStartPayload start1 = firstStart1.parsePayload(GameStartPayload.class);
            GameStartPayload start2 = firstStart2.parsePayload(GameStartPayload.class);
            assertNotNull(start1);
            assertNotNull(start2);

            PlayerState hunter = server.getMatchManager().getPlayers().values().stream()
                    .filter(player -> player.getRole() == PlayerRole.HUNTER)
                    .findFirst()
                    .orElseThrow();
            PlayerState runner = server.getMatchManager().getPlayers().values().stream()
                    .filter(player -> player.getRole() == PlayerRole.RUNNER)
                    .findFirst()
                    .orElseThrow();
            hunter.setPosition(runner.getPosition());

            assertNotNull(c1.waitFor(MessageType.PLAYER_CAUGHT, 3000));
            assertNotNull(c1.waitFor(MessageType.GAME_OVER, 3000));
            assertNotNull(c2.waitFor(MessageType.GAME_OVER, 3000));

            c1.send(MessageEnvelope.rematch());
            assertNotNull(c1.waitFor(MessageType.LOBBY_UPDATE, 3000));
            assertNotNull(c2.waitFor(MessageType.LOBBY_UPDATE, 3000));

            for (PlayerState player : server.getMatchManager().getPlayers().values()) {
                assertEquals(PlayerRole.UNASSIGNED, player.getRole());
                assertEquals(server.getMatchManager().getMaze().getHunterSpawn(), player.getPosition());
                assertFalse(player.isCaught());
                assertFalse(player.isReady());
            }

            c1.send(MessageEnvelope.ready(true));
            c2.send(MessageEnvelope.ready(true));
            MessageEnvelope nextStart1 = c1.waitFor(MessageType.GAME_START, 3000);
            MessageEnvelope nextStart2 = c2.waitFor(MessageType.GAME_START, 3000);
            assertNotNull(nextStart1);
            assertNotNull(nextStart2);
            GameStartPayload secondStart1 = nextStart1.parsePayload(GameStartPayload.class);
            GameStartPayload secondStart2 = nextStart2.parsePayload(GameStartPayload.class);
            assertNotNull(secondStart1);
            assertNotNull(secondStart2);
            assertNotEquals(start1.matchId(), secondStart1.matchId());
            assertNotEquals(start2.matchId(), secondStart2.matchId());
            assertEquals(MatchStatus.IN_PROGRESS, server.getMatchManager().getStatus());
        }
    }

    private IntegrationTestClient connect(String playerName) throws Exception {
        IntegrationTestClient client = new IntegrationTestClient("localhost", port);
        client.send(MessageEnvelope.connect(playerName, "1.0.0"));
        assertNotNull(client.waitFor(MessageType.WELCOME, 3000));
        return client;
    }

    @Test
    @DisplayName("Three-player match broadcasts both catches and ends with a Hunter victory")
    void shouldTriggerCatchAndGameOver() throws Exception {
        List<IntegrationTestClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                clients.add(connect("Catch-" + i));
            }
            for (IntegrationTestClient client : clients) {
                client.send(MessageEnvelope.ready(true));
            }

            for (IntegrationTestClient client : clients) {
                MessageEnvelope start = client.waitFor(MessageType.GAME_START, 3000);
                assertNotNull(start);
            }

            PlayerState hunter = server.getMatchManager().getPlayers().values().stream()
                    .filter(player -> player.getRole() == PlayerRole.HUNTER)
                    .findFirst()
                    .orElseThrow();
            List<PlayerState> runners = server.getMatchManager().getPlayers().values().stream()
                    .filter(player -> player.getRole() == PlayerRole.RUNNER)
                    .toList();
            assertEquals(2, runners.size());

            for (int index = 0; index < runners.size(); index++) {
                PlayerState runner = runners.get(index);
                hunter.setPosition(runner.getPosition());
                for (IntegrationTestClient client : clients) {
                    MessageEnvelope caught = client.waitFor(MessageType.PLAYER_CAUGHT, 3000);
                    assertNotNull(caught, "Every client should receive each catch event");
                    PlayerCaughtPayload payload = caught.parsePayload(PlayerCaughtPayload.class);
                    assertEquals(runner.getPlayerId(), payload.caughtPlayerId());
                    assertEquals(runners.size() - index - 1, payload.remainingRunners());
                }
                assertTrue(runner.isCaught());
            }

            for (IntegrationTestClient client : clients) {
                MessageEnvelope gameOver = client.waitFor(MessageType.GAME_OVER, 3000);
                assertNotNull(gameOver, "Every client should receive GAME_OVER");
                GameOverPayload payload = gameOver.parsePayload(GameOverPayload.class);
                assertEquals(PlayerRole.HUNTER, payload.winningRole());
                assertEquals("ALL_RUNNERS_CAUGHT", payload.reason());
            }
        } finally {
            clients.forEach(IntegrationTestClient::closeQuietly);
        }
    }

    private static class IntegrationTestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private String playerId;

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
                        if (type == MessageType.WELCOME) {
                            playerId = env.parsePayload(WelcomePayload.class).assignedPlayerId();
                        }
                        return env;
                    }
                }
            }
            return null;
        }

        void sendBurst(MessageEnvelope envelope, int count) throws IOException {
            String json = MessageSerializer.serialize(envelope);
            for (int i = 0; i < count; i++) {
                writer.write(json);
                writer.write('\n');
            }
            writer.flush();
        }

        String playerId() {
            return playerId;
        }

        void closeQuietly() {
            try {
                close();
            } catch (IOException ignored) {
                // Cleanup after a test should not hide the test result.
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
