package com.mazehunter.client.screen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.mazehunter.client.MazeHunterGame;
import com.mazehunter.client.net.NetworkClient;
import com.mazehunter.client.ui.GameTheme;
import com.mazehunter.common.protocol.ErrorPayload;
import com.mazehunter.common.protocol.LobbyUpdatePayload;
import com.mazehunter.common.protocol.MessageEnvelope;
import com.mazehunter.common.protocol.MessageType;
import com.mazehunter.common.protocol.WelcomePayload;

/** Connection screen for choosing a server and player name. */
public final class MenuScreen extends BaseStageScreen {

    private final TextField hostField;
    private final TextField portField;
    private final TextField nameField;
    private final TextButton connectButton;
    private final Label statusLabel;

    public MenuScreen(MazeHunterGame game) {
        super(game, true);

        Table card = new Table();
        card.setBackground(game.theme().drawable(GameTheme.PANEL));
        card.pad(30);
        card.defaults().pad(7);

        Label title = new Label("MAZE HUNTER", game.theme().skin(), "heading");
        title.setFontScale(2.0f);
        card.add(title).center().padBottom(4).row();
        card.add(new Label("A real-time multiplayer maze survival game", game.theme().skin(), "muted"))
                .center().padBottom(18).row();

        hostField = new TextField("127.0.0.1", game.theme().skin());
        hostField.setMessageText("Server IP or hostname");
        portField = new TextField("8080", game.theme().skin());
        portField.setMessageText("Port");
        nameField = new TextField("Runner", game.theme().skin());
        nameField.setMessageText("Player name");

        addField(card, "Server address", hostField);
        addField(card, "Port", portField);
        addField(card, "Player name", nameField);

        statusLabel = new Label("Connect to a server to enter the lobby.", game.theme().skin(), "muted");
        statusLabel.setWrap(true);
        card.add(statusLabel).width(340).minHeight(36).left().padTop(12).row();

        connectButton = new TextButton("Connect", game.theme().skin(), "primary");
        connectButton.addListener(event -> {
            if (!connectButton.isPressed()) {
                return false;
            }
            beginConnect();
            return true;
        });
        card.add(connectButton).width(340).height(48).padTop(8).row();

        root.add(card).width(440).padBottom(14).center().row();
        root.add(new Label("Server-authoritative play  •  2–4 players", game.theme().skin(), "muted"))
                .center().padTop(2);
    }

    private void addField(Table card, String labelText, TextField field) {
        card.add(new Label(labelText, game.theme().skin(), "label")).left().padTop(8).row();
        card.add(field).width(340).height(42).left().row();
    }

    private void beginConnect() {
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException("Port is outside the valid range");
            }
            if (hostField.getText().isBlank()) {
                statusLabel.setText("Enter a server address.");
                statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
                return;
            }
        } catch (NumberFormatException exception) {
            statusLabel.setText("Enter a valid port from 1 to 65,535.");
            statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
            return;
        }

        NetworkClient client = new NetworkClient();
        game.setNetworkClient(client);
        try {
            client.connect(hostField.getText(), port, nameField.getText());
            connectButton.setDisabled(true);
            connectButton.setText("Connecting…");
            statusLabel.setStyle(game.theme().skin().get("muted", Label.LabelStyle.class));
            statusLabel.setText("Connecting to " + hostField.getText().trim() + ":" + port + "…");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            game.setNetworkClient(null);
            connectButton.setDisabled(false);
            connectButton.setText("Connect");
            statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
            statusLabel.setText(exception.getMessage());
        }
    }

    @Override
    public void render(float delta) {
        handleMessages();
        if (game.getScreen() != this) {
            return;
        }
        super.render(delta);
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
                } else if (message.type() == MessageType.WELCOME) {
                    WelcomePayload welcome = message.parsePayload(WelcomePayload.class);
                    game.setPlayer(welcome.assignedPlayerId(), nameField.getText().trim());
                    game.showLobby();
                    return;
                } else if (message.type() == MessageType.ERROR) {
                    ErrorPayload error = message.parsePayload(ErrorPayload.class);
                    statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
                    statusLabel.setText(error.message());
                    game.setNetworkClient(null);
                    connectButton.setDisabled(false);
                    connectButton.setText("Connect");
                    return;
                }
            } catch (JsonProcessingException exception) {
                statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
                statusLabel.setText("The server sent an invalid response.");
                game.setNetworkClient(null);
                connectButton.setDisabled(false);
                connectButton.setText("Connect");
                return;
            }
        }

        NetworkClient.State state = client.getState();
        if (state == NetworkClient.State.FAILED || state == NetworkClient.State.DISCONNECTED) {
            statusLabel.setStyle(game.theme().skin().get("error", Label.LabelStyle.class));
            statusLabel.setText(client.getLastError() == null ? "Could not connect to the server." : client.getLastError());
            game.setNetworkClient(null);
            connectButton.setDisabled(false);
            connectButton.setText("Connect");
        }
    }
}
