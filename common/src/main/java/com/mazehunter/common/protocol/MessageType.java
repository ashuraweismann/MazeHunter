package com.mazehunter.common.protocol;

/**
 * Enumerates all message types supported across the client-server protocol.
 */
public enum MessageType {
    // Phase 1: Connection & Diagnostics
    CONNECT,
    WELCOME,
    PING,
    PONG,
    QUIT,
    ERROR,

    // Subsequent Phases: Lobby & Gameplay
    READY,
    LOBBY_UPDATE,
    GAME_START,
    MOVE,
    STATE_UPDATE,
    PLAYER_CAUGHT,
    GAME_OVER,
    REMATCH
}
