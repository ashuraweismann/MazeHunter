package com.mazehunter.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Disposable;

/** Small code-built skin, so the desktop client needs no external artwork files. */
public final class GameTheme implements Disposable {

    public static final Color BACKGROUND = Color.valueOf("101722");
    public static final Color PANEL = Color.valueOf("1b2735");
    public static final Color SURFACE = Color.valueOf("263647");
    public static final Color ACCENT = Color.valueOf("60e0bd");
    public static final Color HUNTER = Color.valueOf("ff5d67");
    public static final Color RUNNER = Color.valueOf("59b9ff");
    public static final Color TEXT = Color.valueOf("f2f6fb");
    public static final Color MUTED = Color.valueOf("a8b6c7");
    public static final Color ERROR = Color.valueOf("ff8181");
    public static final Color FLOOR = Color.valueOf("d9e5e3");
    public static final Color WALL = Color.valueOf("263448");

    private final Skin skin = new Skin();
    private final Texture pixel;

    public GameTheme() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();

        BitmapFont font = new BitmapFont();
        skin.add("font", font, BitmapFont.class);
        skin.add("pixel", pixel, Texture.class);

        skin.add("label", new Label.LabelStyle(font, TEXT), Label.LabelStyle.class);
        skin.add("heading", new Label.LabelStyle(font, ACCENT), Label.LabelStyle.class);
        skin.add("muted", new Label.LabelStyle(font, MUTED), Label.LabelStyle.class);
        skin.add("error", new Label.LabelStyle(font, ERROR), Label.LabelStyle.class);

        TextButton.TextButtonStyle primary = new TextButton.TextButtonStyle();
        primary.up = drawable(ACCENT);
        primary.down = drawable(Color.valueOf("43b99c"));
        primary.over = drawable(Color.valueOf("7aebce"));
        primary.font = font;
        primary.fontColor = BACKGROUND;
        primary.downFontColor = BACKGROUND;
        primary.overFontColor = BACKGROUND;
        skin.add("primary", primary, TextButton.TextButtonStyle.class);

        TextButton.TextButtonStyle secondary = new TextButton.TextButtonStyle();
        secondary.up = drawable(SURFACE);
        secondary.down = drawable(Color.valueOf("34495e"));
        secondary.over = drawable(Color.valueOf("30445a"));
        secondary.font = font;
        secondary.fontColor = TEXT;
        secondary.downFontColor = TEXT;
        secondary.overFontColor = TEXT;
        skin.add("secondary", secondary, TextButton.TextButtonStyle.class);

        TextField.TextFieldStyle field = new TextField.TextFieldStyle();
        field.font = font;
        field.fontColor = TEXT;
        field.focusedFontColor = TEXT;
        field.disabledFontColor = MUTED;
        field.background = drawable(SURFACE);
        field.focusedBackground = drawable(Color.valueOf("30445a"));
        field.cursor = drawable(ACCENT);
        field.selection = drawable(Color.valueOf("47647d"));
        skin.add("default", field, TextField.TextFieldStyle.class);
    }

    public Skin skin() {
        return skin;
    }

    public Drawable drawable(Color tint) {
        return new TextureRegionDrawable(new TextureRegion(pixel)).tint(tint);
    }

    @Override
    public void dispose() {
        skin.dispose();
    }
}
