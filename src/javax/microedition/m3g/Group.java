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

import java.util.Vector;

import javax.microedition.m3g.base.M3GMath;

public class Group extends Node
{
	private static final float EPSILON = 1.0e-6f;

	private Vector<Node> nodes;

	private static final class PickHit
	{
		Node node;
		int submeshIndex;
		float distance = Float.POSITIVE_INFINITY;
		float normalX = 0f;
		float normalY = 0f;
		float normalZ = 1f;
		float textureS = 0f;
		float textureT = 0f;
	}


	public Group()
	{
		nodes = new Vector<Node>();
	}


	public void addChild(Node child)
	{
		if (child == null)
		{
			throw new NullPointerException();
		}
		nodes.add(child);
		child.setParent(this);
	}

	public Node getChild(int index) { return nodes.get(index); }

	public int getChildCount() { return nodes.size(); }

	public boolean pick(int scope, float x, float y, Camera camera, RayIntersection ri)
	{
		if (camera == null)
		{
			throw new NullPointerException();
		}

		Transform cameraToGroup = getCameraToGroup(camera);
		float[] ray = createCameraPickRay(x, y, camera, cameraToGroup);
		return pickInternal(scope, ray, ri, cameraToGroup);
	}

	public boolean pick(int scope, float ox, float oy, float oz, float dx, float dy, float dz, RayIntersection ri)
	{
		if (Math.abs(dx) <= EPSILON && Math.abs(dy) <= EPSILON && Math.abs(dz) <= EPSILON)
		{
			throw new IllegalArgumentException();
		}
		return pickInternal(scope, new float[] { ox, oy, oz, dx, dy, dz }, ri, null);
	}

	public void removeChild(Node child)
	{
		if (nodes.remove(child) && child != null)
		{
			child.setParent(null);
		}
	}

	private boolean pickInternal(int scope, float[] ray, RayIntersection ri, Transform cameraToGroup)
	{
		PickHit hit = new PickHit();
		Transform identity = new Transform();
		collectPick(this, identity, scope, ray, hit, cameraToGroup);
		if (hit.node == null)
		{
			return false;
		}
		if (ri != null)
		{
			ri.setResult(hit.node, hit.distance, hit.submeshIndex,
					hit.normalX, hit.normalY, hit.normalZ,
					hit.textureS, hit.textureT,
					ray);
		}
		return true;
	}

	private void collectPick(Group group, Transform parentTransform, int scope, float[] ray, PickHit hit, Transform cameraToGroup)
	{
		for (int i = 0; i < group.getChildCount(); i++)
		{
			Node child = group.getChild(i);
			Transform combined = new Transform(parentTransform);
			Transform local = new Transform();
			child.getLocalCompositeTransform(local);
			combined.postMultiply(local);

			if (child instanceof Mesh)
			{
				collectMeshPick((Mesh) child, combined, scope, ray, hit);
			}
			if (child instanceof Sprite3D && cameraToGroup != null)
			{
				collectSpritePick((Sprite3D) child, combined, cameraToGroup, scope, ray, hit);
			}
			if (child instanceof Group)
			{
				collectPick((Group) child, combined, scope, ray, hit, cameraToGroup);
			}
		}
	}

	private void collectMeshPick(Mesh mesh, Transform transform, int scope, float[] ray, PickHit hit)
	{
		if (!mesh.isPickingEnabled() || !matchesScope(mesh, scope))
		{
			return;
		}

		VertexBuffer vertices = resolvePickVertexBuffer(mesh);
		VertexArray positions = vertices.getPositions(null);
		if (positions == null)
		{
			return;
		}

		VertexArray normals = vertices.getNormals();
		VertexArray texCoords = vertices.getTexCoords(0, null);
		float[] positionScaleBias = vertices.getPositionScaleBias();
		float[] texScaleBias = texCoords != null ? vertices.getTexCoordScaleBias(0) : null;
		for (int submesh = 0; submesh < mesh.getSubmeshCount(); submesh++)
		{
			Appearance appearance = mesh.getAppearance(submesh);
			int[] triangles = expandTriangles(mesh.getIndexBuffer(submesh));
			for (int triangle = 0; triangle + 2 < triangles.length; triangle += 3)
			{
				int i0 = triangles[triangle];
				int i1 = triangles[triangle + 1];
				int i2 = triangles[triangle + 2];
				float[] local0 = getPosition(positions, positionScaleBias, i0);
				float[] local1 = getPosition(positions, positionScaleBias, i1);
				float[] local2 = getPosition(positions, positionScaleBias, i2);
				float[] world0 = transformPoint(transform, local0);
				float[] world1 = transformPoint(transform, local1);
				float[] world2 = transformPoint(transform, local2);
				if (isCulledForPick(appearance, world0, world1, world2, ray))
				{
					continue;
				}

				float[] barycentricAndDistance = intersectTriangle(ray, world0, world1, world2);
				if (barycentricAndDistance == null)
				{
					continue;
				}
				float distance = barycentricAndDistance[0];
				if (!(distance < hit.distance))
				{
					continue;
				}

				float b1 = barycentricAndDistance[1];
				float b2 = barycentricAndDistance[2];
				float b0 = 1f - b1 - b2;
				float[] localNormal = resolvePickNormal(normals, local0, local1, local2, i0, i1, i2, b0, b1, b2);
				float[] texture = resolvePickTexture(mesh, appearance, texCoords, texScaleBias, i0, i1, i2, b0, b1, b2);
				hit.node = mesh;
				hit.submeshIndex = submesh;
				hit.distance = distance;
				hit.normalX = localNormal[0];
				hit.normalY = localNormal[1];
				hit.normalZ = localNormal[2];
				hit.textureS = texture[0];
				hit.textureT = texture[1];
			}
		}
	}

	private void collectSpritePick(Sprite3D sprite, Transform transform, Transform cameraToGroup, int scope, float[] ray, PickHit hit)
	{
		if (!sprite.isPickingEnabled() || !matchesScope(sprite, scope) || !sprite.isScaled())
		{
			return;
		}

		Image2D image = sprite.getImage();
		Appearance appearance = sprite.getAppearance();
		int cropWidth = sprite.getCropWidth();
		int cropHeight = sprite.getCropHeight();
		if (image == null || appearance == null || cropWidth == 0 || cropHeight == 0)
		{
			return;
		}

		float[] halfSize = resolveSpriteHalfSize(sprite, image);
		if (halfSize == null)
		{
			return;
		}

		Transform billboardTransform = createSpriteBillboardTransform(transform, cameraToGroup);
		float[][] world = new float[][] {
				transformPoint(billboardTransform, new float[] { -halfSize[0], halfSize[1], 0f, 1f }),
				transformPoint(billboardTransform, new float[] { halfSize[0], halfSize[1], 0f, 1f }),
				transformPoint(billboardTransform, new float[] { -halfSize[0], -halfSize[1], 0f, 1f }),
				transformPoint(billboardTransform, new float[] { halfSize[0], -halfSize[1], 0f, 1f })
		};
		float[][] st = new float[][] {
				{ 0f, 0f },
				{ 1f, 0f },
				{ 0f, 1f },
				{ 1f, 1f }
		};
		int[][] triangles = new int[][] {
				{ 0, 1, 2 },
				{ 2, 1, 3 }
		};
		for (int i = 0; i < triangles.length; i++)
		{
			int i0 = triangles[i][0];
			int i1 = triangles[i][1];
			int i2 = triangles[i][2];
			float[] barycentricAndDistance = intersectTriangle(ray, world[i0], world[i1], world[i2]);
			if (barycentricAndDistance == null)
			{
				continue;
			}
			float distance = barycentricAndDistance[0];
			if (!(distance < hit.distance))
			{
				continue;
			}
			float b1 = barycentricAndDistance[1];
			float b2 = barycentricAndDistance[2];
			float b0 = 1f - b1 - b2;
			float s = (st[i0][0] * b0) + (st[i1][0] * b1) + (st[i2][0] * b2);
			float t = (st[i0][1] * b0) + (st[i1][1] * b1) + (st[i2][1] * b2);
			if (!passesSpriteAlphaTest(sprite, appearance, image, s, t))
			{
				continue;
			}

			hit.node = sprite;
			hit.submeshIndex = 0;
			hit.distance = distance;
			hit.normalX = 0f;
			hit.normalY = 0f;
			hit.normalZ = 1f;
			hit.textureS = s;
			hit.textureT = t;
		}
	}

	private float[] createCameraPickRay(float x, float y, Camera camera, Transform cameraToGroup)
	{
		Transform projection = camera.getProjectionTransform(1, 1);
		projection.invert();

		float[] nearPoint = new float[] { (2f * x) - 1f, 1f - (2f * y), -1f, 1f };
		float[] farPoint = new float[] { (2f * x) - 1f, 1f - (2f * y), 1f, 1f };
		projection.transform(nearPoint);
		projection.transform(farPoint);
		normalizeHomogeneous(nearPoint);
		normalizeHomogeneous(farPoint);

		cameraToGroup.transform(nearPoint);
		cameraToGroup.transform(farPoint);
		normalizeHomogeneous(nearPoint);
		normalizeHomogeneous(farPoint);

		return new float[] {
				nearPoint[0], nearPoint[1], nearPoint[2],
				farPoint[0] - nearPoint[0],
				farPoint[1] - nearPoint[1],
				farPoint[2] - nearPoint[2]
		};
	}

	private Transform getCameraToGroup(Camera camera)
	{
		Transform cameraToGroup = new Transform();
		if (!camera.getTransformTo(this, cameraToGroup))
		{
			throw new IllegalStateException();
		}
		return cameraToGroup;
	}

	private static void normalizeHomogeneous(float[] vector)
	{
		if (Math.abs(vector[3]) <= EPSILON)
		{
			throw new ArithmeticException();
		}
		float invW = 1f / vector[3];
		vector[0] *= invW;
		vector[1] *= invW;
		vector[2] *= invW;
		vector[3] = 1f;
	}

	private static boolean matchesScope(Node node, int scope)
	{
		return scope == -1 || (node.getScope() & scope) != 0;
	}

	private static float[] getPosition(VertexArray positions, float[] scaleBias, int index)
	{
		return new float[] {
				positions.getComponentAsFloat(index, 0) * scaleBias[0] + scaleBias[1],
				positions.getComponentAsFloat(index, 1) * scaleBias[0] + scaleBias[2],
				positions.getComponentAsFloat(index, 2) * scaleBias[0] + scaleBias[3],
				1f
		};
	}

	private static float[] transformPoint(Transform transform, float[] point)
	{
		float[] out = new float[4];
		M3GMath.transform(transform.getMatrix(), point, 0, out, 0);
		return out;
	}

	private static float[] resolveSpriteHalfSize(Sprite3D sprite, Image2D image)
	{
		int imageSpan = Math.max(image.getWidth(), image.getHeight());
		float halfWidth = (Math.abs(sprite.getCropWidth()) / (float) imageSpan) * 0.5f;
		float halfHeight = (Math.abs(sprite.getCropHeight()) / (float) imageSpan) * 0.5f;
		if (halfWidth <= 0f || halfHeight <= 0f)
		{
			return null;
		}
		return new float[] { halfWidth, halfHeight };
	}

	private static Transform createSpriteBillboardTransform(Transform transform, Transform cameraToGroup)
	{
		Transform billboard = new Transform();
		float[] matrix = transform.getMatrix();
		float tx = matrix[3];
		float ty = matrix[7];
		float tz = matrix[11];
		float sx = axisLength(matrix[0], matrix[4], matrix[8]);
		float sy = axisLength(matrix[1], matrix[5], matrix[9]);
		float sz = axisLength(matrix[2], matrix[6], matrix[10]);
		float[] cameraMatrix = cameraToGroup.getMatrix();
		float cameraSx = axisLength(cameraMatrix[0], cameraMatrix[4], cameraMatrix[8]);
		float cameraSy = axisLength(cameraMatrix[1], cameraMatrix[5], cameraMatrix[9]);
		float cameraSz = axisLength(cameraMatrix[2], cameraMatrix[6], cameraMatrix[10]);
		float safeSx = sx == 0f ? 1f : sx;
		float safeSy = sy == 0f ? 1f : sy;
		float safeSz = sz == 0f ? 1f : sz;
		float safeCameraSx = cameraSx == 0f ? 1f : cameraSx;
		float safeCameraSy = cameraSy == 0f ? 1f : cameraSy;
		float safeCameraSz = cameraSz == 0f ? 1f : cameraSz;
		float[] out = billboard.getMatrix();
		out[0] = (cameraMatrix[0] / safeCameraSx) * safeSx;
		out[1] = (cameraMatrix[1] / safeCameraSy) * safeSy;
		out[2] = (cameraMatrix[2] / safeCameraSz) * safeSz;
		out[3] = tx;
		out[4] = (cameraMatrix[4] / safeCameraSx) * safeSx;
		out[5] = (cameraMatrix[5] / safeCameraSy) * safeSy;
		out[6] = (cameraMatrix[6] / safeCameraSz) * safeSz;
		out[7] = ty;
		out[8] = (cameraMatrix[8] / safeCameraSx) * safeSx;
		out[9] = (cameraMatrix[9] / safeCameraSy) * safeSy;
		out[10] = (cameraMatrix[10] / safeCameraSz) * safeSz;
		out[11] = tz;
		return billboard;
	}

	private static int[] expandTriangles(IndexBuffer buffer)
	{
		if (buffer instanceof TriangleStripArray)
		{
			TriangleStripArray strips = (TriangleStripArray) buffer;
			int[] raw = strips.getRawIndices();
			int[] stripLengths = strips.getStripLengths();
			int triangleCount = 0;
			for (int i = 0; i < stripLengths.length; i++)
			{
				triangleCount += stripLengths[i] - 2;
			}
			int[] triangles = new int[triangleCount * 3];
			int offset = 0;
			int dst = 0;
			for (int i = 0; i < stripLengths.length; i++)
			{
				int length = stripLengths[i];
				for (int tri = 0; tri < length - 2; tri++)
				{
					if ((tri & 1) == 0)
					{
						triangles[dst++] = raw[offset + tri];
						triangles[dst++] = raw[offset + tri + 1];
						triangles[dst++] = raw[offset + tri + 2];
					}
					else
					{
						triangles[dst++] = raw[offset + tri + 1];
						triangles[dst++] = raw[offset + tri];
						triangles[dst++] = raw[offset + tri + 2];
					}
				}
				offset += length;
			}
			return triangles;
		}

		int[] indices = new int[buffer.getIndexCount()];
		buffer.getIndices(indices);
		int triangleCount = indices.length / 3;
		int[] triangles = new int[triangleCount * 3];
		System.arraycopy(indices, 0, triangles, 0, triangles.length);
		return triangles;
	}

	private static boolean isCulledForPick(Appearance appearance, float[] v0, float[] v1, float[] v2, float[] ray)
	{
		PolygonMode polygonMode = appearance != null ? appearance.getPolygonMode() : null;
		int culling = polygonMode != null ? polygonMode.getCulling() : PolygonMode.CULL_BACK;
		if (culling == PolygonMode.CULL_NONE)
		{
			return false;
		}

		int winding = polygonMode != null ? polygonMode.getWinding() : PolygonMode.WINDING_CCW;
		float[] edge1 = subtract(v1, v0);
		float[] edge2 = subtract(v2, v0);
		float[] normal = cross(edge1, edge2);
		float facing = normal[0] * ray[3] + normal[1] * ray[4] + normal[2] * ray[5];
		boolean frontFacing = winding == PolygonMode.WINDING_CCW ? facing < 0f : facing > 0f;
		return culling == PolygonMode.CULL_BACK ? !frontFacing : frontFacing;
	}

	private static float[] intersectTriangle(float[] ray, float[] v0, float[] v1, float[] v2)
	{
		float[] dir = new float[] { ray[3], ray[4], ray[5] };
		float[] edge1 = subtract(v1, v0);
		float[] edge2 = subtract(v2, v0);
		float[] pvec = cross(dir, edge2);
		float det = dot(edge1, pvec);
		if (Math.abs(det) <= EPSILON)
		{
			return null;
		}
		float invDet = 1f / det;
		float[] originToV0 = new float[] { ray[0] - v0[0], ray[1] - v0[1], ray[2] - v0[2] };
		float u = dot(originToV0, pvec) * invDet;
		if (u < 0f || u > 1f)
		{
			return null;
		}
		float[] qvec = cross(originToV0, edge1);
		float v = dot(dir, qvec) * invDet;
		if (v < 0f || u + v > 1f)
		{
			return null;
		}
		float t = dot(edge2, qvec) * invDet;
		if (t < 0f)
		{
			return null;
		}
		return new float[] { t, u, v };
	}

	private static float[] resolvePickNormal(VertexArray normals,
			float[] local0, float[] local1, float[] local2,
			int i0, int i1, int i2,
			float b0, float b1, float b2)
	{
		if (normals != null)
		{
			float nx = normals.getComponentAsFloat(i0, 0) * b0
					+ normals.getComponentAsFloat(i1, 0) * b1
					+ normals.getComponentAsFloat(i2, 0) * b2;
			float ny = normals.getComponentAsFloat(i0, 1) * b0
					+ normals.getComponentAsFloat(i1, 1) * b1
					+ normals.getComponentAsFloat(i2, 1) * b2;
			float nz = normals.getComponentAsFloat(i0, 2) * b0
					+ normals.getComponentAsFloat(i1, 2) * b1
					+ normals.getComponentAsFloat(i2, 2) * b2;
			return normalize(nx, ny, nz);
		}

		float[] edge1 = subtract(local1, local0);
		float[] edge2 = subtract(local2, local0);
		float[] faceNormal = cross(edge1, edge2);
		return normalize(faceNormal[0], faceNormal[1], faceNormal[2]);
	}

	private static float[] resolvePickTexture(Mesh mesh, Appearance appearance,
			VertexArray texCoords, float[] texScaleBias,
			int i0, int i1, int i2,
			float b0, float b1, float b2)
	{
		if (texCoords == null)
		{
			return new float[] { 0f, 0f };
		}

		float s = (texCoords.getComponentAsFloat(i0, 0) * b0)
				+ (texCoords.getComponentAsFloat(i1, 0) * b1)
				+ (texCoords.getComponentAsFloat(i2, 0) * b2);
		float t = (texCoords.getComponentAsFloat(i0, 1) * b0)
				+ (texCoords.getComponentAsFloat(i1, 1) * b1)
				+ (texCoords.getComponentAsFloat(i2, 1) * b2);
		s = s * texScaleBias[0] + texScaleBias[1];
		t = t * texScaleBias[0] + texScaleBias[2];

		Texture2D texture = appearance != null ? appearance.getTexture(0) : null;
		if (texture == null)
		{
			return new float[] { s, t };
		}

		Transform textureTransform = new Transform();
		texture.getCompositeTransform(textureTransform);
		float[] input = new float[] { s, t, 0f, 1f };
		float[] output = new float[4];
		M3GMath.transform(textureTransform.getMatrix(), input, 0, output, 0);
		if (Math.abs(output[3]) > EPSILON)
		{
			return new float[] { output[0] / output[3], output[1] / output[3] };
		}
		return new float[] { output[0], output[1] };
	}

	private static boolean passesSpriteAlphaTest(Sprite3D sprite, Appearance appearance, Image2D image, float s, float t)
	{
		if (s < 0f || s > 1f || t < 0f || t > 1f)
		{
			return false;
		}

		float sourceU = (sprite.getCropX() + (s * sprite.getCropWidth())) / (float) image.getWidth();
		float sourceV = (sprite.getCropY() + (t * sprite.getCropHeight())) / (float) image.getHeight();
		if (sourceU < 0f || sourceU > 1f || sourceV < 0f || sourceV > 1f)
		{
			return false;
		}

		int x = clamp((int) (sourceU * (image.getWidth() - 1)), 0, image.getWidth() - 1);
		int y = clamp((int) (sourceV * (image.getHeight() - 1)), 0, image.getHeight() - 1);
		int pixel = getImagePixel(image, x, y);
		int alpha = (pixel >>> 24) & 0xFF;
		CompositingMode compositingMode = appearance != null ? appearance.getCompositingMode() : null;
		float alphaThreshold = compositingMode != null ? compositingMode.getAlphaThreshold() : 0f;
		return alpha >= Math.round(alphaThreshold * 255f);
	}

	private static int getImagePixel(Image2D image, int x, int y)
	{
		byte[] data = image.getImageData();
		int offset;
		switch (image.getFormat())
		{
			case Image2D.ALPHA:
				offset = y * image.getWidth() + x;
				return ((data[offset] & 0xFF) << 24) | 0x00FFFFFF;
			case Image2D.LUMINANCE:
			{
				offset = y * image.getWidth() + x;
				int luminance = data[offset] & 0xFF;
				return 0xFF000000 | (luminance << 16) | (luminance << 8) | luminance;
			}
			case Image2D.LUMINANCE_ALPHA:
			{
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

	private static float[] subtract(float[] a, float[] b)
	{
		return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
	}

	private static float[] cross(float[] a, float[] b)
	{
		return new float[] {
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0]
		};
	}

	private static float dot(float[] a, float[] b)
	{
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static float axisLength(float x, float y, float z)
	{
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	private static float[] normalize(float x, float y, float z)
	{
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		if (length <= EPSILON)
		{
			return new float[] { 0f, 0f, 1f };
		}
		return new float[] { x / length, y / length, z / length };
	}

	private static VertexBuffer resolvePickVertexBuffer(Mesh mesh)
	{
		if (mesh instanceof SkinnedMesh)
		{
			return ((SkinnedMesh) mesh).getSkinnedVertexBuffer();
		}
		if (mesh instanceof MorphingMesh)
		{
			return createMorphedVertexBuffer((MorphingMesh) mesh);
		}
		return mesh.getVertexBuffer();
	}

	private static VertexBuffer createMorphedVertexBuffer(MorphingMesh mesh)
	{
		VertexBuffer base = mesh.getVertexBuffer();
		float[] weights = new float[mesh.getMorphTargetCount()];
		mesh.getWeights(weights);

		VertexBuffer morphed = new VertexBuffer();
		VertexArray positions = resolveMorphedArray(base.getPositions(null), mesh, weights, 0);
		float[] positionScaleBias = base.getPositionScaleBias();
		morphed.setPositions(positions, positionScaleBias[0], new float[] {
				positionScaleBias[1], positionScaleBias[2], positionScaleBias[3]
		});

		VertexArray normals = resolveMorphedArray(base.getNormals(), mesh, weights, 1);
		if (normals != null)
		{
			morphed.setNormals(normals);
		}

		VertexArray texCoords = resolveMorphedArray(base.getTexCoords(0, null), mesh, weights, 2);
		if (texCoords != null)
		{
			float[] texScaleBias = base.getTexCoordScaleBias(0);
			morphed.setTexCoords(0, texCoords, texScaleBias[0], new float[] {
					texScaleBias[1], texScaleBias[2], texScaleBias[3]
			});
		}
		morphed.setDefaultColor(base.getDefaultColor());
		return morphed;
	}

	private static VertexArray resolveMorphedArray(VertexArray baseArray, MorphingMesh mesh, float[] weights, int attribute)
	{
		if (baseArray == null)
		{
			return null;
		}
		VertexArray[] targetArrays = new VertexArray[mesh.getMorphTargetCount()];
		int nonNullTargets = 0;
		for (int i = 0; i < targetArrays.length; i++)
		{
			targetArrays[i] = getMorphTargetArray(mesh.getMorphTarget(i), attribute);
			if (targetArrays[i] != null)
			{
				nonNullTargets++;
			}
		}
		if (nonNullTargets == 0)
		{
			return baseArray;
		}
		if (nonNullTargets != targetArrays.length)
		{
			throw new IllegalStateException();
		}
		for (int i = 0; i < targetArrays.length; i++)
		{
			validateMorphArrayCompatibility(baseArray, targetArrays[i]);
		}
		return morphNumericArray(baseArray, targetArrays, weights);
	}

	private static VertexArray getMorphTargetArray(VertexBuffer buffer, int attribute)
	{
		switch (attribute)
		{
			case 0:
				return buffer.getPositions(null);
			case 1:
				return buffer.getNormals();
			case 2:
				return buffer.getTexCoords(0, null);
			default:
				return null;
		}
	}

	private static void validateMorphArrayCompatibility(VertexArray baseArray, VertexArray targetArray)
	{
		if (targetArray == null
				|| baseArray.getVertexCount() != targetArray.getVertexCount()
				|| baseArray.getComponentCount() != targetArray.getComponentCount()
				|| baseArray.getComponentType() != targetArray.getComponentType())
		{
			throw new IllegalStateException();
		}
	}

	private static VertexArray morphNumericArray(VertexArray baseArray, VertexArray[] targetArrays, float[] weights)
	{
		int componentType = baseArray.getComponentType();
		int count = baseArray.getVertexCount() * baseArray.getComponentCount();
		VertexArray morphed = new VertexArray(baseArray.getVertexCount(), baseArray.getComponentCount(), componentType);
		short[] baseValues = baseArray.getRawValues();
		if (componentType == 1)
		{
			byte[] values = new byte[count];
			for (int i = 0; i < count; i++)
			{
				float baseValue = (byte) baseValues[i];
				float morphedValue = baseValue;
				for (int target = 0; target < targetArrays.length; target++)
				{
					float targetValue = (byte) targetArrays[target].getRawValues()[i];
					morphedValue += weights[target] * (targetValue - baseValue);
				}
				values[i] = (byte) clamp(Math.round(morphedValue), -128, 127);
			}
			morphed.set(0, baseArray.getVertexCount(), values);
		}
		else
		{
			short[] values = new short[count];
			for (int i = 0; i < count; i++)
			{
				float baseValue = baseValues[i];
				float morphedValue = baseValue;
				for (int target = 0; target < targetArrays.length; target++)
				{
					float targetValue = targetArrays[target].getRawValues()[i];
					morphedValue += weights[target] * (targetValue - baseValue);
				}
				values[i] = (short) clamp(Math.round(morphedValue), Short.MIN_VALUE, Short.MAX_VALUE);
			}
			morphed.set(0, baseArray.getVertexCount(), values);
		}
		return morphed;
	}

	private static int clamp(int value, int min, int max)
	{
		return value < min ? min : (value > max ? max : value);
	}

}
