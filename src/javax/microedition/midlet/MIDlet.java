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
package javax.microedition.midlet;

import org.recompile.freej2me.FreeJ2ME;

import java.lang.reflect.Method;
import java.util.HashMap;

import javax.microedition.lcdui.*;

public abstract class MIDlet {

    public static HashMap<String, String> properties;

    private Display display = new Display();
    private boolean destroyed;

    protected MIDlet() {
        System.out.println("Create MIDlet");
    }


    public final int checkPermission(String permission) {
        // 0 - denied; 1 - allowed; -1 unknown
        System.out.println("checkPermission: " + permission);
        return -1;
    }

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public String getAppProperty(String key) {
        return properties.get(key);
    }

    public static void initAppProperties(HashMap<String, String> initProperties) {
        properties = initProperties;
    }

    public final void notifyDestroyed() {
        synchronized (this) {
            if (destroyed) {
                return;
            }
            destroyed = true;
        }
        System.out.println("MIDlet sent Destroyed Notification");
        shutdownPlatformMidi();
        //System.exit(0);
        FreeJ2ME.getMobile().notifyDestroy();
        Thread currentThread = Thread.currentThread();
        ThreadGroup tg = currentThread.getThreadGroup();
        int active = tg.activeCount();
        Thread[] threads = new Thread[active];
        int count = tg.enumerate(threads);
        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            if (t == null || t == currentThread) continue;
            System.out.println("Thread interrupted " + t);
            t.stop();//minijvm do it safe
        }
        // notifyDestroyed may run inside a repeating TimerTask. Stop this
        // caller last so its TimerThread cannot survive its own shutdown.
        currentThread.stop();//minijvm do it safe
    }

    private static void shutdownPlatformMidi() {
        try {
            Class<?> midiSystem = Class.forName("javax.sound.midi.MidiSystem");
            Method shutdown = midiSystem.getMethod("shutdownCurrentMidlet");
            shutdown.invoke(null);
        } catch (Throwable ignored) {
            // Standard Java SE MidiSystem has no MIDlet-scoped shutdown hook.
        }
    }

    public final void notifyPaused() {
    }

    protected abstract void pauseApp();

    public final boolean platformRequest(String URL) {
        return false;
    }

    public final void resumeRequest() {
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    public Display getDisplay() {
        return display;
    }

}
