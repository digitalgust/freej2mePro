package javax.microedition.m3g.base;

public final class M3GMath
{
	private static final float EPSILON = 1.0e-6f;

	private M3GMath()
	{
	}

	public static void setIdentity(float[] matrix)
	{
		for (int i = 0; i < 16; i++)
		{
			matrix[i] = 0f;
		}
		matrix[0] = 1f;
		matrix[5] = 1f;
		matrix[10] = 1f;
		matrix[15] = 1f;
	}

	public static void copy(float[] src, float[] dst)
	{
		System.arraycopy(src, 0, dst, 0, 16);
	}

	public static void multiply(float[] left, float[] right, float[] out)
	{
		float[] result = out;
		if (out == left || out == right)
		{
			result = new float[16];
		}
		for (int row = 0; row < 4; row++)
		{
			for (int col = 0; col < 4; col++)
			{
				float sum = 0f;
				for (int k = 0; k < 4; k++)
				{
					sum += left[row * 4 + k] * right[k * 4 + col];
				}
				result[row * 4 + col] = sum;
			}
		}
		if (result != out)
		{
			copy(result, out);
		}
	}

	public static void transpose(float[] matrix)
	{
		for (int row = 0; row < 4; row++)
		{
			for (int col = row + 1; col < 4; col++)
			{
				int a = row * 4 + col;
				int b = col * 4 + row;
				float temp = matrix[a];
				matrix[a] = matrix[b];
				matrix[b] = temp;
			}
		}
	}

	public static void invert(float[] matrix)
	{
		float[] inv = new float[16];

		inv[0] = matrix[5] * matrix[10] * matrix[15] -
				matrix[5] * matrix[11] * matrix[14] -
				matrix[9] * matrix[6] * matrix[15] +
				matrix[9] * matrix[7] * matrix[14] +
				matrix[13] * matrix[6] * matrix[11] -
				matrix[13] * matrix[7] * matrix[10];

		inv[4] = -matrix[4] * matrix[10] * matrix[15] +
				matrix[4] * matrix[11] * matrix[14] +
				matrix[8] * matrix[6] * matrix[15] -
				matrix[8] * matrix[7] * matrix[14] -
				matrix[12] * matrix[6] * matrix[11] +
				matrix[12] * matrix[7] * matrix[10];

		inv[8] = matrix[4] * matrix[9] * matrix[15] -
				matrix[4] * matrix[11] * matrix[13] -
				matrix[8] * matrix[5] * matrix[15] +
				matrix[8] * matrix[7] * matrix[13] +
				matrix[12] * matrix[5] * matrix[11] -
				matrix[12] * matrix[7] * matrix[9];

		inv[12] = -matrix[4] * matrix[9] * matrix[14] +
				matrix[4] * matrix[10] * matrix[13] +
				matrix[8] * matrix[5] * matrix[14] -
				matrix[8] * matrix[6] * matrix[13] -
				matrix[12] * matrix[5] * matrix[10] +
				matrix[12] * matrix[6] * matrix[9];

		inv[1] = -matrix[1] * matrix[10] * matrix[15] +
				matrix[1] * matrix[11] * matrix[14] +
				matrix[9] * matrix[2] * matrix[15] -
				matrix[9] * matrix[3] * matrix[14] -
				matrix[13] * matrix[2] * matrix[11] +
				matrix[13] * matrix[3] * matrix[10];

		inv[5] = matrix[0] * matrix[10] * matrix[15] -
				matrix[0] * matrix[11] * matrix[14] -
				matrix[8] * matrix[2] * matrix[15] +
				matrix[8] * matrix[3] * matrix[14] +
				matrix[12] * matrix[2] * matrix[11] -
				matrix[12] * matrix[3] * matrix[10];

		inv[9] = -matrix[0] * matrix[9] * matrix[15] +
				matrix[0] * matrix[11] * matrix[13] +
				matrix[8] * matrix[1] * matrix[15] -
				matrix[8] * matrix[3] * matrix[13] -
				matrix[12] * matrix[1] * matrix[11] +
				matrix[12] * matrix[3] * matrix[9];

		inv[13] = matrix[0] * matrix[9] * matrix[14] -
				matrix[0] * matrix[10] * matrix[13] -
				matrix[8] * matrix[1] * matrix[14] +
				matrix[8] * matrix[2] * matrix[13] +
				matrix[12] * matrix[1] * matrix[10] -
				matrix[12] * matrix[2] * matrix[9];

		inv[2] = matrix[1] * matrix[6] * matrix[15] -
				matrix[1] * matrix[7] * matrix[14] -
				matrix[5] * matrix[2] * matrix[15] +
				matrix[5] * matrix[3] * matrix[14] +
				matrix[13] * matrix[2] * matrix[7] -
				matrix[13] * matrix[3] * matrix[6];

		inv[6] = -matrix[0] * matrix[6] * matrix[15] +
				matrix[0] * matrix[7] * matrix[14] +
				matrix[4] * matrix[2] * matrix[15] -
				matrix[4] * matrix[3] * matrix[14] -
				matrix[12] * matrix[2] * matrix[7] +
				matrix[12] * matrix[3] * matrix[6];

		inv[10] = matrix[0] * matrix[5] * matrix[15] -
				matrix[0] * matrix[7] * matrix[13] -
				matrix[4] * matrix[1] * matrix[15] +
				matrix[4] * matrix[3] * matrix[13] +
				matrix[12] * matrix[1] * matrix[7] -
				matrix[12] * matrix[3] * matrix[5];

		inv[14] = -matrix[0] * matrix[5] * matrix[14] +
				matrix[0] * matrix[6] * matrix[13] +
				matrix[4] * matrix[1] * matrix[14] -
				matrix[4] * matrix[2] * matrix[13] -
				matrix[12] * matrix[1] * matrix[6] +
				matrix[12] * matrix[2] * matrix[5];

		inv[3] = -matrix[1] * matrix[6] * matrix[11] +
				matrix[1] * matrix[7] * matrix[10] +
				matrix[5] * matrix[2] * matrix[11] -
				matrix[5] * matrix[3] * matrix[10] -
				matrix[9] * matrix[2] * matrix[7] +
				matrix[9] * matrix[3] * matrix[6];

		inv[7] = matrix[0] * matrix[6] * matrix[11] -
				matrix[0] * matrix[7] * matrix[10] -
				matrix[4] * matrix[2] * matrix[11] +
				matrix[4] * matrix[3] * matrix[10] +
				matrix[8] * matrix[2] * matrix[7] -
				matrix[8] * matrix[3] * matrix[6];

		inv[11] = -matrix[0] * matrix[5] * matrix[11] +
				matrix[0] * matrix[7] * matrix[9] +
				matrix[4] * matrix[1] * matrix[11] -
				matrix[4] * matrix[3] * matrix[9] -
				matrix[8] * matrix[1] * matrix[7] +
				matrix[8] * matrix[3] * matrix[5];

		inv[15] = matrix[0] * matrix[5] * matrix[10] -
				matrix[0] * matrix[6] * matrix[9] -
				matrix[4] * matrix[1] * matrix[10] +
				matrix[4] * matrix[2] * matrix[9] +
				matrix[8] * matrix[1] * matrix[6] -
				matrix[8] * matrix[2] * matrix[5];

		float det = matrix[0] * inv[0] + matrix[1] * inv[4] + matrix[2] * inv[8] + matrix[3] * inv[12];
		if (Math.abs(det) <= EPSILON)
		{
			throw new ArithmeticException();
		}

		det = 1f / det;
		for (int i = 0; i < 16; i++)
		{
			matrix[i] = inv[i] * det;
		}
	}

	public static void postTranslate(float[] matrix, float tx, float ty, float tz)
	{
		float[] translate = new float[16];
		setIdentity(translate);
		translate[3] = tx;
		translate[7] = ty;
		translate[11] = tz;
		multiply(matrix, translate, matrix);
	}

	public static void postScale(float[] matrix, float sx, float sy, float sz)
	{
		float[] scale = new float[16];
		setIdentity(scale);
		scale[0] = sx;
		scale[5] = sy;
		scale[10] = sz;
		multiply(matrix, scale, matrix);
	}

	public static void postRotate(float[] matrix, float angle, float ax, float ay, float az)
	{
		if (Math.abs(angle) <= EPSILON)
		{
			return;
		}

		float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		if (length <= EPSILON)
		{
			throw new IllegalArgumentException();
		}

		float x = ax / length;
		float y = ay / length;
		float z = az / length;
		float radians = (float) Math.toRadians(angle);
		float c = (float) Math.cos(radians);
		float s = (float) Math.sin(radians);
		float oneMinusC = 1f - c;

		float[] rotate = new float[16];
		setIdentity(rotate);
		rotate[0] = x * x * oneMinusC + c;
		rotate[1] = x * y * oneMinusC - z * s;
		rotate[2] = x * z * oneMinusC + y * s;
		rotate[4] = y * x * oneMinusC + z * s;
		rotate[5] = y * y * oneMinusC + c;
		rotate[6] = y * z * oneMinusC - x * s;
		rotate[8] = x * z * oneMinusC - y * s;
		rotate[9] = y * z * oneMinusC + x * s;
		rotate[10] = z * z * oneMinusC + c;
		multiply(matrix, rotate, matrix);
	}

	public static void postRotateQuat(float[] matrix, float qx, float qy, float qz, float qw)
	{
		float length = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
		if (length <= EPSILON)
		{
			throw new IllegalArgumentException();
		}

		float x = qx / length;
		float y = qy / length;
		float z = qz / length;
		float w = qw / length;

		float[] rotate = new float[16];
		setIdentity(rotate);
		rotate[0] = 1f - (2f * y * y + 2f * z * z);
		rotate[1] = 2f * x * y - 2f * z * w;
		rotate[2] = 2f * x * z + 2f * y * w;
		rotate[4] = 2f * x * y + 2f * z * w;
		rotate[5] = 1f - (2f * x * x + 2f * z * z);
		rotate[6] = 2f * y * z - 2f * x * w;
		rotate[8] = 2f * x * z - 2f * y * w;
		rotate[9] = 2f * y * z + 2f * x * w;
		rotate[10] = 1f - (2f * x * x + 2f * y * y);
		multiply(matrix, rotate, matrix);
	}

	public static void transform(float[] matrix, float[] vector, int srcOffset, float[] out, int dstOffset)
	{
		float x = vector[srcOffset];
		float y = vector[srcOffset + 1];
		float z = vector[srcOffset + 2];
		float w = vector[srcOffset + 3];
		out[dstOffset] = matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3] * w;
		out[dstOffset + 1] = matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7] * w;
		out[dstOffset + 2] = matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11] * w;
		out[dstOffset + 3] = matrix[12] * x + matrix[13] * y + matrix[14] * z + matrix[15] * w;
	}

	public static boolean isIdentity(float[] matrix)
	{
		float[] identity = new float[16];
		setIdentity(identity);
		for (int i = 0; i < 16; i++)
		{
			if (Math.abs(matrix[i] - identity[i]) > EPSILON)
			{
				return false;
			}
		}
		return true;
	}
}
