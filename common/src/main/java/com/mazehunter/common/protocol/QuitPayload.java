package com.mazehunter.common.protocol;

/**
 * Payload sent when a client gracefully disconnects.
 *
 * @param reason human-readable or programmatic reason for quitting
 */
public record QuitPayload(String reason) {
    public QuitPayload {
        if (reason == null) {
            reason = "Client requested disconnection";
        }
    }
}
