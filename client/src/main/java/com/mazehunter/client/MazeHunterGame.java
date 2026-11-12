package com.mazehunter.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.mazehunter.client.net.NetworkClient;
import com.mazehunter.client.screen.GameOverScreen;
import com.mazehunter.client.screen.GameScreen;
import com.mazehunter.client.screen.LobbyScreen;
import com.mazehunter.client.screen.MenuScreen;
import com.mazehunter.client.ui.GameTheme;
import com.mazehunter.common.protocol.GameOverPayload;
import com.mazehunter.common.protocol.GameStartPayload;
import com.mazehunter.common.protocol.LobbyUpdatePayload;
import com.mazehunter.common.protocol.StateUpdatePayload;

import java.util.List;

/** Owns the client session, shared theme, and screen transitions. */
public final class MazeHunterGame extends Game {

    private GameTheme theme;
    private NetworkClient networkClient;
    private String playerId;
    private String playerName = "Guest";
    private LobbyUpdatePayload lobbySnapshot = new LobbyUpdatePayload(List.of(), false);
    private StateUpdatePayload latestState;
    private GameOverPayload latestGameOver;

    @Override
    public void create() {
        theme = new GameTheme();
        transitionTo(new MenuScreen(this));
    }

    public GameTheme theme() {
        return theme;
    }

    public NetworkClient networkClient() {
        return networkClient;
    }

    public void setNetworkClient(NetworkClient networkClient) {
        if (this.networkClient != null && this.networkClient != networkClient) {
            this.networkClient.close();
        }
        this.networkClient = networkClient;
    }

    public String playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void setPlayer(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName == null || playerName.isBlank() ? "Guest" : playerName;
    }

    public LobbyUpdatePayload lobbySnapshot() {
        return lobbySnapshot;
    }

    public void setLobbySnapshot(LobbyUpdatePayload lobbySnapshot) {
        if (lobbySnapshot != null) {
            this.lobbySnapshot = lobbySnapshot;
        }
    }

    public StateUpdatePayload latestState() {
        return latestState;
    }

    public void setLatestState(StateUpdatePayload latestState) {
        this.latestState = latestState;
    }

    public GameOverPayload latestGameOver() {
        return latestGameOver;
    }

    public void showLobby() {
        transitionTo(new LobbyScreen(this));
    }

    public void showGame(GameStartPayload startPayload) {
        transitionTo(new GameScreen(this, startPayload));
    }

    public void showGameOver(GameOverPayload gameOverPayload) {
        latestGameOver = gameOverPayload;
        transitionTo(new GameOverScreen(this, gameOverPayload));
    }

    public void returnToMenu() {
        setNetworkClient(null);
        playerId = null;
        lobbySnapshot = new LobbyUpdatePayload(List.of(), false);
        latestState = null;
        latestGameOver = null;
        transitionTo(new MenuScreen(this));
    }

    private void transitionTo(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null) {
            previous.dispose();
        }
    }

    @Override
    public void dispose() {
        if (networkClient != null) {
            networkClient.close();
        }
        super.dispose();
        if (theme != null) {
            theme.dispose();
        }
    }
}
