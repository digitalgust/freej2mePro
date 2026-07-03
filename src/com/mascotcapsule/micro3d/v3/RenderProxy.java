/*
 * Copyright 2022 Yury Kharchenko (original KEmulator RenderProxy)
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
 * Ported from KEmulator micro3d_gl into freej2me. Sits in the standard API package
 * so it can read the package-private fields of AffineTrans / Effect3D / FigureLayout /
 * Light / Texture and translate them into the engine Environment + projection state.
 */
package com.mascotcapsule.micro3d.v3;

import com.mascotcapsule.micro3d.v3.base.Engine;
import com.mascotcapsule.micro3d.v3.base.MathUtil;
import com.mascotcapsule.micro3d.v3.base.TextureImpl;

class RenderProxy {

	/** Pack an AffineTrans (fixed-point, row-major m{row}{col}) into a float[12] view matrix. */
	static void getViewTrans(AffineTrans a, float[] out, int n) {
		int offset = n * 12;
		out[offset++] = a.m00 * MathUtil.TO_FLOAT;
		out[offset++] = a.m10 * MathUtil.TO_FLOAT;
		out[offset++] = a.m20 * MathUtil.TO_FLOAT;
		out[offset++] = a.m01 * MathUtil.TO_FLOAT;
		out[offset++] = a.m11 * MathUtil.TO_FLOAT;
		out[offset++] = a.m21 * MathUtil.TO_FLOAT;
		out[offset++] = a.m02 * MathUtil.TO_FLOAT;
		out[offset++] = a.m12 * MathUtil.TO_FLOAT;
		out[offset++] = a.m22 * MathUtil.TO_FLOAT;
		out[offset++] = a.m03;
		out[offset++] = a.m13;
		out[offset  ] = a.m23;
	}

	static void setTextureArray(Engine engine, Texture[] textures) {
		if (textures != null) {
			int len = textures.length;
			if (len > 0) {
				if (len > 16) {
					len = 16;
				}
				TextureImpl[] texArray = new TextureImpl[len];
				for (int i = 0; i < len; i++) {
					Texture texture = textures[i];
					if (texture == null) {
						throw new NullPointerException();
					}
					texArray[i] = texture.impl;
				}
				engine.setTextureArray(texArray);
			}
		}
	}

	static void setEffects(Engine engine, Effect3D effect) {
		int attrs = engine.getAttributes();
		Light light = effect.light;
		if (light != null) {
			int ambIntensity = light.ambIntensity;
			int dirIntensity = light.dirIntensity;
			Vector3D dir = light.direction;
			engine.setLight(ambIntensity, dirIntensity, dir.x, dir.y, dir.z);
			attrs |= Graphics3D.ENV_ATTR_LIGHTING;
		} else {
			attrs &= ~Graphics3D.ENV_ATTR_LIGHTING;
		}

		int shading = effect.shading;
		if (shading == Effect3D.TOON_SHADING) {
			attrs |= Graphics3D.ENV_ATTR_TOON_SHADING;
			engine.setToonParam(effect.toonThreshold, effect.toonHigh, effect.toonLow);
		} else {
			attrs &= ~Graphics3D.ENV_ATTR_TOON_SHADING;
		}

		boolean isBlend = effect.isTransparency;
		if (isBlend) {
			attrs |= Graphics3D.ENV_ATTR_SEMI_TRANSPARENT;
		} else {
			attrs &= ~Graphics3D.ENV_ATTR_SEMI_TRANSPARENT;
		}

		Texture specular = effect.texture;
		if (specular != null) {
			attrs |= Graphics3D.ENV_ATTR_SPHERE_MAP;
			engine.setSphereTexture(specular.impl);
		} else {
			attrs &= ~Graphics3D.ENV_ATTR_SPHERE_MAP;
		}

		engine.setAttribute(attrs);
	}

	static void setProjection(Engine engine, FigureLayout layout) {
		switch (layout.projection) {
			case Graphics3D.COMMAND_PARALLEL_SCALE: {
				engine.setOrthographicScale(layout.scaleX, layout.scaleY);
				break;
			}
			case Graphics3D.COMMAND_PARALLEL_SIZE: {
				engine.setOrthographicWH(layout.parallelWidth, layout.parallelHeight);
				break;
			}
			case Graphics3D.COMMAND_PERSPECTIVE_FOV: {
				engine.setPerspectiveFov(layout.near, layout.far, layout.angle);
				break;
			}
			case Graphics3D.COMMAND_PERSPECTIVE_WH: {
				engine.setPerspectiveWH(layout.near, layout.far, layout.perspectiveWidth, layout.perspectiveHeight);
				break;
			}
		}
	}

	static void setAffineArray(Engine engine, AffineTrans[] affineArray) {
		if (affineArray != null) {
			int len = affineArray.length;
			float[] transArray = new float[len * 12];
			for (int i = 0; i < len; i++) {
				getViewTrans(affineArray[i], transArray, i);
			}
			engine.setViewTransArray(transArray);
		}
	}
}
