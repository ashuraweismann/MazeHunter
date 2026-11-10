package com.mazehunter.client.net;

import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageSerializer;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.PingPayload;
import com.mazehunter.common.protocol.PongPayload;
import com.mazehunter.common.protocol.WelcomePayload;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkClientTest {

    @Test
    void connectsExchangesHandshakeAndQueuesServerMessages() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5_000);
            NetworkClient client = new NetworkClient();
            try {
                client.connect("127.0.0.1", server.getLocalPort(), "Test Runner");

                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(5_000);
                    BufferedReader fromClient = new BufferedReader(new InputStreamReader(
                            peer.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter toClient = new BufferedWriter(new OutputStreamWriter(
                            peer.getOutputStream(), StandardCharsets.UTF_8));

                    MessageEnvelope connect = MessageSerializer.deserialize(fromClient.readLine());
                    assertEquals(MessageType.CONNECT, connect.type());
                    assertEquals("Test Runner", connect.parsePayload(
                            com.mazehunter.common.protocol.ConnectPayload.class).playerName());

                    toClient.write(MessageSerializer.serialize(MessageEnvelope.welcome("player-test", 20, 4)));
                    toClient.write('\n');
                    toClient.flush();
                    MessageEnvelope welcomeEnvelope = awaitType(client, MessageType.WELCOME);
                    WelcomePayload welcome = welcomeEnvelope.parsePayload(WelcomePayload.class);
                    assertEquals("player-test", welcome.assignedPlayerId());

                    assertTrue(client.send(MessageEnvelope.ping(73L)));
                    MessageEnvelope pingEnvelope = MessageSerializer.deserialize(fromClient.readLine());
                    assertEquals(MessageType.PING, pingEnvelope.type());
                    PingPayload ping = pingEnvelope.parsePayload(PingPayload.class);
                    assertEquals(73L, ping.pingId());

                    toClient.write(MessageSerializer.serialize(MessageEnvelope.pong(ping.pingId())));
                    toClient.write('\n');
                    toClient.flush();
                    MessageEnvelope pongEnvelope = awaitType(client, MessageType.PONG);
                    PongPayload pong = pongEnvelope.parsePayload(PongPayload.class);
                    assertEquals(73L, pong.pingId());
                }
            } finally {
                client.close();
            }
        }
    }

    private static MessageEnvelope awaitType(NetworkClient client, MessageType expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            MessageEnvelope message = client.pollMessage();
            if (message != null && message.type() == expected) {
                return message;
            }
            Thread.sleep(10);
        }
        MessageEnvelope remaining = client.pollMessage();
        assertNotNull(remaining, "Timed out waiting for " + expected);
        assertEquals(expected, remaining.type());
        return remaining;
    }
}
