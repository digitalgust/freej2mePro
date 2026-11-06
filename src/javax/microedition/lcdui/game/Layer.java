/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;

public abstract class Layer {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;

    protected Layer(int width, int height) {
        setSize(width, height);
    }

    final void setSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException();
        }
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void move(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public final int getX() { return x; }
    public final int getY() { return y; }
    public final int getWidth() { return width; }
    public final int getHeight() { return height; }
    public final boolean isVisible() { return visible; }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public abstract void paint(Graphics graphics);
}
