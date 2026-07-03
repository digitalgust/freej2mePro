/*
 * Copyright 2022-2023 Yury Kharchenko (original KEmulator implementation)
 *
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
 * Ported from KEmulator ru.woesss.j2me.micro3d into com.mascotcapsule.micro3d.v3.base.
 *
 * GL-specific code (glGenTextures/glTexImage2D, isMutable-from-Image) was removed:
 *   - GL texture upload is the backend's responsibility (reads image.getRaster()).
 *   - mutable-texture-from-Image path is J2ME-game specific and handled by the
 *     Graphics3D.render-to-texture flow if needed later.
 * emulator.custom.ResourceManager replaced with the local Resources helper.
 */
package com.mascotcapsule.micro3d.v3.base;

import java.io.IOException;

public final class TextureImpl {
	public final TextureData image;
	private final boolean isMutable;

	public TextureImpl() {
		image = new TextureData(256, 256);
		isMutable = true;
	}

	public TextureImpl(byte[] b) {
		if (b == null) {
			throw new NullPointerException();
		}
		try {
			image = Loader.loadBmpData(b, 0, b.length);
		} catch (IOException e) {
			System.err.println("Error loading data");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		isMutable = false;
	}

	public TextureImpl(byte[] b, int offset, int length) throws IOException {
		if (b == null) {
			throw new NullPointerException();
		}
		if (offset < 0 || offset + length > b.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		try {
			image = Loader.loadBmpData(b, offset, length);
		} catch (Exception e) {
			System.err.println("Error loading data");
			e.printStackTrace();
			throw e;
		}
		isMutable = false;
	}

	public TextureImpl(String name) throws IOException {
		if (name == null) {
			throw new NullPointerException();
		}
		byte[] b = Resources.getBytes(name);
		if (b == null) {
			throw new IOException();
		}
		try {
			image = Loader.loadBmpData(b, 0, b.length);
		} catch (IOException e) {
			System.err.println("Error loading data from [" + name + "]");
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		isMutable = false;
	}

	public void dispose() {
	}

	public boolean isMutable() {
		return isMutable;
	}

	public int getWidth() {
		return image.width;
	}

	public int getHeight() {
		return image.height;
	}
}
