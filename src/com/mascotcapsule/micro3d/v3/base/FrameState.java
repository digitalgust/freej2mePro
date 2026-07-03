/*
 * Copyright 2022-2023 Yury Kharchenko (original KEmulator Render.Environment / RenderNode)
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
 * Per-frame state contract between the engine (Engine) and a Micro3dBackend.
 * Carries the environment (view/projection/light/toon/sphere/textures) and the
 * queued draw items (figures + immediate-mode primitives) the backend must
 * render in two passes (opaque + translucent).
 */
package com.mascotcapsule.micro3d.v3.base;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Vector;

import com.mascotcapsule.micro3d.v3.Graphics3D;

public final class FrameState {
	// ---- environment (set by Engine before each draw) ----
	public final float[] viewMatrix = new float[12];
	public final float[] projMatrix = new float[16];
	public int projection;
	public float near;
	public int centerX;
	public int centerY;
	public int width;
	public int height;
	public int attrs;
	/** selected ambient + single directional light (intensities in fixed-point 4096==1.0) */
	public final Light light = new Light();
	/** active model textures (up to 16 slots); texturesLen counts valid ones */
	public final TextureImpl[] textures = new TextureImpl[16];
	public int texturesLen;
	public int textureIdx;
	/** sphere/specular map texture, or null */
	public TextureImpl specular;
	public int toonThreshold;
	public int toonHigh;
	public int toonLow;

	// ---- queued draw items ----
	public final Vector<DrawItem> items = new Vector<DrawItem>();

	public TextureImpl getTexture() {
		if (textureIdx < 0 || textureIdx >= texturesLen) {
			return null;
		}
		return textures[textureIdx];
	}

	public void reset(int width, int height) {
		this.width = width;
		this.height = height;
		texturesLen = 0;
		textureIdx = 0;
		specular = null;
		items.clear();
	}

	/** A single queued draw, either a Figure or an immediate-mode primitive batch. */
	public static abstract class DrawItem {
		/** snapshot of environment at queue time */
		public final float[] viewMatrix = new float[12];
		public final float[] projMatrix = new float[16];
		public int attrs;
		public Light light;
		public TextureImpl specular;
		public int toonThreshold;
		public int toonHigh;
		public int toonLow;

		void capture(FrameState env) {
			System.arraycopy(env.viewMatrix, 0, viewMatrix, 0, 12);
			System.arraycopy(env.projMatrix, 0, projMatrix, 0, 16);
			attrs = env.attrs;
			if (light == null) {
				light = new Light(env.light);
			} else {
				light.set(env.light.ambIntensity, env.light.dirIntensity,
						env.light.x, env.light.y, env.light.z);
			}
			specular = env.specular;
			toonThreshold = env.toonThreshold;
			toonHigh = env.toonHigh;
			toonLow = env.toonLow;
		}
	}

	/** A Figure render: pre-sorted opaque/translucent submeshes of a model. */
	public static final class FigureItem extends DrawItem {
		public final Model model;
		public final TextureImpl[] textures;
		public final FloatBuffer vertices;
		public final FloatBuffer normals;

		public FigureItem(Model model, TextureImpl[] textures, FloatBuffer vertices, FloatBuffer normals) {
			this.model = model;
			this.textures = textures;
			this.vertices = vertices;
			this.normals = normals;
		}
	}

	/** An immediate-mode primitive batch (from renderPrimitives / command list). */
	public static final class PrimitiveItem extends DrawItem {
		public final int command;
		public final FloatBuffer vertices;
		public final FloatBuffer normals;
		public final ByteBuffer texCoords;
		public final ByteBuffer colors;
		public final TextureImpl texture;

		public PrimitiveItem(int command, FloatBuffer vertices, FloatBuffer normals,
							 ByteBuffer texCoords, ByteBuffer colors, TextureImpl texture) {
			this.command = command;
			this.vertices = vertices;
			this.normals = normals;
			this.texCoords = texCoords;
			this.colors = colors;
			this.texture = texture;
		}

		/** @return PATTR blend bits (NORMAL/HALF/ADD/SUB) or 0 if no blend. */
		public int blendMode() {
			if ((attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) == 0) {
				return Micro3dRasterizer.BLEND_NORMAL;
			}
			// PATTR_BLEND_SUB = HALF(32)|ADD(64) = 96 is the *combined* value, not a
			// superset mask: it must be matched exactly. The old `(command & SUB) != 0`
			// test wrongly matched any HALF or ADD polygon as SUB, turning water
			// (PATTR_BLEND_HALF=32) into a subtractive blend that produced black speckle.
			int blend = command & Graphics3D.PATTR_BLEND_SUB;
			if (blend == Graphics3D.PATTR_BLEND_SUB) return Model.Polygon.BLEND_SUB;
			if (blend == Graphics3D.PATTR_BLEND_ADD) return Model.Polygon.BLEND_ADD;
			if (blend == Graphics3D.PATTR_BLEND_HALF) return Model.Polygon.BLEND_HALF;
			return Micro3dRasterizer.BLEND_NORMAL;
		}
	}
}
