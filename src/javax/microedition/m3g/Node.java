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

import javax.microedition.m3g.base.M3GMath;

public abstract class Node extends Transformable
{
	private static final float EPSILON = 1.0e-6f;

	public static final int NONE  = 144;
	public static final int ORIGIN  = 145;
	public static final int X_AXIS  = 146;
	public static final int Y_AXIS  = 147;
	public static final int Z_AXIS  = 148;


	private Node alignRef;
	private Node yAlignRef;
	private float alphaFactor = 1f;
	private boolean picking = true;
	private boolean rendering = true;
	private int scope = -1;
	private Node parent;
	private int zTarget = NONE;
	private int yTarget = NONE;


	public void align(Node reference)
	{
		if (reference != null && getRoot(this) != getRoot(reference))
		{
			throw new IllegalArgumentException();
		}
		Node commonReference = reference != null ? reference : this;
		alignRecursive(this, commonReference);
	}

	public Node getAlignmentReference(int axis)
	{
		checkAxis(axis);
		return axis == Z_AXIS ? alignRef : yAlignRef;
	}

	public int getAlignmentTarget(int axis)
	{
		checkAxis(axis);
		return axis == Z_AXIS ? zTarget : yTarget;
	}

	public float getAlphaFactor() { return alphaFactor; }

	public Node getParent() { return parent; }

	public int getScope() { return scope; }

	public boolean getTransformTo(Node target, Transform transform)
	{
		if (target == null || transform == null)
		{
			throw new NullPointerException();
		}
		if (getRoot(this) != getRoot(target))
		{
			return false;
		}

		Transform source = new Transform();
		Transform destination = new Transform();
		getCompositeTransform(source);
		target.getCompositeTransform(destination);
		destination.invert();
		destination.postMultiply(source);
		transform.set(destination);
		return true;
	}

	public boolean isPickingEnabled() { return picking; }

	public boolean isRenderingEnabled() { return rendering; }

	public void setAlignment(Node zRef, int zTarget, Node yRef, int yTarget)
	{
		checkTarget(zTarget);
		checkTarget(yTarget);
		if (zRef == this || yRef == this)
		{
			throw new IllegalArgumentException();
		}
		if (zRef == yRef && zRef != null && zTarget == yTarget && zTarget != NONE)
		{
			throw new IllegalArgumentException();
		}

		alignRef = zRef;
		this.zTarget = zTarget;
		yAlignRef = yRef;
		this.yTarget = yTarget;
	}

	public void setAlphaFactor(float value)
	{
		if (value < 0f || value > 1f)
		{
			throw new IllegalArgumentException();
		}
		alphaFactor = value;
	}

	public void setPickingEnable(boolean enable) { picking = enable; }

	public void setRenderingEnable(boolean enable) { rendering = enable; }

	public void setScope(int value) { scope = value; }

	void setParent(Node parent)
	{
		this.parent = parent;
	}

	private static Node getRoot(Node node)
	{
		Node current = node;
		while (current.getParent() != null)
		{
			current = current.getParent();
		}
		return current;
	}

	private static void checkAxis(int axis)
	{
		if (axis != Y_AXIS && axis != Z_AXIS)
		{
			throw new IllegalArgumentException();
		}
	}

	private static void checkTarget(int target)
	{
		if (target != NONE && target != ORIGIN && target != X_AXIS && target != Y_AXIS && target != Z_AXIS)
		{
			throw new IllegalArgumentException();
		}
	}

	private static void alignRecursive(Node node, Node commonReference)
	{
		if (node.zTarget != NONE || node.yTarget != NONE)
		{
			node.applyAlignment(commonReference);
		}
		if (node instanceof Group)
		{
			Group group = (Group) node;
			for (int i = 0; i < group.getChildCount(); i++)
			{
				alignRecursive(group.getChild(i), commonReference);
			}
		}
		if (node instanceof SkinnedMesh)
		{
			alignRecursive(((SkinnedMesh) node).getSkeleton(), commonReference);
		}
	}

	private void applyAlignment(Node commonReference)
	{
		Node effectiveZRef = zTarget != NONE ? resolveEffectiveReference(alignRef, commonReference) : null;
		Node effectiveYRef = yTarget != NONE ? resolveEffectiveReference(yAlignRef, commonReference) : null;

		float[] targetZ = zTarget != NONE ? resolveTargetVector(effectiveZRef, zTarget) : null;
		float[] targetY = yTarget != NONE ? resolveTargetVector(effectiveYRef, yTarget) : null;
		float[] rotation = buildAlignmentRotation(targetZ, targetY);

		Transform transform = new Transform();
		float[] aligned = transform.getMatrix();
		M3GMath.setIdentity(aligned);
		aligned[0] = rotation[0];
		aligned[4] = rotation[1];
		aligned[8] = rotation[2];
		aligned[1] = rotation[3];
		aligned[5] = rotation[4];
		aligned[9] = rotation[5];
		aligned[2] = rotation[6];
		aligned[6] = rotation[7];
		aligned[10] = rotation[8];
		setOrientationTransform(transform);
	}

	private Node resolveEffectiveReference(Node explicitReference, Node commonReference)
	{
		Node effective = explicitReference != null ? explicitReference : commonReference;
		if (effective == null || getRoot(this) != getRoot(effective))
		{
			throw new IllegalStateException();
		}
		if (effective == this || isDescendant(this, effective))
		{
			throw new IllegalStateException();
		}
		return effective;
	}

	private float[] resolveTargetVector(Node reference, int target)
	{
		if (target == NONE)
		{
			return null;
		}

		Transform parentInverse = new Transform();
		if (parent != null)
		{
			parent.getCompositeTransform(parentInverse);
			parentInverse.invert();
		}

		float[] origin = getNodeOriginInParent(parentInverse);
		if (target == ORIGIN)
		{
			float[] point = transformPoint(parentInverse, getNodeOrigin(reference), 1f);
			return normalize(point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]);
		}

		float[] axis = transformVector(parentInverse, getReferenceAxis(reference, target));
		return normalize(axis[0], axis[1], axis[2]);
	}

	private float[] getNodeOriginInParent(Transform parentInverse)
	{
		return transformPoint(parentInverse, getNodeOrigin(this), 1f);
	}

	private static float[] getNodeOrigin(Node node)
	{
		Transform composite = new Transform();
		node.getCompositeTransform(composite);
		return transformPoint(composite, new float[] { 0f, 0f, 0f }, 1f);
	}

	private static float[] getReferenceAxis(Node reference, int axis)
	{
		Transform composite = new Transform();
		reference.getCompositeTransform(composite);
		switch (axis)
		{
			case X_AXIS:
				return transformVector(composite, new float[] { 1f, 0f, 0f });
			case Y_AXIS:
				return transformVector(composite, new float[] { 0f, 1f, 0f });
			case Z_AXIS:
				return transformVector(composite, new float[] { 0f, 0f, 1f });
			default:
				throw new IllegalArgumentException();
		}
	}

	private static float[] buildAlignmentRotation(float[] targetZ, float[] targetY)
	{
		if (targetZ != null && targetY != null)
		{
			float[] z = normalize(targetZ[0], targetZ[1], targetZ[2]);
			float[] yProjected = subtract(targetY, scale(z, dot(targetY, z)));
			float[] y = lengthSquared(yProjected) <= EPSILON ? arbitraryPerpendicular(z) : normalize(yProjected[0], yProjected[1], yProjected[2]);
			float[] x = normalize(cross(y, z));
			y = normalize(cross(z, x));
			return new float[] {
					x[0], x[1], x[2],
					y[0], y[1], y[2],
					z[0], z[1], z[2]
			};
		}

		float[] source = targetZ != null ? new float[] { 0f, 0f, 1f } : new float[] { 0f, 1f, 0f };
		float[] target = targetZ != null ? targetZ : targetY;
		float[] rotationMatrix = createRotationFromTo(source, normalize(target[0], target[1], target[2]));
		return new float[] {
				rotationMatrix[0], rotationMatrix[4], rotationMatrix[8],
				rotationMatrix[1], rotationMatrix[5], rotationMatrix[9],
				rotationMatrix[2], rotationMatrix[6], rotationMatrix[10]
		};
	}

	private static float[] createRotationFromTo(float[] source, float[] target)
	{
		float[] axis = cross(source, target);
		float axisLengthSquared = lengthSquared(axis);
		float dot = clampDot(dot(source, target));
		float[] matrix = new float[16];
		M3GMath.setIdentity(matrix);
		if (axisLengthSquared <= EPSILON)
		{
			if (dot < 0f)
			{
				float[] fallbackAxis = arbitraryPerpendicular(source);
				M3GMath.postRotate(matrix, 180f, fallbackAxis[0], fallbackAxis[1], fallbackAxis[2]);
			}
			return matrix;
		}

		float angle = (float) Math.toDegrees(Math.acos(dot));
		M3GMath.postRotate(matrix, angle, axis[0], axis[1], axis[2]);
		return matrix;
	}

	private static float[] arbitraryPerpendicular(float[] vector)
	{
		float[] basis = Math.abs(vector[2]) < 0.9f ? new float[] { 0f, 0f, 1f } : new float[] { 0f, 1f, 0f };
		return normalize(cross(vector, basis));
	}

	private static boolean isDescendant(Node ancestor, Node candidate)
	{
		Node current = candidate;
		while (current != null)
		{
			if (current == ancestor)
			{
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	private static float[] transformPoint(Transform transform, float[] point, float w)
	{
		float[] out = new float[4];
		M3GMath.transform(transform.getMatrix(), new float[] { point[0], point[1], point[2], w }, 0, out, 0);
		return out;
	}

	private static float[] transformVector(Transform transform, float[] vector)
	{
		float[] out = new float[4];
		M3GMath.transform(transform.getMatrix(), new float[] { vector[0], vector[1], vector[2], 0f }, 0, out, 0);
		return new float[] { out[0], out[1], out[2] };
	}

	private static float[] normalize(float x, float y, float z)
	{
		float length = axisLength(x, y, z);
		if (length <= EPSILON)
		{
			return new float[] { 0f, 0f, 1f };
		}
		return new float[] { x / length, y / length, z / length };
	}

	private static float[] normalize(float[] vector)
	{
		return normalize(vector[0], vector[1], vector[2]);
	}

	private static float axisLength(float x, float y, float z)
	{
		return (float) Math.sqrt(x * x + y * y + z * z);
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

	private static float[] subtract(float[] a, float[] b)
	{
		return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
	}

	private static float[] scale(float[] vector, float scalar)
	{
		return new float[] { vector[0] * scalar, vector[1] * scalar, vector[2] * scalar };
	}

	private static float lengthSquared(float[] vector)
	{
		return dot(vector, vector);
	}

	private static float clampDot(float value)
	{
		if (value > 1f)
		{
			return 1f;
		}
		if (value < -1f)
		{
			return -1f;
		}
		return value;
	}

}
