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

import java.awt.*;
import java.awt.event.*;
import java.net.URL;

import java.awt.event.KeyEvent;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Graphics3D;

import java.awt.image.BufferedImage;


/*

	Mobile Platform

*/

public class MobilePlatform {

    private PlatformImage lcd;
    private PlatformGraphics gc;
    public int lcdWidth;
    public int lcdHeight;

    public MIDletLoader loader;

    public Runnable painter;

    public String dataPath = "";

    public int keyState = 0;

    java.awt.Frame inputFrame;

    Mobile mobile;

    public MobilePlatform(int width, int height, Mobile mobile) {
        this.mobile = mobile;
        lcdWidth = width;
        lcdHeight = height;

        lcd = new PlatformImage(width, height);
        gc = lcd.getGraphics();

        mobile.setGraphics3D(new Graphics3D());

        painter = new Runnable() {
            public void run() {
                // Placeholder //
            }
        };
    }

    public void resizeLCD(int width, int height) {
        lcdWidth = width;
        lcdHeight = height;

        lcd = new PlatformImage(width, height);
        gc = lcd.getGraphics();
    }

    public BufferedImage getLCD() {
        return lcd.getCanvas();
    }

    public void setPainter(Runnable r) {
        painter = r;
    }

    public void keyPressed(int keycode) {
        updateKeyState(keycode, 1);
        mobile.getDisplay().getCurrent().keyPressed(keycode);
    }

    public void keyReleased(int keycode) {
        updateKeyState(keycode, 0);
        mobile.getDisplay().getCurrent().keyReleased(keycode);
    }

    public void keyRepeated(int keycode) {
        mobile.getDisplay().getCurrent().keyRepeated(keycode);
    }

    public void pointerDragged(int x, int y) {
        if (mobile.getDisplay() != null && mobile.getDisplay().getCurrent() != null) {
            mobile.getDisplay().getCurrent().pointerDragged(x, y);
        }
    }

    public void pointerPressed(int x, int y) {
        if (mobile.getDisplay() != null && mobile.getDisplay().getCurrent() != null) {
            mobile.getDisplay().getCurrent().pointerPressed(x, y);
        }
    }

    public void pointerReleased(int x, int y) {
        if (mobile.getDisplay() != null && mobile.getDisplay().getCurrent() != null) {
            mobile.getDisplay().getCurrent().pointerReleased(x, y);
        }
    }

    private void updateKeyState(int key, int val) {
        int mask = 0;
        switch (key) {
            case Mobile.KEY_NUM2:
                mask = GameCanvas.UP_PRESSED;
                break;
            case Mobile.KEY_NUM4:
                mask = GameCanvas.LEFT_PRESSED;
                break;
            case Mobile.KEY_NUM6:
                mask = GameCanvas.RIGHT_PRESSED;
                break;
            case Mobile.KEY_NUM8:
                mask = GameCanvas.DOWN_PRESSED;
                break;
            case Mobile.KEY_NUM5:
                mask = GameCanvas.FIRE_PRESSED;
                break;
            case Mobile.KEY_NUM1:
                mask = GameCanvas.GAME_A_PRESSED;
                break;
            case Mobile.KEY_NUM3:
                mask = GameCanvas.GAME_B_PRESSED;
                break;
            case Mobile.KEY_NUM7:
                mask = GameCanvas.GAME_C_PRESSED;
                break;
            case Mobile.KEY_NUM9:
                mask = GameCanvas.GAME_D_PRESSED;
                break;
            case Mobile.NOKIA_UP:
                mask = GameCanvas.UP_PRESSED;
                break;
            case Mobile.NOKIA_LEFT:
                mask = GameCanvas.LEFT_PRESSED;
                break;
            case Mobile.NOKIA_RIGHT:
                mask = GameCanvas.RIGHT_PRESSED;
                break;
            case Mobile.NOKIA_DOWN:
                mask = GameCanvas.DOWN_PRESSED;
                break;
        }
        keyState |= mask;
        keyState ^= mask;
        if (val == 1) {
            keyState |= mask;
        }
    }

    /*
     ******** Jar Loading ********
     */

    public boolean loadJar(String jarurl) {
        try {
            URL jar = new URL(jarurl);
            loader = new MIDletLoader(new URL[]{jar});
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return false;
        }

    }

    public void runJar() {
        try {
            loader.start(mobile);
        } catch (Exception e) {
            System.out.println("Error Running Jar");
            e.printStackTrace();
        }
    }

    /*
     ********* Graphics ********
     */

//	public void flushGraphics(Image img, int x, int y, int width, int height)
//	{
//		gc.flushGraphics(img, x, y, width, height);
//
//		painter.run();//画到窗口画布上
//
//		//System.gc();
//	}

    public void flushGraphics(Image img, int x, int y, int width, int height) {
        gc.flushGraphics(img, x, y, width, height);
        painter.run();//画到窗口画布上
        //System.gc();
    }

    public Frame getInputFrame() {
        return inputFrame;
    }

    public void openInputFrame(javax.microedition.lcdui.TextField textField, javax.microedition.lcdui.TextBox textBox, String text) {
        if (textBox == null && textField == null) return;
        if (inputFrame != null) {
            return;
            //inputFrame.dispose();
        }
        Frame frame = new Frame();
        inputFrame = frame;
        frame.setPreferredSize(new Dimension(240, 200));
        frame.setLayout(null);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.pack();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                mobile.getJ2meSandBox().addEvent(() -> {
                    frame.setVisible(false);
                    frame.dispose();
                    inputFrame = null;
                });
            }
        });
        String title = textBox == null ? textField.getLabel() : textBox.getTitle();
        frame.setTitle(title);
        frame.setName("J2ME_INPUT_FRAME");

        Insets insets = frame.getInsets();
        int panW = frame.getWidth() - insets.left - insets.right;
        int panH = frame.getHeight() - insets.top - insets.bottom;
        java.awt.Panel panel = new Panel();
        panel.setLayout(null);
        panel.setLocation(insets.left, insets.top);
        panel.setSize(panW, panH);
        frame.add(panel);
        java.awt.TextArea awtBox = new TextArea(text);
        java.awt.TextField awtField = new java.awt.TextField(text);
        final int COMP_H = 40;
        java.awt.Label lab = new Label("Input text:");
        panel.add(lab);
        lab.setBounds(0, 0, panW, COMP_H);
        if (textBox != null) {
            panel.add(awtBox);
            awtBox.setBounds(0, COMP_H, panW, panH - COMP_H * 2);
            awtBox.requestFocus();
            awtBox.setCaretPosition(awtBox.getText().length());
        } else {
            panel.add(awtField);
            awtField.setBounds(0, (panH - COMP_H * 2) / 2, panW, COMP_H);
            awtField.requestFocus();
            awtField.setCaretPosition(awtField.getText().length());
        }

        java.awt.Button okBtn = new Button("Ok(F1)");
        panel.add(okBtn);
        okBtn.setBounds(panW / 2, panH - COMP_H, panW / 2, COMP_H - 4);
        java.awt.Button cancelBtn = new Button("Cancel(F2)");
        panel.add(cancelBtn);
        cancelBtn.setBounds(0, panH - COMP_H, panW / 2, COMP_H - 4);
        ;
        InputFrameEventResponder frameResp = new InputFrameEventResponder(frame, textBox, textField, awtBox, awtField, okBtn, cancelBtn);
        okBtn.addActionListener(frameResp);
        cancelBtn.addActionListener(frameResp);
        //okBtn响应回车键
        okBtn.addKeyListener(frameResp);
        awtBox.addKeyListener(frameResp);
        awtField.addKeyListener(frameResp);
        frame.addKeyListener(frameResp);
        cancelBtn.addKeyListener(frameResp);
    }

    class InputFrameEventResponder extends KeyAdapter implements ActionListener {
        java.awt.Frame frame;
        javax.microedition.lcdui.TextBox textBox;
        javax.microedition.lcdui.TextField textField;
        java.awt.TextArea awtBox;
        java.awt.TextField awtField;
        java.awt.Button okBtn;
        java.awt.Button cancelBtn;

        InputFrameEventResponder(java.awt.Frame frame, javax.microedition.lcdui.TextBox textBox, javax.microedition.lcdui.TextField textField, java.awt.TextArea awtBox, java.awt.TextField awtField, java.awt.Button okBtn, java.awt.Button cancelBtn) {
            this.frame = frame;
            this.textBox = textBox;
            this.textField = textField;
            this.awtBox = awtBox;
            this.awtField = awtField;
            this.okBtn = okBtn;
            this.cancelBtn = cancelBtn;

        }

        void closeWindow() {
            frame.setVisible(false);
            frame.dispose();
            inputFrame = null;
        }

        void action() {
            if (textBox != null) {
                String s = awtBox.getText();
                textBox.setString(s);
                closeWindow();
            } else if (textField != null) {
                String s = awtField.getText();
                textField.setString(s);
                closeWindow();
            }
            mobile.getDisplay().getCurrent().render();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            mobile.getJ2meSandBox().addEvent(new Runnable() {
                @Override
                public void run() {
                    if (e.getSource() == okBtn) {
                        action();
                    } else if (e.getSource() == cancelBtn) {
                        closeWindow();
                    }
                }
            });

        }

        @Override
        public void keyReleased(KeyEvent e) {
            mobile.getJ2meSandBox().addEvent(() -> {
                if (e.getKeyCode() == KeyEvent.VK_F1) {
                    action();
                } else if (e.getKeyCode() == KeyEvent.VK_F2) {
                    closeWindow();
                }
            });
        }
    }

}
