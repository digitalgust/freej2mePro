/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package org.recompile.mobile;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;

import com.nokia.mid.ui.DirectGraphics;

import java.awt.*;
import java.awt.image.BufferedImage;

public class PlatformGraphics extends javax.microedition.lcdui.Graphics implements DirectGraphics {
    protected BufferedImage canvas;
    protected Graphics2D gc;

    protected Color awtColor;
    public static int countTimes = 0;

    protected int strokeStyle = SOLID;

    protected Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);

    public PlatformGraphics platformGraphics;
    public PlatformImage platformImage;

    static ThreadLocal<Rectangle> cliplv = ThreadLocal.withInitial(() -> new Rectangle());
    static ThreadLocal<int[][]> polygonPoints = ThreadLocal.withInitial(() -> new int[2][3]);

    public PlatformGraphics(PlatformImage image) {
        canvas = image.getCanvas();
        gc = canvas.createGraphics();
        platformImage = image;

        platformGraphics = this;

        clipX = 0;
        clipY = 0;
        clipWidth = canvas.getWidth();
        clipHeight = canvas.getHeight();

        setColor(0, 0, 0);
        gc.setBackground(new Color(0, 0, 0, 0));
        gc.setFont(font.platformFont.awtFont);
    }

    public void reset() //Internal use method, resets the Graphics object to its inital values
    {
        translate(-translateX, -translateY);
        setClip(0, 0, canvas.getWidth(), canvas.getHeight());
        setColor(0, 0, 0);
        setFont(Font.getDefaultFont());
        setStrokeStyle(SOLID);
    }

    public Graphics2D getGraphics2D() {
        return gc;
    }

    public BufferedImage getCanvas() {
        return canvas;
    }

    public void clearRect(int x, int y, int width, int height) {
        gc.clearRect(x, y, width, height);
    }

    public void copyArea(int subx, int suby, int subw, int subh, int x, int y, int anchor) {
        x = AnchorX(x, subw, anchor);
        y = AnchorY(y, subh, anchor);

        BufferedImage sub = canvas.getSubimage(subx, suby, subw, subh);

        gc.drawImage(sub, x, y, null);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        gc.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    public void drawChar(char character, int x, int y, int anchor) {
        drawString(Character.toString(character), x, y, anchor);
    }

    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        drawString(new String(data, offset, length), x, y, anchor);
    }

    public void drawImage(javax.microedition.lcdui.Image image, int x, int y, int anchor) {
        try {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            x = AnchorX(x, imgWidth, anchor);
            y = AnchorY(y, imgHeight, anchor);

            boolean b = gc.drawImage(image.platformImage.getCanvas(), x, y, null);
        } catch (Exception e) {
            System.out.println("drawImage A:" + e.getMessage());
        }
    }

    public void drawImage(javax.microedition.lcdui.Image image, int x, int y) {
        try {
            gc.drawImage(image.platformImage.getCanvas(), x, y, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void drawImage2(Image image, int x, int y) // Internal use method called by PlatformImage
    {
        gc.drawImage(image.platformImage.getCanvas(), x, y, null);
    }

    public void drawImage2(BufferedImage image, int x, int y) // Internal use method called by PlatformImage
    {
        gc.drawImage(image, x, y, null);
    }

    public void drawImage2Test(BufferedImage image, int x, int y) {
        // This Fixes some transparency issues with some images drawn
        // with Nokia drawImage in a few games.
        // There's probably a deeper underlying issue.
        int row = 0;
        int col = 0;
        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();
        int cwidth = canvas.getWidth();
        int cheight = canvas.getHeight();
        int width = imgWidth;
        int height = imgHeight;

        try {
            int[] datargb = image.getRGB(0, 0, imgWidth, imgHeight, null, 0, imgWidth);
            if (x + width >= cwidth + 1) {
                width -= ((x + width) - cwidth);
            }
            if (y + height >= cheight + 1) {
                height -= ((y + height) - cheight);
            }
            for (row = 0; row < height; row++) {
                for (col = 0; col < width; col++) {
                    int c = datargb[col + row * imgWidth] & 0xFFFFFFFF;
                    if (c != 0xFF000000) {
                        canvas.setRGB(col + x, row + y, c);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("drawImage C:" + e.getMessage());
        }
    }

    public void flushGraphics(Image image, int x, int y, int width, int height) {
        // called by MobilePlatform.flushGraphics/repaint
        try {
            BufferedImage sub = image.platformImage.getCanvas().getSubimage(x, y, width, height);
            gc.drawImage(sub, x, y, null);
        } catch (Exception e) {
            e.printStackTrace();
            //System.out.println("flushGraphics A:"+e.getMessage());
        }
    }

    /**
     * Midp 2.0 javax.microedition.lcdui.Graphics.drawRegion implementation
     * Transform :
     * Sprite.TRANS_NONE
     * Sprite.TRANS_ROT90
     * Sprite.TRANS_ROT180
     * Sprite.TRANS_ROT270
     * Sprite.TRANS_MIRROR
     * Sprite.TRANS_MIRROR_ROT90
     * Sprite.TRANS_MIRROR_ROT180
     * Sprite.TRANS_MIRROR_ROT270
     *
     * @param image
     * @param subx
     * @param suby
     * @param subw
     * @param subh
     * @param transform j2me Sprite transform define
     * @param x
     * @param y
     * @param anchor
     */
    public void drawRegion(Image image, int subx, int suby, int subw, int subh, int transform, int x, int y, int anchor) {

        Rectangle r = cliplv.get();
        gc.getClipBounds(r);
        PlatformImageTransform pt = PlatformImage.midpTransformImage(image.platformImage.getCanvas(), subx, suby, subw, subh, transform);

        int imgx = x - pt.regionX;
        int imgy = y - pt.regionY;
        int transX = AnchorX(imgx, pt.regionWidth, anchor);
        int transY = AnchorY(imgy, pt.regionHeight, anchor);
        gc.translate(transX, transY);
        gc.clipRect(pt.regionX, pt.regionY, pt.regionWidth, pt.regionHeight);
        gc.drawImage(image.platformImage.getCanvas(), pt.transform, null);
//        gc.drawRect(pt.regionX, pt.regionY, pt.regionWidth, pt.regionHeight);
        gc.translate(-transX, -transY);
        Rectangle rect = r;
        gc.setClip(rect.x, rect.y, rect.width, rect.height);
    }

    /**
     * j2me 2.0 javax.microedition.lcdui.Graphics.drawRGB implementation
     *
     * @param rgbData
     * @param offset
     * @param scanlength
     * @param x
     * @param y
     * @param width
     * @param height
     * @param processAlpha
     */
    public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        if (rgbData == null || width < 1 || height < 1) {
            return;
        }
        int xOffset = 0, yOffset = 0;

        if (x < 0) {
            xOffset = -x;
            x = 0;
            width -= xOffset;
        }
        int x2 = x + width;
        if (x2 > canvas.getWidth()) {
            width = canvas.getWidth() - x;
            x2 = canvas.getWidth();
        }
        if (y < 0) {
            yOffset = -y;
            y = 0;
            height -= yOffset;
        }
        int y2 = y + height;
        if (y2 > canvas.getHeight()) {
            height = canvas.getHeight() - y;
            y2 = canvas.getHeight();
        }

        if (x2 < 0 || x >= canvas.getWidth() || y2 < 0 || y >= canvas.getHeight()) {
            return;
        }

//        canvas.setRGB(x, y, width, height, rgbData, offset, scanlength);


        for (int dy = 0; dy < height; dy++) {
            int curLineOffset = scanlength * (dy + yOffset) + offset;
            for (int dx = 0; dx < width; dx++) {
                int c = rgbData[curLineOffset + dx + xOffset];
                if (processAlpha && (c & 0xff000000) == 0) {
                    continue;
                }
                if (!processAlpha) {
                    c |= 0xFF000000;
                }
                canvas.setRGB(x + dx, y + dy, c);
            }
        }

    }


    public void drawLine(int x1, int y1, int x2, int y2) {
        gc.drawLine(x1, y1, x2, y2);
    }

    public void drawRect(int x, int y, int width, int height) {
        gc.drawRect(x, y, width, height);
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        gc.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void drawString(String str, int x, int y, int anchor) {
        if (str != null) {
            if (anchor == 0) {
                anchor = javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT;
            }
            x = AnchorX(x, gc.getFontMetrics().stringWidth(str), anchor);
            y = y + gc.getFontMetrics().getAscent() - 1;
            y = AnchorY(y, gc.getFontMetrics().getHeight(), anchor);
            try {
                gc.drawString(str, x, y);
            } catch (Exception e) {
            }
        }
    }

    public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {
        if (str.length() >= offset + len) {
            drawString(str.substring(offset, offset + len), x, y, anchor);
        }
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        gc.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    public void fillRect(int x, int y, int width, int height) {
        gc.fillRect(x, y, width, height);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        gc.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
        //gc.fillRect(x, y, width, height);
    }

    //public int getBlueComponent() { }
    //public Font getFont() { return font; }
    //public int getColor() { return color; }
    //public int getGrayScale() { }
    //public int getGreenComponent() { }
    //public int getRedComponent() { }
    //public int getStrokeStyle() { return strokeStyle; }

    public void setColor(int rgb) {
        setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 0xff);
    }

    public void setColor(int r, int g, int b) {
        setColor(r, g, b, 0xff);
    }

    public void setColor(int r, int g, int b, int a) {
        color = (r << 16) + (g << 8) + b;
        colorAlpha = a;
        awtColor = new Color(r, g, b, a);
        gc.setColor(awtColor);
    }

    public void setFont(Font font) {
        if (font == null) return;
        super.setFont(font);
        gc.setFont(font.platformFont.awtFont);
    }
    //public void setGrayScale(int value)
    //public void setStrokeStyle(int style)

    public void setClip(int x, int y, int width, int height) {
        gc.setClip(x, y, width, height);
        Rectangle rect = cliplv.get();
        gc.getClipBounds(rect);
        clipX = (int) rect.getX();
        clipY = (int) rect.getY();
        clipWidth = (int) rect.getWidth();
        clipHeight = (int) rect.getHeight();
    }

    public void clipRect(int x, int y, int width, int height) {
        gc.clipRect(x, y, width, height);
        Rectangle rect = cliplv.get();
        gc.getClipBounds(rect);
        clipX = (int) rect.getX();
        clipY = (int) rect.getY();
        clipWidth = (int) rect.getWidth();
        clipHeight = (int) rect.getHeight();
    }

    //public int getTranslateX() { }
    //public int getTranslateY() { }

    public synchronized void translate(int x, int y) {
        translateX += x;
        translateY += y;
        gc.translate(x, y);
        Rectangle rect = cliplv.get();
        gc.getClipBounds(rect);
        clipX = (int) rect.getX();
        clipY = (int) rect.getY();
    }

    private int AnchorX(int x, int width, int anchor) {
        int xout = x;
        if ((anchor & HCENTER) > 0) {
            xout = x - (width / 2);
        }
        if ((anchor & RIGHT) > 0) {
            xout = x - width;
        }
        if ((anchor & LEFT) > 0) {
            xout = x;
        }
        return xout;
    }

    private int AnchorY(int y, int height, int anchor) {
        int yout = y;
        if ((anchor & VCENTER) > 0) {
            yout = y - (height / 2);
        }
        if ((anchor & TOP) > 0) {
            yout = y;
        }
        if ((anchor & BOTTOM) > 0) {
            yout = y - height;
        }
        if ((anchor & BASELINE) > 0) {
            yout = y + height;
        }
        return yout;
    }

    public void setAlphaRGB(int ARGB) {
        setARGBColor(ARGB);
    }

	/*
		****************************
			Nokia Direct Graphics
		****************************
	*/
    // http://www.j2megame.org/j2meapi/Nokia_UI_API_1_1/com/nokia/mid/ui/DirectGraphics.html

    private int colorAlpha;

    public int getNativePixelFormat() {
        return DirectGraphics.TYPE_INT_8888_ARGB;
    }

    public int getAlphaComponent() {
        return colorAlpha;
    }

    public void setARGBColor(int argbColor) {
        int a = (argbColor >>> 24) & 0xFF;
        setColor(argbColor & 0xFF, (argbColor >>> 8) & 0xFF, (argbColor >>> 16) & 0xFF, a);
    }

    /**
     * nokia directgraphics.drawImage implementation
     *
     * @param img
     * @param x
     * @param y
     * @param anchor
     * @param manipulation
     */
    public void drawImage(javax.microedition.lcdui.Image img, int x, int y, int anchor, int manipulation) {
        drawImageNokia(img.platformImage.getCanvas(), x, y, anchor, manipulation);
    }

    private void drawImageNokia(BufferedImage bimg, int x, int y, int anchor, int manipulation) {
        PlatformImageTransform pt = PlatformImage.nokiaTransformImage(bimg, manipulation);
        x = AnchorX(x, pt.width, anchor);
        y = AnchorY(y, pt.height, anchor);
        gc.translate(x, y);
        gc.drawImage(bimg, pt.transform, null);
        gc.translate(-x, -y);
    }

    public void drawPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format) {
        //System.out.println("drawPixels A "+format); // Found In Use
        int[] Type1 = {0xFFFFFFFF, 0xFF000000, 0x00FFFFFF, 0x00000000};
        int c = 0;
        int[] data;
        BufferedImage temp;
        PlatformImageTransform pt;
        BufferedImage bi;
        switch (format) {
            case -1: // TYPE_BYTE_1_GRAY_VERTICAL // used by Monkiki's Castles
                data = new int[width * height];
                int ods = offset / scanlength;
                int oms = offset % scanlength;
                int b = ods % 8; //Bit offset in a byte
                for (int yj = 0; yj < height; yj++) {
                    int ypos = yj * width;
                    int tmp = (ods + yj) / 8 * scanlength + oms;
                    for (int xj = 0; xj < width; xj++) {
                        c = ((pixels[tmp + xj] >> b) & 1);
                        if (transparencyMask != null) {
                            c |= (((transparencyMask[tmp + xj] >> b) & 1) ^ 1) << 1;
                        }
                        data[(yj * width) + xj] = Type1[c];
                    }
                    b++;
                    if (b > 7) b = 0;
                }

                temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                temp.setRGB(0, 0, width, height, data, 0, width);
//                pt = PlatformImage.nokiaTransformImage(temp, manipulation);
//                bi = PlatformImage.getTransformedImage(temp, pt);
//                gc.drawImage(bi, x, y, null);
                drawImageNokia(temp, x, y, 0, manipulation);
                break;

            case 1: // TYPE_BYTE_1_GRAY // used by Monkiki's Castles
                data = new int[pixels.length * 8];

                for (int i = (offset / 8); i < pixels.length; i++) {
                    for (int j = 7; j >= 0; j--) {
                        c = ((pixels[i] >> j) & 1);
                        if (transparencyMask != null) {
                            c |= (((transparencyMask[i] >> j) & 1) ^ 1) << 1;
                        }
                        data[(i * 8) + (7 - j)] = Type1[c];
                    }
                }
                temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                temp.setRGB(0, 0, width, height, data, 0, scanlength);
//                pt = PlatformImage.nokiaTransformImage(temp, manipulation);
//                bi = PlatformImage.getTransformedImage(temp, pt);
//                gc.drawImage(bi, x, y, null);
                drawImageNokia(temp, x, y, 0, manipulation);
                break;

            default:
                System.out.println("drawPixels A : Format " + format + " Not Implemented");
        }
    }

    public void drawPixels(int[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format) {
        //System.out.println("drawPixels B "+format+" "+transparency); // Found In Use
        BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        temp.setRGB(0, 0, width, height, pixels, offset, scanlength);
//        PlatformImageTransform pt = PlatformImage.nokiaTransformImage(temp, manipulation);
//        BufferedImage temp2 = PlatformImage.getTransformedImage(temp, pt);
//        gc.drawImage(temp2, x, y, null);
        drawImageNokia(temp, x, y, 0, manipulation);
    }

    public void drawPixels(short[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format) {
        //System.out.println("drawPixels C "+format+" "+transparency); // Found In Use
        int[] data = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            data[i] = pixelToColor(pixels[i], format);
            //if(!transparency) { data[i] &=0x00FFFFFF; } //gust :not same as nokia define
        }

        BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        temp.setRGB(0, 0, width, height, data, offset, scanlength);
//        PlatformImageTransform pt = PlatformImage.nokiaTransformImage(temp, manipulation);
//        BufferedImage temp2 = PlatformImage.getTransformedImage(temp, pt);
//        gc.drawImage(temp2, x, y, null);
        drawImageNokia(temp, x, y, 0, manipulation);
    }

    public void drawPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor) {
        int temp = color;
        int[] x = new int[nPoints];
        int[] y = new int[nPoints];

        setAlphaRGB(argbColor);

        for (int i = 0; i < nPoints; i++) {
            x[i] = xPoints[xOffset + i];
            y[i] = yPoints[yOffset + i];
        }
        gc.drawPolygon(x, y, nPoints);
        setColor(temp);
    }

    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        //System.out.println("drawTriange");
        int temp = color;
        setAlphaRGB(argbColor);
        gc.drawPolygon(new int[]{x1, x2, x3}, new int[]{y1, y2, y3}, 3);
        setColor(temp);
    }

    public void fillPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor) {
        int temp = color;
        int[][] points = polygonPoints.get();
        if (points.length < nPoints) {
            points = new int[2][nPoints];
            polygonPoints.set(points);
        }
        System.arraycopy(xPoints, xOffset, points[0], 0, nPoints);
        System.arraycopy(yPoints, yOffset, points[1], 0, nPoints);

        setAlphaRGB(argbColor);

        gc.fillPolygon(points[0], points[1], nPoints);
        setColor(temp);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        //System.out.println("fillTriangle"); // Found In Use
        int[][] trig = polygonPoints.get();
        trig[0][0] = x1;
        trig[0][1] = x2;
        trig[0][2] = x3;
        trig[1][0] = y1;
        trig[1][1] = y2;
        trig[1][2] = y3;
        gc.fillPolygon(trig[0], trig[1], 3);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        //System.out.println("fillTriangle"); // Found In Use
        int temp = color;
        Color c = gc.getColor();
        setAlphaRGB(argbColor);
        int[][] trig = polygonPoints.get();
        trig[0][0] = x1;
        trig[0][1] = x2;
        trig[0][2] = x3;
        trig[1][0] = y1;
        trig[1][1] = y2;
        trig[1][2] = y3;
        gc.fillPolygon(trig[0], trig[1], 3);
        gc.setColor(c);
        color = temp;
    }

    public void getPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int format) {
        System.out.println("getPixels A");
    }

    public void getPixels(int[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format) {
        //System.out.println("getPixels B");
        canvas.getRGB(x, y, width, height, pixels, offset, scanlength);
    }

    public void getPixels(short[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format) {
        //System.out.println("getPixels C"); // Found In Use
        int i = offset;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                pixels[i] = colorToShortPixel(canvas.getRGB(col + x, row + y), format);
                i++;
            }
        }
    }

    private int pixelToColor(short c, int format) {
        int a = 0xFF;
        int r = 0;
        int g = 0;
        int b = 0;
        switch (format) {
            case DirectGraphics.TYPE_USHORT_1555_ARGB:
                a = ((c >> 15) & 0x01) * 0xFF;
                r = (c >> 10) & 0x1F;
                g = (c >> 5) & 0x1F;
                b = c & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 3) | (g >> 2);
                b = (b << 3) | (b >> 2);
                break;
            case DirectGraphics.TYPE_USHORT_444_RGB:
                r = (c >> 8) & 0xF;
                g = (c >> 4) & 0xF;
                b = c & 0xF;
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
                break;
            case DirectGraphics.TYPE_USHORT_4444_ARGB:
                a = (c >> 12) & 0xF;
                r = (c >> 8) & 0xF;
                g = (c >> 4) & 0xF;
                b = c & 0xF;
                a = (a << 4) | a;
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
                break;
            case DirectGraphics.TYPE_USHORT_555_RGB:
                r = (c >> 10) & 0x1F;
                g = (c >> 5) & 0x1F;
                b = c & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 3) | (g >> 2);
                b = (b << 3) | (b >> 2);
                break;
            case DirectGraphics.TYPE_USHORT_565_RGB:
                r = (c >> 11) & 0x1F;
                g = (c >> 5) & 0x3F;
                b = c & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 2) | (g >> 4);
                b = (b << 3) | (b >> 2);
                break;
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private short colorToShortPixel(int c, int format) {
        int a = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        int out = 0;
        switch (format) {
            case DirectGraphics.TYPE_USHORT_1555_ARGB:
                a = c >>> 31;
                r = ((c >> 19) & 0x1F);
                g = ((c >> 11) & 0x1F);
                b = ((c >> 3) & 0x1F);
                out = (a << 15) | (r << 10) | (g << 5) | b;
                break;
            case DirectGraphics.TYPE_USHORT_444_RGB:
                r = ((c >> 20) & 0xF);
                g = ((c >> 12) & 0xF);
                b = ((c >> 4) & 0xF);
                out = (r << 8) | (g << 4) | b;
                break;
            case DirectGraphics.TYPE_USHORT_4444_ARGB:
                a = ((c >>> 28) & 0xF);
                r = ((c >> 20) & 0xF);
                g = ((c >> 12) & 0xF);
                b = ((c >> 4) & 0xF);
                out = (a << 12) | (r << 8) | (g << 4) | b;
                break;
            case DirectGraphics.TYPE_USHORT_555_RGB:
                r = ((c >> 19) & 0x1F);
                g = ((c >> 11) & 0x1F);
                b = ((c >> 3) & 0x1F);
                out = (r << 10) | (g << 5) | b;
                break;
            case DirectGraphics.TYPE_USHORT_565_RGB:
                r = ((c >> 19) & 0x1F);
                g = ((c >> 10) & 0x3F);
                b = ((c >> 3) & 0x1F);
                out = (r << 11) | (g << 5) | b;
                break;
        }
        return (short) out;
    }

}
