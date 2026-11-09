package com.mazehunter.common.protocol;

/**
 * Payload sent by the client upon connecting to the server.
 *
 * @param playerName the nickname chosen by the player
 * @param clientVersion the protocol or client version identifier
 */
public record ConnectPayload(String playerName, String clientVersion) {
    public ConnectPayload {
        if (playerName == null || playerName.isBlank()) {
            playerName = "Guest";
        }
        if (clientVersion == null) {
            clientVersion = "1.0.0";
        }
    }
}
