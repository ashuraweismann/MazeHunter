package com.mazehunter.client.screen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.mazehunter.client.MazeHunterGame;
import com.mazehunter.client.net.NetworkClient;
import com.mazehunter.client.ui.GameTheme;
import com.mazehunter.common.protocol.ErrorPayload;
import com.mazehunter.common.protocol.GameOverPayload;
import com.mazehunter.common.protocol.GameStartPayload;
import com.mazehunter.common.protocol.LobbyUpdatePayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.PlayerSummary;

/** Player roster and ready-up screen. */
public final class LobbyScreen extends BaseStageScreen {

    private final Table playerRows = new Table();
    private final Label countLabel;
    private final Label lobbyStatusLabel;
    private final Label errorLabel;
    private final TextButton readyButton;
    private boolean returnRequested;

    public LobbyScreen(MazeHunterGame game) {
        super(game, true);
        root.top();

        Label title = new Label("MATCH LOBBY", game.theme().skin(), "heading");
        title.setFontScale(1.8f);
        root.add(title).left().expandX().padBottom(4).row();
        root.add(new Label("Players are assigned roles when everyone is ready.", game.theme().skin(), "muted"))
                .left().padBottom(20).row();

        Table content = new Table();
        Table rosterPanel = new Table();
        rosterPanel.setBackground(game.theme().drawable(GameTheme.PANEL));
        rosterPanel.pad(22);
        rosterPanel.defaults().pad(6);

        countLabel = new Label("Players (0/4)", game.theme().skin(), "label");
        countLabel.setFontScale(1.2f);
        rosterPanel.add(countLabel).left().expandX().row();
        rosterPanel.add(new Label("PLAYER", game.theme().skin(), "muted")).left().expandX();
        rosterPanel.add(new Label("STATUS", game.theme().skin(), "muted")).right().width(100).row();
        rosterPanel.add(playerRows).colspan(2).fillX().padTop(8).row();

        Table actionPanel = new Table();
        actionPanel.setBackground(game.theme().drawable(GameTheme.PANEL));
        actionPanel.pad(22);
        actionPanel.defaults().pad(8);
        lobbyStatusLabel = new Label("Waiting for players…", game.theme().skin(), "muted");
        lobbyStatusLabel.setWrap(true);
        lobbyStatusLabel.setAlignment(Align.center);
        actionPanel.add(lobbyStatusLabel).width(260).minHeight(72).center().row();

        readyButton = new TextButton("Ready up", game.theme().skin(), "primary");
        readyButton.addListener(event -> {
            if (!readyButton.isPressed()) {
                return false;
            }
            toggleReady();
            return true;
        });
        actionPanel.add(readyButton).width(260).height(48).row();

        errorLabel = new Label("", game.theme().skin(), "error");
        errorLabel.setWrap(true);
        actionPanel.add(errorLabel).width(260).minHeight(44).center().row();

        TextButton disconnectButton = new TextButton("Disconnect", game.theme().skin(), "secondary");
        disconnectButton.addListener(event -> {
            if (disconnectButton.isPressed()) {
                returnRequested = true;
                return true;
            }
            return false;
        });
        actionPanel.add(disconnectButton).width(260).height(42).row();

        content.add(rosterPanel).width(470).expandY().fillY().padRight(18);
        content.add(actionPanel).width(310).expandY().fillY();
        root.add(content).expand().fill().row();

        renderSnapshot(game.lobbySnapshot());
    }

    @Override
    public void render(float delta) {
        handleMessages();
        if (game.getScreen() != this) {
            return;
        }
        updateConnectionState();
        super.render(delta);
        if (returnRequested && game.getScreen() == this) {
            game.returnToMenu();
        }
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
                    LobbyUpdatePayload payload = message.parsePayload(LobbyUpdatePayload.class);
                    game.setLobbySnapshot(payload);
                    renderSnapshot(payload);
                } else if (message.type() == MessageType.GAME_START) {
                    GameStartPayload payload = message.parsePayload(GameStartPayload.class);
                    game.setLatestState(null);
                    game.showGame(payload);
                    return;
                } else if (message.type() == MessageType.GAME_OVER) {
                    game.showGameOver(message.parsePayload(GameOverPayload.class));
                    return;
                } else if (message.type() == MessageType.ERROR) {
                    errorLabel.setText(message.parsePayload(ErrorPayload.class).message());
                }
            } catch (JsonProcessingException exception) {
                errorLabel.setText("The server sent an invalid lobby update.");
            }
        }
    }

    private void updateConnectionState() {
        NetworkClient client = game.networkClient();
        if (client == null) {
            return;
        }
        if (client.getState() == NetworkClient.State.FAILED
                || client.getState() == NetworkClient.State.DISCONNECTED) {
            lobbyStatusLabel.setText("Connection lost. Return to the menu and reconnect.");
            lobbyStatusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
            readyButton.setDisabled(true);
        }
    }

    private void toggleReady() {
        PlayerSummary localPlayer = findLocalPlayer(game.lobbySnapshot());
        boolean nextReady = localPlayer == null || !localPlayer.ready();
        NetworkClient client = game.networkClient();
        if (client == null || !client.send(MessageEnvelope.ready(nextReady))) {
            errorLabel.setText("Could not send the ready status.");
            return;
        }
        errorLabel.setText("");
    }

    private PlayerSummary findLocalPlayer(LobbyUpdatePayload snapshot) {
        if (snapshot == null) {
            return null;
        }
        for (PlayerSummary player : snapshot.players()) {
            if (player.id().equals(game.playerId())) {
                return player;
            }
        }
        return null;
    }

    private void renderSnapshot(LobbyUpdatePayload snapshot) {
        playerRows.clearChildren();
        if (snapshot == null) {
            snapshot = new LobbyUpdatePayload(java.util.List.of(), false);
        }

        countLabel.setText("Players (" + snapshot.players().size() + "/4)");
        for (PlayerSummary player : snapshot.players()) {
            boolean local = player.id().equals(game.playerId());
            Label name = new Label(player.name() + (local ? "  •  YOU" : ""), game.theme().skin(),
                    local ? "heading" : "label");
            Label status = new Label(player.ready() ? "READY" : "WAITING", game.theme().skin(),
                    player.ready() ? "heading" : "muted");
            playerRows.add(name).left().expandX();
            playerRows.add(status).right().width(100).row();
        }
        if (snapshot.players().isEmpty()) {
            playerRows.add(new Label("Waiting for players…", game.theme().skin(), "muted"))
                    .left().colspan(2).padTop(14).row();
        }

        PlayerSummary local = findLocalPlayer(snapshot);
        readyButton.setText(local != null && local.ready() ? "Cancel ready" : "Ready up");
        if (snapshot.canStart()) {
            lobbyStatusLabel.setText("Everyone is ready. Starting the match…");
        } else if (snapshot.players().size() < 2) {
            lobbyStatusLabel.setText("At least two players are needed to start.");
        } else {
            lobbyStatusLabel.setText("Ready up when you are prepared to play.");
        }
        lobbyStatusLabel.setStyle(game.theme().skin().get("muted", Label.LabelStyle.class));
    }
}
