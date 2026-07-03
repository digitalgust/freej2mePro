/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Internal helper for the ported micro3D engine: replaces KEmulator's
 * emulator.custom.ResourceManager.getBytes(name) with freej2me's MIDlet
 * resource loading (Mobile.getMIDletResourceAsStream), then classpath/URI/file
 * fallbacks consistent with javax.microedition.m3g.Loader.
 */
package com.mascotcapsule.micro3d.v3.base;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.recompile.mobile.Mobile;

public final class Resources {
	private Resources() {}

	/**
	 * Reads a named resource fully into a byte[]. Returns null if not found.
	 * Lookup order mirrors freej2me m3g Loader: MIDlet jar, classpath, URI, file.
	 */
	public static byte[] getBytes(String name) {
		InputStream stream = openResourceStream(name);
		if (stream == null) {
			return null;
		}
		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] data = new byte[4096];
			int count;
			while ((count = stream.read(data)) != -1) {
				buffer.write(data, 0, count);
			}
			byte[] bytes = buffer.toByteArray();
			if (bytes.length == 0) {
				return null;
			}
			return bytes;
		} catch (IOException e) {
			return null;
		} finally {
			try {
				stream.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static InputStream openResourceStream(String name) {
		// 1. MIDlet resource (jar)
		try {
			InputStream stream = Mobile.getMIDletResourceAsStream(name);
			if (stream != null) {
				return stream;
			}
		} catch (Throwable ignored) {
		}
		// 2. classpath
		String normalized = name.startsWith("/") ? name : "/" + name;
		InputStream classpathStream = Resources.class.getResourceAsStream(normalized);
		if (classpathStream != null) {
			return classpathStream;
		}
		// 3. URI
		if (hasUriScheme(name)) {
			try {
				return new java.net.URL(name).openStream();
			} catch (IOException ignored) {
			}
		}
		// 4. absolute file path
		try {
			return new java.io.FileInputStream(name);
		} catch (IOException ignored) {
		}
		return null;
	}

	private static boolean hasUriScheme(String name) {
		int colon = name.indexOf(':');
		if (colon <= 0) {
			return false;
		}
		for (int i = 0; i < colon; i++) {
			char c = name.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.')) {
				return false;
			}
		}
		return true;
	}
}
