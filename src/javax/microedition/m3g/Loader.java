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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.microedition.lcdui.Image;

import org.recompile.mobile.Mobile;

public class Loader
{
	private static final byte[] M3G_FILE_IDENTIFIER = new byte[] {
			(byte) 0xAB, 0x4A, 0x53, 0x52, 0x31, 0x38, 0x34, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final byte[] PNG_FILE_IDENTIFIER = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final byte[] JPEG_FILE_IDENTIFIER = new byte[] {
			(byte) 0xFF, (byte) 0xD8
	};

	private static final int INVALID_HEADER_TYPE = -1;
	private static final int M3G_TYPE = 0;
	private static final int PNG_TYPE = 1;
	private static final int JPEG_TYPE = 2;

	private static final int PNG_IHDR = 0x49484452;
	private static final int PNG_tRNS = 0x74524E53;
	private static final int PNG_IDAT = 0x49444154;

	private Loader()
	{
	}

	public static Object3D[] load(byte[] data, int offset) throws IOException
	{
		if (data == null)
		{
			throw new NullPointerException();
		}
		if (offset < 0 || offset >= data.length)
		{
			throw new IndexOutOfBoundsException();
		}

		int type = getIdentifierType(data, offset);
		switch (type)
		{
			case PNG_TYPE:
			case JPEG_TYPE:
				return new Object3D[] { new Image2D(resolveImageFormat(type, data, offset), decodeImage(data, offset)) };
			case M3G_TYPE:
				return new M3GBinaryLoader(null, false, new Vector()).load(data, offset);
			default:
				throw new IOException("File not recognized.");
		}
	}

	public static Object3D[] load(java.lang.String name) throws IOException
	{
		if (name == null)
		{
			throw new NullPointerException();
		}
		byte[] bytes = readResourceBytes(name, null);
		int type = getIdentifierType(bytes, 0);
		switch (type)
		{
			case PNG_TYPE:
			case JPEG_TYPE:
				return new Object3D[] { new Image2D(resolveImageFormat(type, bytes, 0), decodeImage(bytes, 0)) };
			case M3G_TYPE:
				return new M3GBinaryLoader(name, true, new Vector()).load(bytes, 0);
			default:
				throw new IOException("File not recognized.");
		}
	}

	private static byte[] readResourceBytes(String name) throws IOException
	{
		return readResourceBytes(name, null);
	}

	private static byte[] readResourceBytes(String name, String parentResourceName) throws IOException
	{
		String resolvedName = resolveResourceName(name, parentResourceName);
		InputStream stream = openResourceStream(resolvedName);
		if (stream == null)
		{
			throw new IOException("Resource not found: " + resolvedName);
		}

		try
		{
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			int read;
			while ((read = stream.read(chunk)) != -1)
			{
				buffer.write(chunk, 0, read);
			}
			byte[] bytes = buffer.toByteArray();
			if (bytes.length == 0)
			{
				throw new IOException("Resource is empty: " + resolvedName);
			}
			return bytes;
		}
		finally
		{
			try
			{
				stream.close();
			}
			catch (IOException ignored)
			{
			}
		}
	}

	private static InputStream openResourceStream(String name) throws IOException
	{
		try
		{
			InputStream stream = Mobile.getMIDletResourceAsStream(name);
			if (stream != null)
			{
				return stream;
			}
		}
		catch (Throwable ignored)
		{
		}

		String normalized = name.startsWith("/") ? name : "/" + name;
		InputStream classpathStream = Loader.class.getResourceAsStream(normalized);
		if (classpathStream != null)
		{
			return classpathStream;
		}

		if (hasUriScheme(name))
		{
			return new URL(name).openStream();
		}

		return new FileInputStream(name);
	}

	private static String resolveResourceName(String name, String parentResourceName) throws IOException
	{
		if (name == null)
		{
			throw new NullPointerException();
		}
		if (hasUriScheme(name) || isAbsoluteFilePath(name) || name.startsWith("/"))
		{
			return name;
		}
		if (parentResourceName == null)
		{
			return name;
		}
		if (hasUriScheme(parentResourceName))
		{
			try
			{
				return new URL(new URL(parentResourceName), name).toString();
			}
			catch (Exception ex)
			{
				throw new IOException("Invalid external reference URI: " + name);
			}
		}

		int separator = Math.max(parentResourceName.lastIndexOf('/'), parentResourceName.lastIndexOf('\\'));
		if (separator >= 0)
		{
			return parentResourceName.substring(0, separator + 1) + name;
		}
		return name;
	}

	private static boolean isAbsoluteFilePath(String name)
	{
		if (name == null || name.length() == 0)
		{
			return false;
		}
		if (name.startsWith("\\\\"))
		{
			return true;
		}
		return name.length() > 2
				&& Character.isLetter(name.charAt(0))
				&& name.charAt(1) == ':'
				&& (name.charAt(2) == '\\' || name.charAt(2) == '/');
	}

	private static boolean hasUriScheme(String name)
	{
		int colon = name.indexOf(':');
		if (colon <= 1)
		{
			return false;
		}
		for (int i = 0; i < colon; i++)
		{
			char ch = name.charAt(i);
			if (!(Character.isLetterOrDigit(ch) || ch == '+' || ch == '-' || ch == '.'))
			{
				return false;
			}
		}
		return true;
	}

	private static Image decodeImage(byte[] data, int offset) throws IOException
	{
		try
		{
			return Image.createImage(data, offset, data.length - offset);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Image decode failed: " + ex.toString());
		}
	}

	private static int resolveImageFormat(int type, byte[] data, int offset) throws IOException
	{
		switch (type)
		{
			case PNG_TYPE:
				return parsePngFormat(data, offset);
			case JPEG_TYPE:
				return parseJpegFormat(data, offset);
			default:
				throw new IOException("Unsupported image type.");
		}
	}

	private static int parsePngFormat(byte[] data, int offset) throws IOException
	{
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(data, offset + PNG_FILE_IDENTIFIER.length, data.length - offset - PNG_FILE_IDENTIFIER.length));
		int format = Image2D.RGB;
		try
		{
			while (true)
			{
				int length = input.readInt();
				int type = input.readInt();
				if (length < 0)
				{
					throw new IOException("Invalid PNG chunk length.");
				}
				if (type == PNG_IHDR)
				{
					if (length < 10)
					{
						throw new IOException("Invalid PNG header.");
					}
					skipFully(input, 9);
					int colorType = input.readUnsignedByte();
					switch (colorType)
					{
						case 0:
							format = Image2D.LUMINANCE;
							break;
						case 2:
						case 3:
							format = Image2D.RGB;
							break;
						case 4:
							format = Image2D.LUMINANCE_ALPHA;
							break;
						case 6:
							format = Image2D.RGBA;
							break;
						default:
							throw new IOException("Unsupported PNG color type: " + colorType);
					}
					skipFully(input, length - 10);
				}
				else
				{
					if (type == PNG_tRNS)
					{
						if (format == Image2D.LUMINANCE)
						{
							format = Image2D.LUMINANCE_ALPHA;
						}
						else if (format == Image2D.RGB)
						{
							format = Image2D.RGBA;
						}
					}
					if (type == PNG_IDAT)
					{
						break;
					}
					skipFully(input, length);
				}
				skipFully(input, 4);
			}
			return format;
		}
		catch (IOException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new IOException("Invalid PNG data.");
		}
		finally
		{
			try
			{
				input.close();
			}
			catch (IOException ignored)
			{
			}
		}
	}

	private static int parseJpegFormat(byte[] data, int offset) throws IOException
	{
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(data, offset + JPEG_FILE_IDENTIFIER.length, data.length - offset - JPEG_FILE_IDENTIFIER.length));
		try
		{
			while (true)
			{
				int markerPrefix;
				do
				{
					markerPrefix = input.readUnsignedByte();
				}
				while (markerPrefix != 0xFF);

				int marker;
				do
				{
					marker = input.readUnsignedByte();
				}
				while (marker == 0xFF);

				if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9))
				{
					continue;
				}
				if (isStartOfFrame(marker))
				{
					skipFully(input, 7);
					int components = input.readUnsignedByte();
					if (components == 1)
					{
						return Image2D.LUMINANCE;
					}
					if (components == 3)
					{
						return Image2D.RGB;
					}
					throw new IOException("Unsupported JPEG component count: " + components);
				}

				int length = input.readUnsignedShort();
				if (length < 2)
				{
					throw new IOException("Invalid JPEG segment length.");
				}
				skipFully(input, length - 2);
			}
		}
		catch (IOException ex)
		{
			throw ex;
		}
		catch (Exception ex)
		{
			throw new IOException("Invalid JPEG data.");
		}
		finally
		{
			try
			{
				input.close();
			}
			catch (IOException ignored)
			{
			}
		}
	}

	private static boolean isStartOfFrame(int marker)
	{
		switch (marker)
		{
			case 0xC0:
			case 0xC1:
			case 0xC2:
			case 0xC3:
			case 0xC5:
			case 0xC6:
			case 0xC7:
			case 0xC9:
			case 0xCA:
			case 0xCB:
			case 0xCD:
			case 0xCE:
			case 0xCF:
				return true;
			default:
				return false;
		}
	}

	private static void skipFully(DataInputStream input, int count) throws IOException
	{
		while (count > 0)
		{
			int skipped = input.skipBytes(count);
			if (skipped <= 0)
			{
				throw new IOException("Unexpected end of stream.");
			}
			count -= skipped;
		}
	}

	private static final class M3GBinaryLoader
	{
		private final String resourceName;
		private final boolean allowRelativeExternalReferences;
		private final Vector fileHistory;
		private Vector currentObjectTable;

		private M3GBinaryLoader(String resourceName, boolean allowRelativeExternalReferences, Vector fileHistory)
		{
			this.resourceName = resourceName;
			this.allowRelativeExternalReferences = allowRelativeExternalReferences;
			this.fileHistory = fileHistory;
		}

		private Object3D[] load(byte[] data, int offset) throws IOException
		{
			if (resourceName != null)
			{
				if (isInFileHistory(resourceName))
				{
					throw new IOException("External reference loop detected: " + resourceName);
				}
				fileHistory.addElement(resourceName);
			}

			try
			{
				return doLoad(data, offset);
			}
			finally
			{
				if (resourceName != null)
				{
					fileHistory.removeElement(resourceName);
				}
			}
		}

		private Object3D[] doLoad(byte[] data, int offset) throws IOException
		{
			if (!parseIdentifier(data, offset, M3G_FILE_IDENTIFIER))
			{
				throw new IOException("File not recognized.");
			}

			M3GByteReader file = new M3GByteReader(data, offset + M3G_FILE_IDENTIFIER.length, data.length);
			Vector objectTable = new Vector();
			objectTable.addElement(null);

			M3GHeader header = null;
			boolean externalSectionConsumed = false;
			int nextObjectIndex = 1;
			int sectionCount = 0;
			int expectedFileEnd = -1;

			while (file.hasRemaining())
			{
				M3GSection section = readSection(file, data);
				sectionCount++;
				if (sectionCount == 1)
				{
					header = parseHeaderSection(section);
					objectTable.addElement(null);
					nextObjectIndex = 2;
					expectedFileEnd = offset + header.totalFileSize;
					if (expectedFileEnd > data.length)
					{
						throw new IOException("Truncated M3G file.");
					}
				}
				else if (header != null && header.hasExternalReferences && !externalSectionConsumed)
				{
					nextObjectIndex = parseExternalReferenceSection(section, objectTable, nextObjectIndex);
					externalSectionConsumed = true;
				}
				else
				{
					nextObjectIndex = parseObjectSection(section, objectTable, nextObjectIndex);
				}

				if (expectedFileEnd != -1 && file.getPosition() >= expectedFileEnd)
				{
					break;
				}
			}

			if (header == null)
			{
				throw new IOException("Missing M3G header section.");
			}
			if (sectionCount < 2)
			{
				throw new IOException("M3G file must contain at least one non-header section.");
			}
			if (expectedFileEnd != file.getPosition())
			{
				throw new IOException("Invalid M3G file length.");
			}
			if (header.hasExternalReferences && !externalSectionConsumed)
			{
				throw new IOException("Missing external reference section.");
			}
			Object3D[] roots = collectRootObjects(objectTable);
			if (roots.length == 0)
			{
				throw new IOException("M3G file parsed, but no root-level objects were found.");
			}
			return roots;
		}

		private int parseExternalReferenceSection(M3GSection section, Vector objectTable, int nextObjectIndex) throws IOException
		{
			M3GByteReader objects = new M3GByteReader(section.objects, 0, section.objects.length);
			int sectionObjectCount = 0;
			while (objects.hasRemaining())
			{
				int objectType = objects.readByte();
				int length = objects.readUInt32();
				byte[] objectData = objects.readBytes(length);
				if (objectType != 0xFF)
				{
					throw new IOException("External reference section contains " + getObjectTypeName(objectType) + ".");
				}
				String uri = new M3GByteReader(objectData, 0, objectData.length).readString();
				objectTable.addElement(loadExternalReference(uri));
				sectionObjectCount++;
				nextObjectIndex++;
			}
			if (sectionObjectCount == 0)
			{
				throw new IOException("External reference section is empty.");
			}
			return nextObjectIndex;
		}

		private int parseObjectSection(M3GSection section, Vector objectTable, int nextObjectIndex) throws IOException
		{
			M3GByteReader objects = new M3GByteReader(section.objects, 0, section.objects.length);
			while (objects.hasRemaining())
			{
				int objectType = objects.readByte();
				int length = objects.readUInt32();
				byte[] objectData = objects.readBytes(length);
				if (objectType == 0 || objectType == 0xFF)
				{
					throw new IOException("Invalid object type " + objectType + " outside its dedicated section.");
				}
				if (objectType >= 23 && objectType <= 254)
				{
					throw new IOException("Reserved M3G object type: " + objectType);
				}
				objectTable.addElement(decodeObject(nextObjectIndex, objectType, objectData, objectTable));
				nextObjectIndex++;
			}
			return nextObjectIndex;
		}

		private Object3D decodeObject(int objectIndex, int objectType, byte[] objectData, Vector objectTable) throws IOException
		{
			currentObjectTable = objectTable;
			M3GByteReader reader = new M3GByteReader(objectData, 0, objectData.length);
			Object3D object;
			switch (objectType)
			{
				case 1:
					object = decodeAnimationController(reader);
					break;
				case 2:
					object = decodeAnimationTrack(reader, objectTable);
					break;
				case 3:
					object = decodeAppearance(reader, objectTable);
					break;
				case 4:
					object = decodeBackground(reader, objectTable);
					break;
				case 5:
					object = decodeCamera(reader, objectTable);
					break;
				case 6:
					object = decodeCompositingMode(reader);
					break;
				case 7:
					object = decodeFog(reader);
					break;
				case 8:
					object = decodePolygonMode(reader);
					break;
				case 9:
					object = decodeGroup(reader, objectTable);
					break;
				case 10:
					object = decodeImage2D(reader);
					break;
				case 11:
					object = decodeTriangleStripArray(reader, objectTable);
					break;
				case 12:
					object = decodeLight(reader, objectTable);
					break;
				case 13:
					object = decodeMaterial(reader);
					break;
				case 14:
					object = decodeMesh(reader, objectTable);
					break;
				case 15:
					object = decodeMorphingMesh(reader, objectTable);
					break;
				case 16:
					object = decodeSkinnedMesh(reader, objectTable);
					break;
				case 17:
					object = decodeTexture2D(reader, objectTable);
					break;
				case 18:
					object = decodeSprite3D(reader, objectTable);
					break;
				case 19:
					object = decodeKeyframeSequence(reader);
					break;
				case 20:
					object = decodeVertexArray(reader);
					break;
				case 21:
					object = decodeVertexBuffer(reader, objectTable);
					break;
				case 22:
					object = decodeWorld(reader, objectTable);
					break;
				default:
					throw new IOException("Unsupported M3G object: " + getObjectTypeName(objectType)
							+ " (type " + objectType + ", index " + objectIndex + ").");
			}
			if (reader.hasRemaining())
			{
				throw new IOException(getObjectTypeName(objectType) + " object contains trailing bytes.");
			}
			return object;
		}

		private AnimationController decodeAnimationController(M3GByteReader reader) throws IOException
		{
			AnimationController controller = new AnimationController();
			readObject3D(reader, controller, null);
			float speed = reader.readFloat32();
			float weight = reader.readFloat32();
			int activeIntervalStart = reader.readInt32();
			int activeIntervalEnd = reader.readInt32();
			float referenceSequenceTime = reader.readFloat32();
			int referenceWorldTime = reader.readInt32();
			controller.setWeight(weight);
			controller.setActiveInterval(activeIntervalStart, activeIntervalEnd);
			controller.setPosition(referenceSequenceTime, referenceWorldTime);
			controller.setSpeed(speed, referenceWorldTime);
			return controller;
		}

		private AnimationTrack decodeAnimationTrack(M3GByteReader reader, Vector objectTable) throws IOException
		{
			AnimationTrackState state = new AnimationTrackState();
			readObject3D(reader, null, state);
			KeyframeSequence sequence = (KeyframeSequence) resolveObjectReference(objectTable, reader.readObjectIndex(), KeyframeSequence.class, "animation track keyframe sequence");
			AnimationController controller = (AnimationController) resolveObjectReference(objectTable, reader.readObjectIndex(), AnimationController.class, "animation track controller");
			int property = reader.readUInt32();
			AnimationTrack track = new AnimationTrack(sequence, property);
			if (controller != null)
			{
				track.setController(controller);
			}
			applyDeferredObject3DState(track, state);
			return track;
		}

		private Appearance decodeAppearance(M3GByteReader reader, Vector objectTable) throws IOException
		{
			Appearance appearance = new Appearance();
			readObject3D(reader, appearance, null);
			appearance.setLayer(reader.readByteSigned());
			appearance.setCompositingMode((CompositingMode) resolveObjectReference(objectTable, reader.readObjectIndex(), CompositingMode.class, "appearance compositing mode"));
			appearance.setFog((Fog) resolveObjectReference(objectTable, reader.readObjectIndex(), Fog.class, "appearance fog"));
			appearance.setPolygonMode((PolygonMode) resolveObjectReference(objectTable, reader.readObjectIndex(), PolygonMode.class, "appearance polygon mode"));
			appearance.setMaterial((Material) resolveObjectReference(objectTable, reader.readObjectIndex(), Material.class, "appearance material"));
			int textureCount = readCount(reader, "appearance texture count");
			if (textureCount > 2)
			{
				throw new IOException("Unsupported appearance texture unit count: " + textureCount);
			}
			for (int i = 0; i < textureCount; i++)
			{
				appearance.setTexture(i, (Texture2D) resolveObjectReference(objectTable, reader.readObjectIndex(), Texture2D.class, "appearance texture"));
			}
			return appearance;
		}

		private Background decodeBackground(M3GByteReader reader, Vector objectTable) throws IOException
		{
			Background background = new Background();
			readObject3D(reader, background, null);
			background.setColor(readColorRGBA(reader));
			Image2D image = (Image2D) resolveObjectReference(objectTable, reader.readObjectIndex(), Image2D.class, "background image");
			background.setImage(image);
			int modeX = reader.readByte();
			int modeY = reader.readByte();
			background.setImageMode(modeX, modeY);
			int cropX = reader.readInt32();
			int cropY = reader.readInt32();
			int cropWidth = reader.readInt32();
			int cropHeight = reader.readInt32();
			background.setCrop(cropX, cropY, cropWidth, cropHeight);
			background.setDepthClearEnable(reader.readBoolean());
			background.setColorClearEnable(reader.readBoolean());
			return background;
		}

		private Camera decodeCamera(M3GByteReader reader, Vector objectTable) throws IOException
		{
			Camera camera = new Camera();
			readNode(reader, camera, objectTable);
			int projectionType = reader.readByte();
			if (projectionType == Camera.GENERIC)
			{
				Transform transform = new Transform();
				transform.set(reader.readMatrix());
				camera.setGeneric(transform);
			}
			else if (projectionType == Camera.PARALLEL)
			{
				camera.setParallel(reader.readFloat32(), reader.readFloat32(), reader.readFloat32(), reader.readFloat32());
			}
			else if (projectionType == Camera.PERSPECTIVE)
			{
				camera.setPerspective(reader.readFloat32(), reader.readFloat32(), reader.readFloat32(), reader.readFloat32());
			}
			else
			{
				throw new IOException("Invalid camera projection type: " + projectionType);
			}
			return camera;
		}

		private CompositingMode decodeCompositingMode(M3GByteReader reader) throws IOException
		{
			CompositingMode mode = new CompositingMode();
			readObject3D(reader, mode, null);
			mode.setDepthTestEnable(reader.readBoolean());
			mode.setDepthWriteEnable(reader.readBoolean());
			mode.setColorWriteEnable(reader.readBoolean());
			mode.setAlphaWriteEnable(reader.readBoolean());
			mode.setBlending(reader.readByte());
			mode.setAlphaThreshold(reader.readByte() / 255f);
			mode.setDepthOffset(reader.readFloat32(), reader.readFloat32());
			return mode;
		}

		private Fog decodeFog(M3GByteReader reader) throws IOException
		{
			Fog fog = new Fog();
			readObject3D(reader, fog, null);
			fog.setColor(readColorRGB(reader));
			int mode = reader.readByte();
			fog.setMode(mode);
			if (mode == Fog.EXPONENTIAL)
			{
				fog.setDensity(reader.readFloat32());
			}
			else if (mode == Fog.LINEAR)
			{
				fog.setLinear(reader.readFloat32(), reader.readFloat32());
			}
			else
			{
				throw new IOException("Invalid fog mode: " + mode);
			}
			return fog;
		}

		private PolygonMode decodePolygonMode(M3GByteReader reader) throws IOException
		{
			PolygonMode mode = new PolygonMode();
			readObject3D(reader, mode, null);
			mode.setCulling(reader.readByte());
			mode.setShading(reader.readByte());
			mode.setWinding(reader.readByte());
			mode.setTwoSidedLightingEnable(reader.readBoolean());
			mode.setLocalCameraLightingEnable(reader.readBoolean());
			mode.setPerspectiveCorrectionEnable(reader.readBoolean());
			return mode;
		}

		private Group decodeGroup(M3GByteReader reader, Vector objectTable) throws IOException
		{
			Group group = new Group();
			readNode(reader, group, objectTable);
			int childCount = readCount(reader, "group child count");
			for (int i = 0; i < childCount; i++)
			{
				group.addChild((Node) requireObjectReference(objectTable, reader.readObjectIndex(), Node.class, "group child"));
			}
			return group;
		}

		private Image2D decodeImage2D(M3GByteReader reader) throws IOException
		{
			Image2DState state = new Image2DState();
			readObject3D(reader, null, state);
			int format = reader.readByte();
			boolean mutable = reader.readBoolean();
			int width = reader.readUInt32();
			int height = reader.readUInt32();
			Image2D image;
			if (mutable)
			{
				image = new Image2D(format, width, height);
			}
			else
			{
				byte[] palette = reader.readByteArray();
				byte[] pixels = reader.readByteArray();
				int requiredLength = palette.length == 0
						? width * height * getBytesPerPixel(format)
						: width * height;
				if (pixels.length < requiredLength)
				{
					throw new IOException("Immutable Image2D pixel data is truncated.");
				}
				if (palette.length == 0)
				{
					image = new Image2D(format, width, height, pixels);
				}
				else
				{
					image = new Image2D(format, width, height, pixels, palette);
				}
			}
			applyDeferredObject3DState(image, state);
			return image;
		}

		private TriangleStripArray decodeTriangleStripArray(M3GByteReader reader, Vector objectTable) throws IOException
		{
			IndexBufferState state = new IndexBufferState();
			readObject3D(reader, null, state);
			int encoding = reader.readByte();
			TriangleStripArray triangles;
			if (encoding == 0)
			{
				triangles = new TriangleStripArray(reader.readUInt32(), reader.readUInt32Array());
			}
			else if (encoding == 1)
			{
				triangles = new TriangleStripArray(reader.readByte(), reader.readUInt32Array());
			}
			else if (encoding == 2)
			{
				triangles = new TriangleStripArray(reader.readUInt16(), reader.readUInt32Array());
			}
			else if (encoding == 128)
			{
				triangles = new TriangleStripArray(reader.readUInt32Array(), reader.readUInt32Array());
			}
			else if (encoding == 129)
			{
				triangles = new TriangleStripArray(readUnsignedByteArrayAsInts(reader), reader.readUInt32Array());
			}
			else if (encoding == 130)
			{
				triangles = new TriangleStripArray(readUInt16Array(reader), reader.readUInt32Array());
			}
			else
			{
				throw new IOException("Invalid TriangleStripArray encoding: " + encoding);
			}
			applyDeferredObject3DState(triangles, state);
			return triangles;
		}

		private Light decodeLight(M3GByteReader reader, Vector objectTable) throws IOException
		{
			Light light = new Light();
			readNode(reader, light, objectTable);
			light.setAttenuation(reader.readFloat32(), reader.readFloat32(), reader.readFloat32());
			light.setColor(readColorRGB(reader));
			light.setMode(reader.readByte());
			light.setIntensity(reader.readFloat32());
			light.setSpotAngle(reader.readFloat32());
			light.setSpotExponent(reader.readFloat32());
			return light;
		}

		private Material decodeMaterial(M3GByteReader reader) throws IOException
		{
			Material material = new Material();
			readObject3D(reader, material, null);
			material.setColor(Material.AMBIENT, readColorRGB(reader));
			material.setColor(Material.DIFFUSE, readColorRGBA(reader));
			material.setColor(Material.EMISSIVE, readColorRGB(reader));
			material.setColor(Material.SPECULAR, readColorRGB(reader));
			material.setShininess(reader.readFloat32());
			material.setVertexColorTrackingEnable(reader.readBoolean());
			return material;
		}

		private Mesh decodeMesh(M3GByteReader reader, Vector objectTable) throws IOException
		{
			MeshState state = readMeshState(reader, objectTable);
			Mesh mesh = new Mesh(state.vertexBuffer, state.indexBuffers, state.appearances);
			applyDeferredNodeState(mesh, state.nodeState, objectTable);
			return mesh;
		}

		private MorphingMesh decodeMorphingMesh(M3GByteReader reader, Vector objectTable) throws IOException
		{
			MeshState state = readMeshState(reader, objectTable);
			int morphTargetCount = readCount(reader, "morph target count");
			VertexBuffer[] morphTargets = new VertexBuffer[morphTargetCount];
			float[] initialWeights = new float[morphTargetCount];
			for (int i = 0; i < morphTargetCount; i++)
			{
				morphTargets[i] = (VertexBuffer) requireObjectReference(objectTable, reader.readObjectIndex(), VertexBuffer.class, "morph target");
				initialWeights[i] = reader.readFloat32();
			}
			MorphingMesh mesh = new MorphingMesh(state.vertexBuffer, morphTargets, state.indexBuffers, state.appearances);
			mesh.setWeights(initialWeights);
			applyDeferredNodeState(mesh, state.nodeState, objectTable);
			return mesh;
		}

		private SkinnedMesh decodeSkinnedMesh(M3GByteReader reader, Vector objectTable) throws IOException
		{
			MeshState state = readMeshState(reader, objectTable);
			Group skeleton = (Group) requireObjectReference(objectTable, reader.readObjectIndex(), Group.class, "skinned mesh skeleton");
			SkinnedMesh mesh = new SkinnedMesh(state.vertexBuffer, state.indexBuffers, state.appearances, skeleton);
			applyDeferredNodeState(mesh, state.nodeState, objectTable);
			int transformReferenceCount = readCount(reader, "skinned mesh transform reference count");
			for (int i = 0; i < transformReferenceCount; i++)
			{
				Node bone = (Node) requireObjectReference(objectTable, reader.readObjectIndex(), Node.class, "skinned mesh bone");
				int firstVertex = reader.readUInt32();
				int vertexCount = reader.readUInt32();
				int weight = reader.readInt32();
				mesh.addTransform(bone, weight, firstVertex, vertexCount);
			}
			return mesh;
		}

		private Texture2D decodeTexture2D(M3GByteReader reader, Vector objectTable) throws IOException
		{
			TransformableState state = new TransformableState();
			readTransformable(reader, null, objectTable, state);
			Image2D image = (Image2D) requireObjectReference(objectTable, reader.readObjectIndex(), Image2D.class, "texture image");
			Texture2D texture = new Texture2D(image);
			applyDeferredTransformableState(texture, state);
			texture.setBlendColor(readColorRGB(reader));
			texture.setBlending(reader.readByte());
			texture.setWrapping(reader.readByte(), reader.readByte());
			texture.setFiltering(reader.readByte(), reader.readByte());
			return texture;
		}

		private Sprite3D decodeSprite3D(M3GByteReader reader, Vector objectTable) throws IOException
		{
			NodeState state = new NodeState();
			readNode(reader, null, objectTable, state);
			Image2D image = (Image2D) resolveObjectReference(objectTable, reader.readObjectIndex(), Image2D.class, "sprite image");
			Appearance appearance = (Appearance) resolveObjectReference(objectTable, reader.readObjectIndex(), Appearance.class, "sprite appearance");
			boolean scaled = reader.readBoolean();
			Sprite3D sprite = new Sprite3D(scaled, image, appearance);
			int cropX = reader.readInt32();
			int cropY = reader.readInt32();
			int cropWidth = reader.readInt32();
			int cropHeight = reader.readInt32();
			sprite.setCrop(cropX, cropY, cropWidth, cropHeight);
			applyDeferredNodeState(sprite, state, objectTable);
			return sprite;
		}

		private KeyframeSequence decodeKeyframeSequence(M3GByteReader reader) throws IOException
		{
			KeyframeSequenceState state = new KeyframeSequenceState();
			readObject3D(reader, null, state);
			int interpolation = reader.readByte();
			int repeatMode = reader.readByte();
			int encoding = reader.readByte();
			int duration = reader.readUInt32();
			int validRangeFirst = reader.readUInt32();
			int validRangeLast = reader.readUInt32();
			int componentCount = readCount(reader, "keyframe component count");
			int keyframeCount = readCount(reader, "keyframe count");
			KeyframeSequence sequence = new KeyframeSequence(keyframeCount, componentCount, interpolation);
			sequence.setDuration(duration);
			sequence.setValidRange(validRangeFirst, validRangeLast);
			sequence.setRepeatMode(repeatMode);
			if (encoding == 0)
			{
				for (int i = 0; i < keyframeCount; i++)
				{
					float[] value = new float[componentCount];
					int time = reader.readUInt32();
					for (int j = 0; j < componentCount; j++)
					{
						value[j] = reader.readFloat32();
					}
					sequence.setKeyframe(i, time, value);
				}
			}
			else if (encoding == 1 || encoding == 2)
			{
				float[] bias = new float[componentCount];
				float[] scale = new float[componentCount];
				for (int i = 0; i < componentCount; i++)
				{
					bias[i] = reader.readFloat32();
				}
				for (int i = 0; i < componentCount; i++)
				{
					scale[i] = reader.readFloat32();
				}
				for (int i = 0; i < keyframeCount; i++)
				{
					int time = reader.readUInt32();
					float[] value = new float[componentCount];
					for (int j = 0; j < componentCount; j++)
					{
						int quantized = encoding == 1 ? reader.readByte() : reader.readUInt16();
						float normalized = encoding == 1 ? (quantized / 255f) : (quantized / 65535f);
						value[j] = bias[j] + (scale[j] * normalized);
					}
					sequence.setKeyframe(i, time, value);
				}
			}
			else
			{
				throw new IOException("Invalid KeyframeSequence encoding: " + encoding);
			}
			applyDeferredObject3DState(sequence, state);
			return sequence;
		}

		private VertexArray decodeVertexArray(M3GByteReader reader) throws IOException
		{
			VertexArrayState state = new VertexArrayState();
			readObject3D(reader, null, state);
			int componentSize = reader.readByte();
			int componentCount = reader.readByte();
			int encoding = reader.readByte();
			int vertexCount = reader.readUInt16();
			VertexArray array = new VertexArray(vertexCount, componentCount, componentSize);
			if (componentSize == 1)
			{
				byte[] data = new byte[vertexCount * componentCount];
				if (encoding == 0)
				{
					for (int i = 0; i < data.length; i++)
					{
						data[i] = (byte) reader.readByte();
					}
				}
				else if (encoding == 1)
				{
					byte[] accumulator = new byte[componentCount];
					for (int vertex = 0; vertex < vertexCount; vertex++)
					{
						for (int component = 0; component < componentCount; component++)
						{
							accumulator[component] = (byte) (accumulator[component] + (byte) reader.readByte());
							data[(vertex * componentCount) + component] = accumulator[component];
						}
					}
				}
				else
				{
					throw new IOException("Invalid VertexArray encoding: " + encoding);
				}
				array.set(0, vertexCount, data);
			}
			else if (componentSize == 2)
			{
				short[] data = new short[vertexCount * componentCount];
				if (encoding == 0)
				{
					for (int i = 0; i < data.length; i++)
					{
						data[i] = reader.readInt16();
					}
				}
				else if (encoding == 1)
				{
					short[] accumulator = new short[componentCount];
					for (int vertex = 0; vertex < vertexCount; vertex++)
					{
						for (int component = 0; component < componentCount; component++)
						{
							accumulator[component] = (short) (accumulator[component] + reader.readInt16());
							data[(vertex * componentCount) + component] = accumulator[component];
						}
					}
				}
				else
				{
					throw new IOException("Invalid VertexArray encoding: " + encoding);
				}
				array.set(0, vertexCount, data);
			}
			else
			{
				throw new IOException("Invalid VertexArray component size: " + componentSize);
			}
			applyDeferredObject3DState(array, state);
			return array;
		}

		private VertexBuffer decodeVertexBuffer(M3GByteReader reader, Vector objectTable) throws IOException
		{
			VertexBuffer buffer = new VertexBuffer();
			readObject3D(reader, buffer, null);
			buffer.setDefaultColor(readColorRGBA(reader));
			VertexArray positions = (VertexArray) resolveObjectReference(objectTable, reader.readObjectIndex(), VertexArray.class, "vertex buffer positions");
			float[] positionBias = reader.readVector3D();
			float positionScale = reader.readFloat32();
			buffer.setPositions(positions, positionScale, positionBias);
			buffer.setNormals((VertexArray) resolveObjectReference(objectTable, reader.readObjectIndex(), VertexArray.class, "vertex buffer normals"));
			VertexArray colors = (VertexArray) resolveObjectReference(objectTable, reader.readObjectIndex(), VertexArray.class, "vertex buffer colors");
			buffer.setColors(colors);
			int texcoordArrayCount = readCount(reader, "vertex buffer texcoord array count");
			if (texcoordArrayCount > 2)
			{
				throw new IOException("Unsupported vertex buffer texcoord array count: " + texcoordArrayCount);
			}
			for (int i = 0; i < texcoordArrayCount; i++)
			{
				VertexArray texCoords = (VertexArray) resolveObjectReference(objectTable, reader.readObjectIndex(), VertexArray.class, "vertex buffer texcoords");
				float[] texCoordBias = reader.readVector3D();
				float texCoordScale = reader.readFloat32();
				buffer.setTexCoords(i, texCoords, texCoordScale, texCoordBias);
			}
			return buffer;
		}

		private World decodeWorld(M3GByteReader reader, Vector objectTable) throws IOException
		{
			WorldState state = new WorldState();
			readNode(reader, null, objectTable, state.groupState.nodeState);
			int childCount = readCount(reader, "world child count");
			state.groupState.children = new Node[childCount];
			for (int i = 0; i < childCount; i++)
			{
				state.groupState.children[i] = (Node) requireObjectReference(objectTable, reader.readObjectIndex(), Node.class, "world child");
			}
			World world = new World();
			applyDeferredNodeState(world, state.groupState.nodeState);
			for (int i = 0; i < state.groupState.children.length; i++)
			{
				world.addChild(state.groupState.children[i]);
			}
			world.setActiveCamera((Camera) resolveObjectReference(objectTable, reader.readObjectIndex(), Camera.class, "world active camera"));
			world.setBackground((Background) resolveObjectReference(objectTable, reader.readObjectIndex(), Background.class, "world background"));
			return world;
		}

		private MeshState readMeshState(M3GByteReader reader, Vector objectTable) throws IOException
		{
			MeshState state = new MeshState();
			readNode(reader, null, objectTable, state.nodeState);
			state.vertexBuffer = (VertexBuffer) requireObjectReference(objectTable, reader.readObjectIndex(), VertexBuffer.class, "mesh vertex buffer");
			int submeshCount = readCount(reader, "mesh submesh count");
			if (submeshCount == 0)
			{
				throw new IOException("Mesh must contain at least one submesh.");
			}
			state.indexBuffers = new IndexBuffer[submeshCount];
			state.appearances = new Appearance[submeshCount];
			for (int i = 0; i < submeshCount; i++)
			{
				state.indexBuffers[i] = (IndexBuffer) requireObjectReference(objectTable, reader.readObjectIndex(), IndexBuffer.class, "mesh index buffer");
				state.appearances[i] = (Appearance) resolveObjectReference(objectTable, reader.readObjectIndex(), Appearance.class, "mesh appearance");
			}
			return state;
		}

		private void readObject3D(M3GByteReader reader, Object3D target, Object3DState state) throws IOException
		{
			Object3DState effectiveState = state != null ? state : new Object3DState();
			effectiveState.userID = reader.readUInt32();
			int animationTrackCount = readCount(reader, "animation track count");
			effectiveState.animationTracks = new AnimationTrack[animationTrackCount];
			for (int i = 0; i < animationTrackCount; i++)
			{
				effectiveState.animationTracks[i] = (AnimationTrack) requireObjectReference(currentObjectTable, reader.readObjectIndex(), AnimationTrack.class, "animation track");
			}
			int userParameterCount = readCount(reader, "user parameter count");
			if (userParameterCount > 0)
			{
				Hashtable parameters = new Hashtable();
				for (int i = 0; i < userParameterCount; i++)
				{
					Integer parameterId = Integer.valueOf(reader.readUInt32());
					if (parameters.containsKey(parameterId))
					{
						throw new IOException("Duplicate Object3D user parameter ID: " + parameterId);
					}
					parameters.put(parameterId, reader.readByteArray());
				}
				effectiveState.userObject = parameters;
			}
			else
			{
				effectiveState.userObject = null;
			}
			if (target != null)
			{
				applyDeferredObject3DState(target, effectiveState);
			}
		}

		private void readTransformable(M3GByteReader reader, Transformable target, Vector objectTable) throws IOException
		{
			readTransformable(reader, target, objectTable, null);
		}

		private void readTransformable(M3GByteReader reader, Transformable target, Vector objectTable, TransformableState state) throws IOException
		{
			TransformableState effectiveState = state != null ? state : new TransformableState();
			readObject3D(reader, target, effectiveState.objectState);
			boolean hasComponentTransform = reader.readBoolean();
			if (hasComponentTransform)
			{
				effectiveState.translation = reader.readVector3D();
				effectiveState.scale = reader.readVector3D();
				effectiveState.orientationAngle = reader.readFloat32();
				effectiveState.orientationAxis = reader.readVector3D();
			}
			boolean hasGeneralTransform = reader.readBoolean();
			if (hasGeneralTransform)
			{
				effectiveState.generalTransform = reader.readMatrix();
			}
			if (target != null)
			{
				applyDeferredTransformableState(target, effectiveState);
			}
		}

		private void readNode(M3GByteReader reader, Node target, Vector objectTable) throws IOException
		{
			readNode(reader, target, objectTable, null);
		}

		private void readNode(M3GByteReader reader, Node target, Vector objectTable, NodeState state) throws IOException
		{
			NodeState effectiveState = state != null ? state : new NodeState();
			readTransformable(reader, target, objectTable, effectiveState.transformableState);
			effectiveState.renderingEnabled = reader.readBoolean();
			effectiveState.pickingEnabled = reader.readBoolean();
			effectiveState.alphaFactor = reader.readByte() / 255f;
			effectiveState.scope = reader.readUInt32();
			if (reader.readBoolean())
			{
				effectiveState.hasAlignment = true;
				effectiveState.zTarget = reader.readByte();
				effectiveState.yTarget = reader.readByte();
				effectiveState.zReferenceIndex = reader.readObjectIndex();
				effectiveState.yReferenceIndex = reader.readObjectIndex();
			}
			if (target != null)
			{
				applyDeferredNodeState(target, effectiveState, objectTable);
			}
		}

		private void applyDeferredObject3DState(Object3D object, Object3DState state)
		{
			object.setUserID(state.userID);
			object.setUserObject(state.userObject);
			for (int i = 0; i < state.animationTracks.length; i++)
			{
				object.addAnimationTrack(state.animationTracks[i]);
			}
		}

		private void applyDeferredTransformableState(Transformable target, TransformableState state)
		{
			applyDeferredObject3DState(target, state.objectState);
			if (state.translation != null)
			{
				target.setTranslation(state.translation[0], state.translation[1], state.translation[2]);
				target.setOrientation(state.orientationAngle, state.orientationAxis[0], state.orientationAxis[1], state.orientationAxis[2]);
				target.setScale(state.scale[0], state.scale[1], state.scale[2]);
			}
			if (state.generalTransform != null)
			{
				Transform general = new Transform();
				general.set(state.generalTransform);
				target.setTransform(general);
			}
		}

		private void applyDeferredNodeState(Node target, NodeState state) throws IOException
		{
			applyDeferredNodeState(target, state, currentObjectTable);
		}

		private void applyDeferredNodeState(Node target, NodeState state, Vector objectTable) throws IOException
		{
			applyDeferredTransformableState(target, state.transformableState);
			target.setRenderingEnable(state.renderingEnabled);
			target.setPickingEnable(state.pickingEnabled);
			target.setAlphaFactor(state.alphaFactor);
			target.setScope(state.scope);
			if (state.hasAlignment)
			{
				Node zReference = objectTable == null ? null : (Node) resolveObjectReference(objectTable, state.zReferenceIndex, Node.class, "node z alignment reference");
				Node yReference = objectTable == null ? null : (Node) resolveObjectReference(objectTable, state.yReferenceIndex, Node.class, "node y alignment reference");
				target.setAlignment(zReference, state.zTarget, yReference, state.yTarget);
			}
		}

		private Object3D[] collectRootObjects(Vector objectTable)
		{
			Vector referenced = new Vector();
			for (int i = 2; i < objectTable.size(); i++)
			{
				Object3D object = (Object3D) objectTable.elementAt(i);
				if (object == null)
				{
					continue;
				}
				int referenceCount = object.getReferences(null);
				if (referenceCount == 0)
				{
					continue;
				}
				Object3D[] references = new Object3D[referenceCount];
				object.getReferences(references);
				for (int j = 0; j < references.length; j++)
				{
					if (references[j] != null && !referenced.contains(references[j]))
					{
						referenced.addElement(references[j]);
					}
				}
			}

			Vector roots = new Vector();
			for (int i = 2; i < objectTable.size(); i++)
			{
				Object3D object = (Object3D) objectTable.elementAt(i);
				if (object != null && !referenced.contains(object))
				{
					roots.addElement(object);
				}
			}
			Object3D[] result = new Object3D[roots.size()];
			roots.copyInto(result);
			return result;
		}

		private Object resolveObjectReference(Vector objectTable, int index, Class expectedType, String description) throws IOException
		{
			if (index == 0)
			{
				return null;
			}
			if (index <= 0 || index >= objectTable.size())
			{
				throw new IOException("Invalid object reference for " + description + ": " + index);
			}
			Object object = objectTable.elementAt(index);
			if (object == null)
			{
				throw new IOException("Unresolved object reference for " + description + ": " + index);
			}
			if (expectedType != null && !expectedType.isInstance(object))
			{
				throw new IOException("Invalid object type for " + description + ": expected "
						+ expectedType.getName() + " but found " + object.getClass().getName() + ".");
			}
			return object;
		}

		private Object requireObjectReference(Vector objectTable, int index, Class expectedType, String description) throws IOException
		{
			Object object = resolveObjectReference(objectTable, index, expectedType, description);
			if (object == null)
			{
				throw new IOException("Missing object reference for " + description + ".");
			}
			return object;
		}

		private int readCount(M3GByteReader reader, String description) throws IOException
		{
			int count = reader.readUInt32();
			if (count < 0)
			{
				throw new IOException("Unsupported " + description + ": " + count);
			}
			return count;
		}

		private int readColorRGB(M3GByteReader reader) throws IOException
		{
			return (reader.readByte() << 16) | (reader.readByte() << 8) | reader.readByte();
		}

		private int readColorRGBA(M3GByteReader reader) throws IOException
		{
			int r = reader.readByte();
			int g = reader.readByte();
			int b = reader.readByte();
			int a = reader.readByte();
			return (a << 24) | (r << 16) | (g << 8) | b;
		}

		private int[] readUnsignedByteArrayAsInts(M3GByteReader reader) throws IOException
		{
			byte[] values = reader.readByteArray();
			int[] result = new int[values.length];
			for (int i = 0; i < values.length; i++)
			{
				result[i] = values[i] & 0xFF;
			}
			return result;
		}

		private int[] readUInt16Array(M3GByteReader reader) throws IOException
		{
			int count = readCount(reader, "UInt16 array count");
			int[] result = new int[count];
			for (int i = 0; i < count; i++)
			{
				result[i] = reader.readUInt16();
			}
			return result;
		}

		private int getBytesPerPixel(int format) throws IOException
		{
			switch (format)
			{
				case Image2D.ALPHA:
				case Image2D.LUMINANCE:
					return 1;
				case Image2D.LUMINANCE_ALPHA:
					return 2;
				case Image2D.RGB:
					return 3;
				case Image2D.RGBA:
					return 4;
				default:
					throw new IOException("Unsupported Image2D format: " + format);
			}
		}

		private Object3D loadExternalReference(String uri) throws IOException
		{
			if (!allowRelativeExternalReferences && !hasUriScheme(uri) && !isAbsoluteFilePath(uri) && !uri.startsWith("/"))
			{
				throw new IOException("Relative external references are not allowed when loading from a byte array: " + uri);
			}

			String resolvedUri = resolveResourceName(uri, resourceName);
			byte[] externalData = readResourceBytes(resolvedUri, null);
			int type = getIdentifierType(externalData, 0);
			Object3D[] roots;
			switch (type)
			{
				case M3G_TYPE:
					roots = new M3GBinaryLoader(resolvedUri, true, fileHistory).load(externalData, 0);
					break;
				case PNG_TYPE:
					roots = new Object3D[] { new Image2D(resolveImageFormat(type, externalData, 0), decodeImage(externalData, 0)) };
					break;
				default:
					throw new IOException("Unsupported external reference type: " + uri);
			}
			if (roots.length != 1)
			{
				throw new IOException("External M3G reference must resolve to a single root object: " + uri);
			}
			return roots[0];
		}

		private M3GHeader parseHeaderSection(M3GSection section) throws IOException
		{
			if (section.compressionScheme != 0)
			{
				throw new IOException("The first M3G section must be uncompressed.");
			}

			M3GByteReader objects = new M3GByteReader(section.objects, 0, section.objects.length);
			int objectType = objects.readByte();
			int length = objects.readUInt32();
			byte[] objectData = objects.readBytes(length);
			if (objects.hasRemaining())
			{
				throw new IOException("Header section must contain exactly one object.");
			}
			if (objectType != 0)
			{
				throw new IOException("First M3G object must be the header.");
			}

			M3GByteReader headerData = new M3GByteReader(objectData, 0, objectData.length);
			int majorVersion = headerData.readByte();
			int minorVersion = headerData.readByte();
			boolean hasExternalReferences = headerData.readBoolean();
			int totalFileSize = headerData.readUInt32();
			int approximateContentSize = headerData.readUInt32();
			String authoringField = headerData.readString();
			if (headerData.hasRemaining())
			{
				throw new IOException("Header object contains trailing bytes.");
			}
			if (majorVersion != 1 || minorVersion != 0)
			{
				throw new IOException("Unsupported M3G version: " + majorVersion + "." + minorVersion);
			}
			if (totalFileSize <= M3G_FILE_IDENTIFIER.length)
			{
				throw new IOException("Invalid M3G total file size.");
			}
			return new M3GHeader(hasExternalReferences, totalFileSize, approximateContentSize, authoringField);
		}

		private M3GSection readSection(M3GByteReader file, byte[] sourceData) throws IOException
		{
			int sectionStart = file.getPosition();
			int compressionScheme = file.readByte();
			int totalSectionLength = file.readUInt32();
			int uncompressedLength = file.readUInt32();
			if (totalSectionLength < 13)
			{
				throw new IOException("Invalid M3G section length: " + totalSectionLength);
			}

			int objectBytesLength = totalSectionLength - 13;
			byte[] objectBytes = file.readBytes(objectBytesLength);
			int checksum = file.readUInt32();
			int actualChecksum = computeAdler32(sourceData, sectionStart, totalSectionLength - 4);
			if (checksum != actualChecksum)
			{
				throw new IOException("Invalid M3G section checksum.");
			}

			byte[] objects;
			if (compressionScheme == 0)
			{
				if (uncompressedLength != objectBytes.length)
				{
					throw new IOException("Section length mismatch.");
				}
				objects = objectBytes;
			}
			else if (compressionScheme == 1)
			{
				objects = inflateSection(objectBytes, uncompressedLength);
			}
			else
			{
				throw new IOException("Unsupported M3G compression scheme: " + compressionScheme);
			}
			return new M3GSection(compressionScheme, objects);
		}

		private boolean isInFileHistory(String name)
		{
			for (int i = 0; i < fileHistory.size(); i++)
			{
				if (name.equals(fileHistory.elementAt(i)))
				{
					return true;
				}
			}
			return false;
		}
	}

	private static final class M3GSection
	{
		private final int compressionScheme;
		private final byte[] objects;

		private M3GSection(int compressionScheme, byte[] objects)
		{
			this.compressionScheme = compressionScheme;
			this.objects = objects;
		}
	}

	private static final class M3GHeader
	{
		private final boolean hasExternalReferences;
		private final int totalFileSize;
		private final int approximateContentSize;
		private final String authoringField;

		private M3GHeader(boolean hasExternalReferences, int totalFileSize, int approximateContentSize, String authoringField)
		{
			this.hasExternalReferences = hasExternalReferences;
			this.totalFileSize = totalFileSize;
			this.approximateContentSize = approximateContentSize;
			this.authoringField = authoringField;
		}
	}

	private static class Object3DState
	{
		private int userID;
		private AnimationTrack[] animationTracks = new AnimationTrack[0];
		private Object userObject;
	}

	private static final class TransformableState
	{
		private final Object3DState objectState = new Object3DState();
		private float[] translation;
		private float[] scale;
		private float orientationAngle;
		private float[] orientationAxis;
		private float[] generalTransform;
	}

	private static final class NodeState
	{
		private final TransformableState transformableState = new TransformableState();
		private boolean renderingEnabled = true;
		private boolean pickingEnabled = true;
		private float alphaFactor = 1f;
		private int scope = -1;
		private boolean hasAlignment;
		private int zTarget = Node.NONE;
		private int yTarget = Node.NONE;
		private int zReferenceIndex;
		private int yReferenceIndex;
	}

	private static final class MeshState
	{
		private final NodeState nodeState = new NodeState();
		private VertexBuffer vertexBuffer;
		private IndexBuffer[] indexBuffers;
		private Appearance[] appearances;
	}

	private static final class GroupState
	{
		private final NodeState nodeState = new NodeState();
		private Node[] children = new Node[0];
	}

	private static final class WorldState
	{
		private final GroupState groupState = new GroupState();
	}

	private static final class AnimationTrackState extends Object3DState
	{
	}

	private static final class Image2DState extends Object3DState
	{
	}

	private static final class IndexBufferState extends Object3DState
	{
	}

	private static final class KeyframeSequenceState extends Object3DState
	{
	}

	private static final class VertexArrayState extends Object3DState
	{
	}

	private static final class M3GByteReader
	{
		private final byte[] data;
		private final int end;
		private int position;

		private M3GByteReader(byte[] data, int offset, int end)
		{
			this.data = data;
			this.position = offset;
			this.end = end;
		}

		private int getPosition()
		{
			return position;
		}

		private boolean hasRemaining()
		{
			return position < end;
		}

		private int readByte() throws IOException
		{
			ensureAvailable(1);
			return data[position++] & 0xFF;
		}

		private boolean readBoolean() throws IOException
		{
			int value = readByte();
			if (value == 0)
			{
				return false;
			}
			if (value == 1)
			{
				return true;
			}
			throw new IOException("Malformed boolean.");
		}

		private int readUInt32() throws IOException
		{
			ensureAvailable(4);
			int value = (data[position] & 0xFF)
					| ((data[position + 1] & 0xFF) << 8)
					| ((data[position + 2] & 0xFF) << 16)
					| ((data[position + 3] & 0xFF) << 24);
			position += 4;
			return value;
		}

		private int readInt32() throws IOException
		{
			return readUInt32();
		}

		private int readUInt16() throws IOException
		{
			ensureAvailable(2);
			int value = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
			position += 2;
			return value;
		}

		private short readInt16() throws IOException
		{
			return (short) readUInt16();
		}

		private int readObjectIndex() throws IOException
		{
			return readUInt32();
		}

		private int readByteSigned() throws IOException
		{
			return (byte) readByte();
		}

		private float readFloat32() throws IOException
		{
			int bits = readInt32();
			float value = Float.intBitsToFloat(bits);
			if (Float.isNaN(value) || Float.isInfinite(value) || bits == 0x80000000)
			{
				throw new IOException("Invalid Float32 value.");
			}
			return value;
		}

		private byte[] readBytes(int length) throws IOException
		{
			if (length < 0)
			{
				throw new IOException("Negative length.");
			}
			ensureAvailable(length);
			byte[] bytes = new byte[length];
			System.arraycopy(data, position, bytes, 0, length);
			position += length;
			return bytes;
		}

		private byte[] readByteArray() throws IOException
		{
			int count = readUInt32();
			if (count < 0)
			{
				throw new IOException("Unsupported array length.");
			}
			return readBytes(count);
		}

		private int[] readUInt32Array() throws IOException
		{
			int count = readUInt32();
			if (count < 0)
			{
				throw new IOException("Unsupported array length.");
			}
			int[] result = new int[count];
			for (int i = 0; i < count; i++)
			{
				result[i] = readUInt32();
			}
			return result;
		}

		private float[] readVector3D() throws IOException
		{
			return new float[] { readFloat32(), readFloat32(), readFloat32() };
		}

		private float[] readMatrix() throws IOException
		{
			float[] matrix = new float[16];
			for (int i = 0; i < matrix.length; i++)
			{
				matrix[i] = readFloat32();
			}
			return matrix;
		}

		private String readString() throws IOException
		{
			StringBuffer result = new StringBuffer();
			while (true)
			{
				int c = readByte();
				if (c == 0)
				{
					return result.toString();
				}
				if ((c & 0x80) == 0)
				{
					result.append((char) c);
				}
				else if ((c & 0xE0) == 0xC0)
				{
					int c2 = readByte();
					if ((c2 & 0xC0) != 0x80)
					{
						throw new IOException("Invalid UTF-8 string.");
					}
					result.append((char) (((c & 0x1F) << 6) | (c2 & 0x3F)));
				}
				else if ((c & 0xF0) == 0xE0)
				{
					int c2 = readByte();
					int c3 = readByte();
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80)
					{
						throw new IOException("Invalid UTF-8 string.");
					}
					result.append((char) (((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F)));
				}
				else
				{
					throw new IOException("Invalid UTF-8 string.");
				}
			}
		}

		private void ensureAvailable(int count) throws IOException
		{
			if (position + count > end)
			{
				throw new IOException("Unexpected end of M3G data.");
			}
		}
	}

	private static byte[] inflateSection(byte[] compressed, int expectedLength) throws IOException
	{
		if (expectedLength < 0)
		{
			throw new IOException("Negative uncompressed section length.");
		}
		try
		{
			return inflateSectionWithInflater(compressed, expectedLength);
		}
		catch (NoClassDefFoundError ex)
		{
			return inflateSectionWithMiniJvmZip(compressed, expectedLength);
		}
	}

	private static byte[] inflateSectionWithInflater(byte[] compressed, int expectedLength) throws IOException
	{
		Inflater inflater = new Inflater();
		try
		{
			inflater.setInput(compressed);
			byte[] output = new byte[expectedLength];
			int written = 0;
			while (written < expectedLength)
			{
				int count = inflater.inflate(output, written, expectedLength - written);
				if (count <= 0)
				{
					break;
				}
				written += count;
			}
			if (!inflater.finished() || written != expectedLength)
			{
				throw new IOException("Compressed M3G section length mismatch.");
			}
			return output;
		}
		catch (NoClassDefFoundError ex)
		{
			throw ex;
		}
		catch (DataFormatException ex)
		{
			throw new IOException("Invalid compressed M3G section.");
		}
		finally
		{
			inflater.end();
		}
	}

	private static byte[] inflateSectionWithMiniJvmZip(byte[] compressed, int expectedLength) throws IOException
	{
		try
		{
			Class zipClass = Class.forName("org.mini.zip.Zip");
			Method method = zipClass.getMethod("zlibExtract", new Class[] { byte[].class, Integer.TYPE });
			byte[] result = (byte[]) method.invoke(null, new Object[] { compressed, Integer.valueOf(expectedLength) });
			if (result == null || result.length != expectedLength)
			{
				throw new IOException("Compressed M3G section length mismatch.");
			}
			return result;
		}
		catch (IOException ex)
		{
			throw ex;
		}
		catch (Throwable ex)
		{
			throw new IOException("Compressed M3G sections require java.util.zip.Inflater or org.mini.zip.Zip.zlibExtract support.");
		}
	}

	private static int computeAdler32(byte[] data, int offset, int length)
	{
		int a = 1;
		int b = 0;
		for (int i = 0; i < length; i++)
		{
			a = (a + (data[offset + i] & 0xFF)) % 65521;
			b = (b + a) % 65521;
		}
		return (b << 16) | a;
	}

	private static String getObjectTypeName(int objectType)
	{
		switch (objectType)
		{
			case 0: return "Header";
			case 1: return "AnimationController";
			case 2: return "AnimationTrack";
			case 3: return "Appearance";
			case 4: return "Background";
			case 5: return "Camera";
			case 6: return "CompositingMode";
			case 7: return "Fog";
			case 8: return "PolygonMode";
			case 9: return "Group";
			case 10: return "Image2D";
			case 11: return "TriangleStripArray";
			case 12: return "Light";
			case 13: return "Material";
			case 14: return "Mesh";
			case 15: return "MorphingMesh";
			case 16: return "SkinnedMesh";
			case 17: return "Texture2D";
			case 18: return "Sprite3D";
			case 19: return "KeyframeSequence";
			case 20: return "VertexArray";
			case 21: return "VertexBuffer";
			case 22: return "World";
			case 255: return "ExternalReference";
			default: return "Unknown";
		}
	}

	private static int getIdentifierType(byte[] data, int offset)
	{
		if (parseIdentifier(data, offset, JPEG_FILE_IDENTIFIER))
		{
			return JPEG_TYPE;
		}
		if (parseIdentifier(data, offset, PNG_FILE_IDENTIFIER))
		{
			return PNG_TYPE;
		}
		if (parseIdentifier(data, offset, M3G_FILE_IDENTIFIER))
		{
			return M3G_TYPE;
		}
		return INVALID_HEADER_TYPE;
	}

	private static boolean parseIdentifier(byte[] data, int offset, byte[] identifier)
	{
		if (data.length - offset < identifier.length)
		{
			return false;
		}
		for (int i = 0; i < identifier.length; i++)
		{
			if (data[offset + i] != identifier[i])
			{
				return false;
			}
		}
		return true;
	}
}
