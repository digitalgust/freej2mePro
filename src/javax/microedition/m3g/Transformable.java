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

public abstract class Transformable extends Object3D
{
	private static final float EPSILON = 1.0e-6f;

	private final Transform matrix = new Transform();
	private final Transform orientation = new Transform();
	private final float[] translation = new float[] { 0f, 0f, 0f };
	private final float[] scale = new float[] { 1f, 1f, 1f };

	public void getCompositeTransform(Transform transform)
	{
		if (transform == null)
		{
			throw new NullPointerException();
		}
		transform.setIdentity();
		appendCompositeTransform(transform, this);
	}

	public void getOrientation(float[] angleAxis)
	{
		if (angleAxis == null)
		{
			throw new NullPointerException();
		}
		if (angleAxis.length < 4)
		{
			throw new IllegalArgumentException();
		}

		extractAngleAxis(orientation.getMatrix(), angleAxis);
	}

	public void getScale(float[] xyz)
	{
		if (xyz == null)
		{
			throw new NullPointerException();
		}
		if (xyz.length < 3)
		{
			throw new IllegalArgumentException();
		}

		xyz[0] = scale[0];
		xyz[1] = scale[1];
		xyz[2] = scale[2];
	}

	public void getTransform(Transform transform)
	{
		if (transform == null)
		{
			throw new NullPointerException();
		}
		transform.set(matrix);
	}

	public void getTranslation(float[] xyz)
	{
		if (xyz == null)
		{
			throw new NullPointerException();
		}
		if (xyz.length < 3)
		{
			throw new IllegalArgumentException();
		}

		xyz[0] = translation[0];
		xyz[1] = translation[1];
		xyz[2] = translation[2];
	}

	public void postRotate(float angle, float ax, float ay, float az)
	{
		if (!validateRotation(angle, ax, ay, az))
		{
			return;
		}
		orientation.postRotate(angle, ax, ay, az);
	}

	public void preRotate(float angle, float ax, float ay, float az)
	{
		if (!validateRotation(angle, ax, ay, az))
		{
			return;
		}

		Transform delta = new Transform();
		delta.postRotate(angle, ax, ay, az);
		delta.postMultiply(orientation);
		orientation.set(delta);
	}

	public void scale(float sx, float sy, float sz)
	{
		scale[0] *= sx;
		scale[1] *= sy;
		scale[2] *= sz;
	}

	public void setOrientation(float angle, float ax, float ay, float az)
	{
		orientation.setIdentity();
		if (!validateRotation(angle, ax, ay, az))
		{
			return;
		}
		orientation.postRotate(angle, ax, ay, az);
	}

	public void setScale(float sx, float sy, float sz)
	{
		scale[0] = sx;
		scale[1] = sy;
		scale[2] = sz;
	}

	public void setTransform(Transform transform)
	{
		if (transform == null)
		{
			throw new NullPointerException();
		}
		this.matrix.set(transform);
	}

	public void setTranslation(float tx, float ty, float tz)
	{
		translation[0] = tx;
		translation[1] = ty;
		translation[2] = tz;
	}

	public void translate(float tx, float ty, float tz)
	{
		translation[0] += tx;
		translation[1] += ty;
		translation[2] += tz;
	}

	private static void appendCompositeTransform(Transform target, Transformable current)
	{
		if (current instanceof Node)
		{
			Node parent = ((Node) current).getParent();
			if (parent != null)
			{
				appendCompositeTransform(target, parent);
			}
		}

		Transform local = new Transform();
		current.getLocalCompositeTransform(local);
		target.postMultiply(local);
	}

	void getLocalCompositeTransform(Transform transform)
	{
		transform.setIdentity();
		transform.postTranslate(translation[0], translation[1], translation[2]);
		transform.postMultiply(orientation);
		transform.postScale(scale[0], scale[1], scale[2]);
		transform.postMultiply(matrix);
	}

	void setOrientationQuat(float qx, float qy, float qz, float qw)
	{
		orientation.setIdentity();
		orientation.postRotateQuat(qx, qy, qz, qw);
	}

	void setOrientationTransform(Transform transform)
	{
		orientation.set(transform);
	}

	private static boolean validateRotation(float angle, float ax, float ay, float az)
	{
		if (Math.abs(angle) <= EPSILON)
		{
			return false;
		}
		float lengthSquared = ax * ax + ay * ay + az * az;
		if (lengthSquared <= EPSILON * EPSILON)
		{
			throw new IllegalArgumentException();
		}
		return true;
	}

	private static void extractAngleAxis(float[] matrix, float[] angleAxis)
	{
		float trace = matrix[0] + matrix[5] + matrix[10];
		float cos = (trace - 1f) * 0.5f;
		cos = Math.max(-1f, Math.min(1f, cos));
		float angle = (float) Math.toDegrees(Math.acos(cos));
		if (Math.abs(angle) <= EPSILON)
		{
			angleAxis[0] = 0f;
			angleAxis[1] = 0f;
			angleAxis[2] = 0f;
			angleAxis[3] = 1f;
			return;
		}

		float rx = matrix[9] - matrix[6];
		float ry = matrix[2] - matrix[8];
		float rz = matrix[4] - matrix[1];
		float length = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
		if (length <= EPSILON)
		{
			angleAxis[0] = angle;
			angleAxis[1] = 0f;
			angleAxis[2] = 0f;
			angleAxis[3] = 1f;
			return;
		}

		angleAxis[0] = angle;
		angleAxis[1] = -rx / length;
		angleAxis[2] = -ry / length;
		angleAxis[3] = -rz / length;
	}

}
