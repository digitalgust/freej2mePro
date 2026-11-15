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

public class Camera extends Node
{

	public static final int GENERIC = 48;
	public static final int PARALLEL = 49;
	public static final int PERSPECTIVE = 50;

	private int projectionType = GENERIC;
	private final float[] projectionParams = new float[] { 60f, 1f, 1f, 100f };
	private final Transform genericTransform = new Transform();

	public Camera() {  }


	public int getProjection(float[] params)
	{
		if (params != null)
		{
			if (params.length < 4)
			{
				throw new IllegalArgumentException();
			}
			if (projectionType == GENERIC)
			{
				throw new IllegalStateException();
			}
			System.arraycopy(projectionParams, 0, params, 0, 4);
		}
		return projectionType;
	}

	public int getProjection(Transform transform)
	{
		if (transform != null)
		{
			if (projectionType == GENERIC)
			{
				transform.set(genericTransform);
			}
			else
			{
				if (projectionParams[1] == 0f || projectionParams[2] == projectionParams[3])
				{
					throw new ArithmeticException();
				}
				transform.set(getProjectionTransform(0, 0));
			}
		}
		return projectionType;
	}

	public void setGeneric(Transform transform)
	{
		if (transform == null)
		{
			throw new NullPointerException();
		}
		genericTransform.set(transform);
		projectionType = GENERIC;
	}

	public void setParallel(float fovy, float aspectRatio, float near, float far)
	{
		if (!(fovy > 0f) || !(aspectRatio > 0f))
		{
			throw new IllegalArgumentException();
		}
		projectionType = PARALLEL;
		projectionParams[0] = fovy;
		projectionParams[1] = aspectRatio;
		projectionParams[2] = near;
		projectionParams[3] = far;
	}

	public void setPerspective(float fovy, float aspectRatio, float near, float far)
	{
		if (!(fovy > 0f) || !(fovy < 180f) || !(aspectRatio > 0f) || !(near > 0f) || !(far > 0f))
		{
			throw new IllegalArgumentException();
		}
		projectionType = PERSPECTIVE;
		projectionParams[0] = fovy;
		projectionParams[1] = aspectRatio;
		projectionParams[2] = near;
		projectionParams[3] = far;
	}

	Transform getProjectionTransform(int viewportWidth, int viewportHeight)
	{
		Transform transform = new Transform();
		if (projectionType == GENERIC)
		{
			transform.set(genericTransform);
			return transform;
		}

		float aspectRatio = projectionParams[1];
		if (aspectRatio == 0f && viewportHeight > 0)
		{
			aspectRatio = (float) viewportWidth / (float) viewportHeight;
		}
		float near = projectionParams[2];
		float far = projectionParams[3];
		float[] matrix = new float[16];
		M3GMath.setIdentity(matrix);

		if (projectionType == PERSPECTIVE)
		{
			float f = 1f / (float) Math.tan(Math.toRadians(projectionParams[0]) * 0.5);
			matrix[0] = f / aspectRatio;
			matrix[5] = f;
			matrix[10] = (far + near) / (near - far);
			matrix[11] = (2f * far * near) / (near - far);
			matrix[14] = -1f;
			matrix[15] = 0f;
		}
		else
		{
			float height = projectionParams[0];
			float width = height * aspectRatio;
			matrix[0] = 2f / width;
			matrix[5] = 2f / height;
			matrix[10] = 2f / (near - far);
			matrix[11] = (far + near) / (near - far);
		}

		transform.set(matrix);
		return transform;
	}

}
