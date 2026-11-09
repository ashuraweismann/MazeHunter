package com.mazehunter.common.protocol;

/**
 * Payload for heartbeat ping requests.
 *
 * @param pingId monotonic or correlation ID sent by the client
 */
public record PingPayload(long pingId) {
}
