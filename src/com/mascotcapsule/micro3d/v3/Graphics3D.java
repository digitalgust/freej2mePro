/*
 * Copyright 2020 Yury Kharchenko (original KEmulator Graphics3D)
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
 * Ported from KEmulator micro3d_gl into freej2me. Standard API Graphics3D:
 * owns the Engine (state machine) and a Micro3dBackend (pixel output). The
 * backend is chosen via a factory: freej2me+J2SE uses the software rasterizer;
 * freej2meOnMinijvm reflects in a GL factory and falls back to software.
 */
package com.mascotcapsule.micro3d.v3;

import com.mascotcapsule.micro3d.v3.base.Engine;
import com.mascotcapsule.micro3d.v3.base.Micro3dBackend;

import javax.microedition.lcdui.Graphics;

@SuppressWarnings("unused")
public class Graphics3D {
	public static final int COMMAND_AFFINE_INDEX = 0x87000000;
	public static final int COMMAND_AMBIENT_LIGHT = 0xA0000000;
	public static final int COMMAND_ATTRIBUTE = 0x83000000;
	public static final int COMMAND_CENTER = 0x85000000;
	public static final int COMMAND_CLIP = 0x84000000;
	public static final int COMMAND_DIRECTION_LIGHT = 0xA1000000;
	public static final int COMMAND_END = 0x80000000;
	public static final int COMMAND_FLUSH = 0x82000000;
	public static final int COMMAND_LIST_VERSION_1_0 = 0xFE000001;
	public static final int COMMAND_NOP = 0x81000000;
	public static final int COMMAND_PARALLEL_SCALE = 0x90000000;
	public static final int COMMAND_PARALLEL_SIZE = 0x91000000;
	public static final int COMMAND_PERSPECTIVE_FOV = 0x92000000;
	public static final int COMMAND_PERSPECTIVE_WH = 0x93000000;
	public static final int COMMAND_TEXTURE_INDEX = 0x86000000;
	public static final int COMMAND_THRESHOLD = 0xAF000000;
	public static final int ENV_ATTR_LIGHTING = 1;
	public static final int ENV_ATTR_SPHERE_MAP = 2;
	public static final int ENV_ATTR_TOON_SHADING = 4;
	public static final int ENV_ATTR_SEMI_TRANSPARENT = 8;
	public static final int PATTR_BLEND_ADD = 64;
	public static final int PATTR_BLEND_HALF = 32;
	public static final int PATTR_BLEND_NORMAL = 0;
	public static final int PATTR_BLEND_SUB = 96;
	public static final int PATTR_COLORKEY = 16;
	public static final int PATTR_LIGHTING = 1;
	public static final int PATTR_SPHERE_MAP = 2;
	public static final int PDATA_COLOR_NONE = 0;
	public static final int PDATA_COLOR_PER_COMMAND = 1024;
	public static final int PDATA_COLOR_PER_FACE = 2048;
	public static final int PDATA_NORMAL_NONE = 0;
	public static final int PDATA_NORMAL_PER_FACE = 512;
	public static final int PDATA_NORMAL_PER_VERTEX = 768;
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_CMD = 4096;
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_FACE = 8192;
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_VERTEX = 12288;
	public static final int PDATA_TEXURE_COORD = 12288;
	public static final int PDATA_TEXURE_COORD_NONE = 0;
	public static final int POINT_SPRITE_LOCAL_SIZE = 0;
	public static final int POINT_SPRITE_NO_PERS = 2;
	public static final int POINT_SPRITE_PERSPECTIVE = 0;
	public static final int POINT_SPRITE_PIXEL_SIZE = 1;
	public static final int PRIMITVE_LINES = 0x2000000;
	public static final int PRIMITVE_POINTS = 0x1000000;
	public static final int PRIMITVE_POINT_SPRITES = 0x5000000;
	public static final int PRIMITVE_QUADS = 0x4000000;
	public static final int PRIMITVE_TRIANGLES = 0x3000000;

	/** Backend factory SPI; the GL implementation in freej2meOnMinijvm sets this via reflection. */
	public interface BackendFactory {
		Micro3dBackend create();
	}

	private static final String MINIJVM_BACKEND_FACTORY =
			"com.mascotcapsule.micro3d.v3.MiniJvmMicro3dFactory";

	private Graphics graphics;
	private final Engine engine;
	private final Micro3dBackend backend;

	public Graphics3D() {
		backend = createBackend();
		engine = new Engine(backend);
	}

	private static Micro3dBackend createBackend() {
		// 1. prefer a reflected GL backend (freej2meOnMinijvm)
		try {
			Class<?> factoryClass = Class.forName(MINIJVM_BACKEND_FACTORY);
			BackendFactory factory = (BackendFactory) factoryClass.newInstance();
			Micro3dBackend gl = factory.create();
			if (gl != null && gl.isAvailable()) {
				return gl;
			}
		} catch (Throwable ignored) {
		}
		// 2. fall back to the software rasterizer (pure J2SE, always available)
		return new com.mascotcapsule.micro3d.v3.base.SoftwareMicro3dBackend();
	}

	public final synchronized void bind(Graphics graphics) {
		bind(graphics, true);
	}

	// internal
	public final synchronized void bind(Graphics graphics, boolean doClip) {
		if (graphics == null) {
			throw new NullPointerException("Argument 'Graphics' is NULL");
		}
		if (this.graphics != null) {
			throw new IllegalStateException("Target already bound");
		}
		this.graphics = graphics;
		backend.bind(graphics, doClip);
	}

	public final void dispose() {
		// resources are reclaimed on finalize / GC
	}

	public final void drawCommandList(Texture[] textures, int x, int y,
									  FigureLayout layout, Effect3D effect, int[] commandList) {
		checkTargetIsValid();
		if (layout == null || effect == null || commandList == null) {
			throw new NullPointerException();
		}

		RenderProxy.getViewTrans(layout.affine, engine.getViewMatrix(), 0);
		RenderProxy.setTextureArray(engine, textures);
		RenderProxy.setAffineArray(engine, layout.affineArray);
		engine.setCenter(layout.centerX + x, layout.centerY + y);
		engine.resetEnvironmentSize();
		RenderProxy.setProjection(engine, layout);
		RenderProxy.setEffects(engine, effect);

		engine.drawCommandList(commandList);
	}

	public final void drawCommandList(Texture texture, int x, int y,
									  FigureLayout layout, Effect3D effect, int[] commandList) {
		checkTargetIsValid();
		if (layout == null || effect == null || commandList == null) {
			throw new NullPointerException();
		}

		RenderProxy.getViewTrans(layout.affine, engine.getViewMatrix(), 0);
		RenderProxy.setAffineArray(engine, layout.affineArray);
		engine.setCenter(layout.centerX + x, layout.centerY + y);
		engine.resetEnvironmentSize();
		RenderProxy.setProjection(engine, layout);
		RenderProxy.setEffects(engine, effect);
		if (texture != null) {
			engine.setTexture(texture.impl);
		}

		engine.drawCommandList(commandList);
	}

	public final void drawFigure(Figure figure, int x, int y, FigureLayout layout, Effect3D effect) {
		checkTargetIsValid();
		if (figure == null || layout == null || effect == null) {
			throw new NullPointerException();
		}

		RenderProxy.getViewTrans(layout.affine, engine.getViewMatrix(), 0);
		engine.setCenter(layout.centerX + x, layout.centerY + y);
		engine.resetEnvironmentSize();
		RenderProxy.setProjection(engine, layout);
		RenderProxy.setEffects(engine, effect);
		Texture texture = figure.getTexture();
		if (texture != null) {
			engine.setTexture(texture.impl);
		}

		engine.postFigure(figure.impl);
		engine.flushFrame();
		engine.resetQueue();
	}

	public final void flush() {
		checkTargetIsValid();
		engine.flushItems();
		engine.resetQueue();
	}

	public final synchronized void release(Graphics graphics) {
		if (graphics == null) {
			throw new NullPointerException("Argument 'Graphics' is NULL");
		}
		if (graphics != this.graphics) {
			// mismatched target: drop any pending draws, do not present
			if (backend != null) {
				engine.resetQueue();
			}
			this.graphics = null;
			return;
		}
		// Mascot Capsule semantics: renderFigure/renderPrimitives/drawCommandList are
		// deferred (they only enqueue). A frame is submitted either by an explicit
		// flush() or by release() (which also presents to the bound target). Games that
		// do bind -> renderFigure* -> release rely on this release() to flush.
		engine.flushItems();
		engine.resetQueue();
		backend.release(graphics);
		this.graphics = null;
	}

	public final void renderFigure(Figure figure, int x, int y,
								   FigureLayout layout, Effect3D effect) {
		checkTargetIsValid();
		if (figure == null || layout == null || effect == null) {
			throw new NullPointerException();
		}

		RenderProxy.getViewTrans(layout.affine, engine.getViewMatrix(), 0);
		RenderProxy.setTextureArray(engine, figure.textures);
		engine.setCenter(layout.centerX + x, layout.centerY + y);
		engine.resetEnvironmentSize();
		RenderProxy.setProjection(engine, layout);
		RenderProxy.setEffects(engine, effect);

		engine.postFigure(figure.impl);
	}

	public final void renderPrimitives(Texture texture, int x, int y,
									   FigureLayout layout, Effect3D effect,
									   int command, int numPrimitives, int[] vertexCoords,
									   int[] normals, int[] textureCoords, int[] colors) {
		checkTargetIsValid();
		if (layout == null || effect == null || vertexCoords == null || normals == null
				|| textureCoords == null || colors == null) {
			throw new NullPointerException();
		}
		if (command < 0 || numPrimitives <= 0 || numPrimitives >= 256) {
			throw new IllegalArgumentException();
		}

		RenderProxy.getViewTrans(layout.affine, engine.getViewMatrix(), 0);
		engine.setCenter(layout.centerX + x, layout.centerY + y);
		engine.resetEnvironmentSize();
		RenderProxy.setProjection(engine, layout);
		RenderProxy.setEffects(engine, effect);
		if (texture != null) {
			engine.setTexture(texture.impl);
		}

		engine.postPrimitives(command | numPrimitives << 16, vertexCoords, 0, normals, 0, textureCoords, 0, colors, 0);
	}

	private void checkTargetIsValid() throws IllegalStateException {
		if (graphics == null) {
			throw new IllegalStateException("No target is bound");
		}
	}
}
