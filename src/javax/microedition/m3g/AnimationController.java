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


public class AnimationController extends Object3D
{

	private float weight = 1f;
	private float speed = 1f;
	private int world;
	private float sequence;
	private int intStart;
	private int intEnd;


	public AnimationController() {  }


	public int getActiveIntervalEnd() { return intEnd; }

	public int getActiveIntervalStart()  { return intStart; }

	public float getPosition(int worldTime)
	{
		return sequence + (speed * ((float) (worldTime - world)));
	}

	public int getRefWorldTime()  { return world; }

	public float getSpeed()  { return speed; }

	public float getWeight()  { return weight; }

	public void setActiveInterval(int start, int end)
	{
		if (start > end)
		{
			throw new IllegalArgumentException();
		}
		intStart = start;
		intEnd = end;
	}

	public void setPosition(float sequenceTime, int worldTime)
	{
		sequence = sequenceTime;
		world = worldTime;
	}

	public void setSpeed(float value, int worldTime)
	{
		sequence = getPosition(worldTime);
		speed = value;
		world = worldTime;
	}

	public void setWeight(float value)
	{
		if (value < 0f)
		{
			throw new IllegalArgumentException();
		}
		weight = value;
	}

	boolean isActive(int worldTime)
	{
		return intStart == intEnd || (worldTime >= intStart && worldTime < intEnd);
	}

	int timeToActivation(int worldTime)
	{
		if (worldTime < intStart)
		{
			return (int) Math.min(Integer.MAX_VALUE, (long) intStart - worldTime);
		}
		if (worldTime >= intEnd)
		{
			return Integer.MAX_VALUE;
		}
		return 1;
	}

}
