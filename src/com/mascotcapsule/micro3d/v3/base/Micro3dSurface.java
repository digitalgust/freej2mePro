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
 * Pixel-access abstraction for the micro3D v3 software rasterizer. Decouples the
 * rasterizer from the concrete render target, mirroring the role of the m3g
 * RenderSurface but fully self-contained (no javax.microedition.m3g dependency).
 *
 * Colors are 0xAARRGGBB ints everywhere. The Graphics-backed surface treats the
 * destination as opaque (alpha forced to 0xFF), matching MIDP 2D target semantics.
 */
package com.mascotcapsule.micro3d.v3.base;

import java.awt.image.BufferedImage;

public interface Micro3dSurface {
	int getWidth();
	int getHeight();
	int getPixel(int x, int y);
	void setPixel(int x, int y, int argb);

	/** Surface backed by a standard AWT BufferedImage (the MIDP Graphics backbuffer). */
	final class BufferedImageSurface implements Micro3dSurface {
		private final BufferedImage image;

		public BufferedImageSurface(BufferedImage image) {
			this.image = image;
		}

		public BufferedImage getImage() {
			return image;
		}

		@Override public int getWidth() { return image.getWidth(); }
		@Override public int getHeight() { return image.getHeight(); }

		@Override
		public int getPixel(int x, int y) {
			return image.getRGB(x, y);
		}

		@Override
		public void setPixel(int x, int y, int argb) {
			// MIDP Graphics target is opaque: drop incoming alpha.
			image.setRGB(x, y, argb | 0xFF000000);
		}
	}
}
