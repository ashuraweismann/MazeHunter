package com.mazehunter.common.protocol;

/**
 * Payload sent by client to toggle ready state in the lobby.
 */
public record ReadyPayload(boolean ready) {
}
