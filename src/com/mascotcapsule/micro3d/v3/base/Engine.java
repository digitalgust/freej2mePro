/*
 * Copyright 2020-2023 Yury Kharchenko (original KEmulator Render)
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
 * Ported from KEmulator ru.woesss.j2me.micro3d.Render into freej2me.
 *
 * Engine is the platform-independent micro3D rendering state machine: it owns the
 * Environment (view matrix / projection matrix / light / toon / sphere / texture
 * array / center), interprets drawCommandList, builds immediate-mode primitive
 * batches, posts Figure draws, and computes the four fixed-point projection modes.
 * It produces a FrameState and hands it to a Micro3dBackend, which performs the
 * actual pixel output (software rasterizer or GL). No GL/copy2d lives here.
 */
package com.mascotcapsule.micro3d.v3.base;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import com.mascotcapsule.micro3d.v3.Graphics3D;

public class Engine {
	private static final int PDATA_COLOR_MASK =
			(Graphics3D.PDATA_COLOR_PER_COMMAND | Graphics3D.PDATA_COLOR_PER_FACE);
	private static final int PDATA_COLOR_PER_VERTEX = PDATA_COLOR_MASK;
	private static final int PDATA_NORMAL_MASK = Graphics3D.PDATA_NORMAL_PER_VERTEX;
	private static final int PDATA_TEXCOORD_MASK = Graphics3D.PDATA_TEXURE_COORD;
	private static final int[] PRIMITIVE_SIZES = {0, 1, 2, 3, 4, 1};

	public final FrameState env = new FrameState();
	private final Micro3dBackend backend;
	private float[] matrices;

	public Engine(Micro3dBackend backend) {
		this.backend = backend;
	}

	public Micro3dBackend getBackend() {
		return backend;
	}

	// ---------- environment setters (called by RenderProxy) ----------

	public float[] getViewMatrix() {
		return env.viewMatrix;
	}

	public void setTexture(TextureImpl tex) {
		if (tex == null) {
			return;
		}
		env.textures[0] = tex;
		env.textureIdx = 0;
		env.texturesLen = 1;
	}

	public void setTextureArray(TextureImpl[] tex) {
		if (tex == null) {
			return;
		}
		int len = Math.min(tex.length, env.textures.length);
		System.arraycopy(tex, 0, env.textures, 0, len);
		env.texturesLen = len;
	}

	public void setViewTransArray(float[] m) {
		matrices = m;
	}

	public void selectAffineTrans(int n) {
		if (matrices != null && matrices.length >= (n + 1) * 12) {
			System.arraycopy(matrices, n * 12, env.viewMatrix, 0, 12);
		}
	}

	public void setCenter(int cx, int cy) {
		env.centerX = cx;
		env.centerY = cy;
	}

	public void setLight(int ambIntensity, int dirIntensity, int x, int y, int z) {
		env.light.set(ambIntensity, dirIntensity, x, y, z);
	}

	public int getAttributes() {
		return env.attrs;
	}

	public void setAttribute(int attrs) {
		env.attrs = attrs;
	}

	public void setSphereTexture(TextureImpl tex) {
		if (tex != null) {
			env.specular = tex;
		}
	}

	public void setToonParam(int threshold, int high, int low) {
		env.toonThreshold = threshold;
		env.toonHigh = high;
		env.toonLow = low;
	}

	// ---------- projection ----------

	public void setOrthographicScale(int scaleX, int scaleY) {
		env.projection = Graphics3D.COMMAND_PARALLEL_SCALE;
		float vw = env.width;
		float vh = env.height;
		float w = vw * (4096.0f / scaleX);
		float h = vh * (4096.0f / scaleY);

		float sx = 2.0f / w;
		float sy = 2.0f / h;
		float sz = 1.0f / 65536.0f;
		float tx = 2.0f * env.centerX / vw - 1.0f;
		float ty = 2.0f * env.centerY / vh - 1.0f;
		float tz = 0.0f;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] = 0.0f; pm[12] =   tx;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] = 0.0f; pm[13] =   ty;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 0.0f; pm[15] = 1.0f;
	}

	public void setOrthographicWH(int w, int h) {
		if (w <= 0 || h <= 0) {
			return;
		}
		env.projection = Graphics3D.COMMAND_PARALLEL_SIZE;
		float sx = 2.0f / w;
		float sy = 2.0f / h;
		float sz = 1.0f / 65536.0f;
		float tx = 2.0f * env.centerX / env.width - 1.0f;
		float ty = 2.0f * env.centerY / env.height - 1.0f;
		float tz = 0.0f;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] = 0.0f; pm[12] =   tx;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] = 0.0f; pm[13] =   ty;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 0.0f; pm[15] = 1.0f;
	}

	public void setPerspectiveFov(int near, int far, int angle) {
		if (near <= 0 || far <= 0 || near >= far) {
			return;
		}
		angle = MathUtil.clamp(angle, 2, 2046);
		env.projection = Graphics3D.COMMAND_PERSPECTIVE_FOV;
		env.near = near;
		float rd = 1.0f / (near - far);
		float sx = 1.0f / (float) Math.tan(angle * MathUtil.TO_FLOAT * Math.PI);
		float vw = env.width;
		float vh = env.height;
		float sy = sx * (vw / vh);
		float sz = -(far + near) * rd;
		float tx = 2.0f * env.centerX / vw - 1.0f;
		float ty = 2.0f * env.centerY / vh - 1.0f;
		float tz = 2.0f * far * near * rd;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] =   tx; pm[12] = 0.0f;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] =   ty; pm[13] = 0.0f;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 1.0f; pm[15] = 0.0f;
	}

	public void setPerspectiveWH(int near, int far, int w, int h) {
		if (near <= 0 || far <= 0 || near >= far || w == 0 || h == 0) {
			return;
		}
		env.projection = Graphics3D.COMMAND_PERSPECTIVE_WH;
		env.near = near;
		float rd = 1.0f / (near - far);
		float sx = 2.0f * near / (w * MathUtil.TO_FLOAT);
		float sy = 2.0f * near / (h * MathUtil.TO_FLOAT);
		float sz = -(near + far) * rd;
		float tx = 2.0f * env.centerX / env.width - 1.0f;
		float ty = 2.0f * env.centerY / env.height - 1.0f;
		float tz = 2.0f * far * near * rd;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] =   tx; pm[12] = 0.0f;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] =   ty; pm[13] = 0.0f;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 1.0f; pm[15] = 0.0f;
	}

	// ---------- draw queue ----------

	/** Post a Figure for rendering (snapshots the current environment). */
	public void postFigure(FigureImpl figure) {
		Model model = figure.model;
		FloatBuffer vertices = BufferUtils.createFloatBuffer(model.vertexArrayCapacity);
		FloatBuffer normals = null;
		if (model.originalNormals != null) {
			normals = BufferUtils.createFloatBuffer(model.vertexArrayCapacity);
		}
		figure.fillBuffers(vertices, normals);

		TextureImpl[] texs = null;
		int len = env.texturesLen;
		if (len > 0) {
			texs = new TextureImpl[len];
			System.arraycopy(env.textures, 0, texs, 0, len);
		}
		FrameState.FigureItem item = new FrameState.FigureItem(model, texs, vertices, normals);
		item.capture(env);
		env.items.addElement(item);
	}

	/**
	 * Build an immediate-mode primitive batch from raw int arrays (from
	 * renderPrimitives, or sliced out of a command list). Mirrors Render.postPrimitives
	 * but produces a PrimitiveItem instead of issuing GL calls.
	 */
	public void postPrimitives(int command,
							   int[] vertices, int vo,
							   int[] normals, int no,
							   int[] textureCoords, int to,
							   int[] colors, int co) {
		if (command < 0) {
			throw new IllegalArgumentException();
		}
		int numPrimitives = command >> 16 & 0xff;
		FloatBuffer vcBuf;
		FloatBuffer ncBuf = null;
		ByteBuffer tcBuf = null;
		ByteBuffer colorBuf = null;
		switch ((command & 0x7000000)) {
			case Graphics3D.PRIMITVE_POINTS: {
				int vcLen = numPrimitives * 3;
				vcBuf = BufferUtils.createFloatBuffer(vcLen);
				for (int i = 0; i < vcLen; i++) {
					vcBuf.put(vertices[vo++]);
				}

				if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(vcLen);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						colorBuf.put((byte) (color >> 16 & 0xFF));
						colorBuf.put((byte) (color >> 8 & 0xFF));
						colorBuf.put((byte) (color & 0xFF));
					}
				} else {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_LINES: {
				int vcLen = numPrimitives * 2 * 3;
				vcBuf = BufferUtils.createFloatBuffer(vcLen);
				for (int i = 0; i < vcLen; i++) {
					vcBuf.put(vertices[vo++]);
				}

				if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(vcLen);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						byte r = (byte) (color >> 16 & 0xFF);
						byte g = (byte) (color >> 8 & 0xFF);
						byte b = (byte) (color & 0xFF);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
					}
				} else {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_TRIANGLES: {
				int vcLen = numPrimitives * 3 * 3;
				vcBuf = BufferUtils.createFloatBuffer(vcLen);
				for (int i = 0; i < vcLen; i++) {
					vcBuf.put(vertices[vo++]);
				}
				if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_FACE) {
					ncBuf = BufferUtils.createFloatBuffer(vcLen);
					for (int end = no + numPrimitives * 3; no < end; ) {
						float x = normals[no++];
						float y = normals[no++];
						float z = normals[no++];
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
					}
				} else if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_VERTEX) {
					ncBuf = BufferUtils.createFloatBuffer(vcLen);
					for (int end = no + vcLen; no < end; ) {
						ncBuf.put(normals[no++]);
					}
				}
				boolean hasTexCoords = (command & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD;
				if (hasTexCoords) {
					if (env.getTexture() == null) {
						return;
					}
					int tcLen = numPrimitives * 3 * 2;
					tcBuf = BufferUtils.createByteBuffer(tcLen);
					for (int i = 0; i < tcLen; i++) {
						tcBuf.put((byte) textureCoords[to++]);
					}
				}
				if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(vcLen);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						byte r = (byte) (color >> 16 & 0xFF);
						byte g = (byte) (color >> 8 & 0xFF);
						byte b = (byte) (color & 0xFF);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
					}
				} else if (!hasTexCoords) {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_QUADS: {
				vcBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 3);
				for (int i = 0; i < numPrimitives; i++) {
					int offset = vo + i * 4 * 3;
					int pos = offset;
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos++]); // A
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos++]); // B
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos++]); // C
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos]);   // D
					pos = offset;
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos]);   // A
					pos = offset + 2 * 3;
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos]);   // C
				}
				if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_FACE) {
					ncBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 3);
					for (int end = no + numPrimitives * 3; no < end; ) {
						float x = normals[no++];
						float y = normals[no++];
						float z = normals[no++];
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
					}
				} else if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_VERTEX) {
					ncBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 3);
					for (int i = 0; i < numPrimitives; i++) {
						int offset = no + i * 4 * 3;
						int pos = offset;
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos++]); // A
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos++]); // B
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos++]); // C
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos]);   // D
						pos = offset;
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos]);   // A
						pos = offset + 2 * 3;
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos]);   // C
					}
				}
				boolean hasTexCoords = (command & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD;
				if (hasTexCoords) {
					if (env.getTexture() == null) {
						return;
					}
					tcBuf = BufferUtils.createByteBuffer(numPrimitives * 6 * 2);
					for (int i = 0; i < numPrimitives; i++) {
						int offset = to + i * 4 * 2;
						int pos = offset;
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos++]); // A
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos++]); // B
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos++]); // C
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos]);   // D
						pos = offset;
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos]);   // A
						pos = offset + 2 * 2;
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos]);   // C
					}
				}
				if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(numPrimitives * 6 * 3);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						byte r = (byte) (color >> 16 & 0xFF);
						byte g = (byte) (color >> 8 & 0xFF);
						byte b = (byte) (color & 0xFF);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
					}
				} else if (!hasTexCoords) {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_POINT_SPRITES: {
				if (env.getTexture() == null) {
					return;
				}
				int psParams = command & PDATA_TEXCOORD_MASK;
				if (psParams == 0) {
					return;
				}

				float[] vertex = new float[6 * 4];

				vcBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 4);
				tcBuf = BufferUtils.createByteBuffer(numPrimitives * 6 * 2);
				int angle = 0;
				float halfWidth = 0;
				float halfHeight = 0;
				byte tx0 = 0;
				byte ty0 = 0;
				byte tx1 = 0;
				byte ty1 = 0;
				float[] mvp = new float[16];
				MathUtil.multiplyMM(mvp, env.projMatrix, env.viewMatrix);
				for (int i = 0; i < numPrimitives; i++) {
					vertex[4] = vertices[vo++];
					vertex[5] = vertices[vo++];
					vertex[6] = vertices[vo++];
					vertex[7] = 1.0f;
					Utils.multiplyMV(vertex, mvp);

					if (psParams != Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD || i == 0) {
						float width = textureCoords[to++];
						float height = textureCoords[to++];
						angle = textureCoords[to++];
						tx0 = (byte) textureCoords[to++];
						ty0 = (byte) textureCoords[to++];
						tx1 = (byte) (textureCoords[to++] - 1);
						ty1 = (byte) (textureCoords[to++] - 1);
						switch (textureCoords[to++]) {
							case Graphics3D.POINT_SPRITE_LOCAL_SIZE | Graphics3D.POINT_SPRITE_PERSPECTIVE:
								halfWidth = width * env.projMatrix[0] * 0.5f;
								halfHeight = height * env.projMatrix[5] * 0.5f;
								break;
							case Graphics3D.POINT_SPRITE_PIXEL_SIZE | Graphics3D.POINT_SPRITE_PERSPECTIVE:
								if (env.projection <= Graphics3D.COMMAND_PARALLEL_SIZE) {
									halfWidth = width / env.width;
									halfHeight = height / env.height;
								} else {
									halfWidth = width / env.width * env.near;
									halfHeight = height / env.height * env.near;
								}
								break;
							case Graphics3D.POINT_SPRITE_LOCAL_SIZE | Graphics3D.POINT_SPRITE_NO_PERS:
								if (env.projection <= Graphics3D.COMMAND_PARALLEL_SIZE) {
									halfWidth = width * env.projMatrix[0] * 0.5f;
									halfHeight = height * env.projMatrix[5] * 0.5f;
								} else {
									float near = env.near;
									halfWidth = width * env.projMatrix[0] / near * 0.5f * vertex[3];
									halfHeight = height * env.projMatrix[5] / near * 0.5f * vertex[3];
								}
								break;
							case Graphics3D.POINT_SPRITE_PIXEL_SIZE | Graphics3D.POINT_SPRITE_NO_PERS:
								halfWidth = width / env.width * vertex[3];
								halfHeight = height / env.height * vertex[3];
								break;
							default:
								throw new IllegalArgumentException();
						}
					}

					Utils.getSpriteVertex(vertex, angle, halfWidth, halfHeight);
					vcBuf.put(vertex);

					tcBuf.put(tx0).put(ty1);
					tcBuf.put(tx0).put(ty0);
					tcBuf.put(tx1).put(ty1);
					tcBuf.put(tx1).put(ty1);
					tcBuf.put(tx0).put(ty0);
					tcBuf.put(tx1).put(ty0);
				}
				break;
			}
			default:
				throw new IllegalArgumentException();
		}
		TextureImpl texture = env.getTexture();
		FrameState.PrimitiveItem item = new FrameState.PrimitiveItem(
				command, vcBuf, ncBuf, tcBuf, colorBuf, texture);
		item.capture(env);
		env.items.addElement(item);
	}

	/** Clear the queued draw items without flushing (used on reset). */
	public void resetQueue() {
		env.items.clear();
	}

	/** Flush queued items through the backend without 2D background copy. */
	public void flushItems() {
		if (!env.items.isEmpty()) {
			backend.flushItems(env);
		}
	}

	/** Flush a full frame (2D background copy + queued items) through the backend. */
	public void flushFrame() {
		backend.flushFrame(env);
	}

	/**
	 * Sync the environment width/height to the currently bound target, so the
	 * fixed-point projection formulas (which encode the screen center) are
	 * evaluated against the right viewport. Called by Graphics3D before each
	 * projection setup.
	 */
	public void resetEnvironmentSize() {
		int w = backend.getTargetWidth();
		int h = backend.getTargetHeight();
		if (w > 0 && h > 0) {
			env.width = w;
			env.height = h;
		}
	}

	/**
	 * Interpret an immediate-mode command list (COMMAND_LIST_VERSION_1_0 .. COMMAND_END).
	 * Mirrors Render.drawCommandList: walks the int stream, mutating the environment
	 * (affine/center/clip/light/attribute/texture/toon/projection) and posting primitives.
	 */
	public void drawCommandList(int[] cmds) {
		if (Graphics3D.COMMAND_LIST_VERSION_1_0 != cmds[0]) {
			throw new IllegalArgumentException("Unsupported command list version: " + cmds[0]);
		}
		for (int i = 1; i < cmds.length; ) {
			int cmd = cmds[i++];
			switch (cmd & 0xFF000000) {
				case Graphics3D.COMMAND_AFFINE_INDEX:
					selectAffineTrans(cmd & 0xFFFFFF);
					break;
				case Graphics3D.COMMAND_AMBIENT_LIGHT: {
					env.light.ambIntensity = cmds[i++];
					break;
				}
				case Graphics3D.COMMAND_ATTRIBUTE:
					env.attrs = cmd & 0xFFFFFF;
					break;
				case Graphics3D.COMMAND_CENTER:
					setCenter(cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_CLIP:
					// clip rect handling is backend-specific; recorded in the environment
					i += 4;
					break;
				case Graphics3D.COMMAND_DIRECTION_LIGHT: {
					env.light.x = cmds[i++];
					env.light.y = cmds[i++];
					env.light.z = cmds[i++];
					env.light.dirIntensity = cmds[i++];
					break;
				}
				case Graphics3D.COMMAND_FLUSH:
					flushItems();
					resetQueue();
					break;
				case Graphics3D.COMMAND_NOP:
					i += cmd & 0xFFFFFF;
					break;
				case Graphics3D.COMMAND_PARALLEL_SCALE:
					setOrthographicScale(cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_PARALLEL_SIZE:
					setOrthographicWH(cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_PERSPECTIVE_FOV:
					setPerspectiveFov(cmds[i++], cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_PERSPECTIVE_WH:
					setPerspectiveWH(cmds[i++], cmds[i++], cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_TEXTURE_INDEX: {
					int tid = cmd & 0xFFFFFF;
					if (tid < 16) {
						env.textureIdx = tid;
					}
					break;
				}
				case Graphics3D.COMMAND_THRESHOLD:
					setToonParam(cmds[i++], cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_END:
					return;
				default: {
					int type = cmd & 0x7000000;
					if (type == 0 || cmd < 0) {
						throw new IllegalArgumentException();
					}
					int num = cmd >> 16 & 0xFF;
					int sizeOf = PRIMITIVE_SIZES[type >> 24];
					int len = num * 3 * sizeOf;
					int vo = i;
					i += len;
					int no = i;
					if ((cmd & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_FACE) {
						i += num * 3;
					} else if ((cmd & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_VERTEX) {
						i += len;
					}
					int to = i;
					if (type == Graphics3D.PRIMITVE_POINT_SPRITES) {
						if ((cmd & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD) {
							i += 8;
						} else if ((cmd & PDATA_TEXCOORD_MASK) != Graphics3D.PDATA_TEXURE_COORD_NONE) {
							i += num * 8;
						}
					} else if ((cmd & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD) {
						i += num * 2 * sizeOf;
					}

					int co = i;
					if ((cmd & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
						i++;
					} else if ((cmd & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_FACE) {
						i += num;
					} else if ((cmd & PDATA_COLOR_MASK) == PDATA_COLOR_PER_VERTEX) {
						i += num * sizeOf;
					}
					if (i > cmds.length) {
						throw new IllegalArgumentException();
					}
					postPrimitives(cmd, cmds, vo, cmds, no, cmds, to, cmds, co);
					break;
				}
			}
		}
	}
}
