/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package javax.microedition.m3g;

import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.m3g.base.M3GMath;

import org.recompile.freej2me.FreeJ2ME;
import org.recompile.mobile.PlatformGraphics;

import javax.microedition.lcdui.Graphics;

public class Graphics3D {
    public static final int ANTIALIAS = 2;
    public static final int DITHER = 4;
    public static final int OVERWRITE = 16;
    public static final int TRUE_COLOR = 8;


    private static Hashtable properties;
    private static final int VALID_HINTS = ANTIALIAS | DITHER | OVERWRITE | TRUE_COLOR;
    private static final String MINIJVM_BACKEND_FACTORY = "javax.microedition.m3g.MiniJvmGraphics3DFactory";
    private static final int MAX_TEXTURE_UNITS = 2;

    private int viewx;
    private int viewy;
    private int vieww;
    private int viewh;

    private static String fmt(float v) {
        return (v >= 0 ? " " : "") + Math.round(v * 10f) / 10f;
    }

    // 诊断开关统一入口：
    //   -Dfreej2me.diag.render  打印每次 render 的视口/网格/变换/Sprite 跳过原因
    //   -Dfreej2me.diag.tint    按顶点数把网格染成纯色，忽略纹理，用于定位几何
    // 默认关闭，正常游戏不应有任何 diag 输出。
    private static boolean diagRenderEnabled() {
        return System.getProperty("freej2me.diag.render") != null;
    }

    private static boolean diagTintEnabled() {
        return System.getProperty("freej2me.diag.tint") != null;
    }

    private float near = 0f;
    private float far = 1f;
    private Object target;
    private boolean depthBufferEnabled;
    private int hints;
    private Camera camera;
    private final Transform cameraTransform = new Transform();
    private final Vector<Light> lights = new Vector<Light>();
    private final Vector<Transform> lightTransforms = new Vector<Transform>();
    private float[] depthBuffer;
    private final Hashtable morphedVertexBufferCache = new Hashtable();
    private final Backend softwareBackend;
    private final Backend backend;
    private final java.util.HashSet paraStackLogged = new java.util.HashSet();

    static {
        properties = new Hashtable();
        properties.put("supportAntialiasing", Boolean.FALSE);
        properties.put("supportTrueColor", Boolean.FALSE);
        properties.put("supportDithering", Boolean.FALSE);
        properties.put("supportMipmapping", Boolean.FALSE);
        properties.put("supportPerspectiveCorrection", Boolean.FALSE);
        properties.put("supportLocalCameraLighting", Boolean.FALSE);
        properties.put("maxLights", Integer.valueOf(8));
        properties.put("maxViewportWidth", Integer.valueOf(4096));
        properties.put("maxViewportHeight", Integer.valueOf(4096));
        properties.put("maxViewportDimension", Integer.valueOf(4096));
        properties.put("maxTextureDimension", Integer.valueOf(4096));
        properties.put("maxSpriteCropDimension", Integer.valueOf(4096));
        properties.put("maxTransformsPerVertex", Integer.valueOf(2));
        properties.put("numTextureUnits", Integer.valueOf(2));
    }


    public Graphics3D() {
        softwareBackend = new SoftwareRenderBackend(this);
        backend = createBackend();
    }


    public int addLight(Light light, Transform transform) {
        if (light == null) {
            throw new NullPointerException();
        }
        lights.addElement(light);
        lightTransforms.addElement(copyTransform(transform));
        return lights.size() - 1;
    }

    public void bindTarget(java.lang.Object target) {
        bindTarget(target, true, 0);
    }

    public void bindTarget(java.lang.Object target, boolean depthBuffer, int hints) {
        if (target == null) {
            throw new NullPointerException();
        }
        if (this.target != null) {
            throw new IllegalStateException();
        }
        if ((hints & ~VALID_HINTS) != 0) {
            throw new IllegalArgumentException();
        }

        int width;
        int height;

        if (target instanceof Graphics) {
            Graphics graphics = (Graphics) target;
            width = graphics.getClipWidth();
            height = graphics.getClipHeight();
            viewx = graphics.getClipX();
            viewy = graphics.getClipY();
        } else if (target instanceof Image2D) {
            Image2D image = (Image2D) target;
            if (!image.isMutable()) {
                throw new IllegalArgumentException();
            }
            if (image.getFormat() != Image2D.RGB && image.getFormat() != Image2D.RGBA) {
                throw new IllegalArgumentException();
            }
            width = image.getWidth();
            height = image.getHeight();
            viewx = 0;
            viewy = 0;
        } else {
            throw new IllegalArgumentException();
        }

        checkViewport(width, height);
        this.target = target;
        this.depthBufferEnabled = depthBuffer;
        this.hints = hints;
        vieww = width;
        viewh = height;
        ensureDepthBuffer();
        backend.bindTarget(target, depthBuffer, hints);
    }

    public void clear(Background background) {
        ensureTargetBound();
        backend.clear(background);
    }

    public Camera getCamera(Transform transform) {
        if (transform != null && camera != null) {
            transform.set(cameraTransform);
        }
        return camera;
    }

    public float getDepthRangeFar() {
        return far;
    }

    public float getDepthRangeNear() {
        return near;
    }

    public int getHints() {
        return hints;
    }

    public static Graphics3D getInstance() {
        return FreeJ2ME.getMobile().getGraphics3D();
    }

    public Light getLight(int index, Transform transform) {
        if (index < 0 || index >= lights.size()) {
            throw new IndexOutOfBoundsException();
        }
        if (transform != null) {
            transform.set(lightTransforms.elementAt(index));
        }
        return lights.elementAt(index);
    }

    public int getLightCount() {
        return lights.size();
    }

    public static Hashtable getProperties() {
        return properties;
    }

    public Object getTarget() {
        return target;
    }

    public int getViewportHeight() {
        return viewh;
    }

    public int getViewportWidth() {
        return vieww;
    }

    public int getViewportX() {
        return viewx;
    }

    public int getViewportY() {
        return viewy;
    }

    public boolean isDepthBufferEnabled() {
        return depthBufferEnabled;
    }

    public void releaseTarget() {
        backend.releaseTarget();
        target = null;
        depthBufferEnabled = false;
        depthBuffer = null;
        hints = 0;
        viewx = 0;
        viewy = 0;
        vieww = 0;
        viewh = 0;
    }

    public void render(Node node, Transform transform) {
        if (node == null) {
            throw new NullPointerException();
        }
        ensureTargetBound();
        ensureCameraBound();
        if (!node.isRenderingEnabled()) {
            return;
        }

        // Per JSR-184 reference implementation (m3gRenderNode), the transform
        // argument is applied as the root node's parent transform: the final
        // camera-space transform is (viewTransform * transform), and the root
        // node's OWN local/composite transform is NOT applied here. Only the
        // local transforms of DESCENDANT nodes are accumulated during traversal.
        // (Mesh.setupRender in the RI uses s.toCamera directly, without
        // multiplying in the mesh composite.)
        Transform combined = copyTransform(transform);

        // DIAGNOSTIC (Tower Bloxx): enabled only when -Dfreej2me.diag.render is set.
        // Logs every root render() call with class + blend mode + vertex count, to
        // identify the parachute mesh and tell when it stops/keeps rendering.
        if (camera != null && node instanceof Mesh
                && diagRenderEnabled()) {
            try {
                float[] m = new float[16];
                combined.get(m);
                Mesh dm = (Mesh) node;
                String blend = "?";
                int verts = -1;
                try {
                    if (dm.getSubmeshCount() > 0) {
                        Appearance ap = dm.getAppearance(0);
                        if (ap != null && ap.getCompositingMode() != null) {
                            blend = String.valueOf(ap.getCompositingMode().getBlending());
                        }
                    }
                    if (dm.getVertexBuffer() != null) {
                        verts = dm.getVertexBuffer().getVertexCount();
                    }
                } catch (Throwable e) { /* ignore */ }
                System.out.println("[diag.render] vp=" + viewx + "," + viewy + "," + vieww + "x" + viewh
                        + " " + node.getClass().getSimpleName()
                        + " blend=" + blend + " verts=" + verts
                        + " tx=" + fmt(m[3]) + " ty=" + fmt(m[7]) + " tz=" + fmt(m[11]));
            } catch (Throwable t) { /* ignore */ }
        }

        renderNodeWithTransform(node, combined);
    }

    /**
     * Renders a node using the given accumulated camera-space transform.
     * For the root call (from render(Node,Transform)) the node's own local
     * transform has already been excluded; for descendant nodes visited here,
     * the child's local transform is multiplied in to match the reference
     * implementation's per-child composite accumulation.
     */
    private void renderNodeWithTransform(Node node, Transform combined) {
        // Per JSR-184 spec (and reference m3gRenderNode), a node is only rendered
        // when its scope has at least one bit in common with the active camera's
        // scope: (node.scope & camera.scope) != 0. Games use setScope(0) to hide
        // nodes (e.g. Tower Bloxx hides parachute characters this way). Without
        // this check, hidden nodes still render -> "never disappears" bug.
        if (camera != null && (node.getScope() & camera.getScope()) == 0) {
            return;
        }

        // Per JSR-184 spec, a node with renderingEnabled == false is never rendered.
        // The reference implementation (and KEmulator's RenderPipe.isVisible) walk
        // the whole ancestor chain, so a single disabled Group hides its entire
        // subtree. PogoRoo's pogoroo.m3g authors the ScoreBoard branch with
        // renderingEnabled == false to keep the HUD hidden; without this check
        // freej2me still draws the ScoreCard sprite ("1-10") and the star mesh.
        if (!node.isRenderingEnabled()) {
            return;
        }

        if (node instanceof Mesh) {
            Mesh mesh = (Mesh) node;
            for (int i = 0; i < mesh.getSubmeshCount(); i++) {
                Appearance appearance = mesh.getAppearance(i);
                if (appearance != null) {
                    IndexBuffer triangles = mesh.getIndexBuffer(i);
                    if (mesh instanceof SkinnedMesh
                            && backend instanceof SkinningBackend
                            && triangles instanceof TriangleStripArray
                            && ((SkinningBackend) backend).renderSkinned((SkinnedMesh) mesh, (TriangleStripArray) triangles, appearance, combined)) {
                        continue;
                    }
                    VertexBuffer vertices;
                    if (mesh instanceof MorphingMesh) {
                        vertices = createMorphedVertexBuffer((MorphingMesh) mesh);
                    } else if (mesh instanceof SkinnedMesh) {
                        vertices = createSkinnedVertexBuffer((SkinnedMesh) mesh);
                    } else {
                        vertices = mesh.getVertexBuffer();
                    }
                    render(mesh, i, vertices, triangles, appearance, combined, node.getScope());
                }
            }
            return;
        }
        if (node instanceof Sprite3D) {
            renderSprite((Sprite3D) node, combined, node.getScope());
            return;
        }
        if (node instanceof Group) {
            Group group = (Group) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                Node child = group.getChild(i);
                if (child instanceof Mesh || child instanceof Sprite3D || child instanceof Group) {
                    // Accumulate the child's own local transform, matching the
                    // reference traversal: child.toCamera = parent.toCamera * child.composite.
                    Transform childCombined = copyTransform(combined);
                    Transform childLocal = new Transform();
                    child.getTransform(childLocal);
                    childCombined.postMultiply(childLocal);
                    renderNodeWithTransform(child, childCombined);
                }
            }
            return;
        }

        throw new IllegalArgumentException();
    }

    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform) {
        render(vertices, triangles, appearance, transform, -1);
    }

    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform, int scope) {
        render(null, -1, vertices, triangles, appearance, transform, scope);
    }

    private void render(Mesh mesh, int submeshIndex, VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform, int scope) {
        if (vertices == null || triangles == null || appearance == null) {
            throw new NullPointerException();
        }
        ensureTargetBound();
        ensureCameraBound();
        validateGeometry(vertices, triangles);
        if (triangles instanceof TriangleStripArray) {
            backend.render(mesh, submeshIndex, vertices, (TriangleStripArray) triangles, appearance, transform);
        }
    }

    public void render(World world) {
        if (world == null) {
            throw new NullPointerException();
        }
        ensureTargetBound();
        if (world.getActiveCamera() == null) {
            throw new IllegalStateException();
        }

        clear(world.getBackground());
        Transform worldCameraTransform = new Transform();
        world.getActiveCamera().getCompositeTransform(worldCameraTransform);
        setCamera(world.getActiveCamera(), worldCameraTransform);
        resetLights();
        collectLights(world);

        for (int i = 0; i < world.getChildCount(); i++) {
            Node child = world.getChild(i);
            if (child instanceof Mesh || child instanceof Sprite3D || child instanceof Group) {
                // In render(World), each world child is a descendant of the world
                // root, so its own composite transform MUST be applied (unlike the
                // explicit render(Node,Transform) entry point where the root node's
                // local is intentionally skipped per the JSR-184 reference). Walk
                // the subtree applying each node's local transform.
                Transform childCombined = copyTransform(null); // identity base
                Transform childLocal = new Transform();
                child.getTransform(childLocal);
                childCombined.postMultiply(childLocal);
                renderNodeWithTransform(child, childCombined);
            }
        }
    }

    public void resetLights() {
        lights.removeAllElements();
        lightTransforms.removeAllElements();
    }

    public void setCamera(Camera camera, Transform transform) {
        if (camera == null) {
            this.camera = null;
            cameraTransform.setIdentity();
            return;
        }

        Transform copy = copyTransform(transform);
        this.camera = camera;
        this.cameraTransform.set(copy);
    }

    public void setDepthRange(float Near, float Far) {
        if (Near < 0f || Near > 1f || Far < 0f || Far > 1f) {
            throw new IllegalArgumentException();
        }
        near = Near;
        far = Far;
    }

    public void setLight(int index, Light light, Transform transform) {
        if (index < 0 || index >= lights.size()) {
            throw new IndexOutOfBoundsException();
        }
        lights.setElementAt(light, index);
        lightTransforms.setElementAt(copyTransform(transform), index);
    }

    public void setViewport(int x, int y, int width, int height) {
        checkViewport(width, height);
        viewx = x;
        viewy = y;
        vieww = width;
        viewh = height;
        ensureDepthBuffer();
    }

    private Transform copyTransform(Transform transform) {
        return transform != null ? new Transform(transform) : new Transform();
    }

    private Backend createBackend() {
        String factoryName = System.getProperty("freej2me.m3g.backendFactory");
        if (factoryName == null && isMiniJvmRuntime()) {
            factoryName = MINIJVM_BACKEND_FACTORY;
        }
        if (factoryName == null || factoryName.length() == 0) {
            return softwareBackend;
        }

        try {
            BackendFactory factory = (BackendFactory) Class.forName(factoryName).newInstance();
            Backend created = factory.create(this, softwareBackend);
            return created != null ? created : softwareBackend;
        } catch (Throwable ignored) {
            return softwareBackend;
        }
    }

    private static boolean isMiniJvmRuntime() {
        return System.getProperty("java.vendor", "").toLowerCase().indexOf("minijvm") >= 0;
    }

    private void validateGeometry(VertexBuffer vertices, IndexBuffer triangles) {
        VertexArray positions = vertices.getPositions(null);
        if (positions == null) {
            throw new IllegalStateException();
        }
        if (!(triangles instanceof TriangleStripArray)) {
            throw new IllegalStateException();
        }
        int[] indices = ((TriangleStripArray) triangles).getRawIndices();
        int vertexCount = vertices.getVertexCount();
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] >= vertexCount) {
                throw new IllegalStateException();
            }
        }
    }

    private void ensureTargetBound() {
        if (target == null) {
            throw new IllegalStateException();
        }
    }

    private void ensureCameraBound() {
        if (camera == null) {
            throw new IllegalStateException();
        }
    }

    private void checkViewport(int width, int height) {
        int maxWidth = ((Integer) properties.get("maxViewportWidth")).intValue();
        int maxHeight = ((Integer) properties.get("maxViewportHeight")).intValue();
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
            throw new IllegalArgumentException();
        }
    }

    private void collectLights(Group group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Node child = group.getChild(i);
            if (child instanceof Light && child.isRenderingEnabled()) {
                Transform lightTransform = new Transform();
                child.getCompositeTransform(lightTransform);
                addLight((Light) child, lightTransform);
            }
            if (child instanceof Group) {
                collectLights((Group) child);
            }
        }
    }

    private void fillImage(Image2D image, int color) {
        byte[] data = image.getImageData();
        if (data == null) {
            return;
        }
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        switch (image.getFormat()) {
            case Image2D.ALPHA:
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) a;
                }
                break;
            case Image2D.LUMINANCE: {
                byte luminance = (byte) ((r + g + b) / 3);
                for (int i = 0; i < data.length; i++) {
                    data[i] = luminance;
                }
                break;
            }
            case Image2D.LUMINANCE_ALPHA: {
                byte luminance = (byte) ((r + g + b) / 3);
                for (int i = 0; i < data.length; i += 2) {
                    data[i] = luminance;
                    data[i + 1] = (byte) a;
                }
                break;
            }
            case Image2D.RGB:
                for (int i = 0; i < data.length; i += 3) {
                    data[i] = (byte) r;
                    data[i + 1] = (byte) g;
                    data[i + 2] = (byte) b;
                }
                break;
            case Image2D.RGBA:
                for (int i = 0; i < data.length; i += 4) {
                    data[i] = (byte) r;
                    data[i + 1] = (byte) g;
                    data[i + 2] = (byte) b;
                    data[i + 3] = (byte) a;
                }
                break;
            default:
                break;
        }
    }

    private void clearWithSoftwareBackend(Background background) {
        int color = 0x00000000;
        boolean clearColor = true;
        if (background != null) {
            color = background.getColor();
            clearColor = background.isColorClearEnabled();
            if (target instanceof Image2D) {
                Image2D image = (Image2D) target;
                Image2D backgroundImage = background.getImage();
                if (backgroundImage != null && backgroundImage.getFormat() != image.getFormat()) {
                    throw new IllegalArgumentException();
                }
            }
        }

        if (clearColor) {
            if (target instanceof PlatformGraphics) {
                PlatformGraphics graphics = (PlatformGraphics) target;
                graphics.setColor(color & 0x00FFFFFF);
                graphics.fillRect(viewx, viewy, vieww, viewh);
            } else if (target instanceof Graphics) {
                Graphics graphics = (Graphics) target;
                graphics.setColor(color & 0x00FFFFFF);
                graphics.fillRect(viewx, viewy, vieww, viewh);
            } else if (target instanceof Image2D) {
                fillImage((Image2D) target, color);
            }
        }

        if (background != null) {
            renderBackgroundImage(background);
        }

        clearDepth(background);
    }

    private void renderBackgroundImage(Background background) {
        Image2D backgroundImage = background.getImage();
        if (backgroundImage == null) {
            return;
        }
        if (backgroundImage.getFormat() != Image2D.RGB && backgroundImage.getFormat() != Image2D.RGBA) {
            if (diagRenderEnabled()) {
                System.out.println("[M3G][Background] skip image render: unsupported image format="
                        + imageFormatName(backgroundImage.getFormat()) + "(" + backgroundImage.getFormat() + ")");
            }
            return;
        }
        RenderSurface surface = getRenderSurface();
        if (surface == null) {
            if (diagRenderEnabled()) {
                System.out.println("[M3G][Background] skip image render: no render surface for target="
                        + (target == null ? "null" : target.getClass().getName()));
            }
            return;
        }

        int sourceWidth = background.getCropWidth() > 0 ? background.getCropWidth() : backgroundImage.getWidth();
        int sourceHeight = background.getCropHeight() > 0 ? background.getCropHeight() : backgroundImage.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            if (diagRenderEnabled()) {
                System.out.println("[M3G][Background] skip image render: invalid crop size "
                        + sourceWidth + "x" + sourceHeight
                        + " crop=(" + background.getCropX() + "," + background.getCropY()
                        + "," + background.getCropWidth() + "," + background.getCropHeight() + ")");
            }
            return;
        }
        for (int y = 0; y < viewh; y++) {
            int sampleY = resolveBackgroundCoordinate(y, viewh, background.getCropY(), sourceHeight, backgroundImage.getHeight(), background.getImageModeY());
            if (sampleY < 0) {
                continue;
            }
            for (int x = 0; x < vieww; x++) {
                int sampleX = resolveBackgroundCoordinate(x, vieww, background.getCropX(), sourceWidth, backgroundImage.getWidth(), background.getImageModeX());
                if (sampleX < 0) {
                    continue;
                }
                surface.setPixel(viewx + x, viewy + y, getImagePixel(backgroundImage, sampleX, sampleY));
            }
        }
    }

    private static String imageFormatName(int format) {
        switch (format) {
            case Image2D.ALPHA:
                return "ALPHA";
            case Image2D.LUMINANCE:
                return "LUMINANCE";
            case Image2D.LUMINANCE_ALPHA:
                return "LUMINANCE_ALPHA";
            case Image2D.RGB:
                return "RGB";
            case Image2D.RGBA:
                return "RGBA";
            default:
                return "UNKNOWN";
        }
    }

    private int resolveBackgroundCoordinate(int viewportCoordinate, int viewportSize, int cropOrigin, int cropSize, int imageSize, int mode) {
        float normalized = ((float) viewportCoordinate + 0.5f) / (float) viewportSize;
        int sample = (int) Math.floor(cropOrigin + (normalized * cropSize));
        if (mode == Background.REPEAT) {
            int wrapped = sample % imageSize;
            if (wrapped < 0) {
                wrapped += imageSize;
            }
            return wrapped;
        }
        if (sample < 0 || sample >= imageSize) {
            return -1;
        }
        return sample;
    }

    private void clearDepth(Background background) {
        boolean clearDepth = depthBufferEnabled && (background == null || background.isDepthClearEnabled());
        if (!clearDepth) {
            return;
        }
        ensureDepthBuffer();
        for (int i = 0; i < depthBuffer.length; i++) {
            depthBuffer[i] = Float.POSITIVE_INFINITY;
        }
    }

    private void ensureDepthBuffer() {
        if (!depthBufferEnabled || vieww <= 0 || viewh <= 0) {
            depthBuffer = null;
            return;
        }
        int size = vieww * viewh;
        if (depthBuffer == null || depthBuffer.length != size) {
            depthBuffer = new float[size];
            for (int i = 0; i < depthBuffer.length; i++) {
                depthBuffer[i] = Float.POSITIVE_INFINITY;
            }
        }
    }

    private void renderTriangleStripsWithSoftwareBackend(VertexBuffer vertices, TriangleStripArray triangles, Appearance appearance, Transform transform) {
        renderTriangleStrips(vertices, triangles, appearance, transform);
    }

    private void renderTriangleStrips(VertexBuffer vertices, TriangleStripArray triangles, Appearance appearance, Transform transform) {
        RenderSurface surface = getRenderSurface();
        if (surface == null) {
            return;
        }

        VertexArray positionArray = vertices.getPositions(null);
        if (positionArray == null) {
            throw new IllegalStateException();
        }

        Transform projection = camera.getProjectionTransform(vieww, viewh);
        Transform view = new Transform(cameraTransform);
        view.invert();
        Transform modelView = new Transform(view);
        modelView.postMultiply(copyTransform(transform));
        Transform modelViewProjection = new Transform(projection);
        modelViewProjection.postMultiply(modelView);

        VertexArray normalArray = vertices.getNormals();
        VertexArray colorArray = vertices.getColors();
        VertexArray[] texCoordArrays = new VertexArray[MAX_TEXTURE_UNITS];
        float[][] texScaleBias = new float[MAX_TEXTURE_UNITS][];
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            texCoordArrays[unit] = vertices.getTexCoords(unit, null);
            if (texCoordArrays[unit] != null) {
                texScaleBias[unit] = vertices.getTexCoordScaleBias(unit);
            }
        }
        float[] positionScaleBias = vertices.getPositionScaleBias();
        TextureInfo[] textures = getTextureInfos(appearance, texCoordArrays);
        FogInfo fog = getFogInfo(appearance);
        PolygonMode polygonMode = appearance != null ? appearance.getPolygonMode() : null;
        CompositingMode compositingMode = appearance != null ? appearance.getCompositingMode() : null;
        int defaultColor = resolveBaseColor(vertices, appearance);

        // DIAGNOSTIC (parachute): when -Dfreej2me.diag.render is set, log the z-position
        // of the parachute meshes (verts 54 = canopy, 126 = body) to see whether they
        // keep moving or freeze (the "never disappears" bug). Includes mesh identity
        // hash to distinguish multiple verts=54 objects, and the call stack so we can
        // see which game code path is rendering each.
        if (diagRenderEnabled()) {
            int vc;
            try {
                vc = vertices.getVertexCount();
            } catch (Throwable t) {
                vc = -1;
            }
            if (vc == 54 || vc == 126 || vc == 100) {
                float[] m = new float[16];
                transform.get(m);
                int id = System.identityHashCode(vertices);
                String key = id + "_" + vc + "_" + fmt(m[3]);
                if (!paraStackLogged.contains(key)) {
                    paraStackLogged.add(key);
                    System.out.println("[diag.para] FIRST id=" + id + " verts=" + vc
                            + " tx=" + fmt(m[3]) + " ty=" + fmt(m[7]) + " tz=" + fmt(m[11]));
                    new Throwable().printStackTrace(System.out);
                }
            }
        }

        // DIAGNOSTIC TINT: enabled by -Dfreej2me.diag.tint=1. Overrides the base
        // color with a fixed color chosen by a "mesh fingerprint" (vertex count).
        // Also disables textures so the flat color is visible. Lets the player
        // visually identify which object is which on screen. Disabled (no change)
        // when the property is absent.
        if (diagTintEnabled()) {
            int tint = pickTintColor(vertices, compositingMode, transform);
            if (tint != 0) {
                defaultColor = tint;
                textures = null;   // force flat color, ignore textures
                fog = null;        // ignore fog for clarity
            }
        }
        ProjectedVertex[] projected = projectVertices(vertices, positionArray, normalArray, colorArray, texCoordArrays, positionScaleBias, texScaleBias, modelViewProjection, modelView, copyTransform(transform), appearance, defaultColor, textures, fog);
        int[] rawIndices = triangles.getRawIndices();
        int[] stripLengths = triangles.getStripLengths();
        int base = 0;
        for (int strip = 0; strip < stripLengths.length; strip++) {
            int stripLength = stripLengths[strip];
            for (int i = 0; i < stripLength - 2; i++) {
                int i0 = rawIndices[base + i];
                int i1 = rawIndices[base + i + 1];
                int i2 = rawIndices[base + i + 2];
                if ((i & 1) != 0) {
                    int swap = i1;
                    i1 = i2;
                    i2 = swap;
                }
                rasterizeClippedTriangle(surface, projected[i0], projected[i1], projected[i2], polygonMode, compositingMode, textures, fog);
            }
            base += stripLength;
        }
    }

    private void renderSprite(Sprite3D sprite, Transform transform, int scope) {
        // Per the JSR-184 reference (m3gGetSpriteCoordinates) and KEmulator
        // (Emulator3D.renderSprite), a sprite is only rendered when its origin
        // projects inside the near/far depth range: in clip space, w > 0 and
        // -w <= z <= w. Without this check a sprite whose origin sits outside the
        // frustum (e.g. behind the camera, or in front of the near plane) is still
        // rasterized, producing stray overlays that the standard implementations
        // never draw (PogoRoo's "1-10" ScoreCard sprite was one such case).
        if (!spriteOriginInsideFrustum(transform)) {
            return;
        }
        SpriteRenderData spriteData = createSpriteRenderData(sprite, transform);
        if (spriteData == null) {
            if (diagRenderEnabled()) {
                System.out.println("[diag.render] SPRITE skipped (null render data) crop="
                        + sprite.getCropWidth() + "x" + sprite.getCropHeight()
                        + " img=" + (sprite.getImage() == null ? "null" : sprite.getImage().getWidth() + "x" + sprite.getImage().getHeight()));
            }
            return;
        }
        if (diagRenderEnabled()) {
            float[] m = new float[16];
            transform.get(m);
            int blend = -1;
            boolean dt = true, dw = true;
            try {
                CompositingMode cm = sprite.getAppearance() != null ? sprite.getAppearance().getCompositingMode() : null;
                if (cm != null) {
                    blend = cm.getBlending();
                    dt = cm.isDepthTestEnabled();
                    dw = cm.isDepthWriteEnabled();
                }
            } catch (Throwable e) {
            }
            System.out.println("[diag.render] SPRITE crop=" + sprite.getCropWidth() + "x" + sprite.getCropHeight()
                    + " blend=" + blend + " depthTest=" + dt + " depthWrite=" + dw
                    + " tx=" + fmt(m[3]) + " ty=" + fmt(m[7]) + " tz=" + fmt(m[11]));
        }
        render(spriteData.vertices, spriteData.triangles, spriteData.appearance, spriteData.transform, scope);
    }

    /**
     * Tests whether the sprite's origin (the translation column of its world
     * transform) projects inside the camera frustum's depth range, matching the
     * reference implementation's visibility gate for sprites. Returns true only
     * when, in clip space, w > 0 and -w <= z <= w.
     */
    private boolean spriteOriginInsideFrustum(Transform worldTransform) {
        if (camera == null) {
            return true;
        }
        float[] m = worldTransform.getMatrix();
        float ox = m[3], oy = m[7], oz = m[11]; // origin = translation column (row-major)

        // view = inverse(cameraTransform); transform origin into camera space
        Transform view = new Transform(cameraTransform);
        view.invert();
        float[] vm = view.getMatrix();
        float cx = vm[0]*ox + vm[1]*oy + vm[2]*oz + vm[3];
        float cy = vm[4]*ox + vm[5]*oy + vm[6]*oz + vm[7];
        float cz = vm[8]*ox + vm[9]*oy + vm[10]*oz + vm[11];

        // Apply the camera projection to the camera-space origin.
        Transform proj = camera.getProjectionTransform(vieww, viewh);
        float[] pm = proj.getMatrix();
        float clipX = pm[0]*cx + pm[1]*cy + pm[2]*cz + pm[3];
        float clipY = pm[4]*cx + pm[5]*cy + pm[6]*cz + pm[7];
        float clipZ = pm[8]*cx + pm[9]*cy + pm[10]*cz + pm[11];
        float clipW = pm[12]*cx + pm[13]*cy + pm[14]*cz + pm[15];

        return clipW > 0f && -clipW <= clipZ && clipZ <= clipW;
    }

    private SpriteRenderData createSpriteRenderData(Sprite3D sprite, Transform transform) {
        Image2D image = sprite.getImage();
        if (image == null) {
            return null;
        }

        int cropWidth = sprite.getCropWidth();
        int cropHeight = sprite.getCropHeight();
        if (cropWidth == 0 || cropHeight == 0) {
            return null;
        }

        // Reference m3gGetSpriteCoordinates (m3g_sprite.c) builds the sprite quad from
        // unit camera-space vectors, so a scaled sprite is a 1.0 x 1.0 world-space quad
        // regardless of the crop rectangle's aspect ratio (the crop only selects a
        // sub-rectangle of the texture). The previous code normalized width and height
        // by max(image.getWidth(), image.getHeight()) (imageSpan), which produced the
        // wrong aspect ratio whenever the crop was not square (e.g. PogoRoo's 128x64
        // ScoreCard was rendered too wide / too short). Use a unit quad here.
        float halfWidth = 0.5f;
        float halfHeight = 0.5f;
        if (!sprite.isScaled()) {
            // Unscaled sprites are defined in screen space; for now reuse the scaled path.
            halfWidth *= 0.5f;
            halfHeight *= 0.5f;
        }

        float u0 = sprite.getCropX() / (float) image.getWidth();
        float v0 = sprite.getCropY() / (float) image.getHeight();
        float u1 = (sprite.getCropX() + cropWidth) / (float) image.getWidth();
        float v1 = (sprite.getCropY() + cropHeight) / (float) image.getHeight();

        VertexArray positions = new VertexArray(4, 3, 2);
        positions.set(0, 4, toFixedPointPositions(halfWidth, halfHeight));
        VertexArray texCoords = new VertexArray(4, 2, 2);
        texCoords.set(0, 4, toFixedPointTexCoords(u0, v0, u1, v1));

        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 1f / 4096f, null);
        vertices.setTexCoords(0, texCoords, 1f / 4096f, null);
        vertices.setDefaultColor((((int) (sprite.getAlphaFactor() * 255f)) & 0xFF) << 24 | 0x00FFFFFF);

        Appearance appearance = createSpriteAppearance(sprite, image);
        if (appearance == null) {
            return null;
        }

        Transform billboardTransform = createSpriteBillboardTransform(transform);
        return new SpriteRenderData(vertices, new TriangleStripArray(0, new int[]{4}), appearance, billboardTransform);
    }

    private short[] toFixedPointPositions(float halfWidth, float halfHeight) {
        return new short[]{
                (short) clamp(Math.round(-halfWidth * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(halfHeight * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                0,
                (short) clamp(Math.round(halfWidth * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(halfHeight * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                0,
                (short) clamp(Math.round(-halfWidth * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(-halfHeight * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                0,
                (short) clamp(Math.round(halfWidth * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(-halfHeight * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                0
        };
    }

    private short[] toFixedPointTexCoords(float u0, float v0, float u1, float v1) {
        return new short[]{
                (short) clamp(Math.round(u0 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(v0 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(u1 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(v0 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(u0 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(v1 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(u1 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE),
                (short) clamp(Math.round(v1 * 4096f), Short.MIN_VALUE, Short.MAX_VALUE)
        };
    }

    private Appearance createSpriteAppearance(Sprite3D sprite, Image2D image) {
        // Per JSR-184 (and as KEmulator / the reference implementation do), a sprite
        // whose Appearance is null must not be rendered at all. Previously this method
        // fabricated a default Appearance for such sprites, which is what caused the
        // PogoRoo "1-10" ScoreCard (a sprite authored with no Appearance in pogoroo.m3g)
        // to be drawn on top of the scene. Return null here so createSpriteRenderData
        // skips it, matching the standard implementations.
        Appearance base = sprite.getAppearance();
        if (base == null) {
            return null;
        }

        Texture2D texture;
        try {
            texture = new Texture2D(image);
        } catch (IllegalArgumentException e) {
            return null;
        }
        texture.setBlending(Texture2D.FUNC_REPLACE);
        texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_LINEAR);

        Appearance appearance = new Appearance();
        appearance.setCompositingMode(base.getCompositingMode());
        appearance.setFog(base.getFog());
        appearance.setMaterial(base.getMaterial());
        appearance.setPolygonMode(base.getPolygonMode());
        appearance.setLayer(base.getLayer());
        // If the sprite's Appearance has no explicit CompositingMode, the rasterizer's
        // "compositingMode == null" branch writes every pixel (FUNC_REPLACE) without
        // honouring texture alpha, turning any transparent texels into a solid block.
        // Default to ALPHA blending so transparent texels stay transparent.
        if (appearance.getCompositingMode() == null) {
            CompositingMode defaultMode = new CompositingMode();
            defaultMode.setBlending(CompositingMode.ALPHA);
            appearance.setCompositingMode(defaultMode);
        }
        appearance.setTexture(0, texture);
        return appearance;
    }

    private Transform createSpriteBillboardTransform(Transform transform) {
        Transform billboard = new Transform();
        if (transform == null) {
            return billboard;
        }

        float[] matrix = transform.getMatrix();
        float tx = matrix[3];
        float ty = matrix[7];
        float tz = matrix[11];
        float sx = (float) Math.sqrt(matrix[0] * matrix[0] + matrix[4] * matrix[4] + matrix[8] * matrix[8]);
        float sy = (float) Math.sqrt(matrix[1] * matrix[1] + matrix[5] * matrix[5] + matrix[9] * matrix[9]);
        float sz = (float) Math.sqrt(matrix[2] * matrix[2] + matrix[6] * matrix[6] + matrix[10] * matrix[10]);
        float[] cameraMatrix = cameraTransform.getMatrix();
        float cameraSx = (float) Math.sqrt(cameraMatrix[0] * cameraMatrix[0] + cameraMatrix[4] * cameraMatrix[4] + cameraMatrix[8] * cameraMatrix[8]);
        float cameraSy = (float) Math.sqrt(cameraMatrix[1] * cameraMatrix[1] + cameraMatrix[5] * cameraMatrix[5] + cameraMatrix[9] * cameraMatrix[9]);
        float cameraSz = (float) Math.sqrt(cameraMatrix[2] * cameraMatrix[2] + cameraMatrix[6] * cameraMatrix[6] + cameraMatrix[10] * cameraMatrix[10]);
        float safeSx = sx == 0f ? 1f : sx;
        float safeSy = sy == 0f ? 1f : sy;
        float safeSz = sz == 0f ? 1f : sz;
        float safeCameraSx = cameraSx == 0f ? 1f : cameraSx;
        float safeCameraSy = cameraSy == 0f ? 1f : cameraSy;
        float safeCameraSz = cameraSz == 0f ? 1f : cameraSz;
        float[] billboardMatrix = billboard.getMatrix();
        billboardMatrix[0] = (cameraMatrix[0] / safeCameraSx) * safeSx;
        billboardMatrix[1] = (cameraMatrix[1] / safeCameraSy) * safeSy;
        billboardMatrix[2] = (cameraMatrix[2] / safeCameraSz) * safeSz;
        billboardMatrix[3] = tx;
        billboardMatrix[4] = (cameraMatrix[4] / safeCameraSx) * safeSx;
        billboardMatrix[5] = (cameraMatrix[5] / safeCameraSy) * safeSy;
        billboardMatrix[6] = (cameraMatrix[6] / safeCameraSz) * safeSz;
        billboardMatrix[7] = ty;
        billboardMatrix[8] = (cameraMatrix[8] / safeCameraSx) * safeSx;
        billboardMatrix[9] = (cameraMatrix[9] / safeCameraSy) * safeSy;
        billboardMatrix[10] = (cameraMatrix[10] / safeCameraSz) * safeSz;
        billboardMatrix[11] = tz;
        return billboard;
    }

    private VertexBuffer createMorphedVertexBuffer(MorphingMesh mesh) {
        VertexBuffer base = mesh.getVertexBuffer();
        float[] weights = new float[mesh.getMorphTargetCount()];
        mesh.getWeights(weights);
        MorphedVertexBufferKey key = new MorphedVertexBufferKey(base, mesh, weights);
        MorphedVertexBufferCacheEntry entry = (MorphedVertexBufferCacheEntry) morphedVertexBufferCache.get(key);
        if (entry == null) {
            entry = new MorphedVertexBufferCacheEntry();
            morphedVertexBufferCache.put(key, entry);
        }
        int sourceRevision = computeMorphSourceRevision(base, mesh);
        if (entry.sourceRevision != sourceRevision) {
            populateMorphedVertexBuffer(entry.vertexBuffer, base, mesh, weights);
            entry.sourceRevision = sourceRevision;
        }
        return entry.vertexBuffer;
    }

    private void populateMorphedVertexBuffer(VertexBuffer morphed, VertexBuffer base, MorphingMesh mesh, float[] weights) {
        VertexArray positions = resolveMorphedArray(base.getPositions(null), mesh, weights, MorphAttribute.POSITIONS);
        float[] positionScaleBias = base.getPositionScaleBias();
        morphed.setPositions(positions, positionScaleBias[0], new float[]{
                positionScaleBias[1], positionScaleBias[2], positionScaleBias[3]
        });

        VertexArray normals = resolveMorphedArray(base.getNormals(), mesh, weights, MorphAttribute.NORMALS);
        morphed.setNormals(normals);

        VertexArray colors = resolveMorphedArray(base.getColors(), mesh, weights, MorphAttribute.COLORS);
        morphed.setColors(colors);
        if (colors != null) {
            morphed.setDefaultColor(base.getDefaultColor());
        } else {
            morphed.setDefaultColor(resolveMorphedDefaultColor(base, mesh, weights));
        }

        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            VertexArray texCoords = resolveMorphedArray(base.getTexCoords(unit, null), mesh, weights, MorphAttribute.TEXCOORDS0 + unit);
            if (texCoords != null) {
                float[] texScaleBias = base.getTexCoordScaleBias(unit);
                morphed.setTexCoords(unit, texCoords, texScaleBias[0], new float[]{
                        texScaleBias[1], texScaleBias[2], texScaleBias[3]
                });
            } else {
                morphed.setTexCoords(unit, null, 1f, null);
            }
        }
    }

    private int computeMorphSourceRevision(VertexBuffer base, MorphingMesh mesh) {
        int revision = computeVertexBufferSourceRevision(base);
        for (int i = 0; i < mesh.getMorphTargetCount(); i++) {
            revision = revision * 31 + computeVertexBufferSourceRevision(mesh.getMorphTarget(i));
        }
        return revision;
    }

    private int computeVertexBufferSourceRevision(VertexBuffer buffer) {
        int revision = buffer.getRevision();
        revision = revision * 31 + buffer.getDefaultColor();
        revision = revision * 31 + getArraySourceRevision(buffer.getPositions(null));
        revision = revision * 31 + getArraySourceRevision(buffer.getNormals());
        revision = revision * 31 + getArraySourceRevision(buffer.getColors());
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            revision = revision * 31 + getArraySourceRevision(buffer.getTexCoords(unit, null));
        }
        return revision;
    }

    private static int getArraySourceRevision(VertexArray array) {
        if (array == null) {
            return 0;
        }
        return System.identityHashCode(array) * 31 + array.getRevision();
    }

    private VertexBuffer createSkinnedVertexBuffer(SkinnedMesh mesh) {
        return mesh.getSkinnedVertexBuffer();
    }

    private VertexArray resolveMorphedArray(VertexArray baseArray, MorphingMesh mesh, float[] weights, int attribute) {
        int targetCount = mesh.getMorphTargetCount();
        VertexArray[] targetArrays = new VertexArray[targetCount];
        int nonNullTargetCount = 0;
        for (int i = 0; i < targetCount; i++) {
            targetArrays[i] = getMorphTargetArray(mesh.getMorphTarget(i), attribute);
            if (targetArrays[i] != null) {
                nonNullTargetCount++;
            }
        }

        if (baseArray == null) {
            if (nonNullTargetCount != 0) {
                throw new IllegalStateException();
            }
            return null;
        }

        if (nonNullTargetCount == 0) {
            return baseArray;
        }
        if (nonNullTargetCount != targetCount) {
            throw new IllegalStateException();
        }

        for (int i = 0; i < targetArrays.length; i++) {
            validateMorphArrayCompatibility(baseArray, targetArrays[i]);
        }

        return attribute == MorphAttribute.COLORS
                ? morphColorArray(baseArray, targetArrays, weights)
                : morphNumericArray(baseArray, targetArrays, weights);
    }

    private static VertexArray getMorphTargetArray(VertexBuffer buffer, int attribute) {
        switch (attribute) {
            case MorphAttribute.POSITIONS:
                return buffer.getPositions(null);
            case MorphAttribute.NORMALS:
                return buffer.getNormals();
            case MorphAttribute.COLORS:
                return buffer.getColors();
            case MorphAttribute.TEXCOORDS0:
                return buffer.getTexCoords(0, null);
            case MorphAttribute.TEXCOORDS1:
                return buffer.getTexCoords(1, null);
            default:
                return null;
        }
    }

    private static void validateMorphArrayCompatibility(VertexArray baseArray, VertexArray targetArray) {
        if (targetArray == null
                || baseArray.getVertexCount() != targetArray.getVertexCount()
                || baseArray.getComponentCount() != targetArray.getComponentCount()
                || baseArray.getComponentType() != targetArray.getComponentType()) {
            throw new IllegalStateException();
        }
    }

    private static VertexArray morphNumericArray(VertexArray baseArray, VertexArray[] targetArrays, float[] weights) {
        int componentType = baseArray.getComponentType();
        int count = baseArray.getVertexCount() * baseArray.getComponentCount();
        VertexArray morphed = new VertexArray(baseArray.getVertexCount(), baseArray.getComponentCount(), componentType);
        short[] baseValues = baseArray.getRawValues();

        if (componentType == 1) {
            byte[] values = new byte[count];
            for (int i = 0; i < count; i++) {
                float baseValue = (byte) baseValues[i];
                float morphedValue = baseValue;
                for (int target = 0; target < targetArrays.length; target++) {
                    float targetValue = (byte) targetArrays[target].getRawValues()[i];
                    morphedValue += weights[target] * (targetValue - baseValue);
                }
                values[i] = (byte) clamp(Math.round(morphedValue), -128, 127);
            }
            morphed.set(0, baseArray.getVertexCount(), values);
        } else {
            short[] values = new short[count];
            for (int i = 0; i < count; i++) {
                float baseValue = baseValues[i];
                float morphedValue = baseValue;
                for (int target = 0; target < targetArrays.length; target++) {
                    float targetValue = targetArrays[target].getRawValues()[i];
                    morphedValue += weights[target] * (targetValue - baseValue);
                }
                values[i] = (short) clamp(Math.round(morphedValue), Short.MIN_VALUE, Short.MAX_VALUE);
            }
            morphed.set(0, baseArray.getVertexCount(), values);
        }
        return morphed;
    }

    private static VertexArray morphColorArray(VertexArray baseArray, VertexArray[] targetArrays, float[] weights) {
        int componentType = baseArray.getComponentType();
        int count = baseArray.getVertexCount() * baseArray.getComponentCount();
        VertexArray morphed = new VertexArray(baseArray.getVertexCount(), baseArray.getComponentCount(), componentType);
        short[] baseValues = baseArray.getRawValues();

        if (componentType == 1) {
            byte[] values = new byte[count];
            for (int i = 0; i < count; i++) {
                float baseValue = baseValues[i] & 0xFF;
                float morphedValue = baseValue;
                for (int target = 0; target < targetArrays.length; target++) {
                    float targetValue = targetArrays[target].getRawValues()[i] & 0xFF;
                    morphedValue += weights[target] * (targetValue - baseValue);
                }
                values[i] = (byte) clamp(Math.round(morphedValue), 0, 255);
            }
            morphed.set(0, baseArray.getVertexCount(), values);
        } else {
            short[] values = new short[count];
            for (int i = 0; i < count; i++) {
                float baseValue = baseValues[i] & 0xFFFF;
                float morphedValue = baseValue;
                for (int target = 0; target < targetArrays.length; target++) {
                    float targetValue = targetArrays[target].getRawValues()[i] & 0xFFFF;
                    morphedValue += weights[target] * (targetValue - baseValue);
                }
                values[i] = (short) clamp(Math.round(morphedValue), 0, 0xFFFF);
            }
            morphed.set(0, baseArray.getVertexCount(), values);
        }
        return morphed;
    }

    private static int resolveMorphedDefaultColor(VertexBuffer base, MorphingMesh mesh, float[] weights) {
        int baseColor = base.getDefaultColor();
        float baseA = (baseColor >>> 24) & 0xFF;
        float baseR = (baseColor >>> 16) & 0xFF;
        float baseG = (baseColor >>> 8) & 0xFF;
        float baseB = baseColor & 0xFF;
        float a = baseA;
        float r = baseR;
        float g = baseG;
        float b = baseB;
        for (int i = 0; i < mesh.getMorphTargetCount(); i++) {
            int targetColor = mesh.getMorphTarget(i).getDefaultColor();
            a += weights[i] * ((((targetColor >>> 24) & 0xFF) - baseA));
            r += weights[i] * ((((targetColor >>> 16) & 0xFF) - baseR));
            g += weights[i] * ((((targetColor >>> 8) & 0xFF) - baseG));
            b += weights[i] * (((targetColor & 0xFF) - baseB));
        }
        return (clampColor(Math.round(a)) << 24)
                | (clampColor(Math.round(r)) << 16)
                | (clampColor(Math.round(g)) << 8)
                | clampColor(Math.round(b));
    }

    private ProjectedVertex[] projectVertices(VertexBuffer vertices, VertexArray positionArray, VertexArray normalArray, VertexArray colorArray, VertexArray[] texCoordArrays, float[] positionScaleBias, float[][] texScaleBias, Transform modelViewProjection, Transform modelView, Transform modelTransform, Appearance appearance, int defaultColor, TextureInfo[] textures, FogInfo fog) {
        int vertexCount = vertices.getVertexCount();
        ProjectedVertex[] projected = new ProjectedVertex[vertexCount];
        float[] matrix = modelViewProjection.getMatrix();
        float[] modelViewMatrix = modelView.getMatrix();
        float[] modelMatrix = modelTransform.getMatrix();
        Material material = appearance != null ? appearance.getMaterial() : null;
        boolean applyLighting = material != null && normalArray != null && getLightCount() > 0;
        boolean applyFog = fog != null;
        float[] input = new float[4];
        float[] clip = new float[4];
        float[] world = applyLighting ? new float[4] : null;
        float[] eye = applyFog ? new float[4] : null;
        float[] normalInput = applyLighting ? new float[4] : null;
        float[] normalWorld = applyLighting ? new float[4] : null;
        float[][] texInputs = new float[MAX_TEXTURE_UNITS][];
        float[][] texOutputs = new float[MAX_TEXTURE_UNITS][];
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            if (textures != null && textures[unit] != null && textures[unit].transformMatrix != null) {
                texInputs[unit] = new float[4];
                texOutputs[unit] = new float[4];
            }
        }
        for (int i = 0; i < vertexCount; i++) {
            ProjectedVertex vertex = new ProjectedVertex();
            input[0] = positionArray.getComponentAsFloat(i, 0) * positionScaleBias[0] + positionScaleBias[1];
            input[1] = positionArray.getComponentAsFloat(i, 1) * positionScaleBias[0] + positionScaleBias[2];
            input[2] = positionArray.getComponentAsFloat(i, 2) * positionScaleBias[0] + positionScaleBias[3];
            input[3] = 1f;
            M3GMath.transform(matrix, input, 0, clip, 0);
            vertex.clipX = clip[0];
            vertex.clipY = clip[1];
            vertex.clipZ = clip[2];
            vertex.clipW = clip[3];
            vertex.visible = Math.abs(clip[3]) > 1.0e-6f;
            if (vertex.visible) {
                updateProjectedVertex(vertex);
            }
            if (applyFog) {
                M3GMath.transform(modelViewMatrix, input, 0, eye, 0);
                vertex.fogDistance = (float) Math.sqrt(eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2]);
            }
            int trackedColor = colorArray != null ? getVertexColor(colorArray, i) : vertices.getDefaultColor();
            int vertexColor = colorArray != null ? trackedColor : defaultColor;
            if (applyLighting) {
                M3GMath.transform(modelMatrix, input, 0, world, 0);
                normalInput[0] = normalArray.getComponentAsFloat(i, 0);
                normalInput[1] = normalArray.getComponentAsFloat(i, 1);
                normalInput[2] = normalArray.getComponentAsFloat(i, 2);
                normalInput[3] = 0f;
                M3GMath.transform(modelMatrix, normalInput, 0, normalWorld, 0);
                vertexColor = applyLighting(trackedColor, material, world, normalWorld);
            }
            vertex.a = (vertexColor >>> 24) & 0xFF;
            vertex.r = (vertexColor >>> 16) & 0xFF;
            vertex.g = (vertexColor >>> 8) & 0xFF;
            vertex.b = vertexColor & 0xFF;
            for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
                VertexArray texCoordArray = texCoordArrays[unit];
                if (texCoordArray == null) {
                    continue;
                }
                float u = texCoordArray.getComponentAsFloat(i, 0) * texScaleBias[unit][0] + texScaleBias[unit][1];
                float v = texCoordArray.getComponentAsFloat(i, 1) * texScaleBias[unit][0] + texScaleBias[unit][2];
                if (textures != null && textures[unit] != null && textures[unit].transformMatrix != null) {
                    float[] texInput = texInputs[unit];
                    float[] texOutput = texOutputs[unit];
                    texInput[0] = u;
                    texInput[1] = v;
                    texInput[2] = 0f;
                    texInput[3] = 1f;
                    M3GMath.transform(textures[unit].transformMatrix, texInput, 0, texOutput, 0);
                    u = texOutput[0];
                    v = texOutput[1];
                }
                vertex.u[unit] = u;
                vertex.v[unit] = v;
            }
            projected[i] = vertex;
        }
        return projected;
    }

    private int applyLighting(int trackedColor, Material material, float[] worldPosition, float[] worldNormal) {
        float nx = worldNormal[0];
        float ny = worldNormal[1];
        float nz = worldNormal[2];
        float normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (normalLength <= 1.0e-6f) {
            return trackedColor;
        }
        nx /= normalLength;
        ny /= normalLength;
        nz /= normalLength;

        float ambientR = 0f;
        float ambientG = 0f;
        float ambientB = 0f;
        float diffuseR = 0f;
        float diffuseG = 0f;
        float diffuseB = 0f;
        float specularR = 0f;
        float specularG = 0f;
        float specularB = 0f;
        float[] cameraMatrix = cameraTransform.getMatrix();
        float vx = cameraMatrix[3] - worldPosition[0];
        float vy = cameraMatrix[7] - worldPosition[1];
        float vz = cameraMatrix[11] - worldPosition[2];
        float viewLength = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (viewLength > 1.0e-6f) {
            vx /= viewLength;
            vy /= viewLength;
            vz /= viewLength;
        } else {
            vx = 0f;
            vy = 0f;
            vz = 1f;
        }
        float[] lightMatrix = new float[16];
        Transform lightTransform = new Transform();
        for (int i = 0; i < getLightCount(); i++) {
            Light light = getLight(i, lightTransform);
            lightTransform.get(lightMatrix);
            float intensity = light.getIntensity();
            float lightR = (((light.getColor() >>> 16) & 0xFF) / 255f) * intensity;
            float lightG = (((light.getColor() >>> 8) & 0xFF) / 255f) * intensity;
            float lightB = ((light.getColor() & 0xFF) / 255f) * intensity;
            if (light.getMode() == Light.AMBIENT) {
                ambientR += lightR;
                ambientG += lightG;
                ambientB += lightB;
                continue;
            }

            float lx;
            float ly;
            float lz;
            float attenuation = 1f;
            float spotFactor = 1f;
            if (light.getMode() == Light.OMNI || light.getMode() == Light.SPOT) {
                lx = lightMatrix[3] - worldPosition[0];
                ly = lightMatrix[7] - worldPosition[1];
                lz = lightMatrix[11] - worldPosition[2];
                float distance = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                if (distance <= 1.0e-6f) {
                    continue;
                }
                lx /= distance;
                ly /= distance;
                lz /= distance;
                float denominator = light.getConstantAttenuation()
                        + light.getLinearAttenuation() * distance
                        + light.getQuadraticAttenuation() * distance * distance;
                if (denominator > 1.0e-6f) {
                    attenuation = 1f / denominator;
                }
                if (light.getMode() == Light.SPOT) {
                    float spotDirX = -lightMatrix[2];
                    float spotDirY = -lightMatrix[6];
                    float spotDirZ = -lightMatrix[10];
                    float spotLength = (float) Math.sqrt(spotDirX * spotDirX + spotDirY * spotDirY + spotDirZ * spotDirZ);
                    if (spotLength <= 1.0e-6f) {
                        continue;
                    }
                    spotDirX /= spotLength;
                    spotDirY /= spotLength;
                    spotDirZ /= spotLength;
                    float cosTheta = -(lx * spotDirX + ly * spotDirY + lz * spotDirZ);
                    float spotAngle = light.getSpotAngle();
                    if (spotAngle >= 0f && spotAngle < 180f) {
                        float cutoff = (float) Math.cos(Math.toRadians(spotAngle * 0.5f));
                        if (cosTheta < cutoff) {
                            continue;
                        }
                    }
                    spotFactor = (float) Math.pow(Math.max(0f, cosTheta), Math.max(0f, light.getSpotExponent()));
                }
            } else {
                lx = -lightMatrix[2];
                ly = -lightMatrix[6];
                lz = -lightMatrix[10];
                float length = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                if (length <= 1.0e-6f) {
                    continue;
                }
                lx /= length;
                ly /= length;
                lz /= length;
            }

            float ndotl = nx * lx + ny * ly + nz * lz;
            if (ndotl > 0f) {
                float lightingScale = attenuation * spotFactor;
                diffuseR += lightR * ndotl * lightingScale;
                diffuseG += lightG * ndotl * lightingScale;
                diffuseB += lightB * ndotl * lightingScale;
                int specularColor = material.getColor(Material.SPECULAR);
                float shininess = material.getShininess();
                if ((specularColor & 0x00FFFFFF) != 0 && shininess > 0f) {
                    float hx = lx + vx;
                    float hy = ly + vy;
                    float hz = lz + vz;
                    float halfLength = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
                    if (halfLength > 1.0e-6f) {
                        hx /= halfLength;
                        hy /= halfLength;
                        hz /= halfLength;
                        float ndoth = Math.max(0f, nx * hx + ny * hy + nz * hz);
                        if (ndoth > 0f) {
                            float specularFactor = (float) Math.pow(ndoth, Math.max(1f, shininess)) * lightingScale;
                            specularR += lightR * specularFactor;
                            specularG += lightG * specularFactor;
                            specularB += lightB * specularFactor;
                        }
                    }
                }
            }
        }

        int ambientColor = material.isVertexColorTrackingEnabled()
                ? (trackedColor & 0x00FFFFFF)
                : material.getColor(Material.AMBIENT);
        int diffuseColor = material.isVertexColorTrackingEnabled()
                ? trackedColor
                : material.getColor(Material.DIFFUSE);
        int emissiveColor = material.getColor(Material.EMISSIVE);
        int specularColor = material.getColor(Material.SPECULAR);
        float diffuseRBase = ((diffuseColor >>> 16) & 0xFF) / 255f;
        float diffuseGBase = ((diffuseColor >>> 8) & 0xFF) / 255f;
        float diffuseBBase = (diffuseColor & 0xFF) / 255f;
        float specularRBase = ((specularColor >>> 16) & 0xFF) / 255f;
        float specularGBase = ((specularColor >>> 8) & 0xFF) / 255f;
        float specularBBase = (specularColor & 0xFF) / 255f;
        float outR = (((emissiveColor >>> 16) & 0xFF) / 255f) + ((((ambientColor >>> 16) & 0xFF) / 255f) * ambientR) + (diffuseRBase * diffuseR) + (specularRBase * specularR);
        float outG = (((emissiveColor >>> 8) & 0xFF) / 255f) + ((((ambientColor >>> 8) & 0xFF) / 255f) * ambientG) + (diffuseGBase * diffuseG) + (specularGBase * specularG);
        float outB = ((emissiveColor & 0xFF) / 255f) + (((ambientColor & 0xFF) / 255f) * ambientB) + (diffuseBBase * diffuseB) + (specularBBase * specularB);
        int alpha = (diffuseColor >>> 24) & 0xFF;
        return (alpha << 24)
                | (clampColor(Math.round(outR * 255f)) << 16)
                | (clampColor(Math.round(outG * 255f)) << 8)
                | clampColor(Math.round(outB * 255f));
    }

    private void rasterizeClippedTriangle(RenderSurface surface, ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2, PolygonMode polygonMode, CompositingMode compositingMode, TextureInfo[] textures, FogInfo fog) {
        ProjectedVertex[] polygon = new ProjectedVertex[]{
                copyProjectedVertex(v0),
                copyProjectedVertex(v1),
                copyProjectedVertex(v2)
        };
        polygon = clipPolygonAgainstPlane(polygon, CLIP_LEFT);
        polygon = clipPolygonAgainstPlane(polygon, CLIP_RIGHT);
        polygon = clipPolygonAgainstPlane(polygon, CLIP_BOTTOM);
        polygon = clipPolygonAgainstPlane(polygon, CLIP_TOP);
        polygon = clipPolygonAgainstPlane(polygon, CLIP_NEAR);
        polygon = clipPolygonAgainstPlane(polygon, CLIP_FAR);
        if (polygon.length < 3) {
            return;
        }
        for (int i = 1; i < polygon.length - 1; i++) {
            rasterizeTriangle(surface, polygon[0], polygon[i], polygon[i + 1], polygonMode, compositingMode, textures, fog);
        }
    }

    private ProjectedVertex[] clipPolygonAgainstPlane(ProjectedVertex[] polygon, int plane) {
        if (polygon.length == 0) {
            return polygon;
        }
        Vector result = new Vector();
        ProjectedVertex previous = polygon[polygon.length - 1];
        float previousDistance = clipDistance(previous, plane);
        boolean previousInside = previousDistance >= 0f;
        for (int i = 0; i < polygon.length; i++) {
            ProjectedVertex current = polygon[i];
            float currentDistance = clipDistance(current, plane);
            boolean currentInside = currentDistance >= 0f;
            if (currentInside != previousInside) {
                float denominator = previousDistance - currentDistance;
                if (Math.abs(denominator) > 1.0e-6f) {
                    float t = previousDistance / denominator;
                    result.addElement(interpolateProjectedVertex(previous, current, t));
                }
            }
            if (currentInside) {
                result.addElement(copyProjectedVertex(current));
            }
            previous = current;
            previousDistance = currentDistance;
            previousInside = currentInside;
        }
        ProjectedVertex[] clipped = new ProjectedVertex[result.size()];
        result.copyInto(clipped);
        return clipped;
    }

    private float clipDistance(ProjectedVertex vertex, int plane) {
        switch (plane) {
            case CLIP_LEFT:
                return vertex.clipX + vertex.clipW;
            case CLIP_RIGHT:
                return vertex.clipW - vertex.clipX;
            case CLIP_BOTTOM:
                return vertex.clipY + vertex.clipW;
            case CLIP_TOP:
                return vertex.clipW - vertex.clipY;
            case CLIP_NEAR:
                return vertex.clipZ + vertex.clipW;
            case CLIP_FAR:
                return vertex.clipW - vertex.clipZ;
            default:
                return -1f;
        }
    }

    private ProjectedVertex interpolateProjectedVertex(ProjectedVertex a, ProjectedVertex b, float t) {
        ProjectedVertex vertex = new ProjectedVertex();
        vertex.clipX = interpolate(a.clipX, b.clipX, t);
        vertex.clipY = interpolate(a.clipY, b.clipY, t);
        vertex.clipZ = interpolate(a.clipZ, b.clipZ, t);
        vertex.clipW = interpolate(a.clipW, b.clipW, t);
        vertex.a = interpolate(a.a, b.a, t);
        vertex.r = interpolate(a.r, b.r, t);
        vertex.g = interpolate(a.g, b.g, t);
        vertex.b = interpolate(a.b, b.b, t);
        vertex.fogDistance = interpolate(a.fogDistance, b.fogDistance, t);
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            vertex.u[unit] = interpolate(a.u[unit], b.u[unit], t);
            vertex.v[unit] = interpolate(a.v[unit], b.v[unit], t);
        }
        vertex.visible = Math.abs(vertex.clipW) > 1.0e-6f;
        if (vertex.visible) {
            updateProjectedVertex(vertex);
        }
        return vertex;
    }

    private ProjectedVertex copyProjectedVertex(ProjectedVertex source) {
        ProjectedVertex copy = new ProjectedVertex();
        copy.visible = source.visible;
        copy.clipX = source.clipX;
        copy.clipY = source.clipY;
        copy.clipZ = source.clipZ;
        copy.clipW = source.clipW;
        copy.ndcX = source.ndcX;
        copy.ndcY = source.ndcY;
        copy.x = source.x;
        copy.y = source.y;
        copy.z = source.z;
        copy.invW = source.invW;
        copy.fogDistance = source.fogDistance;
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            copy.u[unit] = source.u[unit];
            copy.v[unit] = source.v[unit];
        }
        copy.a = source.a;
        copy.r = source.r;
        copy.g = source.g;
        copy.b = source.b;
        return copy;
    }

    private void updateProjectedVertex(ProjectedVertex vertex) {
        vertex.invW = 1f / vertex.clipW;
        vertex.ndcX = vertex.clipX * vertex.invW;
        vertex.ndcY = vertex.clipY * vertex.invW;
        float ndcZ = vertex.clipZ * vertex.invW;
        vertex.x = viewx + ((vertex.ndcX * 0.5f) + 0.5f) * vieww;
        vertex.y = viewy + (1f - ((vertex.ndcY * 0.5f) + 0.5f)) * viewh;
        vertex.z = near + (((ndcZ * 0.5f) + 0.5f) * (far - near));
    }

    private void rasterizeTriangle(RenderSurface surface, ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2, PolygonMode polygonMode, CompositingMode compositingMode, TextureInfo[] textures, FogInfo fog) {
        if (!v0.visible || !v1.visible || !v2.visible) {
            return;
        }

        float ndcArea = edge(v0.ndcX, v0.ndcY, v1.ndcX, v1.ndcY, v2.ndcX, v2.ndcY);
        if (Math.abs(ndcArea) <= 1.0e-6f || isCulled(ndcArea, polygonMode)) {
            return;
        }

        float minXf = min3(v0.x, v1.x, v2.x);
        float maxXf = max3(v0.x, v1.x, v2.x);
        float minYf = min3(v0.y, v1.y, v2.y);
        float maxYf = max3(v0.y, v1.y, v2.y);
        int minX = clamp((int) Math.floor(minXf), viewx, viewx + vieww - 1);
        int maxX = clamp((int) Math.ceil(maxXf), viewx, viewx + vieww - 1);
        int minY = clamp((int) Math.floor(minYf), viewy, viewy + viewh - 1);
        int maxY = clamp((int) Math.ceil(maxYf), viewy, viewy + viewh - 1);
        if (minX > maxX || minY > maxY) {
            return;
        }

        float area = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
        if (Math.abs(area) <= 1.0e-6f) {
            return;
        }

        boolean depthTest = depthBufferEnabled && depthBuffer != null && (compositingMode == null || compositingMode.isDepthTestEnabled());
        boolean depthWrite = depthBufferEnabled && depthBuffer != null && (compositingMode == null || compositingMode.isDepthWriteEnabled());
        float alphaThreshold = compositingMode != null ? compositingMode.getAlphaThreshold() : 0f;
        float depthOffset = compositingMode != null ? compositingMode.getDepthOffsetUnits() : 0f;
        boolean flatShading = polygonMode != null && polygonMode.getShading() == PolygonMode.SHADE_FLAT;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = edge(v1.x, v1.y, v2.x, v2.y, px, py) / area;
                float w1 = edge(v2.x, v2.y, v0.x, v0.y, px, py) / area;
                float w2 = 1f - w0 - w1;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }

                float depth = w0 * v0.z + w1 * v1.z + w2 * v2.z + depthOffset;
                int depthIndex = (y - viewy) * vieww + (x - viewx);
                if (depthTest && depth > depthBuffer[depthIndex]) {
                    continue;
                }

                int baseColor = flatShading ? toColor(v0) : interpolateColor(v0, v1, v2, w0, w1, w2);
                int finalColor = baseColor;
                float denominator = 0f;
                boolean usePerspectiveInterpolation = hasAnyTexture(textures) || fog != null;
                if (usePerspectiveInterpolation) {
                    denominator = w0 * v0.invW + w1 * v1.invW + w2 * v2.invW;
                    if (Math.abs(denominator) <= 1.0e-6f) {
                        continue;
                    }
                }
                for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
                    TextureInfo texture = textures != null ? textures[unit] : null;
                    if (texture == null) {
                        continue;
                    }
                    float u = (w0 * v0.u[unit] * v0.invW + w1 * v1.u[unit] * v1.invW + w2 * v2.u[unit] * v2.invW) / denominator;
                    float v = (w0 * v0.v[unit] * v0.invW + w1 * v1.v[unit] * v1.invW + w2 * v2.v[unit] * v2.invW) / denominator;
                    finalColor = combineTexture(texture, finalColor, u, v);
                }
                if (fog != null) {
                    float fogDistance = (w0 * v0.fogDistance * v0.invW + w1 * v1.fogDistance * v1.invW + w2 * v2.fogDistance * v2.invW) / denominator;
                    finalColor = applyFog(finalColor, fog, fogDistance);
                }
                int srcAlpha = (finalColor >>> 24) & 0xFF;
                if (srcAlpha < Math.round(alphaThreshold * 255f)) {
                    continue;
                }
                // A fully transparent source pixel (alpha == 0) contributes nothing
                // to the framebuffer. Even with ALPHA blending the color is a no-op,
                // so it must also be skipped for depth: otherwise it erects an
                // invisible depth wall that hides geometry drawn afterwards (e.g. the
                // transparent background of PogoRoo's ScoreCard sprite occluding the
                // kangaroo). Only write depth when the pixel is at least partially
                // visible.

                int dstColor = surface.getPixel(x, y);
                int composited = applyCompositing(dstColor, finalColor, compositingMode);
                int masked = applyWriteMask(dstColor, composited, compositingMode);
                if (masked != dstColor) {
                    surface.setPixel(x, y, masked);
                }
                if (depthWrite && srcAlpha > 0) {
                    depthBuffer[depthIndex] = depth;
                }
            }
        }
    }

    private static int toColor(ProjectedVertex vertex) {
        return (clampColor(Math.round(vertex.a)) << 24)
                | (clampColor(Math.round(vertex.r)) << 16)
                | (clampColor(Math.round(vertex.g)) << 8)
                | clampColor(Math.round(vertex.b));
    }

    private RenderSurface getRenderSurface() {
        if (target instanceof PlatformGraphics) {
            PlatformGraphics graphics = (PlatformGraphics) target;
            BufferedImage image = graphics.getCanvas();
            if (image != null) {
                return new BufferedImageSurface(image);
            }
        }
        if (target instanceof Graphics) {
            Graphics graphics = (Graphics) target;
            if (graphics.platformGraphics != null) {
                BufferedImage image = graphics.platformGraphics.getCanvas();
                if (image != null) {
                    return new BufferedImageSurface(image);
                }
            }
        }
        if (target instanceof Image2D) {
            Image2D image = (Image2D) target;
            if (image.getFormat() == Image2D.RGB || image.getFormat() == Image2D.RGBA) {
                return new Image2DSurface(image);
            }
        }
        return null;
    }

    private TextureInfo[] getTextureInfos(Appearance appearance, VertexArray[] texCoordArrays) {
        TextureInfo[] infos = new TextureInfo[MAX_TEXTURE_UNITS];
        if (appearance == null) {
            return infos;
        }
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            if (texCoordArrays[unit] == null) {
                continue;
            }
            Texture2D texture = appearance.getTexture(unit);
            if (texture == null || texture.getImage() == null) {
                continue;
            }
            Image2D image = texture.getImage();
            if (image.getFormat() != Image2D.RGB && image.getFormat() != Image2D.RGBA) {
                continue;
            }
            TextureInfo info = new TextureInfo();
            info.image = image;
            info.wrapS = texture.getWrappingS();
            info.wrapT = texture.getWrappingT();
            info.blending = texture.getBlending();
            info.blendColor = texture.getBlendColor() | 0xFF000000;
            Transform textureTransform = new Transform();
            texture.getCompositeTransform(textureTransform);
            info.transformMatrix = textureTransform.getMatrix().clone();
            infos[unit] = info;
        }
        return infos;
    }

    private boolean hasAnyTexture(TextureInfo[] textures) {
        if (textures == null) {
            return false;
        }
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            if (textures[unit] != null) {
                return true;
            }
        }
        return false;
    }

    private FogInfo getFogInfo(Appearance appearance) {
        if (appearance == null) {
            return null;
        }
        Fog fog = appearance.getFog();
        if (fog == null) {
            return null;
        }
        FogInfo info = new FogInfo();
        info.color = fog.getColor() | 0xFF000000;
        info.mode = fog.getMode();
        info.density = fog.getDensity();
        info.nearDistance = fog.getNearDistance();
        info.farDistance = fog.getFarDistance();
        return info;
    }

    private int resolveBaseColor(VertexBuffer vertices, Appearance appearance) {
        if (appearance != null && appearance.getMaterial() != null) {
            Material material = appearance.getMaterial();
            if (material.isVertexColorTrackingEnabled()) {
                return vertices.getDefaultColor();
            }
            int materialColor = material.getColor(Material.DIFFUSE);
            if (materialColor != 0) {
                return materialColor;
            }
        }
        return vertices.getDefaultColor();
    }

    /**
     * DIAGNOSTIC: returns an opaque ARGB color (or 0 to skip tinting) chosen by
     * vertex count, so the player can visually identify each object on screen.
     * Enabled only when -Dfreej2me.diag.tint is set. A different color per vertex
     * count so the parachute parts can be told apart from tower/plaza/buildings.
     * <p>
     * Legend (ARGB hex):
     * 4 -> FF00FFFF cyan
     * 16 -> FFFF0000 red
     * 32 -> FF00FF00 lime
     * 41 -> FFFF00FF magenta
     * 57 -> FF0000FF blue      (tower layers)
     * 89 -> FFFF8000 orange
     * 90 -> FFC000C0 purple
     * 100 -> FF00AA00 dark-green
     * 150 -> FF000000 black
     * 154 -> FF808080 grey
     * 161 -> FF800000 dark-red
     * 184 -> FF008080 teal
     * 203 -> FFC0C000 olive
     * 212 -> FF808000 olive-drab
     * 299 -> FFFFFF00 yellow
     * other -> FFFFFFFF white
     */
    private int pickTintColor(VertexBuffer vertices, CompositingMode compositingMode, Transform transform) {
        int verts;
        try {
            verts = vertices.getVertexCount();
        } catch (Throwable t) {
            if (diagRenderEnabled())
                System.out.println("[diag.tint] vertexCount FAILED");
            return 0xFF8B4513;  // brown - error case
        }
        switch (verts) {
            case 4:
                return 0xFF00FFFF;
            case 16:
                return 0xFFFF0000;
            case 32:
                return 0xFF00FF00;
            case 41:
                return 0xFFFF00FF;
            case 54:
                return 0xFFFF00FF;  // magenta - parachute canopy
            case 57:
                return 0xFF0000FF;
            case 89:
                return 0xFFFF8000;
            case 90:
                return 0xFFC000C0;
            case 100:
                return 0xFF00AA00;
            case 126:
                return 0xFFFF1493;  // deep pink - parachute body
            case 150:
                return 0xFF000000;
            case 154:
                return 0xFF808080;
            case 161:
                return 0xFF800000;
            case 184:
                return 0xFF008080;
            case 203:
                return 0xFFC0C000;
            case 212:
                return 0xFF808000;
            case 299:
                return 0xFFFFFF00;
            default:
                if (diagRenderEnabled())
                    System.out.println("[diag.tint] UNKNOWN verts=" + verts);
                return 0xFFFFFFFF;  // white - unknown vertex count
        }
    }

    private int getVertexColor(VertexArray colors, int index) {
        int r = ((int) colors.getComponentAsFloat(index, 0)) & 0xFF;
        int g = ((int) colors.getComponentAsFloat(index, 1)) & 0xFF;
        int b = ((int) colors.getComponentAsFloat(index, 2)) & 0xFF;
        int a = colors.getComponentCount() > 3 ? (((int) colors.getComponentAsFloat(index, 3)) & 0xFF) : 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean isCulled(float ndcArea, PolygonMode polygonMode) {
        if (polygonMode == null || polygonMode.getCulling() == PolygonMode.CULL_NONE) {
            return false;
        }
        // Screen-space projection flips Y, so winding on the raster surface is the inverse of raw NDC winding.
        float screenArea = -ndcArea;
        boolean frontFacing = polygonMode.getWinding() == PolygonMode.WINDING_CCW ? screenArea > 0f : screenArea < 0f;
        if (polygonMode.getCulling() == PolygonMode.CULL_BACK) {
            return !frontFacing;
        }
        if (polygonMode.getCulling() == PolygonMode.CULL_FRONT) {
            return frontFacing;
        }
        return false;
    }


    private int interpolateColor(ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2, float w0, float w1, float w2) {
        int a = clampColor(Math.round(w0 * v0.a + w1 * v1.a + w2 * v2.a));
        int r = clampColor(Math.round(w0 * v0.r + w1 * v1.r + w2 * v2.r));
        int g = clampColor(Math.round(w0 * v0.g + w1 * v1.g + w2 * v2.g));
        int b = clampColor(Math.round(w0 * v0.b + w1 * v1.b + w2 * v2.b));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int combineTexture(TextureInfo texture, int baseColor, float u, float v) {
        int sample = sampleTexture(texture, u, v);
        switch (texture.blending) {
            case Texture2D.FUNC_REPLACE:
                return sample;
            case Texture2D.FUNC_DECAL:
                return decalColor(baseColor, sample);
            case Texture2D.FUNC_BLEND:
                return blendTextureColor(baseColor, sample, texture.blendColor);
            case Texture2D.FUNC_ADD:
                return addColor(baseColor, sample);
            case Texture2D.FUNC_MODULATE:
            default:
                return multiplyColor(baseColor, sample);
        }
    }

    private int applyFog(int color, FogInfo fog, float distance) {
        float fogFactor = computeFogFactor(fog, distance);
        if (fogFactor <= 0f) {
            return color;
        }
        if (fogFactor >= 1f) {
            return (color & 0xFF000000) | (fog.color & 0x00FFFFFF);
        }
        int alpha = color & 0xFF000000;
        int r = Math.round(((color >>> 16) & 0xFF) * (1f - fogFactor) + ((fog.color >>> 16) & 0xFF) * fogFactor);
        int g = Math.round(((color >>> 8) & 0xFF) * (1f - fogFactor) + ((fog.color >>> 8) & 0xFF) * fogFactor);
        int b = Math.round((color & 0xFF) * (1f - fogFactor) + (fog.color & 0xFF) * fogFactor);
        return alpha | (clampColor(r) << 16) | (clampColor(g) << 8) | clampColor(b);
    }

    private float computeFogFactor(FogInfo fog, float distance) {
        if (distance <= 0f) {
            return 0f;
        }
        if (fog.mode == Fog.LINEAR) {
            if (fog.farDistance <= fog.nearDistance) {
                return distance >= fog.farDistance ? 1f : 0f;
            }
            return clamp01((distance - fog.nearDistance) / (fog.farDistance - fog.nearDistance));
        }
        if (fog.mode == Fog.EXPONENTIAL) {
            return clamp01(1f - (float) Math.exp(-Math.max(0f, fog.density) * distance));
        }
        return 0f;
    }

    private int sampleTexture(TextureInfo texture, float u, float v) {
        float su = wrapCoordinate(u, texture.wrapS);
        float sv = wrapCoordinate(v, texture.wrapT);
        int width = texture.image.getWidth();
        int height = texture.image.getHeight();
        int x = clamp((int) (su * (width - 1)), 0, width - 1);
        // M3G defines (0,0) at the upper-left of the texture image.
        int y = clamp((int) (sv * (height - 1)), 0, height - 1);
        return getImagePixel(texture.image, x, y);
    }

    private float wrapCoordinate(float value, int wrapMode) {
        if (wrapMode == Texture2D.WRAP_REPEAT) {
            float wrapped = value - (float) Math.floor(value);
            return wrapped < 0f ? wrapped + 1f : wrapped;
        }
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private int getImagePixel(Image2D image, int x, int y) {
        byte[] data = image.getImageData();
        int offset;
        switch (image.getFormat()) {
            case Image2D.ALPHA:
                offset = y * image.getWidth() + x;
                return ((data[offset] & 0xFF) << 24) | 0x00FFFFFF;
            case Image2D.LUMINANCE: {
                offset = y * image.getWidth() + x;
                int luminance = data[offset] & 0xFF;
                return 0xFF000000 | (luminance << 16) | (luminance << 8) | luminance;
            }
            case Image2D.LUMINANCE_ALPHA: {
                offset = (y * image.getWidth() + x) * 2;
                int luminance = data[offset] & 0xFF;
                int alpha = data[offset + 1] & 0xFF;
                return (alpha << 24) | (luminance << 16) | (luminance << 8) | luminance;
            }
            case Image2D.RGB:
                offset = (y * image.getWidth() + x) * 3;
                return 0xFF000000 | ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
            case Image2D.RGBA:
                offset = (y * image.getWidth() + x) * 4;
                return ((data[offset + 3] & 0xFF) << 24) | ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
            default:
                return 0xFFFFFFFF;
        }
    }

    private static int multiplyColor(int baseColor, int textureColor) {
        int a = (((baseColor >>> 24) & 0xFF) * ((textureColor >>> 24) & 0xFF)) / 255;
        int r = (((baseColor >>> 16) & 0xFF) * ((textureColor >>> 16) & 0xFF)) / 255;
        int g = (((baseColor >>> 8) & 0xFF) * ((textureColor >>> 8) & 0xFF)) / 255;
        int b = ((baseColor & 0xFF) * (textureColor & 0xFF)) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int addColor(int baseColor, int textureColor) {
        int a = clampColor(((baseColor >>> 24) & 0xFF) + ((textureColor >>> 24) & 0xFF));
        int r = clampColor(((baseColor >>> 16) & 0xFF) + ((textureColor >>> 16) & 0xFF));
        int g = clampColor(((baseColor >>> 8) & 0xFF) + ((textureColor >>> 8) & 0xFF));
        int b = clampColor((baseColor & 0xFF) + (textureColor & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int decalColor(int baseColor, int textureColor) {
        int baseA = (baseColor >>> 24) & 0xFF;
        int texA = (textureColor >>> 24) & 0xFF;
        int invA = 255 - texA;
        int r = ((((textureColor >>> 16) & 0xFF) * texA) + (((baseColor >>> 16) & 0xFF) * invA)) / 255;
        int g = ((((textureColor >>> 8) & 0xFF) * texA) + (((baseColor >>> 8) & 0xFF) * invA)) / 255;
        int b = (((textureColor & 0xFF) * texA) + ((baseColor & 0xFF) * invA)) / 255;
        return (baseA << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendTextureColor(int baseColor, int textureColor, int blendColor) {
        int texA = (textureColor >>> 24) & 0xFF;
        int outA = (((baseColor >>> 24) & 0xFF) * texA) / 255;
        int texR = (textureColor >>> 16) & 0xFF;
        int texG = (textureColor >>> 8) & 0xFF;
        int texB = textureColor & 0xFF;
        int baseR = (baseColor >>> 16) & 0xFF;
        int baseG = (baseColor >>> 8) & 0xFF;
        int baseB = baseColor & 0xFF;
        int blendR = (blendColor >>> 16) & 0xFF;
        int blendG = (blendColor >>> 8) & 0xFF;
        int blendB = blendColor & 0xFF;
        int r = ((baseR * (255 - texR)) + (blendR * texR)) / 255;
        int g = ((baseG * (255 - texG)) + (blendG * texG)) / 255;
        int b = ((baseB * (255 - texB)) + (blendB * texB)) / 255;
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static float min3(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }

    private static float max3(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static int clampColor(int value) {
        return clamp(value, 0, 255);
    }

    private static float interpolate(float a, float b, float t) {
        return a + ((b - a) * t);
    }

    private static int blend(int dstColor, int srcColor) {
        int srcA = (srcColor >>> 24) & 0xFF;
        if (srcA >= 0xFF) {
            return srcColor;
        }
        if (srcA <= 0) {
            return dstColor;
        }

        int dstA = (dstColor >>> 24) & 0xFF;
        int invA = 255 - srcA;
        int outA = srcA + ((dstA * invA) / 255);
        int outR = (((srcColor >>> 16) & 0xFF) * srcA + ((dstColor >>> 16) & 0xFF) * invA) / 255;
        int outG = (((srcColor >>> 8) & 0xFF) * srcA + ((dstColor >>> 8) & 0xFF) * invA) / 255;
        int outB = ((srcColor & 0xFF) * srcA + (dstColor & 0xFF) * invA) / 255;
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int addAlphaColor(int dstColor, int srcColor) {
        int srcA = (srcColor >>> 24) & 0xFF;
        int outA = clampColor(((dstColor >>> 24) & 0xFF) + srcA);
        int outR = clampColor(((dstColor >>> 16) & 0xFF) + ((((srcColor >>> 16) & 0xFF) * srcA) / 255));
        int outG = clampColor(((dstColor >>> 8) & 0xFF) + ((((srcColor >>> 8) & 0xFF) * srcA) / 255));
        int outB = clampColor((dstColor & 0xFF) + (((srcColor & 0xFF) * srcA) / 255));
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int multiplyColorX2(int firstColor, int secondColor) {
        int a = clampColor(((((firstColor >>> 24) & 0xFF) * ((secondColor >>> 24) & 0xFF)) * 2) / 255);
        int r = clampColor(((((firstColor >>> 16) & 0xFF) * ((secondColor >>> 16) & 0xFF)) * 2) / 255);
        int g = clampColor(((((firstColor >>> 8) & 0xFF) * ((secondColor >>> 8) & 0xFF)) * 2) / 255);
        int b = clampColor((((firstColor & 0xFF) * (secondColor & 0xFF)) * 2) / 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int applyCompositing(int dstColor, int srcColor, CompositingMode compositingMode) {
        if (compositingMode == null) {
            return srcColor;
        }
        switch (compositingMode.getBlending()) {
            case CompositingMode.ALPHA:
                return blend(dstColor, srcColor);
            case CompositingMode.ALPHA_ADD:
                return addAlphaColor(dstColor, srcColor);
            case CompositingMode.MODULATE:
                return multiplyColor(dstColor, srcColor);
            case CompositingMode.MODULATE_X2:
                return multiplyColorX2(dstColor, srcColor);
            case CompositingMode.REPLACE:
            default:
                return srcColor;
        }
    }

    private static int applyWriteMask(int dstColor, int outColor, CompositingMode compositingMode) {
        if (compositingMode == null) {
            return outColor;
        }
        int color = compositingMode.isColorWriteEnabled()
                ? (outColor & 0x00FFFFFF)
                : (dstColor & 0x00FFFFFF);
        int alpha = compositingMode.isAlphaWriteEnabled()
                ? (outColor & 0xFF000000)
                : (dstColor & 0xFF000000);
        return alpha | color;
    }

    private static final class ProjectedVertex {
        boolean visible;
        float clipX;
        float clipY;
        float clipZ;
        float clipW;
        float ndcX;
        float ndcY;
        float x;
        float y;
        float z;
        float invW;
        float fogDistance;
        final float[] u = new float[MAX_TEXTURE_UNITS];
        final float[] v = new float[MAX_TEXTURE_UNITS];
        float a;
        float r;
        float g;
        float b;
    }

    private static final class TextureInfo {
        Image2D image;
        int wrapS;
        int wrapT;
        int blending;
        int blendColor;
        float[] transformMatrix;
    }

    private static final class FogInfo {
        int color;
        int mode;
        float density;
        float nearDistance;
        float farDistance;
    }

    private static final int CLIP_LEFT = 0;
    private static final int CLIP_RIGHT = 1;
    private static final int CLIP_BOTTOM = 2;
    private static final int CLIP_TOP = 3;
    private static final int CLIP_NEAR = 4;
    private static final int CLIP_FAR = 5;

    private static final class SpriteRenderData {
        final VertexBuffer vertices;
        final TriangleStripArray triangles;
        final Appearance appearance;
        final Transform transform;

        SpriteRenderData(VertexBuffer vertices, TriangleStripArray triangles, Appearance appearance, Transform transform) {
            this.vertices = vertices;
            this.triangles = triangles;
            this.appearance = appearance;
            this.transform = transform;
        }
    }

    private static final class MorphAttribute {
        static final int POSITIONS = 0;
        static final int NORMALS = 1;
        static final int COLORS = 2;
        static final int TEXCOORDS0 = 3;
        static final int TEXCOORDS1 = 4;
    }

    private static final class MorphedVertexBufferCacheEntry {
        private final VertexBuffer vertexBuffer = new VertexBuffer();
        private int sourceRevision = Integer.MIN_VALUE;
    }

    private static final class MorphedVertexBufferKey {
        private final VertexBuffer base;
        private final VertexBuffer[] targets;
        private final int[] weightBits;

        MorphedVertexBufferKey(VertexBuffer base, MorphingMesh mesh, float[] weights) {
            this.base = base;
            this.targets = new VertexBuffer[mesh.getMorphTargetCount()];
            for (int i = 0; i < targets.length; i++) {
                targets[i] = mesh.getMorphTarget(i);
            }
            this.weightBits = new int[weights.length];
            for (int i = 0; i < weights.length; i++) {
                weightBits[i] = Float.floatToIntBits(weights[i]);
            }
        }

        public int hashCode() {
            int hash = System.identityHashCode(base);
            for (int i = 0; i < targets.length; i++) {
                hash = hash * 31 + System.identityHashCode(targets[i]);
            }
            for (int i = 0; i < weightBits.length; i++) {
                hash = hash * 31 + weightBits[i];
            }
            return hash;
        }

        public boolean equals(Object object) {
            if (!(object instanceof MorphedVertexBufferKey)) {
                return false;
            }
            MorphedVertexBufferKey other = (MorphedVertexBufferKey) object;
            if (other.base != base || other.targets.length != targets.length || other.weightBits.length != weightBits.length) {
                return false;
            }
            for (int i = 0; i < targets.length; i++) {
                if (other.targets[i] != targets[i]) {
                    return false;
                }
            }
            for (int i = 0; i < weightBits.length; i++) {
                if (other.weightBits[i] != weightBits[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private interface RenderSurface {
        int getPixel(int x, int y);

        void setPixel(int x, int y, int argb);
    }

    private static final class BufferedImageSurface implements RenderSurface {
        private final BufferedImage image;
        private final int width;
        private final int height;

        BufferedImageSurface(BufferedImage image) {
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        private boolean isInBounds(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height;
        }

        public int getPixel(int x, int y) {
            if (!isInBounds(x, y)) {
                return 0;
            }
            return image.getRGB(x, y);
        }

        public void setPixel(int x, int y, int argb) {
            if (!isInBounds(x, y)) {
                return;
            }
            // A Graphics-backed render target is effectively opaque on the desktop path.
            image.setRGB(x, y, argb | 0xFF000000);
        }
    }

    private static final class Image2DSurface implements RenderSurface {
        private final Image2D image;
        private final byte[] data;
        private final int width;
        private final int height;

        Image2DSurface(Image2D image) {
            this.image = image;
            this.data = image.getImageData();
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        private boolean isInBounds(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height;
        }

        public int getPixel(int x, int y) {
            if (!isInBounds(x, y)) {
                return 0;
            }
            int offset = (y * width + x) * (image.getFormat() == Image2D.RGB ? 3 : 4);
            return image.getFormat() == Image2D.RGB
                    ? 0xFF000000 | ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF)
                    : ((data[offset + 3] & 0xFF) << 24) | ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
        }

        public void setPixel(int x, int y, int argb) {
            if (!isInBounds(x, y)) {
                return;
            }
            int offset = (y * width + x) * (image.getFormat() == Image2D.RGB ? 3 : 4);
            data[offset] = (byte) ((argb >>> 16) & 0xFF);
            data[offset + 1] = (byte) ((argb >>> 8) & 0xFF);
            data[offset + 2] = (byte) (argb & 0xFF);
            if (image.getFormat() == Image2D.RGBA) {
                data[offset + 3] = (byte) ((argb >>> 24) & 0xFF);
            }
        }
    }

    public interface BackendFactory {
        Backend create(Graphics3D owner, Backend softwareFallback);
    }

    public interface Backend {
        void bindTarget(Object target, boolean depthBuffer, int hints);

        void clear(Background background);

        void render(Mesh mesh, int submeshIndex, VertexBuffer vertices, TriangleStripArray triangles, Appearance appearance, Transform transform);

        void releaseTarget();
    }

    public interface SkinningBackend extends Backend {
        boolean renderSkinned(SkinnedMesh mesh, TriangleStripArray triangles, Appearance appearance, Transform transform);
    }

    static final class SoftwareRenderBackend implements Backend {
        private final Graphics3D owner;

        SoftwareRenderBackend(Graphics3D owner) {
            this.owner = owner;
        }

        public void clear(Background background) {
            owner.clearWithSoftwareBackend(background);
        }

        public void bindTarget(Object target, boolean depthBuffer, int hints) {
        }

        public void render(Mesh mesh, int submeshIndex, VertexBuffer vertices, TriangleStripArray triangles, Appearance appearance, Transform transform) {
            owner.renderTriangleStripsWithSoftwareBackend(vertices, triangles, appearance, transform);
        }

        public void releaseTarget() {
        }
    }

}
