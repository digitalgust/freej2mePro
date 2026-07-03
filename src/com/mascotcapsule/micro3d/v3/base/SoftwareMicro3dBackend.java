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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import org.recompile.mobile.PlatformGraphics;

import com.mascotcapsule.micro3d.v3.Graphics3D;

public class SoftwareMicro3dBackend implements Micro3dBackend {
    private static final float CLIP_EPSILON = 1.0e-5f;
    private static final int CLIP_PLANE_NEAR = 0;
    private static final int CLIP_PLANE_FAR = 1;
    // #region debug-point A:runtime-collector
    private static final String DEBUG_ENV_PATH = ".dbg/micro3d-texture-noise.env";
    private static volatile String debugServerUrl = "http://127.0.0.1:7777/event";
    private static volatile String debugSessionId = "micro3d-texture-noise";
    private static volatile boolean debugEnvLoaded;
    // Master switch for the runtime debug collector. When false, all debugSend()
    // calls become cheap no-ops (the hot path stays clean) so the game runs at full
    // speed. Set true only when actively capturing evidence for a specific issue.
    private static final boolean DEBUG_COLLECTOR_ENABLED = false;
    private static final boolean DEBUG_TRIANGLE_LOGS_ENABLED = false;
    private static final AtomicInteger DEBUG_FRAME_SEQ = new AtomicInteger();
    private static final AtomicInteger DEBUG_ITEM_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger DEBUG_TRI_LOG_COUNT = new AtomicInteger();

    private static void debugLoadEnv() {
        if (debugEnvLoaded) {
            return;
        }
        synchronized (SoftwareMicro3dBackend.class) {
            if (debugEnvLoaded) {
                return;
            }
            try {
                for (String line : Files.readAllLines(Paths.get(DEBUG_ENV_PATH), StandardCharsets.UTF_8)) {
                    if (line.startsWith("DEBUG_SERVER_URL=")) {
                        debugServerUrl = line.substring("DEBUG_SERVER_URL=".length()).trim();
                    } else if (line.startsWith("DEBUG_SESSION_ID=")) {
                        debugSessionId = line.substring("DEBUG_SESSION_ID=".length()).trim();
                    }
                }
            } catch (Throwable ignored) {
            }
            debugEnvLoaded = true;
        }
    }

    private static String debugEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) out.append(' ');
                    else out.append(c);
            }
        }
        return out.toString();
    }

    private static void debugSend(String runId, String hypothesisId, String location, String msg, String dataJson) {
        if (!DEBUG_COLLECTOR_ENABLED) {
            return;
        }
        debugLoadEnv();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(debugServerUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(150);
            conn.setReadTimeout(150);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            String body = "{\"sessionId\":\"" + debugEscape(debugSessionId) + "\","
                    + "\"runId\":\"" + debugEscape(runId) + "\","
                    + "\"hypothesisId\":\"" + debugEscape(hypothesisId) + "\","
                    + "\"location\":\"" + debugEscape(location) + "\","
                    + "\"msg\":\"" + debugEscape(msg) + "\","
                    + "\"data\":" + (dataJson == null ? "{}" : dataJson) + ","
                    + "\"ts\":" + System.currentTimeMillis() + "}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
            InputStream is = conn.getInputStream();
            while (is.read() != -1) {
            }
            is.close();
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    // #endregion

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
        // Optional one-shot frame dump for debugging texture/UV issues. Enabled by
        // -Dmicro3d.dumpframe=N (dump first N releases), writes PNGs next to the
        // process cwd. Cheap: only triggers on release, not in the hot raster loop.
        try {
            String dmp = System.getProperty("micro3d.dumpframe");
            if (dmp != null && surface != null) {
                int limit = Integer.parseInt(dmp);
                if (dumpCount > 1000 && dumpCount < 1000 + limit) {
                    BufferedImage img = ((Micro3dSurface.BufferedImageSurface) surface).getImage();
                    java.io.File out = new java.io.File("micro3d-frame-" + dumpCount + ".png");
                    javax.imageio.ImageIO.write(img, "png", out);
                }
                    dumpCount++;
            }
        } catch (Throwable ignored) {
        }
        boundGraphics = null;
    }

    private static int dumpCount;

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
        int figureCount = 0;
        int primitiveCount = 0;
        for (FrameState.DrawItem item : frame.items) {
            if (item instanceof FrameState.FigureItem) figureCount++;
            else if (item instanceof FrameState.PrimitiveItem) primitiveCount++;
        }
        int frameSeq = DEBUG_FRAME_SEQ.incrementAndGet();
        DEBUG_ITEM_LOG_COUNT.set(0);
        DEBUG_TRI_LOG_COUNT.set(0);
        // #region debug-point B:frame-summary
        debugSend("post-fix", "B", "SoftwareMicro3dBackend.renderItems", "[DEBUG] frame-summary",
                "{\"frameSeq\":" + frameSeq + ",\"items\":" + frame.items.size()
                        + ",\"figures\":" + figureCount + ",\"primitives\":" + primitiveCount
                        + ",\"clipW\":" + clip.width + ",\"clipH\":" + clip.height + "}");
        // #endregion
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
        if (!model.hasPolyT || item.textures == null || item.textures.length == 0) {
            // TODO(phase 3): polyC (vertex-colored) figure path
            return;
        }
        boolean semiTrans = (item.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0;
        FloatBuffer vertices = item.vertices;
        FloatBuffer normals = item.normals;
        vertices.position(0);
        ByteBuffer texCoords = model.texCoordArray;
        ByteBuffer tc = texCoords.duplicate();
        int[][][] meshes = model.subMeshesLengthsT;
        if (DEBUG_ITEM_LOG_COUNT.getAndIncrement() < 6) {
            // #region debug-point B:figure-batch
            debugSend("post-fix", "B", "SoftwareMicro3dBackend.renderFigure", "[DEBUG] figure-batch",
                    "{\"pass\":" + pass + ",\"depthWrite\":" + depthWrite + ",\"textures\":" + item.textures.length
                            + ",\"numVerticesPolyT\":" + model.numVerticesPolyT + ",\"hasPolyT\":" + model.hasPolyT
                            + ",\"semiTrans\":" + (((item.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0) ? "true" : "false") + "}");
            // #endregion
        }
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

    private void renderPrimitive(Micro3dRasterizer r, FrameState.PrimitiveItem item,
                                 float[] mvp, int pass, boolean depthWrite) {
        int command = item.command;
        int type = command & 0x7000000;
        if (type != Graphics3D.PRIMITVE_TRIANGLES && type != Graphics3D.PRIMITVE_QUADS) {
            // points / lines / sprites: deferred
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
        if (DEBUG_ITEM_LOG_COUNT.getAndIncrement() < 14) {
            int texW = texData != null ? texData.width : -1;
            int texH = texData != null ? texData.height : -1;
            // #region debug-point A:primitive-batch
            debugSend("post-fix", "A", "SoftwareMicro3dBackend.renderPrimitive", "[DEBUG] primitive-batch",
                    "{\"command\":" + command + ",\"type\":" + type + ",\"pass\":" + pass + ",\"depthWrite\":" + depthWrite
                            + ",\"blend\":" + blend + ",\"rawBlendBits\":" + rawBlendBits
                            + ",\"semiTrans\":" + (semiTrans ? "true" : "false") + ",\"vertexCount\":" + vertexCount
                            + ",\"texCoordsCap\":" + (texCoords != null ? texCoords.capacity() : -1)
                            + ",\"colorsCap\":" + (colors != null ? colors.capacity() : -1)
                            + ",\"normalsCap\":" + (normals != null ? normals.capacity() : -1)
                            + ",\"perVertexColor\":" + (expandedVertexColors ? "true" : "false")
                            + ",\"colorMode\":" + (command & (Graphics3D.PDATA_COLOR_PER_COMMAND | Graphics3D.PDATA_COLOR_PER_FACE))
                            + ",\"textured\":" + (texCoords != null ? "true" : "false")
                            + ",\"colorKey\":" + (colorKey ? "true" : "false")
                            + ",\"texW\":" + texW + ",\"texH\":" + texH + "}");
            // #endregion
        }
        for (int triBase = 0; triBase + 2 < vertexCount; triBase += 3) {
            drawPrimitiveTriangle(r, item, vertices, normals, texCoords, colors, triBase,
                    mvp, s, expandedVertexColors, depthWrite, command);
        }
    }

    private Micro3dRasterizer.Shading makeShading(TextureData texture, TextureData sphere,
                                                  FrameState.DrawItem item,
                                                  boolean enableLight, boolean toon,
                                                  int blend, boolean colorKey,
                                                  boolean cullBack, boolean cullFront) {
        return new Micro3dRasterizer.Shading(
                texture, sphere, item.light, enableLight, toon,
                item.toonThreshold, item.toonHigh, item.toonLow,
                blend, colorKey,
                false /*flatShading*/,
                0 /*alphaThreshold*/,
                cullBack, cullFront);
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
        if (DEBUG_TRIANGLE_LOGS_ENABLED && DEBUG_TRI_LOG_COUNT.getAndIncrement() < 24) {
            int texW = item.texture != null ? item.texture.image.width : -1;
            int texH = item.texture != null ? item.texture.image.height : -1;
            // #region debug-point A:primitive-triangle
            debugSend("post-fix", "A", "SoftwareMicro3dBackend.drawPrimitiveTriangle", "[DEBUG] primitive-triangle",
                    "{\"triBase\":" + triBase + ",\"command\":" + command + ",\"depthWrite\":" + depthWrite
                            + ",\"perVertexColor\":" + (expandedVertexColors ? "true" : "false")
                            + ",\"u0\":" + clipVertices[0].u + ",\"v0\":" + clipVertices[0].v + ",\"u1\":" + clipVertices[1].u + ",\"v1\":" + clipVertices[1].v
                            + ",\"u2\":" + clipVertices[2].u + ",\"v2\":" + clipVertices[2].v
                            + ",\"cw0\":" + clipVertices[0].cw + ",\"cw1\":" + clipVertices[1].cw + ",\"cw2\":" + clipVertices[2].cw
                            + ",\"r0\":" + clipVertices[0].r + ",\"g0\":" + clipVertices[0].g + ",\"b0\":" + clipVertices[0].b
                            + ",\"texW\":" + texW + ",\"texH\":" + texH + ",\"colorKey\":" + (s.colorKey ? "true" : "false")
                            + ",\"blend\":" + s.blendMode + "}");
            // #endregion
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
}
