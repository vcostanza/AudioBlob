package software.blob.audio.playback;

import software.blob.audio.wave.WavData;

import javax.sound.sampled.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Audio playback for waveform sounds
 */
public class AudioPlayer implements Runnable {

    private final SourceDataLine speaker;
    private final List<AudioHandle> queued = new ArrayList<>();
    private final List<AudioHandle> removed = new ArrayList<>();
    private boolean running, sleeping;

    private final int sampleRate, channels, fps;
    private final int bufSizeFrames, bufSizeBytes;
    private final double[] bufFrac;

    /**
     * Initialize the audio player
     * @throws LineUnavailableException If the line fails to initialize
     */
    public AudioPlayer(int sampleRate, int fps, int channels) throws LineUnavailableException {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.fps = fps;
        this.bufSizeFrames = sampleRate / fps;
        this.bufSizeBytes = bufSizeFrames * channels * 2;
        this.bufFrac = new double[bufSizeFrames];
        for (int i = 0; i < bufSizeFrames; i++)
            bufFrac[i] = (double) i / bufSizeFrames;

        AudioFormat fmt = new AudioFormat(sampleRate, 16, channels, true, false);
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, fmt);

        speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speaker.open(fmt, this.bufSizeBytes);

        running = true;
        sleeping = true;

        // Start the player thread
        Thread t = new Thread(this, "AudioPlayer");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY); // Audio should always be max priority
        t.start();
    }

    public AudioPlayer() throws LineUnavailableException {
        this(44100, 60, 2);
    }

    public void dispose() {
        running = false;
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getChannels() {
        return this.channels;
    }

    public int getFPS() {
        return this.fps;
    }

    public WavData createClip(double duration) {
        return new WavData(this.channels, duration, this.sampleRate);
    }

    /**
     * Create a new audio handle which can be late queued
     * @param wav Sound effect
     * @return Audio handle
     */
    public AudioHandle createHandle(WavData wav) {
        // Sample rate and channel count correction
        if (wav.channels != this.channels || wav.sampleRate != this.sampleRate) {
            wav = new WavData(wav);
            wav.setChannels(this.channels);
            wav.setSampleRate(this.sampleRate);
        }
        return new AudioHandle(wav);
    }

    /**
     * Add a sound to the queue
     * @param handle Audio handle
     * @param delay Delay in milliseconds
     * @return True if the sound was queued successfully
     */
    public boolean queue(AudioHandle handle, long delay) {
        // Don't queue any sounds while this isn't running
        if (!running)
            return false;

        synchronized (queued) {
            // Specify the time this sound should play
            handle.time = curTime() + delay;

            // Queue the sound and wake up the audio player
            queued.add(handle);
            sleeping = false;
        }

        return true;
    }

    public boolean queue(AudioHandle handle, double delay) {
        return queue(handle, (long) (delay * 1000));
    }

    public boolean queue(AudioHandle handle) {
        return queue(handle, 0);
    }

    /**
     * Add a sound to the queue
     * @param wav Sound effect
     * @param delay Delay (milliseconds)
     * @return Audio handle
     */
    public AudioHandle queue(WavData wav, long delay) {
        AudioHandle handle = createHandle(wav);
        return queue(handle, delay) ? handle : null;
    }

    public AudioHandle queue(WavData wav, double delay) {
        return queue(wav, (long) (delay * 1000));
    }

    public AudioHandle queue(WavData wav) {
        return queue(wav, 1);
    }

    /**
     * Remove audio from the queue
     * @param handle Audio handle
     */
    public void remove(AudioHandle handle) {
        synchronized (queued) {
            queued.remove(handle);
            removed.add(handle);
        }
    }

    /**
     * Remove multiple audio handles from the queue
     * @param handles List of audio handles
     */
    public void removeAll(Collection<AudioHandle> handles) {
        if (handles == null)
            return;
        synchronized (queued) {
            queued.removeAll(handles);
            removed.addAll(handles);
        }
    }

    /**
     * Clear all playing/pending sound effects
     */
    public void clear() {
        synchronized (queued) {
            removed.addAll(queued);
            queued.clear();
        }
    }

    /**
     * Get the current system time offset in milliseconds
     * Note: This is relative to system startup time, not UNIX time
     * @return Current time offset in milliseconds
     */
    public long curTime() {
        return System.nanoTime() / 1_000_000L;
    }

    @Override
    public void run() {
        try {
            speaker.start();

            byte[] data = new byte[this.bufSizeBytes];
            double[][] wav = new double[this.channels][this.bufSizeFrames];
            while (running) {

                // No sounds to play (and no need to check)
                // Sleep and play nothing
                if (sleeping) {
                    speaker.write(data, 0, this.bufSizeBytes);
                    continue;
                }

                long curTime = curTime();

                // Scan samples
                final List<AudioHandle> played;
                synchronized (queued) {

                    // Save handles for callbacks later
                    played = new ArrayList<>(queued);

                    // Remove samples that are finished playing
                    for (int i = 0, j = 0; i < queued.size(); i++, j++) {
                        AudioHandle s = queued.get(i);
                        if (s.time > curTime)
                            played.remove(j--);
                        else if (s.isFinished())
                            queued.remove(i--);
                    }

                    // Set volume to zero on samples that are still playing but have been marked for removal
                    // This ensures there's no clipping when abruptly ending a sample
                    for (AudioHandle h : removed) {
                        played.add(h);
                        h.setVolumeCalculator(null);
                        h.setVolume(0);
                    }

                    if (queued.isEmpty() && removed.isEmpty()) {
                        // No samples to play - sleep
                        Arrays.fill(data, (byte) 0);
                        sleeping = true;
                    } else {
                        // Clear the WAV buffer
                        for (int c = 0; c < this.channels; c++)
                            Arrays.fill(wav[c], 0);

                        // Mix sounds in the queue
                        for (AudioHandle s : played) {
                            final double dstVol = s.getVolume();
                            final boolean volChange = dstVol != s.srcVol;
                            for (int f = 0; f < this.bufSizeFrames; f++) {
                                if (s.isFinished())
                                    break;
                                double vol = s.srcVol;
                                if (volChange)
                                    vol = (s.srcVol * (1 - this.bufFrac[f])) + (dstVol * this.bufFrac[f]);
                                for (int c = 0; c < this.channels; c++) {
                                    double amp = s.wav.samples[c][s.frame];
                                    wav[c][f] += amp * vol;
                                }
                                s.incrementFrame();
                            }
                            s.srcVol = dstVol;
                        }

                        // Convert to byte data
                        int d = 0;
                        for (int f = 0; f < this.bufSizeFrames; f++) {
                            for (int c = 0; c < this.channels; c++) {
                                double s = wav[c][f];
                                if (s > 1) s = 1;
                                else if (s < -1) s = -1;
                                if (s < 0) s += 2;
                                int sh = (int) (s * 32768);
                                int b2 = sh >> 8;
                                int b1 = sh & 0xFF;
                                if (b1 > 127) b1 -= 256;
                                if (b2 > 127) b2 -= 256;
                                data[d++] = (byte) b1;
                                data[d++] = (byte) b2;
                            }
                        }

                        // Mark removed samples as finished and clear removal buffer
                        for (AudioHandle h : removed)
                            h.finish();
                        removed.clear();
                    }
                }

                // Fire callbacks before writing data, since that method blocks until writing is finished
                SwingUtilities.invokeLater(() -> {
                    for (AudioHandle s : played) {
                        if (s.callback != null) {
                            if (s.isFinished())
                                s.callback.onPlaybackFinished();
                            else
                                s.callback.onPlayback(s.getTime(), s.getFrame());
                        }
                    }
                });

                // Send data to sound player
                speaker.write(data, 0, this.bufSizeBytes);
            }

            speaker.drain();
            speaker.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
