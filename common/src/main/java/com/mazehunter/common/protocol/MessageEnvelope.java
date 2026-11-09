package com.mazehunter.common.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

/**
 * Standard protocol envelope wrapping all client-server communications.
 *
 * @param type the message type discriminator
 * @param timestamp epoch millisecond timestamp when the message was sent
 * @param payload JSON tree representing the message-specific payload
 */
public record MessageEnvelope(
        @JsonProperty("type") MessageType type,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("payload") JsonNode payload
) {

    @JsonCreator
    public MessageEnvelope {
        Objects.requireNonNull(type, "Message type cannot be null");
        if (payload == null) {
            payload = MessageSerializer.getMapper().createObjectNode();
        }
    }

    public static MessageEnvelope of(MessageType type, Object payloadObj) {
        long now = Instant.now().toEpochMilli();
        JsonNode node = MessageSerializer.toJsonNode(payloadObj);
        return new MessageEnvelope(type, now, node);
    }

    public <T> T parsePayload(Class<T> payloadClass) throws JsonProcessingException {
        return MessageSerializer.fromJsonNode(payload, payloadClass);
    }

    // Convenience factory methods
    public static MessageEnvelope connect(String playerName, String clientVersion) {
        return of(MessageType.CONNECT, new ConnectPayload(playerName, clientVersion));
    }

    public static MessageEnvelope welcome(String assignedPlayerId, int serverTickRate, int maxPlayers) {
        return of(MessageType.WELCOME, new WelcomePayload(assignedPlayerId, serverTickRate, maxPlayers));
    }

    public static MessageEnvelope ping(long pingId) {
        return of(MessageType.PING, new PingPayload(pingId));
    }

    public static MessageEnvelope pong(long pingId) {
        return of(MessageType.PONG, new PongPayload(pingId));
    }

    public static MessageEnvelope quit(String reason) {
        return of(MessageType.QUIT, new QuitPayload(reason));
    }

    public static MessageEnvelope error(String code, String message) {
        return of(MessageType.ERROR, new ErrorPayload(code, message));
    }

    public static MessageEnvelope ready(boolean ready) {
        return of(MessageType.READY, new ReadyPayload(ready));
    }

    public static MessageEnvelope lobbyUpdate(java.util.List<PlayerSummary> players, boolean canStart) {
        return of(MessageType.LOBBY_UPDATE, new LobbyUpdatePayload(players, canStart));
    }

    public static MessageEnvelope gameStart(
            String matchId,
            int mazeWidth,
            int mazeHeight,
            int[][] mazeGrid,
            com.mazehunter.common.model.PlayerRole assignedRole,
            int spawnX,
            int spawnY,
            int durationSeconds
    ) {
        return of(MessageType.GAME_START, new GameStartPayload(
                matchId, mazeWidth, mazeHeight, mazeGrid, assignedRole, spawnX, spawnY, durationSeconds));
    }

    public static MessageEnvelope move(com.mazehunter.common.model.Direction direction, long sequence) {
        return of(MessageType.MOVE, new MovePayload(direction, sequence));
    }

    public static MessageEnvelope stateUpdate(
            long tick,
            int remainingSeconds,
            java.util.List<PlayerSummary> players,
            com.mazehunter.common.model.MatchStatus matchStatus
    ) {
        return of(MessageType.STATE_UPDATE, new StateUpdatePayload(tick, remainingSeconds, players, matchStatus));
    }

    public static MessageEnvelope playerCaught(String hunterId, String caughtPlayerId, int remainingRunners) {
        return of(MessageType.PLAYER_CAUGHT, new PlayerCaughtPayload(hunterId, caughtPlayerId, remainingRunners));
    }

    public static MessageEnvelope gameOver(
            com.mazehunter.common.model.PlayerRole winningRole,
            String reason,
            int durationSeconds
    ) {
        return of(MessageType.GAME_OVER, new GameOverPayload(winningRole, reason, durationSeconds));
    }

    public static MessageEnvelope rematch() {
        return of(MessageType.REMATCH, MessageSerializer.getMapper().createObjectNode());
    }
}
