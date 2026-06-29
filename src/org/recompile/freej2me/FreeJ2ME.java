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
package org.recompile.freej2me;

/*
	FreeJ2ME - AWT
*/

import org.recompile.mobile.*;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Display;

import static org.recompile.mobile.BytecodeInjectionTool.RULES_PROPERTY;

public class FreeJ2ME extends J2meSandBox {
    public static void main(String args[]) {
        FreeJ2ME app = new FreeJ2ME(args);
        //可以启动多个实例
//        FreeJ2ME app1 = new FreeJ2ME(args);
    }

    private Frame j2meframe;
    private int lcdWidth;
    private int lcdHeight;
    private int scaleFactor = 1;

    private LCD lcd;

    private int xborder;
    private int yborder;

    private PlatformImage img;

    private Config config;
    private boolean useNokiaControls = false;
    private boolean useSiemensControls = false;
    private boolean useMotorolaControls = false;
    private boolean rotateDisplay = false;
    private int limitFPS = 0;
    Mobile mobile;

    static final int MAX_KEYS = 128;
    long[] repeatTime = new long[MAX_KEYS];

    private boolean[] pressedKeys = new boolean[MAX_KEYS];

    /**
     * 通过线程组来获取Mobile对象，这样可以同时运行多个freej2me实例
     * mobile 对象是MIDlet和其他j2me对象之间的桥梁
     */
    static Map<ThreadGroup, Mobile> threadgroup2mobile = Collections.synchronizedMap(new HashMap<>());

    String[] args;

    final java.util.List<Runnable> proxyAwtEvents = Collections.synchronizedList(new ArrayList<>());

    boolean exit = false;
    private Thread eventThread;

    public FreeJ2ME(String args[]) {
        this.args = args;
//        System.setProperty("freej2me.m3g.sprite.skip", "1");
//        System.setProperty("freej2me.m3g.diag.leak", "true");
        //System.setProperty("freej2me.diag.render", "1");
        //System.setProperty("freej2me.diag.tint", "1");
        //System.setProperty(RULES_PROPERTY,"ENTRY_PRINT|q|a|(FFFFFFFFF)V|q.a camera params: {0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}, {8}");
        //System.setProperty(RULES_PROPERTY,"ENTRY_PRINT|k|c|(Ljavax/microedition/lcdui/Graphics;)V|sky draw called");
        //System.setProperty("freej2me.bytecode.injection.rules", "ENTRY_PRINT|o|a|()I|o.a called\nEXIT_PRINT|o|a|()I|o.a ret={ret}");
        ThreadGroup tg = new ThreadGroup("threadgroup-" + this);
        Thread t = new Thread(tg, () -> {//这个线程相当于是一个沙盒，和外部代码隔离，这样就可以同时运行多个freej2me实例
            eventThread = Thread.currentThread();
            openMidlet();
            System.out.println("midlet loaded");

            processEvent();
        }, "thread-eventproxy-" + this);
        t.start();
    }

    public void openMidlet() {

        j2meframe = new Frame("");
        j2meframe.setSize(350, 450);
        //j2meframe.setBackground(new Color(0, 0, 64));
        try {
            j2meframe.setIconImage(ImageIO.read(j2meframe.getClass().getResourceAsStream("/org/recompile/icon.png")));
        } catch (Exception e) {
        }

        j2meframe.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                //交给沙盒线程处理
                addEvent(() -> {
                    if (mobile.getPlatform().loader != null && mobile.getPlatform().loader.getMainMidlet() != null) {
                        mobile.getPlatform().loader.getMainMidlet().notifyDestroyed();
                        exit = true;
                    }
                });
            }

        });

        // Setup Device //

        lcdWidth = 240;
        lcdHeight = 320;

        String jarfile = "";
        if (args.length >= 1) {
            jarfile = args[0];
        }
        String dataPath = "";
        if (args.length >= 2) {
            dataPath = args[1];
        }

        if (args.length >= 4) {
            lcdWidth = Integer.parseInt(args[1]);
            lcdHeight = Integer.parseInt(args[2]);
        }
        if (args.length >= 5) {
            scaleFactor = Integer.parseInt(args[3]);
        }

        mobile = new Mobile(this);
        mobile.setPlatform(new MobilePlatform(lcdWidth, lcdHeight, mobile));
        mobile.getPlatform().dataPath = dataPath;
        bindMobile2Thread(mobile);

        lcd = new LCD();
        lcd.setFocusable(true);
        j2meframe.add(lcd);

        config = new Config(mobile);
        config.onChange = new Runnable() {
            public void run() {
                settingsChanged();
            }
        };

        mobile.getPlatform().setPainter(new Runnable() {
            public void run() {
                lcd.paint(lcd.getGraphics());
            }
        });

        lcd.addKeyListener(new KeyListener() {
            public void keyPressed(KeyEvent e) {
                addEvent(() -> {
                    FreeJ2ME.this.keyPressed(e);
                });
            }

            public void keyReleased(KeyEvent e) {
                addEvent(() -> {
                    FreeJ2ME.this.keyReleased(e);
                });
            }

            public void keyTyped(KeyEvent e) {
            }

        });

        lcd.addMouseListener(new MouseListener() {
            public void mousePressed(MouseEvent e) {
                addEvent(() -> {
                    FreeJ2ME.this.mousePressed(e);
                });
            }

            public void mouseReleased(MouseEvent e) {
                addEvent(() -> {
                    FreeJ2ME.this.mouseReleased(e);
                });
            }

            public void mouseExited(MouseEvent e) {
            }

            public void mouseEntered(MouseEvent e) {
            }

            public void mouseClicked(MouseEvent e) {
            }

        });

        lcd.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                addEvent(() -> {
                    FreeJ2ME.this.mouseDragged(e);
                });
            }
        });

        j2meframe.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                addEvent(() -> {
                    resize();
                });
            }
        });

        j2meframe.setVisible(true);
        j2meframe.pack();

        resize();
        j2meframe.setSize(lcdWidth * scaleFactor + xborder, lcdHeight * scaleFactor + yborder);

        if (args.length < 1) {
            FileDialog t = new FileDialog(j2meframe, "Open JAR File", FileDialog.LOAD);
            t.setFilenameFilter(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".jar");
                }
            });
            t.setVisible(true);
            jarfile = new File(t.getDirectory() + File.separator + t.getFile()).toURI().toString();
        }
        if (mobile.getPlatform().loadJar(jarfile)) {
            config.init();

            /* Allows FreeJ2ME to set the width and height passed as cmd arguments. */
            if (args.length >= 3) {
                lcdWidth = Integer.parseInt(args[1]);
                lcdHeight = Integer.parseInt(args[2]);
                config.settings.put("width", "" + lcdWidth);
                config.settings.put("height", "" + lcdHeight);
            }

            settingsChanged();

            j2meframe.setTitle(mobile.getPlatform().loader.getMidletProperty("MIDlet-Name"));
            mobile.getPlatform().runJar();
        } else {
            System.out.println("Couldn't load jar...");
        }
    }

    public Frame getJ2meframe() {
        return j2meframe;
    }

    public static Mobile getMobile() {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        Mobile m = threadgroup2mobile.get(tg);
        if (m == null) {
            int debug = 1;
            System.out.println("Mobile not found in threadgroup: " + tg);
        }
        return m;
    }

    public static void bindMobile2Thread(Mobile m) {
        threadgroup2mobile.put(Thread.currentThread().getThreadGroup(), m);
    }


    public boolean isEventThread() {
        return Thread.currentThread() == eventThread;
    }

    public void requestRepaint() {
        synchronized (proxyAwtEvents) {
            proxyAwtEvents.notify();
        }
    }

    public void addEvent(Runnable runnable) {
        synchronized (proxyAwtEvents) {
            proxyAwtEvents.add(runnable);
            proxyAwtEvents.notify();
        }
    }

    /**
     * 处理事件
     */
    void processEvent() {
        while (!exit) {
            try {
                Display display = getMobile().getDisplay();
                if (display != null && display.getCurrent() instanceof javax.microedition.lcdui.Canvas) {
                    javax.microedition.lcdui.Canvas canvas = (javax.microedition.lcdui.Canvas) display.getCurrent();
                    while (canvas.drainPendingRepaint()) {
                        // Drain all queued repaints on the event thread to keep Graphics3D lifecycle serialized.
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (proxyAwtEvents.isEmpty()) {
                synchronized (proxyAwtEvents) {
                    try {
                        proxyAwtEvents.wait(30);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                try {
                    Runnable e = proxyAwtEvents.remove(0);
                    e.run();
                    genKeyRepeat();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        if (!System.getProperty("java.vendor", "").contains("minijvm")) {
            System.exit(0);
        }
    }

    /*
     * Generate key repeats for keys that are held down.
     * this is for android and ios
     */
    private void genKeyRepeat() {
        for (int i = 0; i < pressedKeys.length; i++) {
            int mobikey = i - 64;

            if (config.isRunning) {
            } else {
                if (pressedKeys[i]) {
                    long curt = System.currentTimeMillis();
                    if (curt - repeatTime[i] > 100) {
                        repeatTime[i] = curt;
                        mobile.getPlatform().keyRepeated(mobikey);
                        //System.out.println("keyRepeated:  " + Integer.toString(mobikey));
                    }
                }
            }
        }
    }

    public void keyPressed(KeyEvent e) {
        //指定事件线程的调用者，为当前的mobile上下文

        int keycode = e.getKeyCode();
        int mobikey = getMobileKey(keycode, true);
        int mobikeyN = (mobikey + 64) & 0x7F; //Normalized value for indexing the pressedKeys array

        switch (keycode) // Handle emulator control keys
        {
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_ADD:
                scaleFactor++;
                j2meframe.setSize(lcdWidth * scaleFactor + xborder, lcdHeight * scaleFactor + yborder);
                break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
                if (scaleFactor > 1) {
                    scaleFactor--;
                    j2meframe.setSize(lcdWidth * scaleFactor + xborder, lcdHeight * scaleFactor + yborder);
                }
                break;
            case KeyEvent.VK_C:
                if (e.isControlDown()) {
                    ScreenShot.takeScreenshot(false, mobile);
                }
                break;
        }

        if (mobikey == 0) //Ignore events from keys not mapped to a phone keypad key
        {
            return;
        }

        repeatTime[mobikeyN] = System.currentTimeMillis();
        //System.out.println("keyPressed:  " + Integer.toString(mobikey));
        if (config.isRunning) {
            config.keyPressed(mobikey);
        } else {
            if (pressedKeys[mobikeyN] == false) {
                //~ System.out.println("keyPressed:  " + Integer.toString(mobikey));
                mobile.getPlatform().keyPressed(mobikey);
            } else {
                //~ System.out.println("keyRepeated:  " + Integer.toString(mobikey));
                mobile.getPlatform().keyRepeated(mobikey);
            }
        }
        pressedKeys[mobikeyN] = true;
    }

    public void keyReleased(KeyEvent e) {

        int mobikey = getMobileKey(e.getKeyCode(), false);
        int mobikeyN = (mobikey + 64) & 0x7F; //Normalized value for indexing the pressedKeys array

        if (mobikey == 0) //Ignore events from keys not mapped to a phone keypad key
        {
            return;
        }

        pressedKeys[mobikeyN] = false;
        repeatTime[mobikeyN] = 0;
        //System.out.println("keyReleased:  " + Integer.toString(mobikey));
        if (config.isRunning) {
            config.keyReleased(mobikey);
        } else {
            //~ System.out.println("keyReleased: " + Integer.toString(mobikey));
            mobile.getPlatform().keyReleased(mobikey);
        }
        //lcd.repaint();  //gust 导致闪屏
    }

    public void mousePressed(MouseEvent e) {

        int x = (int) ((e.getX() - lcd.cx) * lcd.scalex);
        int y = (int) ((e.getY() - lcd.cy) * lcd.scaley);

        // Adjust the pointer coords if the screen is rotated, same for mouseReleased
        if (rotateDisplay) {
            x = (int) ((lcd.ch - (e.getY() - lcd.cy)) * lcd.scaley);
            y = (int) ((e.getX() - lcd.cx) * lcd.scalex);
        }

        if (config.isRunning) {
            config.mousePressed(x, y);
        } else {
            mobile.getPlatform().pointerPressed(x, y);
        }
    }

    public void mouseReleased(MouseEvent e) {

        int x = (int) ((e.getX() - lcd.cx) * lcd.scalex);
        int y = (int) ((e.getY() - lcd.cy) * lcd.scaley);

        if (rotateDisplay) {
            x = (int) ((lcd.ch - (e.getY() - lcd.cy)) * lcd.scaley);
            y = (int) ((e.getX() - lcd.cx) * lcd.scalex);
        }

        if (config.isRunning) {
            config.mouseReleased(x, y);
        } else {
            mobile.getPlatform().pointerReleased(x, y);
        }
        //lcd.repaint(); //gust 导致闪屏
    }

    public void mouseDragged(MouseEvent e) {

        int x = (int) ((e.getX() - lcd.cx) * lcd.scalex);
        int y = (int) ((e.getY() - lcd.cy) * lcd.scaley);

        if (rotateDisplay) {
            x = (int) ((lcd.ch - (e.getY() - lcd.cy)) * lcd.scaley);
            y = (int) ((e.getX() - lcd.cx) * lcd.scalex);
        }

        mobile.getPlatform().pointerDragged(x, y);
    }

    private void settingsChanged() {
        int w = 240;
        try {
            w = Integer.parseInt(config.settings.get("width"));
        } catch (Exception e) {
        }
        int h = 320;
        try {
            h = Integer.parseInt(config.settings.get("height"));
        } catch (Exception e) {
        }

        try {
            limitFPS = Integer.parseInt(config.settings.get("fps"));
        } catch (Exception e) {
        }
        if (limitFPS > 0) {
            limitFPS = 1000 / limitFPS;
        }

        String sound = config.settings.get("sound");
        Mobile.sound = false;
        if ("on".equals(sound)) {
            Mobile.sound = true;
        }

        String phone = config.settings.get("phone");
        useNokiaControls = false;
        useSiemensControls = false;
        useMotorolaControls = false;
        Mobile.nokia = false;
        Mobile.siemens = false;
        Mobile.motorola = false;
        if ("Nokia".equals(phone)) {
            Mobile.nokia = true;
            useNokiaControls = true;
        }
        if ("Siemens".equals(phone)) {
            Mobile.siemens = true;
            useSiemensControls = true;
        }
        if ("Motorola".equals(phone)) {
            Mobile.motorola = true;
            useMotorolaControls = true;
        }

        String rotate = config.settings.get("rotate");
        if ("on".equals(rotate)) {
            rotateDisplay = true;
        }
        if ("off".equals(rotate)) {
            rotateDisplay = false;
        }

        // Create a standard size LCD if not rotated, else invert window's width and height.
        if (!rotateDisplay) {
            lcdWidth = w;
            lcdHeight = h;

            mobile.getPlatform().resizeLCD(w, h);

            resize();
            j2meframe.setSize(lcdWidth * scaleFactor + xborder, lcdHeight * scaleFactor + yborder);
        } else {
            lcdWidth = h;
            lcdHeight = w;

            mobile.getPlatform().resizeLCD(w, h);

            resize();
            j2meframe.setSize(lcdWidth * scaleFactor + xborder, lcdHeight * scaleFactor + yborder);
        }
    }

    private int getMobileKey(int keycode, boolean isKeyPressed) {
        if (useNokiaControls) {
            switch (keycode) {
                case KeyEvent.VK_UP:
                    return Mobile.NOKIA_UP;
                case KeyEvent.VK_DOWN:
                    return Mobile.NOKIA_DOWN;
                case KeyEvent.VK_LEFT:
                    return Mobile.NOKIA_LEFT;
                case KeyEvent.VK_RIGHT:
                    return Mobile.NOKIA_RIGHT;
                case KeyEvent.VK_ENTER:
                    return Mobile.NOKIA_SOFT3;
            }
        }

        if (useSiemensControls) {
            switch (keycode) {
                case KeyEvent.VK_UP:
                    return Mobile.SIEMENS_UP;
                case KeyEvent.VK_DOWN:
                    return Mobile.SIEMENS_DOWN;
                case KeyEvent.VK_LEFT:
                    return Mobile.SIEMENS_LEFT;
                case KeyEvent.VK_RIGHT:
                    return Mobile.SIEMENS_RIGHT;
                case KeyEvent.VK_F1:
                case KeyEvent.VK_Q:
                    return Mobile.SIEMENS_SOFT1;
                case KeyEvent.VK_F2:
                case KeyEvent.VK_W:
                    return Mobile.SIEMENS_SOFT2;
                case KeyEvent.VK_ENTER:
                    return Mobile.SIEMENS_FIRE;
            }
        }

        if (useMotorolaControls) {
            switch (keycode) {
                case KeyEvent.VK_UP:
                    return Mobile.MOTOROLA_UP;
                case KeyEvent.VK_DOWN:
                    return Mobile.MOTOROLA_DOWN;
                case KeyEvent.VK_LEFT:
                    return Mobile.MOTOROLA_LEFT;
                case KeyEvent.VK_RIGHT:
                    return Mobile.MOTOROLA_RIGHT;
                case KeyEvent.VK_F1:
                case KeyEvent.VK_Q:
                    return Mobile.MOTOROLA_SOFT1;
                case KeyEvent.VK_F2:
                case KeyEvent.VK_W:
                    return Mobile.MOTOROLA_SOFT2;
                case KeyEvent.VK_ENTER:
                    return Mobile.MOTOROLA_FIRE;
            }
        }

        switch (keycode) {
            case KeyEvent.VK_0:
                return Mobile.KEY_NUM0;
            case KeyEvent.VK_1:
                return Mobile.KEY_NUM1;
            case KeyEvent.VK_2:
                return Mobile.KEY_NUM2;
            case KeyEvent.VK_3:
                return Mobile.KEY_NUM3;
            case KeyEvent.VK_4:
                return Mobile.KEY_NUM4;
            case KeyEvent.VK_5:
                return Mobile.KEY_NUM5;
            case KeyEvent.VK_6:
                return Mobile.KEY_NUM6;
            case KeyEvent.VK_7:
                return Mobile.KEY_NUM7;
            case KeyEvent.VK_8:
                return Mobile.KEY_NUM8;
            case KeyEvent.VK_9:
                return Mobile.KEY_NUM9;
            case KeyEvent.VK_ASTERISK:
                return Mobile.KEY_STAR;
            case KeyEvent.VK_NUMBER_SIGN:
                return Mobile.KEY_POUND;

            case KeyEvent.VK_NUMPAD0:
                return Mobile.KEY_NUM0;
            case KeyEvent.VK_NUMPAD7:
                return Mobile.KEY_NUM1;
            case KeyEvent.VK_NUMPAD8:
                return Mobile.KEY_NUM2;
            case KeyEvent.VK_NUMPAD9:
                return Mobile.KEY_NUM3;
            case KeyEvent.VK_NUMPAD4:
                return Mobile.KEY_NUM4;
            case KeyEvent.VK_NUMPAD5:
                return Mobile.KEY_NUM5;
            case KeyEvent.VK_NUMPAD6:
                return Mobile.KEY_NUM6;
            case KeyEvent.VK_NUMPAD1:
                return Mobile.KEY_NUM7;
            case KeyEvent.VK_NUMPAD2:
                return Mobile.KEY_NUM8;
            case KeyEvent.VK_NUMPAD3:
                return Mobile.KEY_NUM9;

            case KeyEvent.VK_UP:
                return Mobile.KEY_NUM2;
            case KeyEvent.VK_DOWN:
                return Mobile.KEY_NUM8;
            case KeyEvent.VK_LEFT:
                return Mobile.KEY_NUM4;
            case KeyEvent.VK_RIGHT:
                return Mobile.KEY_NUM6;

            case KeyEvent.VK_ENTER:
                return Mobile.KEY_NUM5;

            case KeyEvent.VK_F1:
            case KeyEvent.VK_Q:
                return Mobile.NOKIA_SOFT1;
            case KeyEvent.VK_F2:
            case KeyEvent.VK_W:
                return Mobile.NOKIA_SOFT2;
            case KeyEvent.VK_E:
                return Mobile.KEY_STAR;
            case KeyEvent.VK_R:
                return Mobile.KEY_POUND;

            case KeyEvent.VK_A:
                return -1;
            case KeyEvent.VK_Z:
                return -2;

            // Config //
            case KeyEvent.VK_ESCAPE:
                if (!isKeyPressed) {
                    if (config.isRunning) {
                        config.stop();
                    } else {
                        config.start();
                    }
                }
        }
        return 0;
    }

    private void resize() {
        xborder = j2meframe.getInsets().left + j2meframe.getInsets().right;
        yborder = j2meframe.getInsets().top + j2meframe.getInsets().bottom;

        double vw = (j2meframe.getWidth() - xborder) * 1;
        double vh = (j2meframe.getHeight() - yborder) * 1;

        double nw = lcdWidth;
        double nh = lcdHeight;

        nw = vw;
        nh = nw * ((double) lcdHeight / (double) lcdWidth);

        if (nh > vh) {
            nh = vh;
            nw = nh * ((double) lcdWidth / (double) lcdHeight);
        }

        lcd.updateScale((int) nw, (int) nh);
    }

    private class LCD extends java.awt.Canvas {
        public int cx = 0;
        public int cy = 0;
        public int cw = 240;
        public int ch = 320;

        public double scalex = 1;
        public double scaley = 1;

        public void updateScale(int vw, int vh) {
            cx = (this.getWidth() - vw) / 2;
            cy = (this.getHeight() - vh) / 2;
            cw = vw;
            ch = vh;
            scalex = (double) lcdWidth / (double) vw;
            scaley = (double) lcdHeight / (double) vh;
        }

        public void paint(Graphics g) {
            try {
                Graphics2D cgc = (Graphics2D) this.getGraphics();
                if (config.isRunning) {
                    if (!rotateDisplay) {
                        g.drawImage(config.getLCD(), cx, cy, cw, ch, null);
                    } else {
                        // If rotated, simply redraw the config menu with different width and height
                        g.drawImage(config.getLCD(), cy, cx, cw, ch, null);
                    }
                } else {
                    if (!rotateDisplay) {
                        g.drawImage(mobile.getPlatform().getLCD(), cx, cy, cw, ch, null);
                    } else {
                        // Rotate the FB 90 degrees counterclockwise with an adjusted pivot
                        cgc.rotate(Math.toRadians(-90), ch / 2, ch / 2);
                        // Draw the rotated FB with adjusted cy and cx values
                        cgc.drawImage(mobile.getPlatform().getLCD(), 0, cx, ch, cw, null);
                    }

                    if (limitFPS > 0) {
                        Thread.sleep(limitFPS);
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void notifyDestroy() {
        exit = true;
        if (j2meframe != null) {
            j2meframe.setVisible(false);
            j2meframe.dispose();
        }
    }
}
