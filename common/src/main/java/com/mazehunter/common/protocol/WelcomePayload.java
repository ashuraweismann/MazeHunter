package com.mazehunter.common.protocol;

/**
 * Payload sent by the server to confirm a successful connection and assign player identity.
 *
 * @param assignedPlayerId the unique session ID assigned to the player
 * @param serverTickRate the authoritative update frequency (ticks/sec)
 * @param maxPlayers the maximum supported players per match
 */
public record WelcomePayload(String assignedPlayerId, int serverTickRate, int maxPlayers) {
}
