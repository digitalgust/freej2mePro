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
 * micro3D v3 rendering backend SPI. Mirrors the role of the m3g
 * Graphics3D.Backend / BackendFactory pair: the engine data layer
 * (Engine / FrameState) is platform-independent and lives in freej2me;
 * concrete backends (software rasterizer here, miniJVM GL renderer in
 * freej2meOnMinijvm) implement this interface to perform the actual
 * pixel output.
 */
package com.mascotcapsule.micro3d.v3.base;

import javax.microedition.lcdui.Graphics;

/**
 * A backend owns the lifetime of one bound target and consumes queued
 * FrameState draws produced by {@link Engine}.
 */
public interface Micro3dBackend {

	/**
	 * Bind a drawing target for the upcoming frame(s).
	 *
	 * @param target   a {@link Graphics} (MIDP 2D backbuffer) or a
	 *                 {@link com.mascotcapsule.micro3d.v3.Texture} (render-to-texture).
	 * @param doClip   whether to honor the Graphics clip rect.
	 */
	void bind(Object target, boolean doClip);

	/**
	 * Submit a fully-built frame: copy the 2D background (if applicable),
	 * then draw the queued items in two passes (opaque + write-depth, then
	 * translucent + no-depth-write).
	 */
	void flushFrame(FrameState frame);

	/**
	 * Flush only the currently queued items (without re-copying the 2D
	 * background) — used by drawFigure / explicit flush paths.
	 */
	void flushItems(FrameState frame);

	/**
	 * Release the bound target and present the rendered result.
	 */
	void release(Object target);

	/** @return true if this backend is available in the current runtime. */
	boolean isAvailable();

	/** @return width (in pixels) of the currently bound target, or <=0 if unbound. */
	int getTargetWidth();

	/** @return height (in pixels) of the currently bound target, or <=0 if unbound. */
	int getTargetHeight();
}
