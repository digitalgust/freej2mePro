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
 * Software triangle rasterizer for micro3D v3. Self-contained — no m3g / LWJGL.
 * The scan-conversion, depth-test, perspective-correct texture interpolation and
 * alpha/key blend kernels are ported from the (verified) m3g SoftwareRenderBackend,
 * adapted to the v3 data model (TextureData RGBA raster, v3 light/toon/sphere
 * shading, per-polygon blend modes NORMAL/HALF/ADD/SUB).
 *
 * Colors are 0xAARRGGBB ints. Coordinates are already projected to screen space
 * by the caller (x/y in pixels, z in [0,1] depth, invW for perspective interpolation).
 */
package com.mascotcapsule.micro3d.v3.base;

import java.awt.Rectangle;
import java.nio.ByteBuffer;

public final class Micro3dRasterizer {
	/** per-polygon blend modes (Model.Polygon.BLEND_* values) */
	public static final int BLEND_NORMAL = 0;
	public static final int BLEND_HALF = 2;
	public static final int BLEND_ADD = 4;
	public static final int BLEND_SUB = 6;
	private static final java.util.concurrent.atomic.AtomicInteger DEBUG_BLACK_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();

	private final Micro3dSurface surface;
	private final Rectangle clip;
	private final float[] depthBuffer;
	private final int surfaceW;
	private final int surfaceH;

	public Micro3dRasterizer(Micro3dSurface surface, Rectangle clip, float[] depthBuffer) {
		this.surface = surface;
		this.clip = clip;
		this.depthBuffer = depthBuffer;
		this.surfaceW = surface.getWidth();
		this.surfaceH = surface.getHeight();
	}

	/** A projected triangle vertex. */
	public static final class Vertex {
		public float x, y, z;   // screen-space x/y (px), depth z in [0,1]
		public float invW;      // 1/clipW, for perspective-correct interpolation
		public float r, g, b, a; // shaded vertex color (0..255)
		public float u, v;      // texture coords (in texture's own units, e.g. 0..255 byte)
		public float nx, ny, nz; // world-space normal (unit), for toon/lighting at the pixel
		public boolean visible;
	}

	/**
	 * Rasterize a triangle with the given shading context. {@code depthWrite} and
	 * {@code depthTest} are caller-controlled so the two-pass opaque/translucent
	 * dispatch can be expressed at the call site.
	 */
	public void rasterTriangle(Vertex v0, Vertex v1, Vertex v2, Shading shading,
							   boolean depthTest, boolean depthWrite) {
		if (!v0.visible || !v1.visible || !v2.visible) {
			return;
		}
		float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
		if (Math.abs(area) <= 1.0e-6f) {
			return;
		}
		// backface / winding. The edge() function here computes
		//   edge(v0,v1,v2) = (v2-v0) x (v1-v0) = -(signed shoelace area),
		// so its sign is the INVERSE of the usual CCW/front-face convention.
		// A front-facing (CCW) triangle therefore yields area < 0, and a
		// back-facing one yields area > 0. cullBack must reject area > 0,
		// cullFront must reject area < 0. (Previously these were reversed,
		// which discarded front faces and rendered back faces, producing
		// inverted per-part depth ordering when parts overlapped.)
		if (shading.cullBack && area > 0f) {
			return;
		}
		if (shading.cullFront && area < 0f) {
			return;
		}

		float minXf = Math.min(v0.x, Math.min(v1.x, v2.x));
		float maxXf = Math.max(v0.x, Math.max(v1.x, v2.x));
		float minYf = Math.min(v0.y, Math.min(v1.y, v2.y));
		float maxYf = Math.max(v0.y, Math.max(v1.y, v2.y));
		int minX = clamp((int) Math.floor(minXf), clip.x, clip.x + clip.width - 1);
		int maxX = clamp((int) Math.ceil(maxXf), clip.x, clip.x + clip.width - 1);
		int minY = clamp((int) Math.floor(minYf), clip.y, clip.y + clip.height - 1);
		int maxY = clamp((int) Math.ceil(maxYf), clip.y, clip.y + clip.height - 1);
		if (minX > maxX || minY > maxY) {
			return;
		}

		float invArea = 1f / area;
		boolean textured = shading.texture != null;
		ByteBuffer raster = textured ? shading.texture.getRaster() : null;
		int tw = textured ? shading.texture.width : 0;
		int th = textured ? shading.texture.height : 0;
		boolean perspCorrect = textured && v0.invW > 0f && v1.invW > 0f && v2.invW > 0f;
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f;
				float py = y + 0.5f;
				float w0 = edge(v1.x, v1.y, v2.x, v2.y, px, py) * invArea;
				float w1 = edge(v2.x, v2.y, v0.x, v0.y, px, py) * invArea;
				float w2 = 1f - w0 - w1;
				// accept either winding: weights must all be >= 0 (CCW) OR all <= 0 (CW,
				// which happens for back-facing triangles; we still shade them unless
				// culling was requested above).
				if (w0 < 0f || w1 < 0f || w2 < 0f) {
					if (w0 > 0f || w1 > 0f || w2 > 0f) {
						continue;
					}
				}

				float depth = w0 * v0.z + w1 * v1.z + w2 * v2.z;
				int depthIndex = y * surfaceW + x;
				if (depthTest && depthBuffer != null
						&& depthIndex >= 0 && depthIndex < depthBuffer.length
						&& depth > depthBuffer[depthIndex]) {
					continue;
				}

				// base color: interpolate vertex color (flat if requested)
				int baseColor;
				if (shading.flatShading) {
					baseColor = color(v0);
				} else {
					float rr = w0 * v0.r + w1 * v1.r + w2 * v2.r;
					float gg = w0 * v0.g + w1 * v1.g + w2 * v2.g;
					float bb = w0 * v0.b + w1 * v1.b + w2 * v2.b;
					float aa = w0 * v0.a + w1 * v1.a + w2 * v2.a;
					baseColor = toColor(rr, gg, bb, aa);
				}

				// per-pixel lighting / toon: re-evaluate lambert from interpolated normal
				int litColor = baseColor;
				if (shading.light != null && (shading.enableLighting)) {
					float nx, ny, nz;
					if (shading.flatShading) {
						nx = v0.nx; ny = v0.ny; nz = v0.nz;
					} else {
						nx = w0 * v0.nx + w1 * v1.nx + w2 * v2.nx;
						ny = w0 * v0.ny + w1 * v1.ny + w2 * v2.ny;
						nz = w0 * v0.nz + w1 * v1.nz + w2 * v2.nz;
					}
					litColor = applyLight(baseColor, nx, ny, nz, shading);
				}

				// texture sampling (perspective-correct uv)
				int finalColor = litColor;
				if (textured) {
					float u, v;
					if (perspCorrect) {
						float denom = w0 * v0.invW + w1 * v1.invW + w2 * v2.invW;
						if (Math.abs(denom) <= 1.0e-6f) {
							continue;
						}
						u = (w0 * v0.u * v0.invW + w1 * v1.u * v1.invW + w2 * v2.u * v2.invW) / denom;
						v = (w0 * v0.v * v0.invW + w1 * v1.v * v1.invW + w2 * v2.v * v2.invW) / denom;
					} else {
						u = w0 * v0.u + w1 * v1.u + w2 * v2.u;
						v = w0 * v0.v + w1 * v1.v + w2 * v2.v;
					}
					int texel = sampleTexture(raster, tw, th, u, v);
					if (shading.colorKey && ((texel >>> 24) & 0xFF) < 128) {
						continue; // color-key: matches tex.fsh (alpha < 0.5 discard)
					}
					// Matches reference tex.fsh: final alpha is 1.0 — texture alpha is only
					// consulted for color-key discard above. When colorKey is off, palette
					// index 0 must still show its RGB (it is a valid opaque color, not a hole),
					// otherwise large scene areas painted with index 0 turn into black speckle.
					finalColor = modulateOpaque(litColor, texel);
				}

				// sphere/specular map additive sampling (figures + lit primitives only)
				if (shading.sphere != null && shading.enableLighting) {
					float nx, ny;
					if (shading.flatShading) {
						nx = v0.nx; ny = v0.ny;
					} else {
						nx = w0 * v0.nx + w1 * v1.nx + w2 * v2.nx;
						ny = w0 * v0.ny + w1 * v1.ny + w2 * v2.ny;
					}
					finalColor = addSphere(finalColor, shading.sphere, nx, ny);
				}

				int srcAlpha = (finalColor >>> 24) & 0xFF;
				if (srcAlpha < shading.alphaThreshold) {
					continue;
				}

				int dstColor = surface.getPixel(x, y);
				int composited = applyBlend(dstColor, finalColor, shading.blendMode);
				if (DEBUG_BLACK_LOG_COUNT.get() < 300 && (composited & 0xFFFFFF) == 0) {
					DEBUG_BLACK_LOG_COUNT.incrementAndGet();
					System.err.println("[BLACKPIX] x="+x+" y="+y
						+" blendMode="+shading.blendMode+" colorKey="+shading.colorKey
						+" final=0x"+Integer.toHexString(finalColor)
						+" dst=0x"+Integer.toHexString(dstColor)
						+" textured="+(shading.texture!=null)
						+" lit="+ (shading.light!=null));
				}
				if (composited != dstColor) {
					surface.setPixel(x, y, composited);
				}
				if (depthWrite && depthBuffer != null && srcAlpha > 0
						&& depthIndex >= 0 && depthIndex < depthBuffer.length) {
					depthBuffer[depthIndex] = depth;
				}
			}
		}
	}

	/** Per-triangle shading context (immutable snapshot). */
	public static final class Shading {
		public final TextureData texture;
		public final TextureData sphere;
		public final Light light;
		public final boolean enableLighting;
		public final boolean toon;
		public final int toonThreshold;
		public final int toonHigh;
		public final int toonLow;
		public final int blendMode;       // BLEND_NORMAL/HALF/ADD/SUB
		public final boolean colorKey;
		public final boolean flatShading;
		public final int alphaThreshold;  // 0..255
		public final boolean cullBack;
		public final boolean cullFront;

		public Shading(TextureData texture, TextureData sphere, Light light,
					   boolean enableLighting, boolean toon, int toonThreshold,
					   int toonHigh, int toonLow, int blendMode, boolean colorKey,
					   boolean flatShading, int alphaThreshold,
					   boolean cullBack, boolean cullFront) {
			this.texture = texture;
			this.sphere = sphere;
			this.light = light;
			this.enableLighting = enableLighting;
			this.toon = toon;
			this.toonThreshold = toonThreshold;
			this.toonHigh = toonHigh;
			this.toonLow = toonLow;
			this.blendMode = blendMode;
			this.colorKey = colorKey;
			this.flatShading = flatShading;
			this.alphaThreshold = alphaThreshold;
			this.cullBack = cullBack;
			this.cullFront = cullFront;
		}
	}

	// ---------- shading math ----------

	private static int applyLight(int baseColor, float nx, float ny, float nz, Shading s) {
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len <= 1.0e-6f) {
			return baseColor;
		}
		nx /= len; ny /= len; nz /= len;
		// light direction is stored un-normalized in fixed-point; convert to float unit
		float lx = s.light.x, ly = s.light.y, lz = s.light.z;
		float dlen = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
		if (dlen <= 1.0e-6f) {
			lx = 0f; ly = 0f; lz = 1f;
		} else {
			lx /= dlen; ly /= dlen; lz /= dlen;
		}
		float lambert = nx * lx + ny * ly + nz * lz;
		if (lambert < 0f) lambert = 0f;

		// intensities are fixed-point (4096 == 1.0)
		float amb = s.light.ambIntensity * MathUtil.TO_FLOAT;
		float dir = s.light.dirIntensity * MathUtil.TO_FLOAT * lambert;
		if (dir > 4f) dir = 4f;
		float light = amb + dir;
		if (light < 0f) light = 0f;
		if (light > 1f) light = 1f;

		if (s.toon) {
			// toon: step by threshold (intensities stored 0..255 per the Effect3D toon params)
			light = light * 255f < s.toonThreshold ? s.toonLow / 255f : s.toonHigh / 255f;
		}

		int r = (int) (((baseColor >>> 16) & 0xFF) * light);
		int g = (int) (((baseColor >>> 8) & 0xFF) * light);
		int b = (int) ((baseColor & 0xFF) * light);
		int a = (baseColor >>> 24) & 0xFF;
		return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int addSphere(int color, TextureData sphere, float nx, float ny) {
		int sw = sphere.width;
		int sh = sphere.height;
		if (sw <= 0 || sh <= 0) {
			return color;
		}
		float u = (nx / 128f + 32f);
		float v = (ny / 128f + 32f);
		int tx = clamp((int) u, 0, sw - 1);
		int ty = clamp((int) v, 0, sh - 1);
		ByteBuffer r = sphere.getRaster();
		int p = (ty * sw + tx) * 4;
		if (p < 0 || p + 2 >= r.capacity()) return color;
		int sr = r.get(p) & 0xFF;
		int sg = r.get(p + 1) & 0xFF;
		int sb = r.get(p + 2) & 0xFF;
		int cr = (color >>> 16) & 0xFF;
		int cg = (color >>> 8) & 0xFF;
		int cb = color & 0xFF;
		int a = (color >>> 24) & 0xFF;
		return (a << 24) | (clamp(cr + sr) << 16) | (clamp(cg + sg) << 8) | clamp(cb + sb);
	}

	private static int sampleTexture(ByteBuffer raster, int tw, int th, float u, float v) {
		if (raster == null || tw <= 0 || th <= 0) {
			return 0;
		}
		int cap = raster.capacity();
		int maxU = tw - 1;
		int maxV = th - 1;
		if (u < 0f) u = 0f;
		else if (u > maxU) u = maxU;
		if (v < 0f) v = 0f;
		else if (v > maxV) v = maxV;
		// CLAMP_TO_EDGE + tex.vsh/tex.fsh NEAREST lookup.
		float nu = u / tw + 1.0f / 65536.0f;
		float nv = v / th - 1.0f / 65536.0f;
		if (nu > 1f) nu = 1f;
		if (nv > 1f) nv = 1f;
		int tx = clamp((int) Math.floor(nu * tw), 0, maxU);
		int ty = clamp((int) Math.floor(nv * th), 0, maxV);
		int p = (ty * tw + tx) * 4;
		if (p < 0 || p + 3 >= cap) {
			return 0;
		}
		int r = raster.get(p) & 0xFF;
		int g = raster.get(p + 1) & 0xFF;
		int b = raster.get(p + 2) & 0xFF;
		int a = raster.get(p + 3) & 0xFF;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int modulate(int litColor, int texel) {
		int lr = (litColor >>> 16) & 0xFF;
		int lg = (litColor >>> 8) & 0xFF;
		int lb = litColor & 0xFF;
		int la = (litColor >>> 24) & 0xFF;
		int tr = (texel >>> 16) & 0xFF;
		int tg = (texel >>> 8) & 0xFF;
		int tb = texel & 0xFF;
		int ta = (texel >>> 24) & 0xFF;
		int a = (la * ta) / 255;
		return (a << 24) | ((lr * tr) / 255 << 16) | ((lg * tg) / 255 << 8) | (lb * tb) / 255;
	}

	/**
	 * Texture modulation with forced-opaque alpha, matching reference tex.fsh which
	 * always emits vec4(color.rgb, 1.0). Texture alpha is only used for color-key
	 * discard (handled by the caller), never carried into the output color.
	 */
	private static int modulateOpaque(int litColor, int texel) {
		int lr = (litColor >>> 16) & 0xFF;
		int lg = (litColor >>> 8) & 0xFF;
		int lb = litColor & 0xFF;
		int tr = (texel >>> 16) & 0xFF;
		int tg = (texel >>> 8) & 0xFF;
		int tb = texel & 0xFF;
		return 0xFF000000 | ((lr * tr) / 255 << 16) | ((lg * tg) / 255 << 8) | (lb * tb) / 255;
	}

	private static int applyBlend(int dst, int src, int blendMode) {
		if (blendMode == BLEND_NORMAL) {
			int srcA = (src >>> 24) & 0xFF;
			if (srcA >= 0xFF) {
				return src | 0xFF000000;
			}
			if (srcA <= 0) {
				return dst;
			}
			return blendOver(dst, src, srcA);
		}
		int dr = (dst >>> 16) & 0xFF;
		int dg = (dst >>> 8) & 0xFF;
		int db = dst & 0xFF;
		int sr = (src >>> 16) & 0xFF;
		int sg = (src >>> 8) & 0xFF;
		int sb = src & 0xFF;
		switch (blendMode) {
			case BLEND_HALF: { // src*0.5 + dst*0.5
				return 0xFF000000 | (((sr + dr) >> 1) << 16) | (((sg + dg) >> 1) << 8) | ((sb + db) >> 1);
			}
			case BLEND_ADD: { // src + dst
				return 0xFF000000 | (clamp(sr + dr) << 16) | (clamp(sg + dg) << 8) | clamp(sb + db);
			}
			case BLEND_SUB: { // dst - src
				return 0xFF000000 | (clamp(dr - sr) << 16) | (clamp(dg - sg) << 8) | clamp(db - sb);
			}
			default:
				return src | 0xFF000000;
		}
	}

	private static int blendOver(int dst, int src, int srcA) {
		int invA = 255 - srcA;
		int dr = (dst >>> 16) & 0xFF;
		int dg = (dst >>> 8) & 0xFF;
		int db = dst & 0xFF;
		int sr = (src >>> 16) & 0xFF;
		int sg = (src >>> 8) & 0xFF;
		int sb = src & 0xFF;
		int r = (sr * srcA + dr * invA) / 255;
		int g = (sg * srcA + dg * invA) / 255;
		int b = (sb * srcA + db * invA) / 255;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int color(Vertex v) {
		return (clamp((int) v.a) << 24) | (clamp((int) v.r) << 16) | (clamp((int) v.g) << 8) | clamp((int) v.b);
	}

	private static int toColor(float r, float g, float b, float a) {
		return (clamp(Math.round(a)) << 24) | (clamp(Math.round(r)) << 16) | (clamp(Math.round(g)) << 8) | clamp(Math.round(b));
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : v > 255 ? 255 : v;
	}

	private static int clamp(int v, int min, int max) {
		return v < min ? min : v > max ? max : v;
	}

	private static float edge(float ax, float ay, float bx, float by, float px, float py) {
		return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
	}
}
