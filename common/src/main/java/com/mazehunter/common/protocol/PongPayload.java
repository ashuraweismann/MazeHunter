package com.mazehunter.common.protocol;

/**
 * Payload for heartbeat pong responses.
 *
 * @param pingId correlation ID matching the incoming ping
 */
public record PongPayload(long pingId) {
}
