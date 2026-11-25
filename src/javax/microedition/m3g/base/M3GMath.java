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
		if (out == left && out == right)
		{
			// The uncommon A = A * A case needs all inputs to remain intact.
			float[] result = new float[16];
			multiply(left, right, result);
			copy(result, out);
			return;
		}

		if (out == left)
		{
			// Each output row only reads the matching input row from left, so
			// calculate the complete row before overwriting it. This keeps the
			// common Transform.postMultiply() path alias-safe without allocating.
			for (int row = 0; row < 4; row++)
			{
				int offset = row * 4;
				float l0 = left[offset];
				float l1 = left[offset + 1];
				float l2 = left[offset + 2];
				float l3 = left[offset + 3];
				float r0 = l0 * right[0] + l1 * right[4] + l2 * right[8] + l3 * right[12];
				float r1 = l0 * right[1] + l1 * right[5] + l2 * right[9] + l3 * right[13];
				float r2 = l0 * right[2] + l1 * right[6] + l2 * right[10] + l3 * right[14];
				float r3 = l0 * right[3] + l1 * right[7] + l2 * right[11] + l3 * right[15];
				out[offset] = r0;
				out[offset + 1] = r1;
				out[offset + 2] = r2;
				out[offset + 3] = r3;
			}
			return;
		}

		if (out == right)
		{
			// For an aliased right operand, complete one output column before
			// overwriting that column. Later columns do not read it.
			for (int col = 0; col < 4; col++)
			{
				float r0 = right[col];
				float r1 = right[4 + col];
				float r2 = right[8 + col];
				float r3 = right[12 + col];
				float c0 = left[0] * r0 + left[1] * r1 + left[2] * r2 + left[3] * r3;
				float c1 = left[4] * r0 + left[5] * r1 + left[6] * r2 + left[7] * r3;
				float c2 = left[8] * r0 + left[9] * r1 + left[10] * r2 + left[11] * r3;
				float c3 = left[12] * r0 + left[13] * r1 + left[14] * r2 + left[15] * r3;
				out[col] = c0;
				out[4 + col] = c1;
				out[8 + col] = c2;
				out[12 + col] = c3;
			}
			return;
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
				out[row * 4 + col] = sum;
			}
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
		invert(matrix, new float[16]);
	}

	public static void invert(float[] matrix, float[] inv)
	{
		if (inv == matrix)
		{
			inv = new float[16];
		}

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
		if (tx == 0f && ty == 0f && tz == 0f)
		{
			return;
		}
		for (int row = 0; row < 4; row++)
		{
			int offset = row * 4;
			matrix[offset + 3] += matrix[offset] * tx + matrix[offset + 1] * ty + matrix[offset + 2] * tz;
		}
	}

	public static void postScale(float[] matrix, float sx, float sy, float sz)
	{
		if (sx != 1f)
		{
			for (int i = 0; i < 16; i += 4)
			{
				matrix[i] *= sx;
			}
		}
		if (sy != 1f)
		{
			for (int i = 1; i < 16; i += 4)
			{
				matrix[i] *= sy;
			}
		}
		if (sz != 1f)
		{
			for (int i = 2; i < 16; i += 4)
			{
				matrix[i] *= sz;
			}
		}
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
