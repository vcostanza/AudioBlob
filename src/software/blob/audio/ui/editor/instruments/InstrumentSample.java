package software.blob.audio.ui.editor.instruments;

import software.blob.audio.effects.volume.FadeEffect;
import software.blob.audio.util.Misc;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import java.io.File;

/**
 * Audio clip tied to a specific note
 */
public class InstrumentSample {

    public final int note;
    public final int velocity;
    public final double frequency;

    protected File file;
    protected SampleWav wav;
    protected double maxDuration = Double.MAX_VALUE;
    protected int sampleRate, channels;
    protected int startLoopFrame, endLoopFrame;

    protected InstrumentSample(int note, int velocity) {
        this.note = note;
        this.velocity = velocity;
        this.frequency = Misc.getNoteFrequency(note);
    }

    public InstrumentSample(int note, int velocity, File file) {
        this(note, velocity);
        this.file = file;
    }

    public InstrumentSample(int note, int velocity, WavData wav) {
        this(note, velocity);
        this.wav = new SampleWav(wav);
    }

    public InstrumentSample(int note, WavData wav) {
        this(note, 0, wav);
    }

    /**
     * Set max duration for this sample
     * @param duration Duration in seconds
     */
    public void setMaxDuration(double duration) {
        this.maxDuration = duration;
    }

    /**
     * Set the desired sample rate for this sample
     * @param sampleRate Sample rate (number of samples per second)
     */
    public void setSampleRate(int sampleRate) {
        if (this.sampleRate != sampleRate) {
            this.sampleRate = sampleRate;
            if (this.wav != null && sampleRate > 0) {
                this.wav.unlock();
                this.wav.setSampleRate(sampleRate);
                this.wav.lock();
            }
        }
    }

    /**
     * Set the desired number of channels for this sample
     * @param channels Number of channels
     */
    public void setChannels(int channels) {
        if (this.channels != channels) {
            this.channels = channels;
            if (this.wav != null && channels > 0) {
                this.wav.unlock();
                this.wav.setChannels(channels);
                this.wav.lock();
            }
        }
    }

    /**
     * Set the loop start and end frame
     * @param startFrame Frame to start looping
     * @param endFrame Frame to end the loop (rewind to start frame)
     */
    public void setLoopFrames(int startFrame, int endFrame) {
        this.startLoopFrame = startFrame;
        this.endLoopFrame = endFrame;
    }

    /**
     * Check whether this sample loops
     * @return True if this is a looping sample
     */
    public boolean isLoopable() {
        return this.endLoopFrame > 0 && this.startLoopFrame < this.endLoopFrame;
    }

    /**
     * Get the frame that begins the loop segment
     * @return Start frame
     */
    public int getStartLoopFrame() {
        return this.startLoopFrame;
    }

    /**
     * Get the frame that ends the loop segment
     * @return End frame
     */
    public int getEndLoopFrame() {
        return this.endLoopFrame;
    }

    /**
     * Get the note key for this sample
     * @return Note key (note * 128 + velocity)
     */
    public int getKey() {
        return Instrument.getNoteKey(this.note, this.velocity);
    }

    /**
     * Get the wav data for this sample
     * @return WAV data
     */
    public SampleWav getWav() {
        if (this.wav == null) {
            try {
                this.wav = loadWav(this.file);
            } catch (Exception e) {
                Log.e("Failed to read WAV file: " + this.file, e);
            }
        }
        return this.wav;
    }

    protected SampleWav loadWav(File file) throws Exception {
        WavData wav = new WavData(file);
        if (wav.duration > this.maxDuration) {
            int durFrames = wav.getFrame(this.maxDuration);
            wav.trim(0, durFrames);
            if (wav.duration > 0.1)
                wav = new FadeEffect(1, 0, 0.1, FadeEffect.Shape.QUADRATIC).process(wav);
        }
        if (this.sampleRate > 0)
            wav.setSampleRate(this.sampleRate);
        if (this.channels > 0)
            wav.setChannels(this.channels);
        if (isLoopable())
            wav.setLoopFrames(startLoopFrame, endLoopFrame);
        return new SampleWav(wav);
    }

    @Override
    public String toString() {
        String noteVel = Misc.getNoteName(note, velocity);
        return file != null ? (file.getName() + " (" + noteVel + ")") : noteVel;
    }
}
