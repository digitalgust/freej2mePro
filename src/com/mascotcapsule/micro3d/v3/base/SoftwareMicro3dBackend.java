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
 * Software backend for micro3D v3 (pure J2SE, no GL, no m3g dependency).
 *
 * Orchestrates the FrameState draw items over a Micro3dRasterizer:
 *   - projects vertices (Engine produces column-major proj*view),
 *   - builds a Shading per item (ambient+directional light / toon / sphere /
 *     per-polygon blend / colorKey),
 *   - runs the two-pass opaque (depth-write) then translucent (no depth-write)
 *     dispatch that v3 requires.
 *
 * The scan-conversion / depth / blend / texture / shading kernels live in
 * Micro3dRasterizer; this class only wires FrameState data into them.
 */
package com.mascotcapsule.micro3d.v3.base;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.recompile.mobile.PlatformGraphics;

import com.mascotcapsule.micro3d.v3.Graphics3D;

public class SoftwareMicro3dBackend implements Micro3dBackend {
    private static final float CLIP_EPSILON = 1.0e-5f;
    private static final int CLIP_PLANE_NEAR = 0;
    private static final int CLIP_PLANE_FAR = 1;

    private javax.microedition.lcdui.Graphics boundGraphics;
    private Micro3dSurface surface;
    private final Rectangle clip = new Rectangle();
    private int targetWidth;
    private int targetHeight;
    private float[] depthBuffer;
    private final float[] mvp = new float[16];

    private static final class ClipVertex {
        float cx, cy, cz, cw;
        float r, g, b, a;
        float u, v;
        float nx, ny, nz;
    }

    @Override
    public void bind(Object target, boolean doClip) {
        if (!(target instanceof javax.microedition.lcdui.Graphics)) {
            throw new IllegalStateException("Software backend only supports Graphics targets");
        }
        javax.microedition.lcdui.Graphics g = (javax.microedition.lcdui.Graphics) target;
        boundGraphics = g;
        BufferedImage canvas = canvasOf(g);
        surface = new Micro3dSurface.BufferedImageSurface(canvas);
        targetWidth = canvas.getWidth();
        targetHeight = canvas.getHeight();
        if (doClip) {
            clip.setBounds(g.getClipX(), g.getClipY(), g.getClipWidth(), g.getClipHeight());
        } else {
            clip.setBounds(0, 0, targetWidth, targetHeight);
        }
        if (depthBuffer == null || depthBuffer.length < targetWidth * targetHeight) {
            depthBuffer = new float[targetWidth * targetHeight];
        }
    }

    private static BufferedImage canvasOf(javax.microedition.lcdui.Graphics g) {
        PlatformGraphics pg;
        if (g instanceof PlatformGraphics) {
            pg = (PlatformGraphics) g;
        } else {
            pg = (PlatformGraphics) g.platformGraphics;
        }
        return pg.getCanvas();
    }

    @Override
    public int getTargetWidth() {
        return targetWidth;
    }

    @Override
    public int getTargetHeight() {
        return targetHeight;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void flushFrame(FrameState frame) {
        renderItems(frame);
    }

    @Override
    public void flushItems(FrameState frame) {
        renderItems(frame);
    }

    @Override
    public void release(Object target) {
        boundGraphics = null;
    }

    private void clearDepth() {
        if (depthBuffer != null) {
            for (int i = 0; i < depthBuffer.length; i++) {
                depthBuffer[i] = Float.POSITIVE_INFINITY;
            }
        }
    }

    private void renderItems(FrameState frame) {
        if (frame.items.isEmpty()) {
            return;
        }
        clearDepth();
        Micro3dRasterizer r = new Micro3dRasterizer(surface, clip, depthBuffer);
        // pass 0: opaque (depth write on), pass 1: translucent (depth write off)
        for (int pass = 0; pass < 2; pass++) {
            boolean depthWrite = (pass == 0);
            for (FrameState.DrawItem item : frame.items) {
                MathUtil.multiplyMM(mvp, item.projMatrix, item.viewMatrix);
                if (item instanceof FrameState.FigureItem) {
                    renderFigure(r, (FrameState.FigureItem) item, mvp, pass, depthWrite);
                } else if (item instanceof FrameState.PrimitiveItem) {
                    renderPrimitive(r, (FrameState.PrimitiveItem) item, mvp, pass, depthWrite);
                }
            }
        }
    }

    private void renderFigure(Micro3dRasterizer r, FrameState.FigureItem item,
                              float[] mvp, int pass, boolean depthWrite) {
        Model model = item.model;
        if (!model.hasPolyT && !model.hasPolyC) {
            return;
        }
        FloatBuffer vertices = item.vertices;
        FloatBuffer normals = item.normals;
        vertices.position(0);
        ByteBuffer texCoords = model.texCoordArray;
        if (model.hasPolyT && item.textures != null && item.textures.length > 0) {
            renderFigureTextured(r, item, vertices, normals, texCoords.duplicate(), pass, depthWrite);
        }
        if (model.hasPolyC) {
            renderFigureColored(r, item, vertices, normals, texCoords.duplicate(), pass, depthWrite);
        }
    }

    private void renderFigureTextured(Micro3dRasterizer r, FrameState.FigureItem item,
                                      FloatBuffer vertices, FloatBuffer normals, ByteBuffer tc,
                                      int pass, boolean depthWrite) {
        boolean semiTrans = (item.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0;
        int[][][] meshes = item.model.subMeshesLengthsT;
        int length = meshes.length;
        int blendIndex = 0;
        int pos = 0;
        if (semiTrans) {
            if (pass == 0) {
                length = 1;
            } else {
                int[][] mesh = meshes[blendIndex++];
                for (int[] lens : mesh) {
                    for (int cnt : lens) {
                        pos += cnt;
                    }
                }
            }
        } else if (pass == 1) {
            return;
        }

        while (blendIndex < length) {
            int[][] texMesh = meshes[blendIndex];
            int blendMode = semiTrans && pass == 1 ? (blendIndex << 1) : Micro3dRasterizer.BLEND_NORMAL;
            for (int face = 0; face < texMesh.length; face++) {
                TextureImpl tex = face < item.textures.length ? item.textures[face] : null;
                TextureData texData = tex != null ? tex.image : null;
                int[] lens = texMesh[face];
                int cnt = lens[0];
                if (cnt > 0) {
                    drawFigureBucket(r, item, vertices, normals, tc, pos, cnt, mvp, texData, blendMode, true);
                    pos += cnt;
                }
                cnt = lens[1];
                if (cnt > 0) {
                    drawFigureBucket(r, item, vertices, normals, tc, pos, cnt, mvp, texData, blendMode, false);
                    pos += cnt;
                }
            }
            blendIndex++;
        }
    }

    private void renderFigureColored(Micro3dRasterizer r, FrameState.FigureItem item,
                                     FloatBuffer vertices, FloatBuffer normals, ByteBuffer materialData,
                                     int pass, boolean depthWrite) {
        boolean semiTrans = (item.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0;
        int[][] meshes = item.model.subMeshesLengthsC;
        int length = meshes.length;
        int blendIndex = 0;
        int pos = 0;
        int startVertex = item.model.numVerticesPolyT;
        if (semiTrans) {
            if (pass == 0) {
                length = 1;
            } else {
                int[] bucket = meshes[blendIndex++];
                pos += bucket[0] + bucket[1];
            }
        } else if (pass == 1) {
            return;
        }
        while (blendIndex < length) {
            int[] bucket = meshes[blendIndex];
            int blendMode = semiTrans && pass == 1 ? (blendIndex << 1) : Micro3dRasterizer.BLEND_NORMAL;
            int cnt = bucket[0];
            if (cnt > 0) {
                drawFigureColorBucket(r, item, vertices, normals, materialData,
                        startVertex + pos, cnt, mvp, blendMode, true);
                pos += cnt;
            }
            cnt = bucket[1];
            if (cnt > 0) {
                drawFigureColorBucket(r, item, vertices, normals, materialData,
                        startVertex + pos, cnt, mvp, blendMode, false);
                pos += cnt;
            }
            blendIndex++;
        }
    }

    private void drawFigureBucket(Micro3dRasterizer r, FrameState.FigureItem item,
                                  FloatBuffer vertices, FloatBuffer normals, ByteBuffer texCoords,
                                  int start, int count, float[] mvp, TextureData texture,
                                  int blendMode, boolean cullBack) {
        boolean globalLight = (item.attrs & Graphics3D.ENV_ATTR_LIGHTING) != 0 && normals != null;
        boolean toon = (item.attrs & Graphics3D.ENV_ATTR_TOON_SHADING) != 0;
        TextureData sphereGlobal = (item.specular != null) ? item.specular.image : null;
        for (int triBase = start; triBase + 2 < start + count; triBase += 3) {
            int tcOff = triBase * 5;
            boolean lightFlag = (texCoords.get(tcOff + 2) & 0xFF) != 0;
            boolean specularFlag = (texCoords.get(tcOff + 3) & 0xFF) != 0;
            boolean transparentFlag = (texCoords.get(tcOff + 4) & 0xFF) != 0;
            Micro3dRasterizer.Shading s = makeShading(
                    texture,
                    specularFlag ? sphereGlobal : null,
                    item,
                    globalLight && lightFlag,
                    toon,
                    blendMode,
                    transparentFlag,
                    cullBack,
                    false);
            drawTriangle(r, vertices, normals, texCoords, triBase, mvp, item.viewMatrix, s,
                    blendMode == Micro3dRasterizer.BLEND_NORMAL);
        }
    }

    private void drawFigureColorBucket(Micro3dRasterizer r, FrameState.FigureItem item,
                                       FloatBuffer vertices, FloatBuffer normals, ByteBuffer materialData,
                                       int start, int count, float[] mvp, int blendMode, boolean cullBack) {
        boolean globalLight = (item.attrs & Graphics3D.ENV_ATTR_LIGHTING) != 0 && normals != null;
        boolean toon = (item.attrs & Graphics3D.ENV_ATTR_TOON_SHADING) != 0;
        TextureData sphereGlobal = (item.specular != null
                && (item.attrs & Graphics3D.ENV_ATTR_SPHERE_MAP) != 0) ? item.specular.image : null;
        for (int triBase = start; triBase + 2 < start + count; triBase += 3) {
            int tcOff = triBase * 5;
            boolean lightFlag = (materialData.get(tcOff + 3) & 0xFF) != 0;
            boolean specularFlag = (materialData.get(tcOff + 4) & 0xFF) != 0;
            Micro3dRasterizer.Shading s = makeShading(
                    null,
                    specularFlag ? sphereGlobal : null,
                    item,
                    globalLight && lightFlag,
                    toon,
                    blendMode,
                    false,
                    cullBack,
                    false);
            drawColorTriangle(r, vertices, normals, materialData, triBase, mvp, item.viewMatrix, s,
                    blendMode == Micro3dRasterizer.BLEND_NORMAL);
        }
    }

    private void renderPrimitive(Micro3dRasterizer r, FrameState.PrimitiveItem item,
                                 float[] mvp, int pass, boolean depthWrite) {
        int command = item.command;
        int type = command & 0x7000000;
        if (type == Graphics3D.PRIMITVE_POINTS) {
            renderPoints(r, item, mvp, pass, depthWrite);
            return;
        }
        if (type == Graphics3D.PRIMITVE_LINES) {
            renderLines(r, item, mvp, pass, depthWrite);
            return;
        }
        if (type == Graphics3D.PRIMITVE_POINT_SPRITES) {
            renderPointSprites(r, item, pass, depthWrite);
            return;
        }
        if (type != Graphics3D.PRIMITVE_TRIANGLES && type != Graphics3D.PRIMITVE_QUADS) {
            // points / lines: deferred
            return;
        }
        int blend = item.blendMode();
        int rawBlendBits = command & Graphics3D.PATTR_BLEND_SUB;
        boolean semiTrans = (item.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0;
        boolean drawThisPass = (blend == Micro3dRasterizer.BLEND_NORMAL) ? pass == 0 : pass == 1;
        if (!drawThisPass) {
            return;
        }
        TextureData texData = (item.texture != null) ? item.texture.image : null;
        boolean enableLight = (item.attrs & Graphics3D.ENV_ATTR_LIGHTING) != 0
                && (command & Graphics3D.PATTR_LIGHTING) != 0 && item.normals != null;
        TextureData sphere = null;
        if (enableLight && (item.attrs & Graphics3D.ENV_ATTR_SPHERE_MAP) != 0
                && (command & Graphics3D.PATTR_SPHERE_MAP) != 0 && item.specular != null) {
            sphere = item.specular.image;
        }
        boolean toon = (item.attrs & Graphics3D.ENV_ATTR_TOON_SHADING) != 0;
        boolean colorKey = (command & Graphics3D.PATTR_COLORKEY) != 0;
        Micro3dRasterizer.Shading s = makeShading(texData, sphere, item, enableLight, toon, blend, colorKey,
                false, false);

        FloatBuffer vertices = item.vertices;
        FloatBuffer normals = item.normals;
        ByteBuffer colors = item.colors;
        ByteBuffer texCoords = item.texCoords;
        int vertexCount = vertices.capacity() / 3;
        vertices.position(0);
        // Engine.postPrimitives() expands both PER_FACE and PER_VERTEX colors into a
        // packed per-vertex RGB buffer. Only PER_COMMAND stays as a single 3-byte color.
        boolean expandedVertexColors = colors != null
                && (command & Graphics3D.PDATA_COLOR_PER_COMMAND) != Graphics3D.PDATA_COLOR_PER_COMMAND;
        for (int triBase = 0; triBase + 2 < vertexCount; triBase += 3) {
            drawPrimitiveTriangle(r, item, vertices, normals, texCoords, colors, triBase,
                    mvp, s, expandedVertexColors, depthWrite, command);
        }
    }

    private void renderPoints(Micro3dRasterizer r, FrameState.PrimitiveItem item,
                              float[] mvp, int pass, boolean depthWrite) {
        int blend = item.blendMode();
        boolean drawThisPass = (blend == Micro3dRasterizer.BLEND_NORMAL) ? pass == 0 : pass == 1;
        if (!drawThisPass || item.vertices == null || item.colors == null) {
            return;
        }
        FloatBuffer vertices = item.vertices;
        ByteBuffer colors = item.colors;
        int vertexCount = vertices.capacity() / 3;
        int command = item.command;
        int perCommandColor = 0xFFFFFFFF;
        boolean expandedVertexColors = (command & Graphics3D.PDATA_COLOR_PER_COMMAND)
                != Graphics3D.PDATA_COLOR_PER_COMMAND;
        if (!expandedVertexColors && colors.capacity() >= 3) {
            perCommandColor = 0xFF000000 | ((colors.get(0) & 0xFF) << 16)
                    | ((colors.get(1) & 0xFF) << 8) | (colors.get(2) & 0xFF);
        }
        for (int i = 0; i < vertexCount; i++) {
            Micro3dRasterizer.Vertex v = projectPrimitiveVertex(vertices, mvp, i);
            if (v == null) {
                continue;
            }
            int color = expandedVertexColors ? vertexColor(colors, i) : perCommandColor;
            r.rasterPoint(v, color, blend, true, depthWrite);
        }
    }

    private void renderLines(Micro3dRasterizer r, FrameState.PrimitiveItem item,
                             float[] mvp, int pass, boolean depthWrite) {
        int blend = item.blendMode();
        boolean drawThisPass = (blend == Micro3dRasterizer.BLEND_NORMAL) ? pass == 0 : pass == 1;
        if (!drawThisPass || item.vertices == null || item.colors == null) {
            return;
        }
        FloatBuffer vertices = item.vertices;
        ByteBuffer colors = item.colors;
        int vertexCount = vertices.capacity() / 3;
        int command = item.command;
        int perCommandColor = 0xFFFFFFFF;
        boolean expandedVertexColors = (command & Graphics3D.PDATA_COLOR_PER_COMMAND)
                != Graphics3D.PDATA_COLOR_PER_COMMAND;
        if (!expandedVertexColors && colors.capacity() >= 3) {
            perCommandColor = 0xFF000000 | ((colors.get(0) & 0xFF) << 16)
                    | ((colors.get(1) & 0xFF) << 8) | (colors.get(2) & 0xFF);
        }
        for (int i = 0; i + 1 < vertexCount; i += 2) {
            Micro3dRasterizer.Vertex v0 = projectPrimitiveVertex(vertices, mvp, i);
            Micro3dRasterizer.Vertex v1 = projectPrimitiveVertex(vertices, mvp, i + 1);
            if (v0 == null || v1 == null) {
                continue;
            }
            int c0 = expandedVertexColors ? vertexColor(colors, i) : perCommandColor;
            int c1 = expandedVertexColors ? vertexColor(colors, i + 1) : perCommandColor;
            r.rasterLine(v0, v1, c0, c1, blend, true, depthWrite);
        }
    }

    private void renderPointSprites(Micro3dRasterizer r, FrameState.PrimitiveItem item,
                                    int pass, boolean depthWrite) {
        TextureData texData = (item.texture != null) ? item.texture.image : null;
        if (texData == null || item.vertices == null || item.texCoords == null) {
            return;
        }
        int blend = item.blendMode();
        boolean semiTrans = (item.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0;
        boolean drawThisPass = (blend == Micro3dRasterizer.BLEND_NORMAL) ? pass == 0 : pass == 1;
        if (!drawThisPass) {
            return;
        }
        Micro3dRasterizer.Shading s = makeShading(
                texData,
                null,
                item,
                false,
                false,
                blend,
                (item.command & Graphics3D.PATTR_COLORKEY) != 0,
                false,
                false,
                semiTrans);
        FloatBuffer vertices = item.vertices;
        ByteBuffer texCoords = item.texCoords;
        int vertexCount = vertices.capacity() / 4;
        for (int triBase = 0; triBase + 2 < vertexCount; triBase += 3) {
            drawClipSpaceTriangle(r, vertices, texCoords, triBase, s, depthWrite);
        }
    }

    private Micro3dRasterizer.Shading makeShading(TextureData texture, TextureData sphere,
                                                  FrameState.DrawItem item,
                                                  boolean enableLight, boolean toon,
                                                  int blend, boolean colorKey,
                                                  boolean cullBack, boolean cullFront) {
        return makeShading(texture, sphere, item, enableLight, toon, blend, colorKey,
                cullBack, cullFront, false);
    }

    private Micro3dRasterizer.Shading makeShading(TextureData texture, TextureData sphere,
                                                  FrameState.DrawItem item,
                                                  boolean enableLight, boolean toon,
                                                  int blend, boolean colorKey,
                                                  boolean cullBack, boolean cullFront,
                                                  boolean useTextureAlpha) {
        return new Micro3dRasterizer.Shading(
                texture, sphere, item.light, enableLight, toon,
                item.toonThreshold, item.toonHigh, item.toonLow,
                blend, colorKey,
                false /*flatShading*/,
                0 /*alphaThreshold*/,
                cullBack, cullFront, useTextureAlpha);
    }

    private void drawPrimitiveTriangle(Micro3dRasterizer r, FrameState.PrimitiveItem item,
                                       FloatBuffer vertices, FloatBuffer normals,
                                       ByteBuffer texCoords, ByteBuffer colors, int triBase,
                                       float[] mvp, Micro3dRasterizer.Shading s,
                                       boolean expandedVertexColors, boolean depthWrite, int command) {
        ClipVertex[] clipVertices = new ClipVertex[3];
        float[] cc = new float[4];
        for (int k = 0; k < 3; k++) {
            int vi = (triBase + k) * 3;
            float x = vertices.get(vi);
            float y = vertices.get(vi + 1);
            float z = vertices.get(vi + 2);
            Mat4.transformPoint(mvp, x, y, z, cc);
            ClipVertex v = new ClipVertex();
            v.cx = cc[0];
            v.cy = cc[1];
            v.cz = cc[2];
            v.cw = cc[3];
            v.r = v.g = v.b = 255;
            v.a = 255;
            if (expandedVertexColors && colors != null) {
                int ci = (triBase + k) * 3;
                if (ci + 2 < colors.capacity()) {
                    colors.position(ci);
                    v.r = colors.get() & 0xFF;
                    v.g = colors.get() & 0xFF;
                    v.b = colors.get() & 0xFF;
                }
            } else if (colors != null && colors.capacity() >= 3
                    && (command & Graphics3D.PDATA_COLOR_PER_COMMAND) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
                v.r = colors.get(0) & 0xFF;
                v.g = colors.get(1) & 0xFF;
                v.b = colors.get(2) & 0xFF;
            }
            if (normals != null) {
                int ni = (triBase + k) * 3;
                v.nx = normals.get(ni);
                v.ny = normals.get(ni + 1);
                v.nz = normals.get(ni + 2);
                if (s.enableLighting) {
                    transformNormal(item.viewMatrix, v);
                }
            }
            if (texCoords != null) {
                int ti = (triBase + k) * 2;
                if (ti + 1 < texCoords.capacity()) {
                    v.u = texCoords.get(ti) & 0xFF;
                    v.v = texCoords.get(ti + 1) & 0xFF;
                }
            }
            clipVertices[k] = v;
        }
        rasterClippedTriangle(r, clipVertices, s, depthWrite);
    }

    private void drawClipSpaceTriangle(Micro3dRasterizer r,
                                       FloatBuffer vertices, ByteBuffer texCoords,
                                       int triBase, Micro3dRasterizer.Shading s, boolean depthWrite) {
        ClipVertex[] clipVertices = new ClipVertex[3];
        for (int k = 0; k < 3; k++) {
            int vi = (triBase + k) * 4;
            ClipVertex v = new ClipVertex();
            v.cx = vertices.get(vi);
            v.cy = vertices.get(vi + 1);
            v.cz = vertices.get(vi + 2);
            v.cw = vertices.get(vi + 3);
            v.r = v.g = v.b = 255;
            v.a = 255;
            int ti = (triBase + k) * 2;
            if (ti + 1 < texCoords.capacity()) {
                v.u = texCoords.get(ti) & 0xFF;
                v.v = texCoords.get(ti + 1) & 0xFF;
            }
            clipVertices[k] = v;
        }
        rasterClippedTriangle(r, clipVertices, s, depthWrite);
    }

    private void drawTriangle(Micro3dRasterizer r,
                              FloatBuffer vertices, FloatBuffer normals, ByteBuffer texCoords,
                              int triBase, float[] mvp, float[] viewMatrix,
                              Micro3dRasterizer.Shading s, boolean depthWrite) {
        ClipVertex[] clipVertices = new ClipVertex[3];
        float[] cc = new float[4];
        for (int k = 0; k < 3; k++) {
            int vi = (triBase + k) * 3;
            float x = vertices.get(vi);
            float y = vertices.get(vi + 1);
            float z = vertices.get(vi + 2);
            Mat4.transformPoint(mvp, x, y, z, cc);
            ClipVertex v = new ClipVertex();
            v.cx = cc[0];
            v.cy = cc[1];
            v.cz = cc[2];
            v.cw = cc[3];
            v.r = v.g = v.b = 255;
            v.a = 255;
            if (normals != null) {
                int ni = (triBase + k) * 3;
                v.nx = normals.get(ni);
                v.ny = normals.get(ni + 1);
                v.nz = normals.get(ni + 2);
                if (s.enableLighting) {
                    transformNormal(viewMatrix, v);
                }
            }
            int tcOff = (triBase + k) * 5;
            v.u = texCoords.get(tcOff) & 0xFF;
            v.v = texCoords.get(tcOff + 1) & 0xFF;
            clipVertices[k] = v;
        }
        rasterClippedTriangle(r, clipVertices, s, depthWrite);
    }

    private void drawColorTriangle(Micro3dRasterizer r,
                                   FloatBuffer vertices, FloatBuffer normals, ByteBuffer materialData,
                                   int triBase, float[] mvp, float[] viewMatrix,
                                   Micro3dRasterizer.Shading s, boolean depthWrite) {
        ClipVertex[] clipVertices = new ClipVertex[3];
        float[] cc = new float[4];
        for (int k = 0; k < 3; k++) {
            int vi = (triBase + k) * 3;
            float x = vertices.get(vi);
            float y = vertices.get(vi + 1);
            float z = vertices.get(vi + 2);
            Mat4.transformPoint(mvp, x, y, z, cc);
            ClipVertex v = new ClipVertex();
            v.cx = cc[0];
            v.cy = cc[1];
            v.cz = cc[2];
            v.cw = cc[3];
            int tcOff = (triBase + k) * 5;
            v.r = materialData.get(tcOff) & 0xFF;
            v.g = materialData.get(tcOff + 1) & 0xFF;
            v.b = materialData.get(tcOff + 2) & 0xFF;
            v.a = 255;
            if (normals != null) {
                int ni = (triBase + k) * 3;
                v.nx = normals.get(ni);
                v.ny = normals.get(ni + 1);
                v.nz = normals.get(ni + 2);
                if (s.enableLighting) {
                    transformNormal(viewMatrix, v);
                }
            }
            clipVertices[k] = v;
        }
        rasterClippedTriangle(r, clipVertices, s, depthWrite);
    }

    private void rasterClippedTriangle(Micro3dRasterizer r, ClipVertex[] source,
                                       Micro3dRasterizer.Shading s, boolean depthWrite) {
        ClipVertex[] tmpA = new ClipVertex[8];
        ClipVertex[] tmpB = new ClipVertex[8];
        int count = source.length;
        for (int i = 0; i < count; i++) {
            tmpA[i] = source[i];
        }
        // Clip against the near plane using the SIGNED clip-space w. A vertex with
        // cw <= 0 lies on or behind the camera; keeping it (as the previous abs(w)
        // approximation did) lets the 1/cw perspective divide project it to garbage
        // screen coords, stretching huge triangles across the screen that overdraw
        // distant geometry (e.g. ground colour filling the night sky when a scene
        // is rotated so that ground/wall vertices cross behind the viewpoint).
        count = clipPolygonAgainstPlane(tmpA, count, tmpB, CLIP_PLANE_NEAR);
        if (count < 3) {
            return;
        }
        count = clipPolygonAgainstPlane(tmpB, count, tmpA, CLIP_PLANE_FAR);
        if (count < 3) {
            return;
        }
        Micro3dRasterizer.Vertex[] projected = new Micro3dRasterizer.Vertex[count];
        for (int i = 0; i < count; i++) {
            Micro3dRasterizer.Vertex v = new Micro3dRasterizer.Vertex();
            if (!projectToScreen(v, tmpA[i])) {
                return;
            }
            projected[i] = v;
        }
        for (int i = 1; i + 1 < count; i++) {
            r.rasterTriangle(projected[0], projected[i], projected[i + 1], s, true, depthWrite);
        }
    }

    private int clipPolygonAgainstPlane(ClipVertex[] input, int count, ClipVertex[] output, int plane) {
        if (count <= 0) {
            return 0;
        }
        int outCount = 0;
        ClipVertex prev = input[count - 1];
        float prevDist = clipDistance(prev, plane);
        boolean prevInside = prevDist >= 0f;
        for (int i = 0; i < count; i++) {
            ClipVertex curr = input[i];
            float currDist = clipDistance(curr, plane);
            boolean currInside = currDist >= 0f;
            if (currInside != prevInside) {
                float denom = prevDist - currDist;
                float t = Math.abs(denom) <= CLIP_EPSILON ? 0f : prevDist / denom;
                output[outCount++] = interpolateClipVertex(prev, curr, t);
            }
            if (currInside) {
                output[outCount++] = curr;
            }
            prev = curr;
            prevDist = currDist;
            prevInside = currInside;
        }
        return outCount;
    }

    private float clipDistance(ClipVertex v, int plane) {
        // Use the SIGNED clip-space w. The valid view volume requires cw > 0; any
        // vertex with cw <= 0 is behind/through the camera and must be clipped,
        // otherwise the perspective divide (1/cw) projects it to invalid screen
        // coordinates and stretches triangles across the whole framebuffer.
        switch (plane) {
            case CLIP_PLANE_NEAR:
                return v.cw + v.cz;   // inside when cz >= -cw  (and implicitly cw>0)
            case CLIP_PLANE_FAR:
                return v.cw - v.cz;   // inside when cz <=  cw
            default:
                return -1f;
        }
    }

    private ClipVertex interpolateClipVertex(ClipVertex a, ClipVertex b, float t) {
        ClipVertex out = new ClipVertex();
        out.cx = lerp(a.cx, b.cx, t);
        out.cy = lerp(a.cy, b.cy, t);
        out.cz = lerp(a.cz, b.cz, t);
        out.cw = lerp(a.cw, b.cw, t);
        out.r = lerp(a.r, b.r, t);
        out.g = lerp(a.g, b.g, t);
        out.b = lerp(a.b, b.b, t);
        out.a = lerp(a.a, b.a, t);
        out.u = lerp(a.u, b.u, t);
        out.v = lerp(a.v, b.v, t);
        out.nx = lerp(a.nx, b.nx, t);
        out.ny = lerp(a.ny, b.ny, t);
        out.nz = lerp(a.nz, b.nz, t);
        return out;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Transform model-space normal by the 3x3 view matrix (matches uNormalMatrix in tex.vsh).
     */
    private static void transformNormal(float[] viewMatrix, ClipVertex v) {
        float nx = v.nx;
        float ny = v.ny;
        float nz = v.nz;
        v.nx = nx * viewMatrix[0] + ny * viewMatrix[3] + nz * viewMatrix[6];
        v.ny = nx * viewMatrix[1] + ny * viewMatrix[4] + nz * viewMatrix[7];
        v.nz = nx * viewMatrix[2] + ny * viewMatrix[5] + nz * viewMatrix[8];
    }

    /**
     * Map a clip-space point to screen pixel coords + depth; sets visible=false if degenerate.
     */
    private boolean projectToScreen(Micro3dRasterizer.Vertex v, ClipVertex cv) {
        float cw = cv.cw;
        if (Math.abs(cw) < CLIP_EPSILON) {
            v.visible = false;
            return false;
        }
        float invW = 1.0f / cw;
        v.invW = invW;
        float ndcX = cv.cx * invW;
        float ndcY = cv.cy * invW;
        float ndcZ = cv.cz * invW;
        v.x = clip.x + ((ndcX * 0.5f) + 0.5f) * clip.width;
        v.y = clip.y + ((ndcY * 0.5f) + 0.5f) * clip.height;
        v.z = (ndcZ * 0.5f) + 0.5f;
        v.r = cv.r;
        v.g = cv.g;
        v.b = cv.b;
        v.a = cv.a;
        v.u = cv.u;
        v.v = cv.v;
        v.nx = cv.nx;
        v.ny = cv.ny;
        v.nz = cv.nz;
        v.visible = true;
        return true;
    }

    private Micro3dRasterizer.Vertex projectPrimitiveVertex(FloatBuffer vertices, float[] mvp, int vertexIndex) {
        int vi = vertexIndex * 3;
        float[] cc = new float[4];
        Mat4.transformPoint(mvp, vertices.get(vi), vertices.get(vi + 1), vertices.get(vi + 2), cc);
        ClipVertex cv = new ClipVertex();
        cv.cx = cc[0];
        cv.cy = cc[1];
        cv.cz = cc[2];
        cv.cw = cc[3];
        Micro3dRasterizer.Vertex out = new Micro3dRasterizer.Vertex();
        return projectToScreen(out, cv) ? out : null;
    }

    private static int vertexColor(ByteBuffer colors, int vertexIndex) {
        int ci = vertexIndex * 3;
        if (ci + 2 >= colors.capacity()) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | ((colors.get(ci) & 0xFF) << 16)
                | ((colors.get(ci + 1) & 0xFF) << 8) | (colors.get(ci + 2) & 0xFF);
    }
}
