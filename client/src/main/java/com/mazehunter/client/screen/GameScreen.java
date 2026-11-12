package com.mazehunter.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.mazehunter.client.MazeHunterGame;
import com.mazehunter.client.net.NetworkClient;
import com.mazehunter.client.ui.GameTheme;
import com.mazehunter.common.model.Direction;
import com.mazehunter.common.model.PlayerRole;
import com.mazehunter.common.protocol.ErrorPayload;
import com.mazehunter.common.protocol.GameOverPayload;
import com.mazehunter.common.protocol.GameStartPayload;
import com.mazehunter.common.protocol.LobbyUpdatePayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.PlayerCaughtPayload;
import com.mazehunter.common.protocol.PlayerSummary;
import com.mazehunter.common.protocol.StateUpdatePayload;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;

/** Server-authoritative maze view. Input submits movement intentions only. */
public final class GameScreen extends BaseStageScreen {

    private static final Color HUNTER_SPAWN = Color.valueOf("f1c66d");
    private static final Color RUNNER_SPAWN = Color.valueOf("b993e8");

    private final GameStartPayload start;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Label roleLabel;
    private final Label timerLabel;
    private final Label runnersLabel;
    private final Label statusLabel;
    private StateUpdatePayload currentState;
    private String localRole;
    private int localX;
    private int localY;
    private long moveSequence;
    private float cellSize;
    private float mazeX;
    private float mazeY;

    public GameScreen(MazeHunterGame game, GameStartPayload start) {
        super(game, false);
        this.start = start;
        this.localRole = start.assignedRole().name();
        this.localX = start.spawnX();
        this.localY = start.spawnY();
        this.currentState = game.latestState();

        Table panel = new Table();
        panel.setBackground(game.theme().drawable(GameTheme.PANEL));
        panel.pad(20);
        panel.defaults().left().pad(7);
        Label heading = new Label("MATCH STATUS", game.theme().skin(), "heading");
        heading.setFontScale(1.3f);
        panel.add(heading).left().row();
        roleLabel = new Label("Role: " + localRole, game.theme().skin(), "label");
        panel.add(roleLabel).left().padTop(20).row();
        timerLabel = new Label("Time: " + formatTime(start.durationSeconds()), game.theme().skin(), "heading");
        timerLabel.setFontScale(1.5f);
        panel.add(timerLabel).left().padTop(12).row();
        runnersLabel = new Label("Runners: —", game.theme().skin(), "label");
        panel.add(runnersLabel).left().padTop(10).row();
        statusLabel = new Label("Move with WASD or arrow keys.", game.theme().skin(), "muted");
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.left);
        panel.add(statusLabel).width(220).minHeight(74).left().padTop(20).row();
        Label controls = new Label("W / ↑   Move up\nA / ←   Move left\nS / ↓   Move down\nD / →   Move right",
                game.theme().skin(), "muted");
        controls.setAlignment(Align.left, Align.left);
        panel.add(controls).left().padTop(8).row();
        root.top().right();
        root.add(panel).width(270).top().right();

        refreshMazeBounds();
        if (currentState != null) {
            applyState(currentState);
        }
    }

    @Override
    public void show() {
        super.show();
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                Direction direction = switch (keycode) {
                    case Input.Keys.W, Input.Keys.UP -> Direction.UP;
                    case Input.Keys.S, Input.Keys.DOWN -> Direction.DOWN;
                    case Input.Keys.A, Input.Keys.LEFT -> Direction.LEFT;
                    case Input.Keys.D, Input.Keys.RIGHT -> Direction.RIGHT;
                    default -> null;
                };
                if (direction == null) {
                    return false;
                }
                NetworkClient client = game.networkClient();
                if (client != null && client.send(MessageEnvelope.move(direction, ++moveSequence))) {
                    statusLabel.setText("Move sent. The server validates each step.");
                }
                return true;
            }
        }));
    }

    @Override
    public void render(float delta) {
        handleMessages();
        if (game.getScreen() != this) {
            return;
        }
        com.badlogic.gdx.utils.ScreenUtils.clear(GameTheme.BACKGROUND);
        refreshMazeBounds();
        drawMaze();
        drawPlayers();
        drawStage(delta);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        refreshMazeBounds();
    }

    @Override
    public void dispose() {
        super.dispose();
        shapes.dispose();
    }

    private void handleMessages() {
        NetworkClient client = game.networkClient();
        if (client == null) {
            return;
        }
        MessageEnvelope message;
        while ((message = client.pollMessage()) != null) {
            try {
                if (message.type() == MessageType.STATE_UPDATE) {
                    currentState = message.parsePayload(StateUpdatePayload.class);
                    game.setLatestState(currentState);
                    applyState(currentState);
                } else if (message.type() == MessageType.PLAYER_CAUGHT) {
                    PlayerCaughtPayload caught = message.parsePayload(PlayerCaughtPayload.class);
                    applyCaught(caught);
                    if (caught.caughtPlayerId().equals(game.playerId())) {
                        statusLabel.setText("You were caught! Waiting for match results…");
                    } else {
                        statusLabel.setText(caught.remainingRunners() + " runner(s) remain.");
                    }
                } else if (message.type() == MessageType.ERROR) {
                    statusLabel.setText(message.parsePayload(ErrorPayload.class).message());
                } else if (message.type() == MessageType.GAME_OVER) {
                    game.showGameOver(message.parsePayload(GameOverPayload.class));
                    return;
                } else if (message.type() == MessageType.LOBBY_UPDATE) {
                    game.setLobbySnapshot(message.parsePayload(LobbyUpdatePayload.class));
                    game.showLobby();
                    return;
                }
            } catch (JsonProcessingException exception) {
                statusLabel.setText("Received an invalid game update.");
            }
        }
        if (client.getState() == NetworkClient.State.FAILED
                || client.getState() == NetworkClient.State.DISCONNECTED) {
            statusLabel.setText("Connection lost. " + client.getLastError());
        }
    }

    private void applyState(StateUpdatePayload state) {
        timerLabel.setText("Time: " + formatTime(state.remainingSeconds()));
        int totalRunners = 0;
        int caughtRunners = 0;
        for (PlayerSummary player : state.players()) {
            if (player.role() == PlayerRole.RUNNER) {
                totalRunners++;
                if (player.caught()) {
                    caughtRunners++;
                }
            }
            if (player.id().equals(game.playerId())) {
                localRole = player.role().name();
                localX = player.x();
                localY = player.y();
            }
        }
        roleLabel.setText("Role: " + localRole);
        runnersLabel.setText("Runners alive: " + (totalRunners - caughtRunners)
                + " / " + totalRunners + "\nCaught: " + caughtRunners);
    }

    private void applyCaught(PlayerCaughtPayload caught) {
        if (currentState == null) {
            return;
        }
        ArrayList<PlayerSummary> players = new ArrayList<>(currentState.players().size());
        for (PlayerSummary player : currentState.players()) {
            boolean isCaught = player.caught() || player.id().equals(caught.caughtPlayerId());
            players.add(new PlayerSummary(player.id(), player.name(), player.ready(), player.role(),
                    player.x(), player.y(), isCaught));
        }
        currentState = new StateUpdatePayload(currentState.tick(), currentState.remainingSeconds(),
                players, currentState.matchStatus());
        game.setLatestState(currentState);
        applyState(currentState);
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", Math.max(seconds, 0) / 60, Math.max(seconds, 0) % 60);
    }

    private void refreshMazeBounds() {
        float width = stage.getViewport().getWorldWidth();
        float height = stage.getViewport().getWorldHeight();
        cellSize = Math.min((width - 330f) / start.mazeWidth(), (height - 64f) / start.mazeHeight());
        cellSize = Math.max(cellSize, 12f);
        mazeX = 30f;
        mazeY = Math.max(20f, (height - cellSize * start.mazeHeight()) / 2f);
        camera.setToOrtho(false, width, height);
        camera.update();
    }

    private void drawMaze() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        int[][] grid = start.mazeGrid();
        for (int y = 0; y < start.mazeHeight(); y++) {
            for (int x = 0; x < start.mazeWidth(); x++) {
                int cell = grid[y][x];
                float px = mazeX + x * cellSize;
                float py = mazeY + y * cellSize;
                if (cell == 1) {
                    shapes.setColor(GameTheme.WALL);
                } else if (cell == 2) {
                    shapes.setColor(HUNTER_SPAWN);
                } else if (cell == 3) {
                    shapes.setColor(RUNNER_SPAWN);
                } else {
                    shapes.setColor(GameTheme.FLOOR);
                }
                shapes.rect(px, py, cellSize - 1, cellSize - 1);
            }
        }
        shapes.end();
    }

    private void drawPlayers() {
        StateUpdatePayload state = currentState;
        if (state == null) {
            drawPlayer(localRole, localX, localY, false, true, game.playerName());
            return;
        }
        for (PlayerSummary player : state.players()) {
            drawPlayer(player.role().name(), player.x(), player.y(), player.caught(),
                    player.id().equals(game.playerId()), player.name());
        }
    }

    private void drawPlayer(String role, int x, int y, boolean caught, boolean local, String name) {
        float centerX = mazeX + (x + 0.5f) * cellSize;
        float centerY = mazeY + (y + 0.5f) * cellSize;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (local) {
            shapes.setColor(Color.WHITE);
            shapes.circle(centerX, centerY, cellSize * 0.39f);
        }
        if (caught) {
            shapes.setColor(GameTheme.MUTED);
            shapes.rect(centerX - cellSize * .2f, centerY - cellSize * .2f, cellSize * .4f, cellSize * .4f);
        } else {
            shapes.setColor("HUNTER".equals(role) ? GameTheme.HUNTER : GameTheme.RUNNER);
            shapes.circle(centerX, centerY, cellSize * (local ? .31f : .27f));
        }
        shapes.end();

        if (local) {
            var font = game.theme().skin().getFont("font");
            font.setColor(Color.WHITE);
            var batch = stage.getBatch();
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            font.draw(batch, name, centerX - cellSize * 2, centerY + cellSize * .48f,
                    cellSize * 4, Align.center, false);
            batch.end();
        }
    }
}
