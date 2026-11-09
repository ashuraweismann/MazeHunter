package com.mazehunter.common.protocol;

/**
 * Payload sent by the server when a protocol violation, bad state, or invalid command occurs.
 *
 * @param code machine-readable error code (e.g., MALFORMED_MESSAGE, INVALID_STATE)
 * @param message human-readable diagnostic message
 */
public record ErrorPayload(String code, String message) {
    public ErrorPayload {
        if (code == null || code.isBlank()) {
            code = "UNKNOWN_ERROR";
        }
        if (message == null) {
            message = "An error occurred";
        }
    }
}
