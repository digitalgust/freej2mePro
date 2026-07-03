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


import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import java.net.URL;
import java.net.URLClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.ArrayList;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.microedition.midlet.*;
import javax.microedition.midlet.MIDletStateChangeException;

public class MIDletLoader extends URLClassLoader {
    public String name;
    public String icon;
    private String className;

    public String suitename;

    private Class<?> mainClass;
    private MIDlet mainMidlet;

    private HashMap<String, String> properties = new HashMap<String, String>(32);
    private final BytecodeInjectionTool bytecodeInjectionTool;

    public static Mobile curMobile;

    public MIDletLoader(URL urls[]) {
        super(urls);
        bytecodeInjectionTool = BytecodeInjectionTool.load(urls);

        try {
            System.setProperty("microedition.platform", "j2me");
            System.setProperty("microedition.profiles", "MIDP-2.0");
            System.setProperty("microedition.configuration", "CLDC-1.0");
            System.setProperty("microedition.locale", "en-US");
            System.setProperty("microedition.encoding", "file.encoding");
        } catch (Exception e) {
            System.out.println("Can't add CLDC System Properties");
        }

        try {
            loadManifest();

            properties.put("microedition.platform", "j2me");
            properties.put("microedition.profiles", "MIDP-2.0");
            properties.put("microedition.configuration", "CLDC-1.0");
            properties.put("microedition.locale", "en-US");
            properties.put("microedition.encoding", "file.encoding");
        } catch (Exception e) {
            System.out.println("Can't Read Manifest!");
            return;
        }

    }

    public synchronized void start(Mobile mobile) throws MIDletStateChangeException {
        Method start;

        try {
            mainClass = loadClass(className, true);

            Constructor constructor;
            constructor = mainClass.getConstructor();
            constructor.setAccessible(true);

            MIDlet.initAppProperties(properties);
            curMobile = mobile;
            mainMidlet = (MIDlet) constructor.newInstance();
        } catch (Exception e) {
            System.out.println("Problem Constructing " + name + " class: " + className);
            System.out.println("Reason: " + e.getMessage());
            e.printStackTrace();
            //System.exit(0);
            return;
        }

        try {
            start = mainClass.getDeclaredMethod("startApp");
            start.setAccessible(true);
        } catch (Exception e) {
            try {
                mainClass = loadClass(mainClass.getSuperclass().getName(), true);
                start = mainClass.getDeclaredMethod("startApp");
                start.setAccessible(true);
            } catch (Exception f) {
                System.out.println("Can't Find startApp Method");
                f.printStackTrace();
                //System.exit(0);
                return;
            }
        }

        try {
            start.invoke(mainMidlet);
        } catch (Exception e) {
            e.printStackTrace();
            //System.exit(0);
        }
    }

    private void loadManifest() {
        String resource = "META-INF/MANIFEST.MF";
        URL url = findResource(resource);
        if (url == null) {
            resource = "meta-inf/MANIFEST.MF";
            url = findResource(resource);
            if (url == null) {
                resource = "META-INF/manifest.mf";
                url = findResource(resource);
                if (url == null) {
                    resource = "meta-inf/manifest.mf";
                    url = findResource(resource);
                    if (url == null) {
                        return;
                    }
                }
            }
        }

        String line;
        String[] parts;
        int split;
        String key;
        String value;
        try {
            InputStream is = url.openStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int ch = is.read();
            while (ch != -1) {
                baos.write(ch);
                ch = is.read();
            }
            String manifest = new String(baos.toByteArray(), "UTF-8");
            String[] strs = manifest.split("\n");

            ArrayList<String> lines = new ArrayList<String>();
            for (int i = 0; i < strs.length; i++) {
                line = strs[i];
                if (line.startsWith(" ")) {
                    line = lines.get(lines.size() - 1).trim() + line.trim();
                    lines.remove(lines.size() - 1);
                }
                lines.add(line);
            }

            for (int i = 0; i < lines.size(); i++) {
                line = lines.get(i);
                if (line.startsWith("MIDlet-1:")) {
                    System.out.println(line);
                    line = line.substring(9);
                    parts = line.split(",");
                    if (parts.length == 3) {
                        name = parts[0].trim();
                        icon = parts[1].trim();
                        className = parts[2].trim();
                        suitename = name;
                    }
                    //System.out.println("Loading " + name);
                }

                split = line.indexOf(":");
                if (split > 0) {
                    key = line.substring(0, split).trim();
                    value = line.substring(split + 1).trim();
                    properties.put(key, value);
                }
            }
            // for RecordStore, remove illegal chars from name
            suitename = suitename.replace(":", "");
        } catch (Exception e) {
            System.out.println("Can't Read Jar Manifest!");
            e.printStackTrace();
        }
    }

    public String getMidletProperty(String key) {
        String s = properties.get(key);
        return s == null ? "" : s;
    }


    public InputStream getResourceAsStream(String resource) {
        URL url;
        //System.out.println("Loading Resource: " + resource);

        if (resource.startsWith("/")) {
            resource = resource.substring(1);
        }

        try {
            url = findResource(resource);
            // Read all bytes, return ByteArrayInputStream //
            InputStream stream = url.openStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int count = 0;
            byte[] data = new byte[4096];
            while (count != -1) {
                count = stream.read(data);
                if (count != -1) {
                    buffer.write(data, 0, count);
                }
            }
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (Exception e) {
            System.out.println(resource + " Not Found");
            return super.getResourceAsStream(resource);
        }
    }


    public URL getResource(String resource) {
        if (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        try {
            URL url = findResource(resource);
            return url;
        } catch (Exception e) {
            System.out.println(resource + " Not Found");
            return super.getResource(resource);
        }
    }

	/*
		********  loadClass Modifies Methods with ObjectWeb ASM  ********
		Replaces java.lang.Class.getResourceAsStream calls with calls
		to Mobile.getResourceAsStream which calls
		MIDletLoader.getResourceAsStream(class, string)
	*/

    public InputStream getMIDletResourceAsStream(String resource) {
        //System.out.println("Get Resource: "+resource);

        URL url = getResource(resource);

        // Read all bytes, return ByteArrayInputStream //
        try {
            InputStream stream = url.openStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int count = 0;
            byte[] data = new byte[4096];
            while (count != -1) {
                count = stream.read(data);
                if (count != -1) {
                    buffer.write(data, 0, count);
                }
            }
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (Exception e) {
            return super.getResourceAsStream(resource);
        }
    }

    public byte[] getMIDletResourceAsByteArray(String resource) {
        URL url = getResource(resource);

        try {
            InputStream stream = url.openStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int count = 0;
            byte[] data = new byte[4096];
            while (count != -1) {
                count = stream.read(data);
                if (count != -1) {
                    buffer.write(data, 0, count);
                }
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            System.out.println(resource + " Not Found");
            return new byte[0];
        }
    }


    public Class loadClass(String name) throws ClassNotFoundException {
        InputStream stream;
        String resource;
        byte[] code;

        //System.out.println("Load Class "+name);

        if (
                name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("com.nokia") ||
                        name.startsWith("com.mascotcapsule") || name.startsWith("com.samsung") || name.startsWith("sun.") ||
                        name.startsWith("com.siemens") || name.startsWith("org.recompile")
        ) {
            return loadClass(name, true);
        }

        try {
            //System.out.println("Instrumenting Class "+name);
            resource = name.replace(".", "/") + ".class";
            stream = super.getResourceAsStream(resource);
            code = instrument(stream);
            return defineClass(name, code, 0, code.length);
        } catch (Exception e) {
            System.out.println("Error Adapting Class " + name);
            return null;
        }

    }


    /* **************************************************************
     * Special Siemens Stuff
     * ************************************************************** */

    public InputStream getMIDletResourceAsSiemensStream(String resource) {
        URL url = getResource(resource);

        try {
            InputStream stream = url.openStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int count = 0;
            byte[] data = new byte[4096];
            while (count != -1) {
                count = stream.read(data);
                if (count != -1) {
                    buffer.write(data, 0, count);
                }
            }
            return new SiemensInputStream(buffer.toByteArray());
        } catch (Exception e) {
            return super.getResourceAsStream(resource);
        }
    }

    private class SiemensInputStream extends InputStream {
        private ByteArrayInputStream iostream;

        public SiemensInputStream(byte[] data) {
            iostream = new ByteArrayInputStream(data);
        }

        public int read() {
            int t = iostream.read();
            if (t == -1) {
                return 0;
            }
            return t;
        }

        public int read(byte[] b, int off, int len) {
            int t = iostream.read(b, off, len);
            if (t == -1) {
                return 0;
            }
            return t;
        }
    }


    /* **************************************************************
     * Instrumentation
     * ************************************************************** */

    private byte[] instrument(InputStream stream) throws Exception {
        if (stream == null) {
            throw new IllegalArgumentException("Class stream is null");
        }
        ClassReader reader = new ClassReader(stream);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ASMVisitor(writer);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private class ASMVisitor extends ClassAdapter {
        private String owner;

        public ASMVisitor(ClassVisitor visitor) {
            super(visitor);
        }

        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
            System.out.println("Instrumenting Class " + name);
            owner = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
            //System.out.println("Instrumenting Method "+name + desc);
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            visitor = bytecodeInjectionTool.wrapMethod(owner, access, name, desc, visitor);
            return new ASMMethodVisitor(visitor);
        }

        private class ASMMethodVisitor extends MethodAdapter implements Opcodes {
            public ASMMethodVisitor(MethodVisitor visitor) {
                super(visitor);
            }

            public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                if (opcode == INVOKEVIRTUAL && name.equals("getResourceAsStream") && owner.equals("java/lang/Class")) {
                    mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/Mobile", name, "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;");
                } else {
                    mv.visitMethodInsn(opcode, owner, name, desc);
                }
            }
        }
    }

    public MIDlet getMainMidlet() {
        return mainMidlet;
    }
}
