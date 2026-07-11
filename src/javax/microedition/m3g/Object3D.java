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

import java.util.Vector;

public abstract class Object3D
{

	private int userid;
	private Object userObject;
	private final Vector<AnimationTrack> animationTracks = new Vector<AnimationTrack>();


	public void addAnimationTrack(AnimationTrack animationTrack)
	{
		if (animationTrack == null)
		{
			throw new NullPointerException();
		}

		int property = animationTrack.getTargetProperty();
		int index = 0;
		while (index < animationTracks.size() && animationTracks.elementAt(index).getTargetProperty() <= property)
		{
			index++;
		}
		animationTracks.insertElementAt(animationTrack, index);
	}

	public int animate(int time)
	{
		return animate(time, new Vector<Object3D>());
	}

	public Object3D duplicate() { return this; }

	public Object3D find(int userID)
	{
		return find(userID, new Vector<Object3D>());
	}

	public AnimationTrack getAnimationTrack(int index) { return animationTracks.elementAt(index); }

	public int getAnimationTrackCount() { return animationTracks.size(); }

	public int getReferences(Object3D[] references)
	{
		Vector<Object3D> directReferences = new Vector<Object3D>();
		collectReferences(directReferences);
		if (references != null)
		{
			if (references.length < directReferences.size())
			{
				throw new IllegalArgumentException();
			}
			for (int i = 0; i < directReferences.size(); i++)
			{
				references[i] = directReferences.elementAt(i);
			}
		}
		return directReferences.size();
	}

	public int getUserID() { return userid; }

	public Object getUserObject() { return userObject; }

	public void removeAnimationTrack(AnimationTrack animationTrack)
	{
		animationTracks.removeElement(animationTrack);
	}

	public void setUserID(int userID) { userid = userID; }

	public void setUserObject(java.lang.Object userObject) { this.userObject = userObject; }

	protected void collectReferences(Vector<Object3D> references)
	{
		for (int i = 0; i < animationTracks.size(); i++)
		{
			references.addElement(animationTracks.elementAt(i));
		}

		if (this instanceof World)
		{
			World world = (World) this;
			addReference(references, world.getBackground());
			addReference(references, world.getActiveCamera());
		}
		if (this instanceof Group)
		{
			Group group = (Group) this;
			for (int i = 0; i < group.getChildCount(); i++)
			{
				addReference(references, group.getChild(i));
			}
		}
		if (this instanceof Node)
		{
			Node node = (Node) this;
			addReference(references, node.getAlignmentReference(Node.Y_AXIS));
			addReference(references, node.getAlignmentReference(Node.Z_AXIS));
		}
		if (this instanceof Mesh)
		{
			Mesh mesh = (Mesh) this;
			addReference(references, mesh.getVertexBuffer());
			for (int i = 0; i < mesh.getSubmeshCount(); i++)
			{
				addReference(references, mesh.getIndexBuffer(i));
				addReference(references, mesh.getAppearance(i));
			}
		}
		if (this instanceof MorphingMesh)
		{
			MorphingMesh morphingMesh = (MorphingMesh) this;
			for (int i = 0; i < morphingMesh.getMorphTargetCount(); i++)
			{
				addReference(references, morphingMesh.getMorphTarget(i));
			}
		}
		if (this instanceof SkinnedMesh)
		{
			addReference(references, ((SkinnedMesh) this).getSkeleton());
		}
		if (this instanceof VertexBuffer)
		{
			VertexBuffer vertexBuffer = (VertexBuffer) this;
			addReference(references, vertexBuffer.getPositions(null));
			addReference(references, vertexBuffer.getNormals());
			addReference(references, vertexBuffer.getColors());
			for (int i = 0; i < 2; i++)
			{
				addReference(references, vertexBuffer.getTexCoords(i, null));
			}
		}
		if (this instanceof Appearance)
		{
			Appearance appearance = (Appearance) this;
			addReference(references, appearance.getCompositingMode());
			addReference(references, appearance.getFog());
			addReference(references, appearance.getMaterial());
			addReference(references, appearance.getPolygonMode());
			for (int i = 0; i < 2; i++)
			{
				addReference(references, appearance.getTexture(i));
			}
		}
		if (this instanceof Texture2D)
		{
			addReference(references, ((Texture2D) this).getImage());
		}
		if (this instanceof Background)
		{
			addReference(references, ((Background) this).getImage());
		}
		if (this instanceof Sprite3D)
		{
			Sprite3D sprite = (Sprite3D) this;
			addReference(references, sprite.getImage());
			addReference(references, sprite.getAppearance());
		}
		if (this instanceof AnimationTrack)
		{
			AnimationTrack track = (AnimationTrack) this;
			addReference(references, track.getKeyframeSequence());
			addReference(references, track.getController());
		}
	}

	private int animate(int time, Vector<Object3D> visited)
	{
		if (visited.contains(this))
		{
			return Integer.MAX_VALUE;
		}
		visited.addElement(this);

		int validity = applyAnimationTracks(time);
		int referenceCount = getReferences(null);
		if (referenceCount > 0)
		{
			Object3D[] references = new Object3D[referenceCount];
			getReferences(references);
			for (int i = 0; i < references.length; i++)
			{
				Object3D reference = references[i];
				if (reference != null)
				{
					int refValidity = reference.animate(time, visited);
					if (refValidity < validity)
					{
						validity = refValidity;
					}
				}
			}
		}
		return validity;
	}

	private Object3D find(int userID, Vector<Object3D> visited)
	{
		if (visited.contains(this))
		{
			return null;
		}
		visited.addElement(this);

		if (userid == userID)
		{
			return this;
		}

		int referenceCount = getReferences(null);
		if (referenceCount == 0)
		{
			return null;
		}

		Object3D[] references = new Object3D[referenceCount];
		getReferences(references);
		for (int i = 0; i < references.length; i++)
		{
			Object3D reference = references[i];
			if (reference == null)
			{
				continue;
			}
			Object3D found = reference.find(userID, visited);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private int applyAnimationTracks(int time)
	{
		if (animationTracks.isEmpty())
		{
			return Integer.MAX_VALUE;
		}

		int validity = Integer.MAX_VALUE;
		int index = 0;
		while (index < animationTracks.size())
		{
			AnimationTrack leadTrack = animationTracks.elementAt(index);
			KeyframeSequence leadSequence = leadTrack.getKeyframeSequence();
			int property = leadTrack.getTargetProperty();
			int components = leadSequence.getComponentCount();
			float[] blendedValue = new float[components];
			float totalWeight = 0f;

			while (index < animationTracks.size() && animationTracks.elementAt(index).getTargetProperty() == property)
			{
				AnimationTrack track = animationTracks.elementAt(index);
				AnimationController controller = track.getController();
				if (controller == null)
				{
					index++;
					continue;
				}

				if (!controller.isActive(time))
				{
					int inactiveValidity = controller.timeToActivation(time);
					if (inactiveValidity < validity)
					{
						validity = inactiveValidity;
					}
					index++;
					continue;
				}

				float weight = controller.getWeight();
				if (weight > 0f)
				{
					float[] sample = new float[components];
					int sampleValidity = track.getKeyframeSequence().getSample(controller.getPosition(time), sample);
					if (sampleValidity < validity)
					{
						validity = sampleValidity;
					}
					for (int i = 0; i < components; i++)
					{
						blendedValue[i] += sample[i] * weight;
					}
					totalWeight += weight;
				}
				index++;
			}

			if (totalWeight > 0f)
			{
				for (int i = 0; i < blendedValue.length; i++)
				{
					blendedValue[i] /= totalWeight;
				}
				applyAnimatedValue(property, blendedValue);
			}
		}
		return validity;
	}

	private void applyAnimatedValue(int property, float[] value)
	{
		if (this instanceof Transformable)
		{
			Transformable transformable = (Transformable) this;
			switch (property)
			{
				case AnimationTrack.TRANSLATION:
					transformable.setTranslation(value[0], value[1], value[2]);
					return;
				case AnimationTrack.SCALE:
					if (value.length == 1)
					{
						transformable.setScale(value[0], value[0], value[0]);
					}
					else
					{
						transformable.setScale(value[0], value[1], value[2]);
					}
					return;
				case AnimationTrack.ORIENTATION:
					if (value.length == 4)
					{
						applyQuaternionOrientation(transformable, value);
					}
					return;
				default:
					break;
			}
		}

		if (this instanceof Node)
		{
			Node node = (Node) this;
			switch (property)
			{
				case AnimationTrack.MORPH_WEIGHTS:
					if (node instanceof MorphingMesh)
					{
						((MorphingMesh) node).setWeights(value);
					}
					return;
				case AnimationTrack.ALPHA:
					node.setAlphaFactor(clamp01(value[0]));
					return;
				case AnimationTrack.PICKABILITY:
					node.setPickingEnable(value[0] >= 0.5f);
					return;
				case AnimationTrack.VISIBILITY:
					node.setRenderingEnable(value[0] >= 0.5f);
					return;
				default:
					break;
			}
		}

		if (this instanceof Camera)
		{
			Camera camera = (Camera) this;
			float[] params = new float[4];
			int projectionType;
			try
			{
				projectionType = camera.getProjection(params);
			}
			catch (IllegalStateException ignored)
			{
				return;
			}

			switch (property)
			{
				case AnimationTrack.FIELD_OF_VIEW:
					params[0] = value[0];
					break;
				case AnimationTrack.NEAR_DISTANCE:
					params[2] = value[0];
					break;
				case AnimationTrack.FAR_DISTANCE:
					params[3] = value[0];
					break;
				default:
					projectionType = -1;
					break;
			}

			if (projectionType == Camera.PARALLEL)
			{
				camera.setParallel(params[0], params[1], params[2], params[3]);
			}
			else if (projectionType == Camera.PERSPECTIVE)
			{
				camera.setPerspective(params[0], params[1], params[2], params[3]);
			}
			return;
		}

		if (this instanceof Material)
		{
			Material material = (Material) this;
			switch (property)
			{
				case AnimationTrack.AMBIENT_COLOR:
					material.setColor(Material.AMBIENT, toRgbColor(value));
					return;
				case AnimationTrack.DIFFUSE_COLOR:
					material.setColor(Material.DIFFUSE, toArgbColor(value, (material.getColor(Material.DIFFUSE) >>> 24) / 255f));
					return;
				case AnimationTrack.EMISSIVE_COLOR:
					material.setColor(Material.EMISSIVE, toRgbColor(value));
					return;
				case AnimationTrack.SPECULAR_COLOR:
					material.setColor(Material.SPECULAR, toRgbColor(value));
					return;
				case AnimationTrack.ALPHA:
					material.setColor(Material.DIFFUSE, toArgbColor(extractRgb(material.getColor(Material.DIFFUSE)), value[0]));
					return;
				case AnimationTrack.SHININESS:
					material.setShininess(Math.max(0f, value[0]));
					return;
				default:
					break;
			}
		}

		if (this instanceof Background)
		{
			Background background = (Background) this;
			switch (property)
			{
				case AnimationTrack.COLOR:
					background.setColor(toArgbColor(value, (background.getColor() >>> 24) / 255f));
					return;
				case AnimationTrack.ALPHA:
					background.setColor(toArgbColor(extractRgb(background.getColor()), value[0]));
					return;
				case AnimationTrack.CROP:
					applyCrop(background, value);
					return;
				default:
					break;
			}
		}

		if (this instanceof Sprite3D)
		{
			Sprite3D sprite = (Sprite3D) this;
			switch (property)
			{
				case AnimationTrack.CROP:
					applyCrop(sprite, value);
					return;
				default:
					break;
			}
		}

		if (this instanceof Fog)
		{
			Fog fog = (Fog) this;
			switch (property)
			{
				case AnimationTrack.COLOR:
					fog.setColor(toRgbColor(value));
					return;
				case AnimationTrack.DENSITY:
					fog.setDensity(Math.max(0f, value[0]));
					return;
				case AnimationTrack.NEAR_DISTANCE:
					fog.setLinear(value[0], fog.getFarDistance());
					return;
				case AnimationTrack.FAR_DISTANCE:
					fog.setLinear(fog.getNearDistance(), value[0]);
					return;
				default:
					break;
			}
		}

		if (this instanceof Light)
		{
			Light light = (Light) this;
			switch (property)
			{
				case AnimationTrack.COLOR:
					light.setColor(toRgbColor(value));
					return;
				case AnimationTrack.INTENSITY:
					light.setIntensity(value[0]);
					return;
				case AnimationTrack.SPOT_ANGLE:
					light.setSpotAngle(value[0]);
					return;
				case AnimationTrack.SPOT_EXPONENT:
					light.setSpotExponent(value[0]);
					return;
				default:
					break;
			}
		}
	}

	private static void applyQuaternionOrientation(Transformable transformable, float[] quaternion)
	{
		transformable.setOrientationQuat(quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
	}

	private static void applyCrop(Background background, float[] value)
	{
		if (value.length >= 4)
		{
			background.setCrop(Math.round(value[0]), Math.round(value[1]), Math.round(value[2]), Math.round(value[3]));
		}
		else if (value.length >= 2)
		{
			background.setCrop(background.getCropX(), background.getCropY(), Math.round(value[0]), Math.round(value[1]));
		}
	}

	private static void applyCrop(Sprite3D sprite, float[] value)
	{
		if (value.length >= 4)
		{
			sprite.setCrop(Math.round(value[0]), Math.round(value[1]), Math.round(value[2]), Math.round(value[3]));
		}
		else if (value.length >= 2)
		{
			sprite.setCrop(sprite.getCropX(), sprite.getCropY(), Math.round(value[0]), Math.round(value[1]));
		}
	}

	private static int toRgbColor(float[] value)
	{
		return (clampColor(value[0]) << 16) | (clampColor(value[1]) << 8) | clampColor(value[2]);
	}

	private static int toArgbColor(float[] value, float alpha)
	{
		return (clampColor(alpha) << 24) | (clampColor(value[0]) << 16) | (clampColor(value[1]) << 8) | clampColor(value[2]);
	}

	private static float[] extractRgb(int color)
	{
		return new float[] {
			((color >>> 16) & 0xFF) / 255f,
			((color >>> 8) & 0xFF) / 255f,
			(color & 0xFF) / 255f
		};
	}

	private static float clamp01(float value)
	{
		if (value < 0f)
		{
			return 0f;
		}
		if (value > 1f)
		{
			return 1f;
		}
		return value;
	}

	private static int clampColor(float value)
	{
		return Math.max(0, Math.min(255, Math.round(clamp01(value) * 255f)));
	}

	private static void addReference(Vector<Object3D> references, Object3D reference)
	{
		if (reference != null)
		{
			references.addElement(reference);
		}
	}

}
