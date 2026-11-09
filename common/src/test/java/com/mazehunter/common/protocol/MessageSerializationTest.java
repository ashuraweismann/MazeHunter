package com.mazehunter.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageSerializationTest {

    @Test
    @DisplayName("Should serialize and deserialize CONNECT message")
    void shouldSerializeAndDeserializeConnect() throws Exception {
        MessageEnvelope original = MessageEnvelope.connect("Alice", "1.0.0");
        String json = MessageSerializer.serialize(original);

        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"CONNECT\""));
        assertTrue(json.contains("\"playerName\":\"Alice\""));

        MessageEnvelope restored = MessageSerializer.deserialize(json);
        assertEquals(MessageType.CONNECT, restored.type());
        assertEquals(original.timestamp(), restored.timestamp());

        ConnectPayload payload = restored.parsePayload(ConnectPayload.class);
        assertEquals("Alice", payload.playerName());
        assertEquals("1.0.0", payload.clientVersion());
    }

    @Test
    @DisplayName("Should serialize and deserialize WELCOME message")
    void shouldSerializeAndDeserializeWelcome() throws Exception {
        MessageEnvelope original = MessageEnvelope.welcome("p-101", 20, 4);
        String json = MessageSerializer.serialize(original);

        MessageEnvelope restored = MessageSerializer.deserialize(json);
        assertEquals(MessageType.WELCOME, restored.type());

        WelcomePayload payload = restored.parsePayload(WelcomePayload.class);
        assertEquals("p-101", payload.assignedPlayerId());
        assertEquals(20, payload.serverTickRate());
        assertEquals(4, payload.maxPlayers());
    }

    @Test
    @DisplayName("Should serialize and deserialize PING and PONG messages")
    void shouldSerializeAndDeserializePingPong() throws Exception {
        MessageEnvelope ping = MessageEnvelope.ping(42L);
        String pingJson = MessageSerializer.serialize(ping);
        MessageEnvelope pingRestored = MessageSerializer.deserialize(pingJson);
        assertEquals(42L, pingRestored.parsePayload(PingPayload.class).pingId());

        MessageEnvelope pong = MessageEnvelope.pong(42L);
        String pongJson = MessageSerializer.serialize(pong);
        MessageEnvelope pongRestored = MessageSerializer.deserialize(pongJson);
        assertEquals(42L, pongRestored.parsePayload(PongPayload.class).pingId());
    }

    @Test
    @DisplayName("Should serialize and deserialize ERROR and QUIT messages")
    void shouldSerializeAndDeserializeErrorAndQuit() throws Exception {
        MessageEnvelope error = MessageEnvelope.error("INVALID_INPUT", "Missing field");
        String errorJson = MessageSerializer.serialize(error);
        MessageEnvelope errorRestored = MessageSerializer.deserialize(errorJson);
        ErrorPayload errPayload = errorRestored.parsePayload(ErrorPayload.class);
        assertEquals("INVALID_INPUT", errPayload.code());
        assertEquals("Missing field", errPayload.message());

        MessageEnvelope quit = MessageEnvelope.quit("Normal exit");
        String quitJson = MessageSerializer.serialize(quit);
        MessageEnvelope quitRestored = MessageSerializer.deserialize(quitJson);
        assertEquals("Normal exit", quitRestored.parsePayload(QuitPayload.class).reason());
    }

    @Test
    @DisplayName("Should serialize and deserialize READY and LOBBY_UPDATE messages")
    void shouldSerializeAndDeserializeReadyAndLobby() throws Exception {
        MessageEnvelope ready = MessageEnvelope.ready(true);
        String readyJson = MessageSerializer.serialize(ready);
        MessageEnvelope readyRestored = MessageSerializer.deserialize(readyJson);
        assertEquals(MessageType.READY, readyRestored.type());
        assertTrue(readyRestored.parsePayload(ReadyPayload.class).ready());

        PlayerSummary p1 = new PlayerSummary("p-1", "Alice", true, com.mazehunter.common.model.PlayerRole.UNASSIGNED, 0, 0, false);
        MessageEnvelope lobby = MessageEnvelope.lobbyUpdate(java.util.List.of(p1), true);
        String lobbyJson = MessageSerializer.serialize(lobby);
        MessageEnvelope lobbyRestored = MessageSerializer.deserialize(lobbyJson);
        assertEquals(MessageType.LOBBY_UPDATE, lobbyRestored.type());
        LobbyUpdatePayload lobbyPayload = lobbyRestored.parsePayload(LobbyUpdatePayload.class);
        assertTrue(lobbyPayload.canStart());
        assertEquals(1, lobbyPayload.players().size());
        assertEquals("Alice", lobbyPayload.players().get(0).name());
    }

    @Test
    @DisplayName("Should serialize and deserialize GAME_START, MOVE, STATE_UPDATE, PLAYER_CAUGHT, and GAME_OVER")
    void shouldSerializeAndDeserializeGameplayMessages() throws Exception {
        // GAME_START
        int[][] dummyGrid = {{1, 1}, {1, 0}};
        MessageEnvelope gameStart = MessageEnvelope.gameStart("m-1", 2, 2, dummyGrid, com.mazehunter.common.model.PlayerRole.HUNTER, 1, 1, 60);
        String gsJson = MessageSerializer.serialize(gameStart);
        MessageEnvelope gsRestored = MessageSerializer.deserialize(gsJson);
        GameStartPayload gsPayload = gsRestored.parsePayload(GameStartPayload.class);
        assertEquals("m-1", gsPayload.matchId());
        assertEquals(com.mazehunter.common.model.PlayerRole.HUNTER, gsPayload.assignedRole());

        // MOVE
        MessageEnvelope move = MessageEnvelope.move(com.mazehunter.common.model.Direction.UP, 101L);
        String moveJson = MessageSerializer.serialize(move);
        MessageEnvelope moveRestored = MessageSerializer.deserialize(moveJson);
        MovePayload movePayload = moveRestored.parsePayload(MovePayload.class);
        assertEquals(com.mazehunter.common.model.Direction.UP, movePayload.direction());
        assertEquals(101L, movePayload.sequence());

        // STATE_UPDATE
        PlayerSummary pState = new PlayerSummary("p-1", "Alice", true, com.mazehunter.common.model.PlayerRole.HUNTER, 5, 8, false);
        MessageEnvelope state = MessageEnvelope.stateUpdate(50L, 45, java.util.List.of(pState), com.mazehunter.common.model.MatchStatus.IN_PROGRESS);
        String stateJson = MessageSerializer.serialize(state);
        MessageEnvelope stateRestored = MessageSerializer.deserialize(stateJson);
        StateUpdatePayload statePayload = stateRestored.parsePayload(StateUpdatePayload.class);
        assertEquals(50L, statePayload.tick());
        assertEquals(45, statePayload.remainingSeconds());
        assertEquals(com.mazehunter.common.model.MatchStatus.IN_PROGRESS, statePayload.matchStatus());

        // PLAYER_CAUGHT
        MessageEnvelope caught = MessageEnvelope.playerCaught("p-1", "p-2", 0);
        String caughtJson = MessageSerializer.serialize(caught);
        MessageEnvelope caughtRestored = MessageSerializer.deserialize(caughtJson);
        PlayerCaughtPayload caughtPayload = caughtRestored.parsePayload(PlayerCaughtPayload.class);
        assertEquals("p-1", caughtPayload.hunterId());
        assertEquals("p-2", caughtPayload.caughtPlayerId());
        assertEquals(0, caughtPayload.remainingRunners());

        // GAME_OVER
        MessageEnvelope gameOver = MessageEnvelope.gameOver(com.mazehunter.common.model.PlayerRole.HUNTER, "ALL_RUNNERS_CAUGHT", 25);
        String goJson = MessageSerializer.serialize(gameOver);
        MessageEnvelope goRestored = MessageSerializer.deserialize(goJson);
        GameOverPayload goPayload = goRestored.parsePayload(GameOverPayload.class);
        assertEquals(com.mazehunter.common.model.PlayerRole.HUNTER, goPayload.winningRole());
        assertEquals("ALL_RUNNERS_CAUGHT", goPayload.reason());
    }

    @Test
    @DisplayName("Should throw JsonProcessingException on malformed JSON")
    void shouldThrowOnMalformedJson() {
        String invalidJson = "{ not a valid json line }";
        assertThrows(JsonProcessingException.class, () -> MessageSerializer.deserialize(invalidJson));
    }
}
