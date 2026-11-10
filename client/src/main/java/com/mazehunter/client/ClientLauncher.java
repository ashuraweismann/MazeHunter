package com.mazehunter.client;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Starts the desktop LibGDX client. */
public final class ClientLauncher {

    private ClientLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Maze Hunter");
        configuration.setWindowedMode(960, 720);
        configuration.setForegroundFPS(60);
        configuration.useVsync(true);
        configuration.setResizable(true);

        new Lwjgl3Application(new MazeHunterGame(), configuration);
    }
}
