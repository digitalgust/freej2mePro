/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.lcdui.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.microedition.lcdui.Graphics;

public class LayerManager {
    private final List<Layer> layers = new ArrayList<>();
    protected int viewX;
    protected int viewY;
    protected int viewWidth = Integer.MAX_VALUE;
    protected int viewHeight = Integer.MAX_VALUE;

    public void append(Layer layer) {
        removeLayer(layer);
        layers.add(layer);
    }

    public void insert(Layer layer, int index) {
        int previous = indexOf(layer);
        int finalSize = layers.size() + (previous < 0 ? 1 : 0);
        if (index < 0 || index >= finalSize) {
            throw new IndexOutOfBoundsException();
        }
        Objects.requireNonNull(layer);
        if (previous >= 0) {
            layers.remove(previous);
        }
        layers.add(index, layer);
    }

    public Layer getLayerAt(int index) {
        return layers.get(index);
    }

    public int getSize() {
        return layers.size();
    }

    public void remove(Layer layer) {
        removeLayer(layer);
    }

    private void removeLayer(Layer layer) {
        int index = indexOf(Objects.requireNonNull(layer));
        if (index >= 0) {
            layers.remove(index);
        }
    }

    private int indexOf(Layer layer) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i) == layer) {
                return i;
            }
        }
        return -1;
    }

    public void paint(Graphics graphics, int x, int y) {
        int translateX = graphics.getTranslateX();
        int translateY = graphics.getTranslateY();
        int clipX = graphics.getClipX();
        int clipY = graphics.getClipY();
        int clipWidth = graphics.getClipWidth();
        int clipHeight = graphics.getClipHeight();
        graphics.translate(x - viewX, y - viewY);
        try {
            graphics.clipRect(viewX, viewY, viewWidth, viewHeight);
            for (int i = layers.size() - 1; i >= 0; i--) {
                Layer layer = layers.get(i);
                if (layer.isVisible()) {
                    layer.paint(graphics);
                }
            }
        } finally {
            graphics.translate(translateX - graphics.getTranslateX(),
                    translateY - graphics.getTranslateY());
            graphics.setClip(clipX, clipY, clipWidth, clipHeight);
        }
    }

    public void setViewWindow(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException();
        }
        viewX = x;
        viewY = y;
        viewWidth = width;
        viewHeight = height;
    }
}
