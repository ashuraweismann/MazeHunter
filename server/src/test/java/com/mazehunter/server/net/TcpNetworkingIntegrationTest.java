package com.mazehunter.server.net;

import com.mazehunter.common.protocol.ErrorPayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.PongPayload;
import com.mazehunter.common.protocol.WelcomePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpNetworkingIntegrationTest {

    private GameServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new GameServer(0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void acceptsConcurrentConnectionsAndAssignsUniqueIds() throws Exception {
        int clientCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<TestClient>> futures = new ArrayList<>();
        List<TestClient> clients = new ArrayList<>();
        try {
            for (int index = 0; index < clientCount; index++) {
                String playerName = "Player" + index;
                futures.add(executor.submit(() -> {
                    startTogether.await();
                    return connect(playerName);
                }));
            }
            startTogether.countDown();
            for (Future<TestClient> future : futures) {
                clients.add(future.get(3, TimeUnit.SECONDS));
            }

            Set<String> playerIds = new HashSet<>();
            for (TestClient client : clients) {
                playerIds.add(client.welcome().assignedPlayerId());
            }
            assertEquals(clientCount, playerIds.size());
            assertEquals(clientCount, server.getConnectionManager().getConnectedCount());
        } finally {
            clients.forEach(TestClient::closeQuietly);
            executor.shutdownNow();
        }
    }

    @Test
    void completesConnectHandshakeAndAnswersPing() throws Exception {
        try (TestClient client = connect("HeartbeatPlayer")) {
            client.send(MessageEnvelope.ping(73));
            MessageEnvelope response = client.readEnvelope();

            assertEquals(MessageType.PONG, response.type());
            assertEquals(73L, response.parsePayload(PongPayload.class).pingId());
            assertEquals("HeartbeatPlayer", server.getConnectionManager()
                    .findSession(client.welcome().assignedPlayerId()).orElseThrow().getPlayerName());
        }
    }

    @Test
    void removesAbruptlyDisconnectedClientAndContinuesAcceptingConnections() throws Exception {
        TestClient disconnected = connect("DropPlayer");
        disconnected.closeQuietly();
        awaitConnectedCount(0);

        try (TestClient nextClient = connect("NextPlayer")) {
            assertNotNull(nextClient.welcome().assignedPlayerId());
            assertTrue(server.isRunning());
        }
    }

    @Test
    void rejectsLinesLongerThanEightKilobytesAndClosesThatConnection() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort());
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(2_000);
            writer.write("\u00e9".repeat((ClientSession.MAX_MESSAGE_BYTES / 2) + 1));
            writer.write('\n');
            writer.flush();

            MessageEnvelope error = MessageSerializer.deserialize(reader.readLine());
            assertEquals(MessageType.ERROR, error.type());
            assertEquals("LINE_TOO_LONG", error.parsePayload(ErrorPayload.class).code());
            assertNull(reader.readLine());
        }
    }

    private TestClient connect(String playerName) throws Exception {
        Socket socket = new Socket("127.0.0.1", server.getPort());
        socket.setSoTimeout(2_000);
        TestClient client = new TestClient(socket);
        try {
            client.send(MessageEnvelope.connect(playerName, "1.0.0"));
            MessageEnvelope response = client.readEnvelope();
            assertEquals(MessageType.WELCOME, response.type());
            client.setWelcome(response.parsePayload(WelcomePayload.class));
            return client;
        } catch (Exception exception) {
            client.closeQuietly();
            throw exception;
        }
    }

    private void awaitConnectedCount(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (server.getConnectionManager().getConnectedCount() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, server.getConnectionManager().getConnectedCount());
    }

    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private WelcomePayload welcome;

        private TestClient(Socket socket) throws Exception {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void send(MessageEnvelope envelope) throws Exception {
            writer.write(MessageSerializer.serialize(envelope));
            writer.write('\n');
            writer.flush();
        }

        private MessageEnvelope readEnvelope() throws Exception {
            while (true) {
                String line = reader.readLine();
                assertNotNull(line, "Server closed the connection before replying");
                MessageEnvelope envelope = MessageSerializer.deserialize(line);
                if (envelope.type() == MessageType.LOBBY_UPDATE) {
                    continue; // Skip asynchronous lobby broadcasts to read the direct response
                }
                return envelope;
            }
        }

        private WelcomePayload welcome() {
            return welcome;
        }

        private void setWelcome(WelcomePayload welcome) {
            this.welcome = welcome;
        }

        private void closeQuietly() {
            try {
                close();
            } catch (Exception ignored) {
                // Cleanup after a test should not hide the test result.
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
