package com.mazehunter.client.screen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.mazehunter.client.MazeHunterGame;
import com.mazehunter.client.net.NetworkClient;
import com.mazehunter.client.ui.GameTheme;
import com.mazehunter.common.model.PlayerRole;
import com.mazehunter.common.protocol.ErrorPayload;
import com.mazehunter.common.protocol.GameOverPayload;
import com.mazehunter.common.protocol.LobbyUpdatePayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.PlayerSummary;
import com.mazehunter.common.protocol.StateUpdatePayload;

/** Match result and server-coordinated rematch screen. */
public final class GameOverScreen extends BaseStageScreen {

    private final Label statusLabel;
    private final TextButton lobbyButton;
    private boolean returnRequested;

    public GameOverScreen(MazeHunterGame game, GameOverPayload result) {
        super(game, true);

        Table card = new Table();
        card.setBackground(game.theme().drawable(GameTheme.PANEL));
        card.pad(36);
        card.defaults().pad(9);

        Label heading = new Label(resultTitle(result.winningRole()), game.theme().skin(), "heading");
        heading.setFontScale(2f);
        heading.setWrap(true);
        heading.setAlignment(com.badlogic.gdx.utils.Align.center);
        card.add(heading).width(560).center().padBottom(8).row();

        Label reason = new Label(formatReason(result.reason()), game.theme().skin(), "muted");
        reason.setWrap(true);
        reason.setAlignment(com.badlogic.gdx.utils.Align.center);
        card.add(reason).width(500).center().padBottom(18).row();

        Label stats = new Label(buildStats(result, game.latestState()), game.theme().skin(), "label");
        stats.setAlignment(com.badlogic.gdx.utils.Align.center);
        stats.setAlignment(com.badlogic.gdx.utils.Align.center, com.badlogic.gdx.utils.Align.center);
        card.add(stats).center().padBottom(24).row();

        statusLabel = new Label("Rematch with the same players?", game.theme().skin(), "muted");
        statusLabel.setWrap(true);
        statusLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        card.add(statusLabel).width(420).minHeight(36).center().row();

        lobbyButton = new TextButton("Back to Lobby", game.theme().skin(), "primary");
        lobbyButton.addListener(event -> {
            if (!lobbyButton.isPressed()) {
                return false;
            }
            requestRematch();
            return true;
        });
        card.add(lobbyButton).width(260).height(48).padTop(8).row();

        TextButton menuButton = new TextButton("Disconnect", game.theme().skin(), "secondary");
        menuButton.addListener(event -> {
            if (menuButton.isPressed()) {
                returnRequested = true;
                return true;
            }
            return false;
        });
        card.add(menuButton).width(260).height(42).padTop(6).row();

        root.add(card).center().width(650).center();
    }

    @Override
    public void render(float delta) {
        handleMessages();
        if (game.getScreen() != this) {
            return;
        }
        super.render(delta);
        if (returnRequested && game.getScreen() == this) {
            game.returnToMenu();
        }
    }

    private void requestRematch() {
        NetworkClient client = game.networkClient();
        if (client == null || !client.send(MessageEnvelope.rematch())) {
            statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
            statusLabel.setText("Could not request a rematch. The connection may have closed.");
            return;
        }
        lobbyButton.setDisabled(true);
        statusLabel.setText("Waiting for the server to open the lobby…");
    }

    private void handleMessages() {
        NetworkClient client = game.networkClient();
        if (client == null) {
            return;
        }
        MessageEnvelope message;
        while ((message = client.pollMessage()) != null) {
            try {
                if (message.type() == MessageType.LOBBY_UPDATE) {
                    game.setLobbySnapshot(message.parsePayload(LobbyUpdatePayload.class));
                    game.setLatestState(null);
                    game.showLobby();
                    return;
                }
                if (message.type() == MessageType.ERROR) {
                    statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
                    statusLabel.setText(message.parsePayload(ErrorPayload.class).message());
                    lobbyButton.setDisabled(false);
                }
            } catch (JsonProcessingException exception) {
                statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
                statusLabel.setText("The server sent an invalid response.");
            }
        }
        if (client.getState() == NetworkClient.State.FAILED
                || client.getState() == NetworkClient.State.DISCONNECTED) {
            statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
            statusLabel.setText("Connection lost. You can disconnect and return to the menu.");
            lobbyButton.setDisabled(true);
        }
    }

    private static String resultTitle(PlayerRole winner) {
        return switch (winner) {
            case HUNTER -> "HUNTER VICTORY";
            case RUNNER -> "RUNNERS SURVIVED!";
            case UNASSIGNED -> "MATCH ENDED";
        };
    }

    private static String formatReason(String reason) {
        return switch (reason) {
            case "ALL_RUNNERS_CAUGHT" -> "The Hunter caught every Runner.";
            case "TIME_EXPIRED" -> "The Runners survived until time ran out.";
            case "HUNTER_DISCONNECTED" -> "The Hunter disconnected. The Runners win by forfeit.";
            case "ALL_RUNNERS_DISCONNECTED" -> "All Runners disconnected.";
            case "INSUFFICIENT_PLAYERS" -> "The match ended because too few players remained.";
            default -> reason.replace('_', ' ').toLowerCase();
        };
    }

    private static String buildStats(GameOverPayload result, StateUpdatePayload state) {
        int caught = 0;
        int runners = 0;
        if (state != null) {
            for (PlayerSummary player : state.players()) {
                if (player.role() == PlayerRole.RUNNER) {
                    runners++;
                    if (player.caught()) {
                        caught++;
                    }
                }
            }
        }
        String runnerStats = runners == 0 ? "Runner status unavailable" :
                "Runners caught: " + caught + " / " + runners;
        return "Match duration: " + result.durationSeconds() + " seconds\n" + runnerStats;
    }
}
