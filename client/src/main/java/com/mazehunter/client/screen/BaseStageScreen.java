package com.mazehunter.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mazehunter.client.MazeHunterGame;
import com.mazehunter.client.ui.GameTheme;

/** Shared Scene2D lifecycle for the menu, lobby, and result screens. */
public abstract class BaseStageScreen implements Screen {

    protected final MazeHunterGame game;
    protected final Stage stage;
    protected final Table root;

    protected BaseStageScreen(MazeHunterGame game, boolean paintBackground) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.root = new Table();
        root.setFillParent(true);
        root.pad(28);
        if (paintBackground) {
            root.setBackground(game.theme().drawable(GameTheme.BACKGROUND));
        }
        stage.addActor(root);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(GameTheme.BACKGROUND);
        drawStage(delta);
    }

    protected void drawStage(float delta) {
        stage.act(Math.min(delta, 0.1f));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
