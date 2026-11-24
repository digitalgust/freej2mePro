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
package org.recompile.mobile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;

import javax.microedition.media.Control;
import javax.microedition.media.Controllable;
import javax.microedition.media.control.VideoControl;

public class PlatformPlayer implements Player
{
	private static final Set<String> reportedUnsupportedTypes = new HashSet<String>();

	private String contentType = "";

	private audioplayer player;
	private boolean unsupportedVideo;
	private final VideoControl unsupportedVideoControl = new UnsupportedVideoControl();

	private int state = Player.UNREALIZED;

	private Vector<PlayerListener> listeners;

	private Control[] controls;

	public PlatformPlayer(InputStream stream, String type)
	{
		listeners = new Vector<PlayerListener>();
		contentType = type == null ? "" : type.trim();
		unsupportedVideo = contentType.length() == 0
				|| contentType.regionMatches(true, 0, "video/", 0, 6);
		controls = new Control[unsupportedVideo ? 4 : 3];

		if(Mobile.sound == false)
		{
			player = new audioplayer();
		}
		else
		{
			if(contentType.equalsIgnoreCase("audio/mid") || contentType.equalsIgnoreCase("audio/midi") || contentType.equalsIgnoreCase("sp-midi") || contentType.equalsIgnoreCase("audio/spmidi"))
			{
				player = new midiPlayer(stream);
			}
			else
			{
				if(contentType.equalsIgnoreCase("audio/x-wav") || contentType.equalsIgnoreCase("audio/wav"))
				{
					player = new wavPlayer(stream);
				}
				else /* TODO: Implement a player for amr and mpeg audio types */
				{
					if (contentType.length() > 0) {
						reportUnsupportedType(contentType);
					}
					player = new audioplayer();
				}
			}
		}
		controls[0] = new volumeControl();
		controls[1] = new tempoControl();
		controls[2] = new midiControl();
		if (unsupportedVideo) {
			controls[3] = unsupportedVideoControl;
		}

		//System.out.println("media type: "+type);
	}

	public PlatformPlayer(String locator)
	{
		player = new audioplayer();
		listeners = new Vector<PlayerListener>();
		controls = new Control[3];
		System.out.println("Player locator: "+locator);
	}

	private static void reportUnsupportedType(String type)
	{
		synchronized (reportedUnsupportedTypes)
		{
			if (reportedUnsupportedTypes.add(type))
			{
				System.out.println("No Player For: " + type + " (silence)");
			}
		}
	}


	public void close()
	{
		try
		{
			player.stop();
			state = Player.CLOSED;
			notifyListeners(PlayerListener.CLOSED, null);	
		}
		catch (Exception e) { }
		state = Player.CLOSED;	
	}

	public int getState()
	{
		if(player.isRunning()==false)
		{
			state = Player.PREFETCHED;
		}
		return state;
	}

	public void start()
	{
		//System.out.println("Play "+contentType);
		try
		{
			player.start();
		}
		catch (Exception e) {  }
	}

	public void stop()
	{
		//System.out.println("Stop "+contentType);
		try
		{
			player.stop();
		}
		catch (Exception e) { }
	}

	public void addPlayerListener(PlayerListener playerListener)
	{
		//System.out.println("Add Player Listener");
		listeners.add(playerListener);
	}

	public void removePlayerListener(PlayerListener playerListener)
	{
		//System.out.println("Remove Player Listener");
		listeners.remove(playerListener);
	}

	private void notifyListeners(String event, Object eventData)
	{
		for(int i=0; i<listeners.size(); i++)
		{
			listeners.get(i).playerUpdate(this, event, eventData);
		}
	}

	public void deallocate()
	{
		stop();
		player.deallocate();
		state = Player.CLOSED;
	}

	public String getContentType() { return contentType; }

	public long getDuration() { return Player.TIME_UNKNOWN; }

	public long getMediaTime() { return player.getMediaTime(); }

	public void prefetch() { state = Player.PREFETCHED; }

	public void realize() { state = Player.REALIZED; }

	public void setLoopCount(int count) { player.setLoopCount(count); }

	public long setMediaTime(long now) { return player.setMediaTime(now); }

	// Controllable interface //

	public Control getControl(String controlType)
	{
		if(controlType == null) { return null; }
		if(controlType.equals("VolumeControl")) { return controls[0]; }
		if(controlType.equals("TempoControl")) { return controls[1]; }
		if(controlType.equals("MIDIControl")) { return controls[2]; }
		if(controlType.equals("javax.microedition.media.control.VolumeControl")) { return controls[0]; }
		if(controlType.equals("javax.microedition.media.control.TempoControl")) { return controls[1]; }
		if(controlType.equals("javax.microedition.media.control.MIDIControl")) { return controls[2]; }
		if (unsupportedVideo && (controlType.equals("VideoControl")
				|| controlType.equals("javax.microedition.media.control.VideoControl"))) {
			return unsupportedVideoControl;
		}
		return null;
	}

	public Control[] getControls()
	{
		return controls;
	}

	// Players //

	private class audioplayer
	{
		public void start() {  }
		public void stop() {  }
		public void setLoopCount(int count) {  }
		public long setMediaTime(long now) { return now; }
		public long getMediaTime() { return 0; }
		public boolean isRunning() { return false; }
		public void deallocate() {  }
	}

	/** No-op video control used when this emulator has no player for the video stream. */
	private static class UnsupportedVideoControl implements VideoControl
	{
		public int getDisplayHeight() { return 0; }
		public int getDisplayWidth() { return 0; }
		public int getDisplayX() { return 0; }
		public int getDisplayY() { return 0; }
		public byte[] getSnapshot(String imageType) { return null; }
		public int getSourceHeight() { return 0; }
		public int getSourceWidth() { return 0; }
		public Object initDisplayMode(int mode, Object arg) { return null; }
		public void setDisplayFullScreen(boolean fullScreenMode) { }
		public void setDisplayLocation(int x, int y) { }
		public void setDisplaySize(int width, int height) { }
		public void setVisible(boolean visible) { }
	}

	private class midiPlayer extends audioplayer
	{
		private Sequencer midi;

		private int loops = 0;

		private long tick = 0L;

		public midiPlayer(InputStream stream)
		{
			try
			{
				midi = MidiSystem.getSequencer();
				midi.open();
				midi.setSequence(stream);
				state = Player.PREFETCHED;
			}
			catch (Exception e) { }
		}

		public void start()
		{
			if(isRunning()) { return; }

			if(midi.getTickPosition() >= midi.getTickLength())
			{
				midi.setTickPosition(0);
			}
			tick = midi.getTickPosition();
			midi.start();
			state = Player.STARTED;
			notifyListeners(PlayerListener.STARTED, tick);
		}

		public void stop()
		{
			midi.stop();
			state = Player.PREFETCHED;
			tick = midi.getTickPosition();
			notifyListeners(PlayerListener.STOPPED, tick);
		}
		public void deallocate()
		{
			midi.close();
		}

		public void setLoopCount(int count)
		{
			loops = count;
			midi.setLoopCount(count);
		}
		public long setMediaTime(long now)
		{
			try
			{
				midi.setTickPosition(now);
			}
			catch (Exception e) { }
			return now;
		}
		public long getMediaTime()
		{
			return midi.getTickPosition();
		}
		public boolean isRunning()
		{
			return midi.isRunning();
		}
	}

	private class wavPlayer extends audioplayer
	{

		private byte[] wavData;
		private AudioInputStream wavStream;
		private Clip wavClip;

		private int loops = 0;

		private long time = 0L;

		public wavPlayer(InputStream stream)
		{
			try
			{
				wavData = readAllBytes(stream);
				reopenClip();
				state = Player.PREFETCHED;
			}
			catch (Exception e) 
			{ 
				System.out.println("Couldn't load wav file: " + e.getMessage());
				closeClip();
			}
		}

		public void start()
		{
			if(isRunning()) { return; }
			if (wavClip == null) return;
			if(wavClip.getFramePosition() >= wavClip.getFrameLength())
			{
				wavClip.setFramePosition(0);
			}
			time = wavClip.getMicrosecondPosition();
			if (loops == -1)
			{
				wavClip.loop(Clip.LOOP_CONTINUOUSLY);
			}
			else
			{
				wavClip.start();
			}
			state = Player.STARTED;
			notifyListeners(PlayerListener.STARTED, time);
		}

		public void stop()
		{
			if (wavClip == null) return;
			wavClip.stop();
			time = wavClip.getMicrosecondPosition();
			state = Player.PREFETCHED;
			notifyListeners(PlayerListener.STOPPED, time);
		}

		public void setLoopCount(int count)
		{
			loops = count;
		}

		public long setMediaTime(long now)
		{
			if (wavClip == null) return 0;
			try
			{
				// Re-create the clip when rewinding to the beginning. DirectClip seek can
				// block inside DirectAudioDevice.flush(), which freezes the game thread.
				if (now <= 0)
				{
					reopenClip();
					return 0;
				}
				wavClip.setMicrosecondPosition(now);
			}
			catch (Exception e)
			{
				return getMediaTime();
			}
			return now;
		}
		public long getMediaTime()
		{
			if (wavClip == null) return 0;
			return wavClip.getMicrosecondPosition();
		}

		public boolean isRunning()
		{
			if (wavClip == null) return false;
			return wavClip.isRunning();
		}

		public void deallocate()
		{
			closeClip();
		}

		private void reopenClip() throws Exception
		{
			closeClip();
			if (wavData == null || wavData.length == 0) return;
			wavStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavData));
			wavClip = AudioSystem.getClip();
			wavClip.open(wavStream);
			time = 0L;
			if (loops > 0)
			{
				wavClip.setLoopPoints(0, -1);
			}
		}

		private void closeClip()
		{
			try
			{
				if (wavClip != null)
				{
					wavClip.stop();
					wavClip.close();
				}
			}
			catch (Exception e) { }
			try
			{
				if (wavStream != null)
				{
					wavStream.close();
				}
			}
			catch (Exception e) { }
			wavClip = null;
			wavStream = null;
		}
	}

	private static byte[] readAllBytes(InputStream stream) throws java.io.IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try
		{
			byte[] buffer = new byte[4096];
			int read;
			while ((read = stream.read(buffer)) != -1)
			{
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
		finally
		{
			try { stream.close(); } catch (Exception e) { }
		}
	}

	// Controls //

	private class midiControl implements javax.microedition.media.control.MIDIControl
	{
		public int[] getBankList(boolean custom) { return new int[]{}; }

		public int getChannelVolume(int channel) { return 0; }

		public java.lang.String getKeyName(int bank, int prog, int key) { return ""; }

		public int[] getProgram(int channel) { return new int[]{}; }

		public int[] getProgramList(int bank) { return new int[]{}; }

		public java.lang.String getProgramName(int bank, int prog) { return ""; }

		public boolean isBankQuerySupported() { return false; }

		public int longMidiEvent(byte[] data, int offset, int length) { return 0; }

		public void setChannelVolume(int channel, int volume) {  }

		public void setProgram(int channel, int bank, int program) {  }

		public void shortMidiEvent(int type, int data1, int data2) {  }
	}

	private class volumeControl implements javax.microedition.media.control.VolumeControl
	{
		private int level = 100;
		private boolean muted = false;

		public int getLevel() { return level; }

		public boolean isMuted() { return muted; }

		public int setLevel(int value) { level = value; return level; }

		public void setMute(boolean mute) { muted = mute; }
	}

	private class tempoControl implements javax.microedition.media.control.TempoControl
	{
		int tempo = 5000;
		int rate = 5000;

		public int getTempo() { return tempo; }

		public int setTempo(int millitempo) { tempo = millitempo; return tempo; }

		// RateControl interface
		public int getMaxRate() { return rate; }

		public int getMinRate() { return rate; }

		public int getRate() { return rate; }

		public int setRate(int millirate) { rate=millirate; return rate; }
	}
}
