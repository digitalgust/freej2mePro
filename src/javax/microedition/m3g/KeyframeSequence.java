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

public class KeyframeSequence extends Object3D
{

	public static final int CONSTANT = 192;
	public static final int LINEAR = 176;
	public static final int LOOP = 193;
	public static final int SLERP = 177;
	public static final int SPLINE = 178;
	public static final int SQUAD = 179;
	public static final int STEP = 180;


	private final int intType;
	private final int keyframes;
	private final int components;
	private int duration;
	private int repeat;
	private int rangeFirst;
	private int rangeLast;
	private final int[] times;
	private final float[][] values;
	private float[][] inTangents;
	private float[][] outTangents;
	private boolean tangentsDirty = true;


	public KeyframeSequence(int numKeyframes, int numComponents, int interpolation)
	{
		if (numKeyframes < 1 || numComponents < 1)
		{
			throw new IllegalArgumentException();
		}
		if (interpolation < LINEAR || interpolation > STEP)
		{
			throw new IllegalArgumentException();
		}
		if ((interpolation == SLERP || interpolation == SQUAD) && numComponents != 4)
		{
			throw new IllegalArgumentException();
		}

		intType = interpolation;
		keyframes = numKeyframes;
		components = numComponents;
		rangeFirst = 0;
		rangeLast = numKeyframes - 1;
		repeat = CONSTANT;
		times = new int[numKeyframes];
		values = new float[numKeyframes][numComponents];
	}


	public int getComponentCount() { return components; }

	public int getDuration() { return duration; }

	public int getInterpolationType() { return intType; }

	public int getKeyframe(int index, float[] value)
	{
		checkIndex(index);
		if (value != null)
		{
			if (value.length < components)
			{
				throw new IllegalArgumentException();
			}
			System.arraycopy(values[index], 0, value, 0, components);
		}
		return times[index];
	}

	public int getKeyframeCount() { return keyframes; }

	public int getRepeatMode() { return repeat; }

	public int getValidRangeFirst() { return rangeFirst; }

	public int getValidRangeLast() { return rangeLast; }

	public void setDuration(int value)
	{
		if (value <= 0)
		{
			throw new IllegalArgumentException();
		}
		duration = value;
		tangentsDirty = true;
	}

	public void setKeyframe(int index, int time, float[] value)
	{
		checkIndex(index);
		if (value == null)
		{
			throw new NullPointerException();
		}
		if (value.length < components || time < 0)
		{
			throw new IllegalArgumentException();
		}

		System.arraycopy(value, 0, values[index], 0, components);
		if (intType == SLERP || intType == SQUAD)
		{
			normalizeQuaternion(values[index]);
		}
		tangentsDirty = true;
		times[index] = time;
	}

	public void setRepeatMode(int mode)
	{
		if (mode != CONSTANT && mode != LOOP)
		{
			throw new IllegalArgumentException();
		}
		repeat = mode;
		tangentsDirty = true;
	}

	public void setValidRange(int first, int last)
	{
		checkIndex(first);
		checkIndex(last);
		rangeFirst = first;
		rangeLast = last;
		tangentsDirty = true;
	}

	int getSample(float sequenceTime, float[] sample)
	{
		if (sample == null)
		{
			throw new NullPointerException();
		}
		if (sample.length < components)
		{
			throw new IllegalArgumentException();
		}

		if (duration <= 0)
		{
			copyValue(rangeFirst, sample);
			return Integer.MAX_VALUE;
		}

		int first = rangeFirst;
		int last = rangeLast;
		float time = sequenceTime;

		if (repeat == LOOP)
		{
			float loopTime = time % duration;
			if (loopTime < 0f)
			{
				loopTime += duration;
			}
			time = loopTime;
			if (time < times[first])
			{
				time += duration;
			}
		}
		else
		{
			if (time <= times[first])
			{
				copyValue(first, sample);
				return clampValidity(times[first] - time);
			}
			if (time >= times[last])
			{
				copyValue(last, sample);
				return Integer.MAX_VALUE;
			}
		}

		int start = first;
		while (start != last)
		{
			int next = nextIndex(start);
			float nextTime = times[next];
			if (next == first && repeat == LOOP && nextTime <= times[start])
			{
				nextTime += duration;
			}
			if (time < nextTime)
			{
				break;
			}
			start = next;
		}

		int end = nextIndex(start);
		float startTime = times[start];
		float endTime = times[end];
		if (end == first && repeat == LOOP && endTime <= startTime)
		{
			endTime += duration;
		}
		if (time == startTime || intType == STEP || endTime <= startTime)
		{
			copyValue(start, sample);
			if (intType == STEP && endTime > startTime)
			{
				return clampValidity(endTime - time);
			}
			return 1;
		}

		float s = (time - startTime) / (endTime - startTime);
		interpolate(start, end, s, sample);
		return 1;
	}

	private void interpolate(int start, int end, float s, float[] sample)
	{
		switch (intType)
		{
			case SLERP:
				slerp(values[start], values[end], s, sample);
				return;
			case SPLINE:
				interpolateSpline(start, end, s, sample);
				return;
			case SQUAD:
				slerp(values[start], values[end], s, sample);
				return;
			case LINEAR:
			default:
				for (int i = 0; i < components; i++)
				{
					float from = values[start][i];
					sample[i] = from + ((values[end][i] - from) * s);
				}
				return;
		}
	}

	private void interpolateSpline(int start, int end, float s, float[] sample)
	{
		ensureTangents();
		float s2 = s * s;
		float s3 = s2 * s;
		float h00 = (2f * s3) - (3f * s2) + 1f;
		float h01 = (-2f * s3) + (3f * s2);
		float h10 = s3 - (2f * s2) + s;
		float h11 = s3 - s2;
		for (int i = 0; i < components; i++)
		{
			float from = values[start][i];
			float to = values[end][i];
			sample[i] = (h00 * from) + (h01 * to)
					+ (h10 * outTangents[start][i]) + (h11 * inTangents[end][i]);
		}
	}

	private void ensureTangents()
	{
		if (!tangentsDirty)
		{
			return;
		}
		if (inTangents == null)
		{
			inTangents = new float[keyframes][components];
			outTangents = new float[keyframes][components];
		}

		int frame = rangeFirst;
		do
		{
			int previous = previousIndex(frame);
			int next = nextIndex(frame);
			float previousInterval = intervalDuration(previous);
			float nextInterval = intervalDuration(frame);
			float intervalSum = previousInterval + nextInterval;
			float inScale = 0f;
			float outScale = 0f;
			if (intervalSum > 0f && (repeat != CONSTANT || (frame != rangeFirst && frame != rangeLast)))
			{
				inScale = 2f * previousInterval / intervalSum;
				outScale = 2f * nextInterval / intervalSum;
			}

			for (int i = 0; i < components; i++)
			{
				float delta = values[next][i] - values[previous][i];
				inTangents[frame][i] = 0.5f * delta * inScale;
				outTangents[frame][i] = 0.5f * delta * outScale;
			}
			frame = next;
		} while (frame != rangeFirst);
		tangentsDirty = false;
	}

	private float intervalDuration(int frame)
	{
		if (frame == rangeLast)
		{
			return (float) duration - times[rangeLast] + times[rangeFirst];
		}
		return times[nextIndex(frame)] - times[frame];
	}

	private void copyValue(int index, float[] sample)
	{
		System.arraycopy(values[index], 0, sample, 0, components);
	}

	private int nextIndex(int index)
	{
		if (index == rangeLast)
		{
			return rangeFirst;
		}
		return index + 1;
	}

	private int previousIndex(int index)
	{
		if (index == rangeFirst)
		{
			return rangeLast;
		}
		return index - 1;
	}

	private void checkIndex(int index)
	{
		if (index < 0 || index >= keyframes)
		{
			throw new IndexOutOfBoundsException();
		}
	}

	private static int clampValidity(float delta)
	{
		if (delta <= 1f)
		{
			return 1;
		}
		if (delta >= Integer.MAX_VALUE)
		{
			return Integer.MAX_VALUE;
		}
		return (int) delta;
	}

	private static void normalizeQuaternion(float[] quaternion)
	{
		float x = quaternion[0];
		float y = quaternion[1];
		float z = quaternion[2];
		float w = quaternion[3];
		float length = (float) Math.sqrt((x * x) + (y * y) + (z * z) + (w * w));
		if (length == 0f)
		{
			quaternion[0] = 0f;
			quaternion[1] = 0f;
			quaternion[2] = 0f;
			quaternion[3] = 1f;
			return;
		}
		quaternion[0] = x / length;
		quaternion[1] = y / length;
		quaternion[2] = z / length;
		quaternion[3] = w / length;
	}

	private static void slerp(float[] from, float[] to, float s, float[] out)
	{
		float dot = (from[0] * to[0]) + (from[1] * to[1]) + (from[2] * to[2]) + (from[3] * to[3]);
		float tx = to[0];
		float ty = to[1];
		float tz = to[2];
		float tw = to[3];
		if (dot < 0f)
		{
			dot = -dot;
			tx = -tx;
			ty = -ty;
			tz = -tz;
			tw = -tw;
		}

		if (dot > 0.9995f)
		{
			out[0] = from[0] + ((tx - from[0]) * s);
			out[1] = from[1] + ((ty - from[1]) * s);
			out[2] = from[2] + ((tz - from[2]) * s);
			out[3] = from[3] + ((tw - from[3]) * s);
			normalizeQuaternion(out);
			return;
		}

		double theta0 = Math.acos(dot);
		double theta = theta0 * s;
		double sinTheta = Math.sin(theta);
		double sinTheta0 = Math.sin(theta0);
		float scale0 = (float) (Math.cos(theta) - (dot * sinTheta / sinTheta0));
		float scale1 = (float) (sinTheta / sinTheta0);

		out[0] = (from[0] * scale0) + (tx * scale1);
		out[1] = (from[1] * scale0) + (ty * scale1);
		out[2] = (from[2] * scale0) + (tz * scale1);
		out[3] = (from[3] * scale0) + (tw * scale1);
		normalizeQuaternion(out);
	}

}
