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

public class Transform
{
	private final float[] matrix = new float[16];

	public Transform()
	{
		setIdentity();
	}

	public Transform(Transform transform)
	{
		set(transform);
	}


	public void get(float[] matrix)
	{
		if (matrix == null)
		{
			throw new NullPointerException();
		}
		if (matrix.length < 16)
		{
			throw new IllegalArgumentException();
		}

		System.arraycopy(this.matrix, 0, matrix, 0, 16);
	}

	public void invert()
	{
		M3GMath.invert(matrix);
	}

	void invert(Transform scratch)
	{
		if (scratch == null)
		{
			throw new NullPointerException();
		}
		M3GMath.invert(matrix, scratch.matrix);
	}

	public void postMultiply(Transform transform)
	{
		if (transform == null)
		{
			throw new NullPointerException();
		}
		M3GMath.multiply(matrix, transform.matrix, matrix);
	}

	public void postRotate(float angle, float ax, float ay, float az)
	{
		M3GMath.postRotate(matrix, angle, ax, ay, az);
	}

	public void postRotateQuat(float qx, float qy, float qz, float qw)
	{
		M3GMath.postRotateQuat(matrix, qx, qy, qz, qw);
	}

	public void postScale(float sx, float sy, float sz)
	{
		M3GMath.postScale(matrix, sx, sy, sz);
	}

	public void postTranslate(float tx, float ty, float tz)
	{
		M3GMath.postTranslate(matrix, tx, ty, tz);
	}

	public void set(float[] matrix)
	{
		if (matrix == null)
		{
			throw new NullPointerException();
		}
		if (matrix.length < 16)
		{
			throw new IllegalArgumentException();
		}

		System.arraycopy(matrix, 0, this.matrix, 0, 16);
	}

	public void set(Transform transform)
	{
		if (transform == null)
		{
			throw new NullPointerException();
		}

		System.arraycopy(transform.matrix, 0, matrix, 0, 16);
	}

	public void setIdentity()
	{
		M3GMath.setIdentity(matrix);
	}

	public void transform(float[] vectors)
	{
		if (vectors == null)
		{
			throw new NullPointerException();
		}
		if ((vectors.length % 4) != 0)
		{
			throw new IllegalArgumentException();
		}

		float[] input = vectors.clone();
		for (int i = 0; i < vectors.length; i += 4)
		{
			M3GMath.transform(matrix, input, i, vectors, i);
		}
	}

	public void transform(VertexArray in, float[] out, boolean W)
	{
		if (in == null || out == null)
		{
			throw new NullPointerException();
		}
		if (in.getComponentCount() == 4)
		{
			throw new IllegalArgumentException();
		}
		if (out.length < in.getVertexCount() * 4)
		{
			throw new IllegalArgumentException();
		}

		float[] vector = new float[4];
		for (int i = 0; i < in.getVertexCount(); i++)
		{
			vector[0] = in.getComponentAsFloat(i, 0);
			vector[1] = in.getComponentCount() > 1 ? in.getComponentAsFloat(i, 1) : 0f;
			vector[2] = in.getComponentCount() > 2 ? in.getComponentAsFloat(i, 2) : 0f;
			vector[3] = W ? 1f : 0f;
			M3GMath.transform(matrix, vector, 0, out, i * 4);
		}
	}

	public void transpose()
	{
		M3GMath.transpose(matrix);
	}

	float[] getMatrix()
	{
		return matrix;
	}

}
