package com.mazehunter.server.game;

import com.mazehunter.common.model.GridPosition;
import com.mazehunter.common.model.MatchStatus;
import com.mazehunter.common.model.MazeGrid;
import com.mazehunter.common.model.PlayerRole;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MovePayload;
import com.mazehunter.common.protocol.PlayerSummary;
import com.mazehunter.common.protocol.ReadyPayload;
import com.mazehunter.server.net.ClientSession;
import com.mazehunter.server.net.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages match lifecycle, player roles, spawning, countdown timekeeping,
 * win conditions, and state synchronization broadcasts.
 */
public class MatchManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchManager.class);
    private static final int DEFAULT_DURATION_SECONDS = 60;
    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int MAX_PLAYERS = 4;

    private final ConnectionManager connectionManager;
    private final MazeGrid maze;
    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();

    private volatile MatchStatus status = MatchStatus.LOBBY;
    private volatile long matchStartTimeMs = 0;
    private volatile int durationSeconds = DEFAULT_DURATION_SECONDS;
    private volatile String matchId = "m-none";

    public MatchManager(ConnectionManager connectionManager) {
        this(connectionManager, MazeGrid.createDefault());
    }

    public MatchManager(ConnectionManager connectionManager, MazeGrid maze) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager");
        this.maze = maze != null ? maze : MazeGrid.createDefault();
    }

    public MatchStatus getStatus() {
        return status;
    }

    public MazeGrid getMaze() {
        return maze;
    }

    public Map<String, PlayerState> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    public synchronized void registerPlayer(String playerId, String playerName) {
        if (!players.containsKey(playerId)) {
            PlayerState state = new PlayerState(playerId, playerName, maze.getHunterSpawn());
            players.put(playerId, state);
            LOGGER.info("Player {} ({}) registered in MatchManager", playerId, playerName);
            broadcastLobbyUpdate();
        }
    }

    public synchronized void unregisterPlayer(String playerId) {
        PlayerState removed = players.remove(playerId);
        if (removed != null) {
            removed.setConnected(false);
            GameLoop loop = connectionManager.getGameLoop();
            if (loop != null) {
                loop.removePlayerCommands(playerId);
            }
            LOGGER.info("Player {} removed from MatchManager", playerId);

            if (status == MatchStatus.IN_PROGRESS) {
                // If the Hunter disconnects, remaining Runners win
                if (removed.getRole() == PlayerRole.HUNTER) {
                    LOGGER.info("Hunter disconnected during active match. Runners win by forfeit.");
                    endMatch(PlayerRole.RUNNER, "HUNTER_DISCONNECTED");
                } else if (countActiveRunners() == 0) {
                    // All active runners left or caught
                    LOGGER.info("All runners disconnected or caught. Hunter wins.");
                    endMatch(PlayerRole.HUNTER, "ALL_RUNNERS_DISCONNECTED");
                } else if (getConnectedPlayerCount() < MIN_PLAYERS_TO_START) {
                    LOGGER.info("Player count dropped below minimum. Match ended.");
                    endMatch(PlayerRole.UNASSIGNED, "INSUFFICIENT_PLAYERS");
                }
                if (status == MatchStatus.IN_PROGRESS) {
                    int remainingSeconds = Math.max(0, durationSeconds - getElapsedSeconds());
                    connectionManager.broadcast(MessageEnvelope.stateUpdate(
                            loop != null ? loop.getTickIndex() : 0,
                            remainingSeconds,
                            buildSummaries(),
                            status));
                }
            } else if (status == MatchStatus.LOBBY) {
                broadcastLobbyUpdate();
            }
        }
    }

    public synchronized void setPlayerReady(String playerId, boolean ready) {
        PlayerState player = players.get(playerId);
        if (player != null && status == MatchStatus.LOBBY) {
            player.setReady(ready);
            connectionManager.findSession(playerId).ifPresent(s -> s.setReady(ready));
            LOGGER.info("Player {} ({}) ready status changed to {}", playerId, player.getName(), ready);
            broadcastLobbyUpdate();

            if (canStartMatch()) {
                startMatch();
            }
        }
    }

    public boolean canStartMatch() {
        if (status != MatchStatus.LOBBY) {
            return false;
        }
        int count = getConnectedPlayerCount();
        if (count < MIN_PLAYERS_TO_START || count > MAX_PLAYERS) {
            return false;
        }
        for (PlayerState player : players.values()) {
            if (player.isConnected()) {
                ClientSession session = connectionManager.findSession(player.getPlayerId()).orElse(null);
                if (session == null || !session.isReady()) {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized void startMatch() {
        if (status != MatchStatus.LOBBY) {
            return;
        }

        List<PlayerState> activePlayers = new ArrayList<>();
        for (PlayerState p : players.values()) {
            if (p.isConnected()) {
                activePlayers.add(p);
            }
        }

        if (activePlayers.size() < MIN_PLAYERS_TO_START) {
            return;
        }

        this.status = MatchStatus.IN_PROGRESS;
        this.matchId = "m-" + System.currentTimeMillis();
        this.matchStartTimeMs = System.currentTimeMillis();
        this.durationSeconds = DEFAULT_DURATION_SECONDS;

        // 1. Select random Hunter
        int hunterIndex = ThreadLocalRandom.current().nextInt(activePlayers.size());
        PlayerState hunter = activePlayers.get(hunterIndex);
        hunter.setRole(PlayerRole.HUNTER);
        hunter.setPosition(maze.getHunterSpawn());
        hunter.setCaught(false);

        // 2. Assign Runners to distinct spawns
        List<GridPosition> runnerSpawns = new ArrayList<>(maze.getRunnerSpawns());
        Collections.shuffle(runnerSpawns);

        int runnerSpawnIdx = 0;
        for (int i = 0; i < activePlayers.size(); i++) {
            if (i == hunterIndex) {
                continue;
            }
            PlayerState runner = activePlayers.get(i);
            runner.setRole(PlayerRole.RUNNER);
            runner.setCaught(false);

            GridPosition spawn = runnerSpawns.get(runnerSpawnIdx % runnerSpawns.size());
            runner.setPosition(spawn);
            runnerSpawnIdx++;
        }

        LOGGER.info("Match {} started! Hunter: {} ({}) with {} Runners",
                matchId, hunter.getPlayerId(), hunter.getName(), activePlayers.size() - 1);

        // 3. Send tailored GAME_START message to each client
        for (PlayerState p : activePlayers) {
            connectionManager.findSession(p.getPlayerId()).ifPresent(session -> {
                MessageEnvelope startMsg = MessageEnvelope.gameStart(
                        matchId,
                        maze.getWidth(),
                        maze.getHeight(),
                        maze.getGrid(),
                        p.getRole(),
                        p.getPosition().x(),
                        p.getPosition().y(),
                        durationSeconds
                );
                session.sendMessage(startMsg);
            });
        }
    }

    /**
     * Processes a client command on the authoritative game thread.
     */
    public void processCommand(ClientCommand command) {
        if (command == null || status != MatchStatus.IN_PROGRESS) {
            return;
        }

        if (command.payload() instanceof MovePayload movePayload) {
            PlayerState player = players.get(command.playerId());
            if (player != null && player.isActive()) {
                if (MovementValidator.isValidMove(player, movePayload.direction(), maze)) {
                    player.setPosition(player.getPosition().translate(movePayload.direction()));
                }
            }
        } else if (command.payload() instanceof ReadyPayload readyPayload) {
            setPlayerReady(command.playerId(), readyPayload.ready());
        }
    }

    /**
     * Executes one tick of the authoritative simulation loop (called at 20 Hz).
     */
    public synchronized void tick(long tickIndex) {
        if (status != MatchStatus.IN_PROGRESS) {
            return;
        }

        // 1. Check Hunter-Runner collisions
        List<PlayerState> caughtRunners = CollisionDetector.detectCaughtRunners(players.values());
        String hunterId = findActiveHunterId();
        for (PlayerState caughtRunner : caughtRunners) {
            int remainingRunners = countActiveRunners();
            LOGGER.info("Catch detected: Hunter {} caught Runner {}! Remaining: {}",
                    hunterId, caughtRunner.getPlayerId(), remainingRunners);
            connectionManager.broadcast(MessageEnvelope.playerCaught(
                    hunterId, caughtRunner.getPlayerId(), remainingRunners));
        }

        // 2. Evaluate win conditions
        int activeRunners = countActiveRunners();
        if (activeRunners == 0) {
            endMatch(PlayerRole.HUNTER, "ALL_RUNNERS_CAUGHT");
            return;
        }

        int remainingSeconds = Math.max(0, durationSeconds - getElapsedSeconds());
        if (remainingSeconds <= 0) {
            endMatch(PlayerRole.RUNNER, "TIME_EXPIRED");
            return;
        }

        // 3. Broadcast authoritative STATE_UPDATE snapshot
        List<PlayerSummary> summaries = buildSummaries();
        MessageEnvelope stateMsg = MessageEnvelope.stateUpdate(tickIndex, remainingSeconds, summaries, status);
        connectionManager.broadcast(stateMsg);
    }

    private synchronized void endMatch(PlayerRole winningRole, String reason) {
        if (status == MatchStatus.GAME_OVER) {
            return;
        }
        this.status = MatchStatus.GAME_OVER;
        int elapsed = getElapsedSeconds();
        LOGGER.info("Match {} ended! Winner: {}, Reason: {}, Duration: {}s", matchId, winningRole, reason, elapsed);

        connectionManager.broadcast(MessageEnvelope.gameOver(winningRole, reason, elapsed));
    }

    public synchronized void resetToLobby() {
        if (status != MatchStatus.GAME_OVER) {
            return;
        }
        this.status = MatchStatus.LOBBY;
        for (PlayerState p : players.values()) {
            p.setRole(PlayerRole.UNASSIGNED);
            p.setCaught(false);
            p.setPosition(maze.getHunterSpawn());
            p.setReady(false);
            connectionManager.findSession(p.getPlayerId()).ifPresent(s -> s.setReady(false));
        }
        matchStartTimeMs = 0;
        durationSeconds = DEFAULT_DURATION_SECONDS;
        matchId = "m-none";
        broadcastLobbyUpdate();
    }

    public void broadcastLobbyUpdate() {
        if (status == MatchStatus.LOBBY) {
            List<PlayerSummary> summaries = buildSummaries();
            boolean canStart = canStartMatch();
            connectionManager.broadcast(MessageEnvelope.lobbyUpdate(summaries, canStart));
        }
    }

    public List<PlayerSummary> buildSummaries() {
        List<PlayerSummary> list = new ArrayList<>();
        for (PlayerState p : players.values()) {
            boolean ready = connectionManager.findSession(p.getPlayerId())
                    .map(ClientSession::isReady)
                    .orElse(p.isReady());
            list.add(new PlayerSummary(
                    p.getPlayerId(),
                    p.getName(),
                    ready,
                    p.getRole(),
                    p.getPosition().x(),
                    p.getPosition().y(),
                    p.isCaught()
            ));
        }
        return list;
    }

    public int countActiveRunners() {
        int count = 0;
        for (PlayerState p : players.values()) {
            if (p.isActiveRunner()) {
                count++;
            }
        }
        return count;
    }

    public int getConnectedPlayerCount() {
        int count = 0;
        for (PlayerState p : players.values()) {
            if (p.isConnected()) {
                count++;
            }
        }
        return count;
    }

    private String findActiveHunterId() {
        for (PlayerState p : players.values()) {
            if (p.getRole() == PlayerRole.HUNTER && p.isConnected()) {
                return p.getPlayerId();
            }
        }
        return "unknown";
    }

    private int getElapsedSeconds() {
        if (matchStartTimeMs == 0) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() - matchStartTimeMs) / 1000);
    }
}
