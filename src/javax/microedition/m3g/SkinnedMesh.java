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

public class SkinnedMesh extends Mesh
{
	private static final int MAX_TEXTURE_UNITS = 2;
	private static final int MAX_GPU_INFLUENCES = 4;

	private Group skeleton;
	private java.util.Vector boneTransforms = new java.util.Vector();
	private int cachedVertexCount = -1;
	private int cachedBoneTransformCount = -1;
	private int cachedMaxVertexInfluenceCount = -1;
	private int[][] cachedVertexBoneIndices;
	private float[][] cachedVertexBoneWeights;
	private Transform[] cachedBoneMatrices;
	private int cachedGpuMaxInfluenceCount = -1;
	private float[] cachedGpuBoneIndices;
	private float[] cachedGpuBoneWeights;
	private float[] cachedGpuBoneMatrices;
	private int cachedGpuPaletteRecordCount = -1;
	private int cachedGpuPaletteBoneCount = -1;
	private int[] cachedGpuRecordToPaletteIndices;
	private int[] cachedGpuPaletteSourceRecords;
	private boolean cachedGpuPaletteSupported;
	private final Transform cachedGpuWorkingTransform = new Transform();
	private VertexBuffer cachedSkinnedVertexBuffer;
	private VertexArray cachedSkinnedPositions;
	private VertexArray cachedSkinnedNormals;
	private float[] cachedSkinnedPositionValues;
	private short[] cachedOutPositions;
	private byte[] cachedOutNormals;
	private final float[] cachedSkinnedBias = new float[3];
	private int cachedPositionComponentCount = -1;
	private int cachedNormalComponentCount = -1;

	private static class BoneTransform {
		Node bone;
		int weight;
		int firstVertex;
		int numVertices;
		Transform atRestTransform;
	}

	static final class GpuSkinningData
	{
		final GpuSkinningTiming timing;
		final VertexBuffer baseVertices;
		final int vertexCount;
		final int boneCount;
		final int maxVertexInfluenceCount;
		final int packedInfluenceCount;
		final float[] boneIndices;
		final float[] boneWeights;
		final float[] boneMatrices;

		GpuSkinningData(GpuSkinningTiming timing, VertexBuffer baseVertices, int vertexCount, int boneCount, int maxVertexInfluenceCount,
				int packedInfluenceCount, float[] boneIndices, float[] boneWeights, float[] boneMatrices)
		{
			this.timing = timing;
			this.baseVertices = baseVertices;
			this.vertexCount = vertexCount;
			this.boneCount = boneCount;
			this.maxVertexInfluenceCount = maxVertexInfluenceCount;
			this.packedInfluenceCount = packedInfluenceCount;
			this.boneIndices = boneIndices;
			this.boneWeights = boneWeights;
			this.boneMatrices = boneMatrices;
		}
	}

	static final class GpuSkinningTiming
	{
		long totalNs;
		long ensureInfluenceCacheNs;
		long ensureGpuPaletteCacheNs;
		long packGpuInfluencesNs;
		long ensureGpuBoneMatricesBufferNs;
		long updateBoneMatricesNs;
		long copyBoneMatricesNs;
	}

	public SkinnedMesh(VertexBuffer vertices, IndexBuffer[] submeshes, Appearance[] appearances, Group skeleton)
	{
		super(vertices, submeshes, appearances);
		if (skeleton == null)
		{
			throw new NullPointerException();
		}
		if (skeleton.getParent() != null || skeleton instanceof World)
		{
			throw new IllegalArgumentException();
		}
		this.skeleton = skeleton;
		skeleton.setParent(this);
	}

	public SkinnedMesh(VertexBuffer vertices, IndexBuffer submesh, Appearance appearance, Group skeleton)
	{
		super(vertices, submesh, appearance);
		if (skeleton == null)
		{
			throw new NullPointerException();
		}
		if (skeleton.getParent() != null || skeleton instanceof World)
		{
			throw new IllegalArgumentException();
		}
		this.skeleton = skeleton;
		skeleton.setParent(this);
	}


	private boolean isDescendant(Node ancestor, Node node) {
		while (node != null) {
			if (node == ancestor) return true;
			node = node.getParent();
		}
		return false;
	}

	public void addTransform(Node bone, int weight, int firstVertex, int numVertices) {
		if (bone == null) {
			throw new NullPointerException();
		}
		if (!isDescendant(skeleton, bone)) {
			throw new IllegalArgumentException();
		}
		if (weight <= 0 || numVertices <= 0) {
			throw new IllegalArgumentException();
		}
		if (firstVertex < 0 || firstVertex + numVertices > 65535) {
			throw new IndexOutOfBoundsException();
		}
		
		Transform atRest = new Transform();
		if (!this.getTransformTo(bone, atRest)) {
			throw new ArithmeticException();
		}
		
		BoneTransform bt = new BoneTransform();
		bt.bone = bone;
		bt.weight = weight;
		bt.firstVertex = firstVertex;
		bt.numVertices = numVertices;
		bt.atRestTransform = atRest;
		
		boneTransforms.addElement(bt);
		invalidateSkinningCache();
	}

	public void getBoneTransform(Node bone, Transform transform) {
		if (bone == null || transform == null) {
			throw new NullPointerException();
		}
		if (!isDescendant(skeleton, bone)) {
			throw new IllegalArgumentException();
		}
		
		Transform atRest = null;
		for (int i = 0; i < boneTransforms.size(); i++) {
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			if (bt.bone == bone) {
				atRest = bt.atRestTransform;
				// the final at-rest transformation can be any, just break
				break;
			}
		}
		
		if (atRest != null) {
			transform.set(atRest);
		}
	}

	public int getBoneVertices(Node bone, int[] indices, float[] weights) {
		if (bone == null) {
			throw new NullPointerException();
		}
		if (!isDescendant(skeleton, bone)) {
			throw new IllegalArgumentException();
		}
		
		int maxVertex = 0;
		for (int i = 0; i < boneTransforms.size(); i++) {
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			if (bt.firstVertex + bt.numVertices > maxVertex) {
				maxVertex = bt.firstVertex + bt.numVertices;
			}
		}
		
		if (maxVertex == 0) {
			return 0;
		}
		
		int[] totalWeights = new int[maxVertex];
		int[] boneWeights = new int[maxVertex];
		
		for (int i = 0; i < boneTransforms.size(); i++) {
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			for (int v = bt.firstVertex; v < bt.firstVertex + bt.numVertices; v++) {
				totalWeights[v] += bt.weight;
				if (bt.bone == bone) {
					boneWeights[v] += bt.weight;
				}
			}
		}
		
		int count = 0;
		for (int v = 0; v < maxVertex; v++) {
			if (boneWeights[v] > 0) {
				count++;
			}
		}
		
		if (indices != null || weights != null) {
			if ((indices != null && indices.length < count) || (weights != null && weights.length < count)) {
				throw new IllegalArgumentException();
			}
			
			int idx = 0;
			for (int v = 0; v < maxVertex; v++) {
				if (boneWeights[v] > 0) {
					if (indices != null) {
						indices[idx] = v;
					}
					if (weights != null) {
						weights[idx] = (float)boneWeights[v] / totalWeights[v];
					}
					idx++;
				}
			}
		}
		
		return count;
	}

	public Group getSkeleton() { return skeleton; }

	VertexBuffer getSkinnedVertexBuffer() {
		VertexBuffer base = getVertexBuffer();
		if (boneTransforms.isEmpty()) {
			return base;
		}
		
		int vertexCount = base.getVertexCount();
		VertexArray basePositions = base.getPositions(null);
		if (basePositions == null) {
			return base;
		}
		ensureInfluenceCache(vertexCount);
		
		float[] positionScaleBias = base.getPositionScaleBias();
		VertexArray baseNormals = base.getNormals();
		ensureOutputBuffers(base, basePositions, baseNormals, vertexCount);

		updateBoneMatrices();
		
		float[] vPos = new float[4];
		float[] vPosOut = new float[4];
		float[] vNorm = new float[4];
		float[] vNormOut = new float[4];
		
		float[] skinnedPositionValues = cachedSkinnedPositionValues;
		byte[] outNormals = cachedOutNormals;
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		
		for (int v = 0; v < vertexCount; v++) {
			float px = basePositions.getComponentAsFloat(v, 0) * positionScaleBias[0] + positionScaleBias[1];
			float py = basePositions.getComponentAsFloat(v, 1) * positionScaleBias[0] + positionScaleBias[2];
			float pz = basePositions.getComponentAsFloat(v, 2) * positionScaleBias[0] + positionScaleBias[3];
			
			float nx = 0, ny = 0, nz = 0;
			if (baseNormals != null) {
				nx = baseNormals.getComponentAsFloat(v, 0) / 127.0f;
				ny = baseNormals.getComponentAsFloat(v, 1) / 127.0f;
				nz = baseNormals.getComponentAsFloat(v, 2) / 127.0f;
			}
			
			int[] boneIndices = cachedVertexBoneIndices[v];
			float[] boneWeights = cachedVertexBoneWeights[v];
			if (boneIndices == null) {
				// No bones affect this vertex, keep original
				vPosOut[0] = px;
				vPosOut[1] = py;
				vPosOut[2] = pz;
				vNormOut[0] = nx;
				vNormOut[1] = ny;
				vNormOut[2] = nz;
			} else {
				vPos[0] = px;
				vPos[1] = py;
				vPos[2] = pz;
				vPos[3] = 1.0f;
				
				vNorm[0] = nx;
				vNorm[1] = ny;
				vNorm[2] = nz;
				vNorm[3] = 0.0f; // direction vector
				
				vPosOut[0] = 0; vPosOut[1] = 0; vPosOut[2] = 0;
				vNormOut[0] = 0; vNormOut[1] = 0; vNormOut[2] = 0;
				
				for (int i = 0; i < boneIndices.length; i++) {
					float w = boneWeights[i];
					float[] matrix = cachedBoneMatrices[boneIndices[i]].getMatrix();
					
					vPosOut[0] += w * (matrix[0] * vPos[0] + matrix[1] * vPos[1] + matrix[2] * vPos[2] + matrix[3] * vPos[3]);
					vPosOut[1] += w * (matrix[4] * vPos[0] + matrix[5] * vPos[1] + matrix[6] * vPos[2] + matrix[7] * vPos[3]);
					vPosOut[2] += w * (matrix[8] * vPos[0] + matrix[9] * vPos[1] + matrix[10] * vPos[2] + matrix[11] * vPos[3]);
					
					if (baseNormals != null) {
						vNormOut[0] += w * (matrix[0] * vNorm[0] + matrix[1] * vNorm[1] + matrix[2] * vNorm[2]);
						vNormOut[1] += w * (matrix[4] * vNorm[0] + matrix[5] * vNorm[1] + matrix[6] * vNorm[2]);
						vNormOut[2] += w * (matrix[8] * vNorm[0] + matrix[9] * vNorm[1] + matrix[10] * vNorm[2]);
					}
				}
			}
			
			int pIdx = v * basePositions.getComponentCount();
			skinnedPositionValues[pIdx] = vPosOut[0];
			skinnedPositionValues[pIdx + 1] = vPosOut[1];
			skinnedPositionValues[pIdx + 2] = vPosOut[2];
			if (vPosOut[0] < minX) { minX = vPosOut[0]; }
			if (vPosOut[1] < minY) { minY = vPosOut[1]; }
			if (vPosOut[2] < minZ) { minZ = vPosOut[2]; }
			if (vPosOut[0] > maxX) { maxX = vPosOut[0]; }
			if (vPosOut[1] > maxY) { maxY = vPosOut[1]; }
			if (vPosOut[2] > maxZ) { maxZ = vPosOut[2]; }
			
			if (baseNormals != null) {
				// Normalize the normal vector
				float len = (float) Math.sqrt(vNormOut[0]*vNormOut[0] + vNormOut[1]*vNormOut[1] + vNormOut[2]*vNormOut[2]);
				if (len > 1.0e-6f) {
					vNormOut[0] /= len;
					vNormOut[1] /= len;
					vNormOut[2] /= len;
				}
				int nIdx = v * baseNormals.getComponentCount();
				outNormals[nIdx] = (byte) Math.max(-128, Math.min(127, Math.round(vNormOut[0] * 127.0f)));
				outNormals[nIdx + 1] = (byte) Math.max(-128, Math.min(127, Math.round(vNormOut[1] * 127.0f)));
				outNormals[nIdx + 2] = (byte) Math.max(-128, Math.min(127, Math.round(vNormOut[2] * 127.0f)));
			}
		}

		cachedSkinnedBias[0] = (minX + maxX) * 0.5f;
		cachedSkinnedBias[1] = (minY + maxY) * 0.5f;
		cachedSkinnedBias[2] = (minZ + maxZ) * 0.5f;
		float maxDeviation = 0f;
		for (int i = 0; i < skinnedPositionValues.length; i += 3) {
			float dx = Math.abs(skinnedPositionValues[i] - cachedSkinnedBias[0]);
			float dy = Math.abs(skinnedPositionValues[i + 1] - cachedSkinnedBias[1]);
			float dz = Math.abs(skinnedPositionValues[i + 2] - cachedSkinnedBias[2]);
			if (dx > maxDeviation) { maxDeviation = dx; }
			if (dy > maxDeviation) { maxDeviation = dy; }
			if (dz > maxDeviation) { maxDeviation = dz; }
		}
		float skinnedScale = maxDeviation > 0f ? (maxDeviation / 32767f) : 1f;
		short[] outPositions = cachedOutPositions;
		for (int i = 0; i < skinnedPositionValues.length; i += 3) {
			outPositions[i] = quantizeSkinnedComponent(skinnedPositionValues[i], cachedSkinnedBias[0], skinnedScale);
			outPositions[i + 1] = quantizeSkinnedComponent(skinnedPositionValues[i + 1], cachedSkinnedBias[1], skinnedScale);
			outPositions[i + 2] = quantizeSkinnedComponent(skinnedPositionValues[i + 2], cachedSkinnedBias[2], skinnedScale);
		}
		
		cachedSkinnedPositions.set(0, vertexCount, outPositions);
		if (cachedSkinnedNormals != null) {
			cachedSkinnedNormals.set(0, vertexCount, outNormals);
		}
		
		VertexBuffer skinned = cachedSkinnedVertexBuffer;
		skinned.setPositions(cachedSkinnedPositions, skinnedScale, cachedSkinnedBias);
		if (cachedSkinnedNormals != null) {
			skinned.setNormals(cachedSkinnedNormals);
		}
		skinned.setColors(base.getColors());
		skinned.setDefaultColor(base.getDefaultColor());
		for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
			VertexArray texCoords = base.getTexCoords(i, null);
			if (texCoords != null) {
				float[] texScaleBias = base.getTexCoordScaleBias(i);
				skinned.setTexCoords(i, texCoords, texScaleBias[0], new float[] { texScaleBias[1], texScaleBias[2], texScaleBias[3] });
			} else {
				skinned.setTexCoords(i, null, 1f, null);
			}
		}
		return skinned;
	}

	private void invalidateSkinningCache()
	{
		cachedVertexCount = -1;
		cachedBoneTransformCount = -1;
		cachedMaxVertexInfluenceCount = -1;
		cachedVertexBoneIndices = null;
		cachedVertexBoneWeights = null;
		cachedBoneMatrices = null;
		cachedGpuMaxInfluenceCount = -1;
		cachedGpuBoneIndices = null;
		cachedGpuBoneWeights = null;
		cachedGpuBoneMatrices = null;
		cachedGpuPaletteRecordCount = -1;
		cachedGpuPaletteBoneCount = -1;
		cachedGpuRecordToPaletteIndices = null;
		cachedGpuPaletteSourceRecords = null;
		cachedGpuPaletteSupported = false;
	}

	private void ensureInfluenceCache(int vertexCount)
	{
		int boneCount = boneTransforms.size();
		if (cachedVertexBoneIndices != null && cachedVertexBoneWeights != null
				&& cachedVertexCount == vertexCount && cachedBoneTransformCount == boneCount)
		{
			return;
		}

		cachedMaxVertexInfluenceCount = 0;
		int[] influenceCounts = new int[vertexCount];
		for (int i = 0; i < boneCount; i++)
		{
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			int start = Math.max(0, bt.firstVertex);
			int end = Math.min(vertexCount, bt.firstVertex + bt.numVertices);
			for (int v = start; v < end; v++)
			{
				influenceCounts[v]++;
			}
		}

		int[][] boneIndices = new int[vertexCount][];
		float[][] boneWeights = new float[vertexCount][];
		int[] totalWeights = new int[vertexCount];
		for (int i = 0; i < boneCount; i++)
		{
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			int start = Math.max(0, bt.firstVertex);
			int end = Math.min(vertexCount, bt.firstVertex + bt.numVertices);
			for (int v = start; v < end; v++)
			{
				totalWeights[v] += bt.weight;
			}
		}

		for (int v = 0; v < vertexCount; v++)
		{
			if (influenceCounts[v] > 0)
			{
				if (influenceCounts[v] > cachedMaxVertexInfluenceCount)
				{
					cachedMaxVertexInfluenceCount = influenceCounts[v];
				}
				boneIndices[v] = new int[influenceCounts[v]];
				boneWeights[v] = new float[influenceCounts[v]];
			}
		}

		int[] offsets = new int[vertexCount];
		for (int i = 0; i < boneCount; i++)
		{
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			int start = Math.max(0, bt.firstVertex);
			int end = Math.min(vertexCount, bt.firstVertex + bt.numVertices);
			for (int v = start; v < end; v++)
			{
				int offset = offsets[v]++;
				boneIndices[v][offset] = i;
				boneWeights[v][offset] = (float) bt.weight / totalWeights[v];
			}
		}

		Transform[] boneMatrices = new Transform[boneCount];
		for (int i = 0; i < boneCount; i++)
		{
			boneMatrices[i] = new Transform();
		}

		cachedVertexCount = vertexCount;
		cachedBoneTransformCount = boneCount;
		cachedVertexBoneIndices = boneIndices;
		cachedVertexBoneWeights = boneWeights;
		cachedBoneMatrices = boneMatrices;
	}

	GpuSkinningData getGpuSkinningData()
	{
		return getGpuSkinningData(MAX_GPU_INFLUENCES);
	}

	String getGpuSkinningUnavailableReason(int maxInfluences)
	{
		if (maxInfluences < 1)
		{
			return "invalidMaxInfluences";
		}
		VertexBuffer base = getVertexBuffer();
		if (boneTransforms.isEmpty())
		{
			return "noBoneTransforms";
		}
		int vertexCount = base.getVertexCount();
		if (vertexCount == 0 || base.getPositions(null) == null)
		{
			return "missingPositions";
		}
		ensureInfluenceCache(vertexCount);
		if (!ensureGpuPaletteCache())
		{
			return "inconsistentAtRestTransform";
		}
		return null;
	}

	GpuSkinningData getGpuSkinningData(int maxInfluences)
	{
		GpuSkinningTiming timing = new GpuSkinningTiming();
		long totalStartNs = System.nanoTime();
		if (maxInfluences < 1)
		{
			throw new IllegalArgumentException();
		}
		VertexBuffer base = getVertexBuffer();
		if (boneTransforms.isEmpty())
		{
			return null;
		}
		int vertexCount = base.getVertexCount();
		if (vertexCount == 0 || base.getPositions(null) == null)
		{
			return null;
		}

		long ensureInfluenceCacheStartNs = System.nanoTime();
		ensureInfluenceCache(vertexCount);
		timing.ensureInfluenceCacheNs = System.nanoTime() - ensureInfluenceCacheStartNs;
		long ensureGpuPaletteCacheStartNs = System.nanoTime();
		if (!ensureGpuPaletteCache())
		{
			timing.ensureGpuPaletteCacheNs = System.nanoTime() - ensureGpuPaletteCacheStartNs;
			return null;
		}
		timing.ensureGpuPaletteCacheNs = System.nanoTime() - ensureGpuPaletteCacheStartNs;
		int boneCount = cachedGpuPaletteBoneCount;
		if (cachedGpuBoneIndices == null
				|| cachedGpuBoneWeights == null
				|| cachedGpuMaxInfluenceCount != maxInfluences
				|| cachedGpuBoneIndices.length != vertexCount * maxInfluences
				|| cachedGpuBoneWeights.length != vertexCount * maxInfluences)
		{
			cachedGpuMaxInfluenceCount = maxInfluences;
			cachedGpuBoneIndices = new float[vertexCount * maxInfluences];
			cachedGpuBoneWeights = new float[vertexCount * maxInfluences];
			long packGpuInfluencesStartNs = System.nanoTime();
			for (int v = 0; v < vertexCount; v++)
			{
				int[] boneIndices = cachedVertexBoneIndices[v];
				float[] boneWeights = cachedVertexBoneWeights[v];
				if (boneIndices == null)
				{
					continue;
				}
				int offset = v * maxInfluences;
				packGpuInfluences(boneIndices, boneWeights, cachedGpuBoneIndices, cachedGpuBoneWeights, offset, maxInfluences,
						cachedGpuRecordToPaletteIndices);
			}
			timing.packGpuInfluencesNs = System.nanoTime() - packGpuInfluencesStartNs;
		}

		long ensureGpuBoneMatricesBufferStartNs = System.nanoTime();
		if (cachedGpuBoneMatrices == null || cachedGpuBoneMatrices.length != boneCount * 16)
		{
			cachedGpuBoneMatrices = new float[boneCount * 16];
		}
		timing.ensureGpuBoneMatricesBufferNs = System.nanoTime() - ensureGpuBoneMatricesBufferStartNs;
		long updateBoneMatricesStartNs = System.nanoTime();
		updateGpuBoneMatrices(boneCount);
		timing.updateBoneMatricesNs = System.nanoTime() - updateBoneMatricesStartNs;
		timing.copyBoneMatricesNs = 0L;
		timing.totalNs = System.nanoTime() - totalStartNs;
		return new GpuSkinningData(timing, base, vertexCount, boneCount, Math.min(cachedMaxVertexInfluenceCount, maxInfluences), maxInfluences,
				cachedGpuBoneIndices, cachedGpuBoneWeights, cachedGpuBoneMatrices);
	}

	private boolean ensureGpuPaletteCache()
	{
		int recordCount = boneTransforms.size();
		if (cachedGpuRecordToPaletteIndices != null && cachedGpuPaletteSourceRecords != null
				&& cachedGpuPaletteRecordCount == recordCount)
		{
			return cachedGpuPaletteSupported;
		}
		cachedGpuPaletteRecordCount = recordCount;
		cachedGpuPaletteBoneCount = 0;
		cachedGpuPaletteSupported = true;
		cachedGpuRecordToPaletteIndices = new int[recordCount];
		cachedGpuPaletteSourceRecords = new int[recordCount];
		for (int i = 0; i < recordCount; i++)
		{
			BoneTransform current = (BoneTransform) boneTransforms.elementAt(i);
			int paletteIndex = -1;
			for (int j = 0; j < i; j++)
			{
				BoneTransform previous = (BoneTransform) boneTransforms.elementAt(j);
				if (previous.bone != current.bone)
				{
					continue;
				}
				if (!hasSameAtRestTransform(previous.atRestTransform, current.atRestTransform))
				{
					cachedGpuPaletteSupported = false;
					cachedGpuPaletteBoneCount = 0;
					cachedGpuRecordToPaletteIndices = null;
					cachedGpuPaletteSourceRecords = null;
					return false;
				}
				paletteIndex = cachedGpuRecordToPaletteIndices[j];
				break;
			}
			if (paletteIndex < 0)
			{
				paletteIndex = cachedGpuPaletteBoneCount++;
				cachedGpuPaletteSourceRecords[paletteIndex] = i;
			}
			cachedGpuRecordToPaletteIndices[i] = paletteIndex;
		}
		return true;
	}

	private boolean hasSameAtRestTransform(Transform a, Transform b)
	{
		float[] am = a.getMatrix();
		float[] bm = b.getMatrix();
		for (int i = 0; i < 16; i++)
		{
			if (am[i] != bm[i])
			{
				return false;
			}
		}
		return true;
	}

	private static void packGpuInfluences(int[] sourceIndices, float[] sourceWeights, float[] targetIndices,
			float[] targetWeights, int targetOffset, int maxInfluences, int[] recordToPaletteIndices)
	{
		for (int i = 0; i < maxInfluences; i++)
		{
			targetIndices[targetOffset + i] = 0f;
			targetWeights[targetOffset + i] = 0f;
		}
		if (sourceIndices == null || sourceWeights == null)
		{
			return;
		}

		int influenceCount = sourceIndices.length;
		if (influenceCount <= maxInfluences)
		{
			float totalWeight = 0f;
			for (int i = 0; i < influenceCount; i++)
			{
				totalWeight += sourceWeights[i];
			}
			if (totalWeight <= 0f)
			{
				return;
			}
			for (int i = 0; i < influenceCount; i++)
			{
				targetIndices[targetOffset + i] = recordToPaletteIndices[sourceIndices[i]];
				targetWeights[targetOffset + i] = sourceWeights[i] / totalWeight;
			}
			return;
		}

		int[] selectedIndices = new int[maxInfluences];
		float[] selectedWeights = new float[maxInfluences];
		int selectedCount = 0;
		for (int i = 0; i < influenceCount; i++)
		{
			float weight = sourceWeights[i];
			if (selectedCount < maxInfluences)
			{
				selectedIndices[selectedCount] = sourceIndices[i];
				selectedWeights[selectedCount] = weight;
				selectedCount++;
				continue;
			}

			int lowestIndex = 0;
			for (int j = 1; j < maxInfluences; j++)
			{
				if (selectedWeights[j] < selectedWeights[lowestIndex])
				{
					lowestIndex = j;
				}
			}
			if (weight > selectedWeights[lowestIndex])
			{
				selectedIndices[lowestIndex] = sourceIndices[i];
				selectedWeights[lowestIndex] = weight;
			}
		}

		float totalSelectedWeight = 0f;
		for (int i = 0; i < maxInfluences; i++)
		{
			totalSelectedWeight += selectedWeights[i];
		}
		if (totalSelectedWeight <= 0f)
		{
			return;
		}
		for (int i = 0; i < maxInfluences; i++)
		{
			targetIndices[targetOffset + i] = recordToPaletteIndices[selectedIndices[i]];
			targetWeights[targetOffset + i] = selectedWeights[i] / totalSelectedWeight;
		}
	}

	private void updateBoneMatrices()
	{
		int boneCount = boneTransforms.size();
		for (int i = 0; i < boneCount; i++)
		{
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(i);
			Transform matrix = cachedBoneMatrices[i];
			if (bt.bone.getTransformTo(this, matrix))
			{
				matrix.postMultiply(bt.atRestTransform);
			}
			else
			{
				matrix.setIdentity();
			}
		}
	}

	private void updateGpuBoneMatrices(int boneCount)
	{
		Transform matrix = cachedGpuWorkingTransform;
		for (int i = 0; i < boneCount; i++)
		{
			int sourceRecord = cachedGpuPaletteSourceRecords[i];
			BoneTransform bt = (BoneTransform) boneTransforms.elementAt(sourceRecord);
			if (bt.bone.getTransformTo(this, matrix))
			{
				matrix.postMultiply(bt.atRestTransform);
			}
			else
			{
				matrix.setIdentity();
			}
			System.arraycopy(matrix.getMatrix(), 0, cachedGpuBoneMatrices, i * 16, 16);
		}
	}

	private void ensureOutputBuffers(VertexBuffer base, VertexArray basePositions, VertexArray baseNormals, int vertexCount)
	{
		int positionComponents = basePositions.getComponentCount();
		int normalComponents = baseNormals != null ? baseNormals.getComponentCount() : 0;
		boolean rebuild = cachedSkinnedVertexBuffer == null
				|| cachedPositionComponentCount != positionComponents
				|| cachedNormalComponentCount != normalComponents
				|| cachedSkinnedPositions == null
				|| cachedSkinnedPositions.getVertexCount() != vertexCount;
		if (!rebuild)
		{
			return;
		}

		cachedPositionComponentCount = positionComponents;
		cachedNormalComponentCount = normalComponents;
		cachedSkinnedPositions = new VertexArray(vertexCount, positionComponents, 2);
		cachedSkinnedNormals = baseNormals != null ? new VertexArray(vertexCount, normalComponents, 1) : null;
		cachedSkinnedPositionValues = new float[vertexCount * positionComponents];
		cachedOutPositions = new short[vertexCount * positionComponents];
		cachedOutNormals = baseNormals != null ? new byte[vertexCount * normalComponents] : null;
		cachedSkinnedVertexBuffer = new VertexBuffer();
		cachedSkinnedVertexBuffer.setColors(base.getColors());
		cachedSkinnedVertexBuffer.setDefaultColor(base.getDefaultColor());
		for (int i = 0; i < MAX_TEXTURE_UNITS; i++)
		{
			VertexArray texCoords = base.getTexCoords(i, null);
			if (texCoords != null)
			{
				float[] texScaleBias = base.getTexCoordScaleBias(i);
				cachedSkinnedVertexBuffer.setTexCoords(i, texCoords, texScaleBias[0], new float[] {
						texScaleBias[1], texScaleBias[2], texScaleBias[3]
				});
			}
		}
	}

	private static short quantizeSkinnedComponent(float value, float bias, float scale) {
		if (scale == 0f) {
			return 0;
		}
		int quantized = Math.round((value - bias) / scale);
		if (quantized < Short.MIN_VALUE) {
			quantized = Short.MIN_VALUE;
		} else if (quantized > Short.MAX_VALUE) {
			quantized = Short.MAX_VALUE;
		}
		return (short) quantized;
	}

}
