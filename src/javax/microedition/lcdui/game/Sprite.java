/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.lcdui.game;

import java.awt.Rectangle;
import java.util.Objects;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class Sprite extends Layer {
    public static final int TRANS_NONE = 0;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_MIRROR_ROT270 = 4;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR_ROT90 = 7;

    private Image image;
    private int frameWidth;
    private int frameHeight;
    private int framesAcross;
    private int rawFrames;
    private int[] sequence;
    private int frame;
    private int refX;
    private int refY;
    private int transform;
    private Rectangle collision;

    public Sprite(Image image) {
        this(image, image.getWidth(), image.getHeight());
    }

    public Sprite(Image image, int frameWidth, int frameHeight) {
        super(0, 0);
        replaceImage(image, frameWidth, frameHeight);
    }

    public Sprite(Sprite source) {
        super(source.width, source.height);
        image = Image.createImage(source.image);
        frameWidth = source.frameWidth;
        frameHeight = source.frameHeight;
        framesAcross = source.framesAcross;
        rawFrames = source.rawFrames;
        sequence = source.sequence == null ? null : source.sequence.clone();
        frame = source.frame;
        refX = source.refX;
        refY = source.refY;
        transform = source.transform;
        collision = new Rectangle(source.collision);
        x = source.x;
        y = source.y;
        visible = source.visible;
    }

    public void defineReferencePixel(int x, int y) {
        refX = x;
        refY = y;
    }

    public void setRefPixelPosition(int x, int y) {
        positionReference(x, y);
    }

    private void positionReference(int x, int y) {
        this.x += x - referenceX();
        this.y += y - referenceY();
    }

    public int getRefPixelX() {
        return referenceX();
    }

    private int referenceX() {
        return x + (swapsAxes() ? mappedY(refY) : mappedX(refX));
    }

    public int getRefPixelY() {
        return referenceY();
    }

    private int referenceY() {
        return y + (swapsAxes() ? mappedX(refX) : mappedY(refY));
    }

    public void setTransform(int transform) {
        if (transform < TRANS_NONE || transform > TRANS_MIRROR_ROT90) {
            throw new IllegalArgumentException();
        }
        int anchorX = referenceX();
        int anchorY = referenceY();
        this.transform = transform;
        resize();
        positionReference(anchorX, anchorY);
    }

    // The MIDP transform constants encode source-axis flips in the low bits.
    // The third bit exchanges the axes after those flips.
    private boolean swapsAxes() { return (transform & 4) != 0; }
    private boolean flipsX() { return (transform & 2) != 0; }
    private boolean flipsY() { return (transform & 1) != 0; }
    private int mappedX(int x) { return flipsX() ? frameWidth - 1 - x : x; }
    private int mappedY(int y) { return flipsY() ? frameHeight - 1 - y : y; }

    private void resize() {
        setSize(swapsAxes() ? frameHeight : frameWidth, swapsAxes() ? frameWidth : frameHeight);
    }

    public void setFrame(int index) {
        if (index < 0 || index >= sequenceLength()) {
            throw new IndexOutOfBoundsException();
        }
        frame = index;
    }

    public final int getFrame() { return frame; }
    public int getRawFrameCount() { return rawFrames; }
    public int getFrameSequenceLength() { return sequenceLength(); }

    private int sequenceLength() { return sequence == null ? rawFrames : sequence.length; }

    public void nextFrame() {
        frame = (frame + 1) % sequenceLength();
    }

    public void prevFrame() {
        frame = frame == 0 ? sequenceLength() - 1 : frame - 1;
    }

    public void setFrameSequence(int[] sequence) {
        if (sequence != null) {
            if (sequence.length == 0) {
                throw new IllegalArgumentException();
            }
            sequence = sequence.clone();
            for (int index : sequence) {
                if (index < 0 || index >= rawFrames) {
                    throw new ArrayIndexOutOfBoundsException();
                }
            }
        }
        this.sequence = sequence;
        frame = 0;
    }

    public void setImage(Image image, int frameWidth, int frameHeight) {
        replaceImage(image, frameWidth, frameHeight);
    }

    private void replaceImage(Image image, int frameWidth, int frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0
                || image.getWidth() % frameWidth != 0 || image.getHeight() % frameHeight != 0) {
            throw new IllegalArgumentException();
        }
        int anchorX = referenceX();
        int anchorY = referenceY();
        int across = image.getWidth() / frameWidth;
        int count = across * (image.getHeight() / frameHeight);
        if (count < rawFrames) {
            sequence = null;
            frame = 0;
        }
        if (frameWidth != this.frameWidth || frameHeight != this.frameHeight) {
            collision = new Rectangle(0, 0, frameWidth, frameHeight);
        }
        this.image = image;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        framesAcross = across;
        rawFrames = count;
        resize();
        positionReference(anchorX, anchorY);
    }

    private int imageX() {
        int raw = sequence == null ? frame : sequence[frame];
        return raw % framesAcross * frameWidth;
    }

    private int imageY() {
        int raw = sequence == null ? frame : sequence[frame];
        return raw / framesAcross * frameHeight;
    }

    public final void paint(Graphics graphics) {
        Objects.requireNonNull(graphics);
        if (visible) {
            graphics.drawRegion(image, imageX(), imageY(), frameWidth, frameHeight,
                    transform, x, y, Graphics.TOP | Graphics.LEFT);
        }
    }

    public void defineCollisionRectangle(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException();
        }
        collision = new Rectangle(x, y, width, height);
    }

    private Rectangle collisionArea(boolean pixels) {
        Rectangle rect = pixels
                ? collision.intersection(new Rectangle(0, 0, frameWidth, frameHeight))
                : collision;
        if (rect.isEmpty()) {
            return new Rectangle();
        }
        int left = flipsX() ? frameWidth - rect.x - rect.width : rect.x;
        int top = flipsY() ? frameHeight - rect.y - rect.height : rect.y;
        return swapsAxes()
                ? new Rectangle(x + top, y + left, rect.height, rect.width)
                : new Rectangle(x + left, y + top, rect.width, rect.height);
    }

    public final boolean collidesWith(Sprite sprite, boolean pixelLevel) {
        if (!sprite.visible || !visible) {
            return false;
        }
        Rectangle overlap = collisionArea(pixelLevel).intersection(sprite.collisionArea(pixelLevel));
        return !overlap.isEmpty() && (!pixelLevel
                || hasOpaqueOverlap(overlap, pixels(overlap), sprite.pixels(overlap)));
    }

    public final boolean collidesWith(Image image, int x, int y, boolean pixelLevel) {
        if (!visible) {
            return false;
        }
        Rectangle bounds = new Rectangle(x, y, image.getWidth(), image.getHeight());
        Rectangle overlap = collisionArea(pixelLevel).intersection(bounds);
        return !overlap.isEmpty() && (!pixelLevel || hasOpaqueOverlap(overlap, pixels(overlap),
                new Pixels(image, overlap.x - x, overlap.y - y, overlap.width, overlap.height)));
    }

    public final boolean collidesWith(TiledLayer layer, boolean pixelLevel) {
        if (!layer.visible || !visible) {
            return false;
        }
        Rectangle overlap = collisionArea(pixelLevel).intersection(
                new Rectangle(layer.x, layer.y, layer.width, layer.height));
        if (overlap.isEmpty()) {
            return false;
        }
        int tileWidth = layer.getCellWidth();
        int tileHeight = layer.getCellHeight();
        int lastCol = (overlap.x - layer.x + overlap.width - 1) / tileWidth;
        int lastRow = (overlap.y - layer.y + overlap.height - 1) / tileHeight;
        for (int row = (overlap.y - layer.y) / tileHeight; row <= lastRow; row++) {
            for (int col = (overlap.x - layer.x) / tileWidth; col <= lastCol; col++) {
                int tile = pixelLevel ? layer.staticTileAt(col, row) : layer.getCell(col, row);
                if (tile == 0) {
                    continue;
                }
                if (!pixelLevel) {
                    return true;
                }
                int tileX = layer.x + col * tileWidth;
                int tileY = layer.y + row * tileHeight;
                Rectangle area = overlap.intersection(new Rectangle(tileX, tileY, tileWidth, tileHeight));
                Pixels tilePixels = new Pixels(layer.tiles(),
                        layer.sourceX(tile) + area.x - tileX, layer.sourceY(tile) + area.y - tileY,
                        area.width, area.height);
                if (hasOpaqueOverlap(area, pixels(area), tilePixels)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Pixels pixels(Rectangle area) {
        int localX = area.x - x;
        int localY = area.y - y;
        boolean swap = swapsAxes();
        int sourceX = swap ? localY : localX;
        int sourceY = swap ? localX : localY;
        int sourceWidth = swap ? area.height : area.width;
        int sourceHeight = swap ? area.width : area.height;
        int firstX = mappedX(sourceX);
        int firstY = mappedY(sourceY);
        int left = flipsX() ? firstX - sourceWidth + 1 : firstX;
        int top = flipsY() ? firstY - sourceHeight + 1 : firstY;
        Pixels result = new Pixels(image, imageX() + left, imageY() + top, sourceWidth, sourceHeight);
        result.origin = (firstY - top) * sourceWidth + firstX - left;
        int xStep = flipsX() ? -1 : 1;
        int yStep = flipsY() ? -sourceWidth : sourceWidth;
        result.columnStep = swap ? yStep : xStep;
        result.rowStep = swap ? xStep : yStep;
        return result;
    }

    private static boolean hasOpaqueOverlap(Rectangle area, Pixels a, Pixels b) {
        for (int row = 0; row < area.height; row++) {
            int ai = a.origin + row * a.rowStep;
            int bi = b.origin + row * b.rowStep;
            for (int col = 0; col < area.width; col++, ai += a.columnStep, bi += b.columnStep) {
                if ((a.argb[ai] & b.argb[bi]) >>> 24 == 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Pixels {
        final int[] argb;
        int origin;
        int columnStep = 1;
        int rowStep;

        Pixels(Image image, int x, int y, int width, int height) {
            argb = new int[width * height];
            rowStep = width;
            image.getRGB(argb, 0, width, x, y, width, height);
        }
    }
}
