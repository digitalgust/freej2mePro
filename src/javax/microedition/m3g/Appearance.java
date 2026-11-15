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

public class Appearance extends Object3D
{
	private static final int MAX_TEXTURE_UNITS = 2;

	private CompositingMode compositingMode;
	private Fog fog;
	private Material material;
	private PolygonMode polygonMode;
	private int layer;
	private Texture2D[] textures = new Texture2D[MAX_TEXTURE_UNITS];


	public Appearance() {  }

	protected Object3D duplicateImpl()
	{
		Appearance copy = (Appearance) super.duplicateImpl();
		copy.textures = textures.clone();
		return copy;
	}


	public CompositingMode getCompositingMode() { return compositingMode; }

	public Fog getFog() { return fog; }

	public int getLayer() { return layer; }

	public Material getMaterial() { return material; }

	public PolygonMode getPolygonMode() { return polygonMode; }

	public Texture2D getTexture(int index)
	{
		checkTextureIndex(index);
		return textures[index];
	}

	public void setCompositingMode(CompositingMode compMode) { compositingMode = compMode; }

	public void setFog(Fog f) { fog = f; }

	public void setLayer(int i) { layer = i; }

	public void setMaterial(Material mat) { material = mat; }

	public void setPolygonMode(PolygonMode mode) { polygonMode = mode; }

	public void setTexture(int index, Texture2D tex)
	{
		checkTextureIndex(index);
		textures[index] = tex;
	}

	private void checkTextureIndex(int index)
	{
		if (index < 0 || index >= MAX_TEXTURE_UNITS)
		{
			throw new IndexOutOfBoundsException();
		}
	}

}
