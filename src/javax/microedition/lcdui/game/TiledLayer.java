/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.lcdui.game;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class TiledLayer extends Layer {
    private final int columns;
    private final int rows;
    private final int[][] cells;
    private final List<Integer> animatedTiles = new ArrayList<>();
    private Image tiles;
    private int cellWidth;
    private int cellHeight;
    private int tilesAcross;
    private int tileCount;

    public TiledLayer(int columns, int rows, Image image, int tileWidth, int tileHeight) {
        super(0, 0);
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException();
        }
        this.columns = columns;
        this.rows = rows;
        configureTiles(image, tileWidth, tileHeight);
        cells = new int[rows][columns];
    }

    public int createAnimatedTile(int staticTileIndex) {
        checkStaticTile(staticTileIndex);
        animatedTiles.add(staticTileIndex);
        return -animatedTiles.size();
    }

    public void setAnimatedTile(int animatedTileIndex, int staticTileIndex) {
        checkStaticTile(staticTileIndex);
        animatedTiles.set(animationSlot(animatedTileIndex), staticTileIndex);
    }

    public int getAnimatedTile(int animatedTileIndex) {
        return animatedTiles.get(animationSlot(animatedTileIndex));
    }

    private int animationSlot(int index) {
        if (index >= 0 || index < -animatedTiles.size()) {
            throw new IndexOutOfBoundsException();
        }
        return -index - 1;
    }

    private void checkStaticTile(int index) {
        if (index < 0 || index > tileCount) {
            throw new IndexOutOfBoundsException();
        }
    }

    public void setCell(int col, int row, int tileIndex) {
        checkCells(col, row, 1, 1, tileIndex);
        cells[row][col] = tileIndex;
    }

    public int getCell(int col, int row) {
        return cells[row][col];
    }

    public void fillCells(int col, int row, int numCols, int numRows, int tileIndex) {
        checkCells(col, row, numCols, numRows, tileIndex);
        for (int r = row; r < row + numRows; r++) {
            Arrays.fill(cells[r], col, col + numCols, tileIndex);
        }
    }

    private void checkCells(int col, int row, int numCols, int numRows, int tileIndex) {
        if (numCols < 0 || numRows < 0) {
            throw new IllegalArgumentException();
        }
        if (col < 0 || col >= columns || row < 0 || row >= rows
                || numCols > columns - col || numRows > rows - row
                || tileIndex < -animatedTiles.size() || tileIndex > tileCount) {
            throw new IndexOutOfBoundsException();
        }
    }

    public final int getCellWidth() { return cellWidth; }
    public final int getCellHeight() { return cellHeight; }
    public final int getColumns() { return columns; }
    public final int getRows() { return rows; }

    public void setStaticTileSet(Image image, int tileWidth, int tileHeight) {
        int previousCount = tileCount;
        configureTiles(image, tileWidth, tileHeight);
        if (tileCount < previousCount) {
            for (int[] row : cells) {
                Arrays.fill(row, 0);
            }
            animatedTiles.clear();
        }
    }

    private void configureTiles(Image image, int tileWidth, int tileHeight) {
        if (tileWidth <= 0 || tileHeight <= 0
                || image.getWidth() % tileWidth != 0 || image.getHeight() % tileHeight != 0
                || columns > Integer.MAX_VALUE / tileWidth || rows > Integer.MAX_VALUE / tileHeight) {
            throw new IllegalArgumentException();
        }
        setSize(columns * tileWidth, rows * tileHeight);
        tiles = image;
        cellWidth = tileWidth;
        cellHeight = tileHeight;
        tilesAcross = image.getWidth() / tileWidth;
        tileCount = tilesAcross * (image.getHeight() / tileHeight);
    }

    int staticTileAt(int col, int row) {
        int index = cells[row][col];
        return index < 0 ? getAnimatedTile(index) : index;
    }

    int sourceX(int tile) {
        return (tile - 1) % tilesAcross * cellWidth;
    }

    int sourceY(int tile) {
        return (tile - 1) / tilesAcross * cellHeight;
    }

    Image tiles() {
        return tiles;
    }

    public final void paint(Graphics graphics) {
        Objects.requireNonNull(graphics);
        if (!visible) {
            return;
        }
        Rectangle clip = new Rectangle(graphics.getClipX(), graphics.getClipY(),
                graphics.getClipWidth(), graphics.getClipHeight());
        Rectangle area = clip.intersection(new Rectangle(x, y, width, height));
        if (area.isEmpty()) {
            return;
        }
        int lastRow = (area.y - y + area.height - 1) / cellHeight;
        int lastCol = (area.x - x + area.width - 1) / cellWidth;
        for (int row = (area.y - y) / cellHeight; row <= lastRow; row++) {
            for (int col = (area.x - x) / cellWidth; col <= lastCol; col++) {
                int tile = staticTileAt(col, row);
                if (tile != 0) {
                    graphics.drawRegion(tiles, sourceX(tile), sourceY(tile), cellWidth, cellHeight,
                            Sprite.TRANS_NONE, x + col * cellWidth, y + row * cellHeight,
                            Graphics.TOP | Graphics.LEFT);
                }
            }
        }
    }
}
