package software.blob.audio.wave;

import software.blob.audio.util.Misc;
import software.blob.ui.util.FileUtils;
import software.blob.ui.util.Log;

import java.io.File;

/**
 * Main container for waveform data
 */
public class WavData {

    private static final int BUF_SIZE = 8192;
    private static final double EPSILON = 1e-4;

    public File file;
    public String name;
    public int channels, numFrames, sampleRate;
    public double duration;
    public double[][] samples;
    public int loopStartFrame, loopEndFrame;
    public boolean randomStart;

    protected WavData() {
    }

    public WavData(String path) throws Exception {
        this(new File(path));
    }

    public WavData(File file) throws Exception {

        this.file = file;
        this.name = FileUtils.stripExtension(file);

        // Open the wav file specified as the first argument
        WavFile wavFile = WavFile.openWavFile(file);

        // Read metadata and create samples buffer
        this.channels = wavFile.getNumChannels();
        setSampleRate((int) wavFile.getSampleRate());
        setNumFrames((int) wavFile.getNumFrames());
        this.samples = new double[this.channels][this.numFrames];

        int framesRead;
        int offset = 0;
        do {
            // Read frames into buffer
            framesRead = wavFile.readFrames(this.samples, offset, BUF_SIZE);
            offset += framesRead;
        } while (framesRead != 0);

        // Close the wavFile
        wavFile.close();
    }

    public WavData(WavData other, int startFrame, int numFrames) {
        this.name = other.name;
        startFrame = Math.max(0, startFrame);
        numFrames = Math.min(other.numFrames - startFrame, numFrames);
        this.channels = other.channels;
        setSampleRate(other.sampleRate);
        setNumFrames(numFrames);
        setRandomStart(other.randomStart);
        setLoopFrames(other.loopStartFrame, other.loopEndFrame);
        this.samples = new double[this.channels][this.numFrames];
        for (int c = 0; c < this.channels; c++)
            System.arraycopy(other.samples[c], startFrame, this.samples[c], 0, this.numFrames);
    }

    public WavData(WavData other) {
        this(other, 0, other.numFrames);
    }

    public WavData(double[][] samples, int sampleRate) {
        this.channels = samples.length;
        setSampleRate(sampleRate);
        setNumFrames(samples[0].length);
        this.samples = samples;
    }

    public WavData(int channels, int numFrames, int sampleRate) {
        this.channels = channels;
        setSampleRate(sampleRate);
        setNumFrames(numFrames);
        this.samples = new double[channels][numFrames];
    }

    public WavData(int channels, double duration, int sampleRate) {
        this(channels, (int) Math.round(duration * sampleRate), sampleRate);
    }

    /**
     * Set the number of frames in this data (internal usage only)
     * Also updates duration
     * @param numFrames Number of frames
     */
    private void setNumFrames(int numFrames) {
        this.numFrames = numFrames;
        this.duration = getTime(numFrames);
    }

    /**
     * Set the sample rate of this audio
     * Sample rate change will be applied to existing sample data, if it exists
     * @param sampleRate Sample rate
     */
    public void setSampleRate(int sampleRate) {
        if (this.sampleRate == sampleRate)
            return;

        int newSize = 0;
        if (this.samples != null) {
            // Need to modify sample data to accord with the new sample rate
            newSize = (int) Math.round(sampleRate * this.duration);
            double[][] buf = new double[this.channels][newSize];
            for (int c = 0; c < this.channels; c++) {
                for (int dstFrame = 0; dstFrame < newSize; dstFrame++) {
                    double srcFrame = ((double) dstFrame / sampleRate) * this.sampleRate;
                    int f1 = Misc.clamp((int) Math.floor(srcFrame), 0, this.numFrames - 1);
                    int f2 = Misc.clamp((int) Math.ceil(srcFrame), 0, this.numFrames - 1);
                    double s;
                    if (f1 == f2)
                        s = this.samples[c][f1];
                    else {
                        double interp = srcFrame - f1;
                        s = this.samples[c][f1] * (1 - interp) + this.samples[c][f2] * interp;
                    }
                    buf[c][dstFrame] = s;
                }
            }
            this.samples = buf;
        }
        this.sampleRate = sampleRate;
        setNumFrames(newSize);
    }

    /**
     * Set the loop start and end frame
     * @param startFrame Frame to begin looping
     * @param endFrame Frame to end looping
     */
    public void setLoopFrames(int startFrame, int endFrame) {
        this.loopStartFrame = startFrame;
        this.loopEndFrame = endFrame;
    }

    /**
     * Check if this wav is loopable
     * @return True if loopable
     */
    public boolean isLoopable() {
        return this.loopEndFrame > 0 && this.loopStartFrame < this.loopEndFrame;
    }

    /**
     * Flag this wav for starting at a random frame during mixing and playback
     * @param randomStart True if the start time should be random
     */
    public void setRandomStart(boolean randomStart) {
        this.randomStart = randomStart;
    }

    /**
     * Get the frame that the given position corresponds to
     * @param seconds Audio position (seconds)
     * @return Frame number
     */
    public int getFrame(double seconds) {
        return (int) Math.round(seconds * this.sampleRate);
    }

    /**
     * Get the audio position in seconds given a frame number
     * @param frame Frame number
     * @return Audio position (seconds)
     */
    public double getTime(int frame) {
        return (double) frame / this.sampleRate;
    }

    /**
     * Create a copy of this WAV's samples at a given offset/length
     * @param channel Channel number
     * @param startFrame Frame to start copying from
     * @param length Length to copy
     * @return Copy of samples
     */
    public double[] copySamples(int channel, int startFrame, int length) {
        double[] copy = new double[length];
        length = Math.min(length, this.numFrames - startFrame);
        System.arraycopy(this.samples[channel], startFrame, copy, 0, length);
        return copy;
    }

    /**
     * Pad the start or end of the clip with silence
     * @param numFrames Number of frames to add
     */
    public void pad(int numFrames, boolean end) {
        if (numFrames <= 0)
            return;
        int newSize = this.numFrames + numFrames;
        int start = end ? 0 : numFrames;
        double[][] buf = new double[this.channels][newSize];
        for (int c = 0; c < this.channels; c++)
            System.arraycopy(this.samples[c], 0, buf[c], start, this.numFrames);
        setNumFrames(newSize);
        this.samples = buf;
    }

    public void pad(int numFrames) {
        pad(numFrames, true);
    }

    /**
     * Pad the end of the clip with a loop of the audio starting over
     * @param startFrame Start frame to begin the loop copy
     * @param endFrame End frame to end the loop copy
     * @param numFrames Number of additional frames to add
     */
    public void padLoop(int startFrame, int endFrame, int numFrames) {
        if (numFrames <= 0)
            return;
        int newSize = this.numFrames + numFrames;
        startFrame = Math.max(startFrame, 0);
        endFrame = Math.min(endFrame, this.numFrames);
        int loopSize = endFrame - startFrame;
        double[][] buf = new double[this.channels][newSize];
        for (int c = 0; c < this.channels; c++) {
            int remFrames = newSize;
            int pos = 0;
            while (remFrames > 0) {
                int len = Math.min(remFrames, loopSize);
                System.arraycopy(this.samples[c], startFrame, buf[c], pos, len);
                remFrames -= len;
                pos += len;
            }
        }
        setNumFrames(newSize);
        this.samples = buf;
    }

    public void padLoop(int numFrames) {
        if (isLoopable())
            padLoop(loopStartFrame, loopEndFrame, numFrames);
        else
            padLoop(0, this.numFrames, numFrames);
    }

    /**
     * Append another clip to the end of this clip
     * @param other Other audio clip
     */
    public void append(WavData other) {
        // Ensure channel layout is the same
        other = matchDepth(other);

        // Append data
        int start = this.numFrames;
        pad(other.numFrames);
        for (int c = 0; c < this.channels; c++)
            System.arraycopy(other.samples[c], 0, this.samples[c], start, other.numFrames);
    }

    /**
     * Mix another audio track with this track
     * @param other Audio track
     * @param startFrame Frame to begin mixing in the other track
     * @param numFrames Number of frames to mix in
     */
    public void mix(WavData other, int startFrame, int numFrames) {
        // Ensure channel layout and sample rate are the same
        other = matchDepth(other);

        // Resize audio if we can't already fit the full track
        int endFrame = startFrame + numFrames;
        if (endFrame > this.numFrames)
            pad(endFrame - this.numFrames);

        int oStart = 0;
        if (startFrame < 0) {
            oStart = Math.abs(startFrame);
            startFrame = 0;
        }

        // Mix
        for (int c = 0; c < this.channels; c++) {
            for (int s1 = startFrame, s2 = oStart; s1 < endFrame; s1++, s2++)
                this.samples[c][s1] += other.samples[c][s2];
        }
    }

    public void mix(WavData other, int startFrame) {
        mix(other, startFrame, other.numFrames);
    }

    public void mix(WavData other, double startTime, double duration) {
        mix(other, getFrame(startTime), getFrame(duration));
    }

    public void mix(WavData other, double startTime) {
        mix(other, startTime, other.duration);
    }

    /**
     * Fade this track into another track
     * @param other Other audio track
     * @param startFrame Start frame to begin the fade
     *                   The fade duration is as long as the overlap between this
     *                   clip and the other clip
     */
    public void crossFade(WavData other, final int startFrame) {
        // Ensure channel layout is the same
        other = matchDepth(other);

        final int endFrame = Math.min(this.numFrames, startFrame + other.numFrames);
        final int overlap = endFrame - startFrame;
        if (overlap <= 0) {
            // No overlap - just append
            pad(-overlap);
            append(other);
            return;
        }

        // Need to expand clip length by the amount of non-overlap on the second clip
        int padding = other.numFrames - overlap;
        pad(padding);

        // Mix the overlapping portion of the two clips
        final double[][] osamples = other.samples;
        forEachSample((c, f, amp) -> {
            int of = f - startFrame;
            double mix = (double) of / overlap;
            samples[c][f] = (amp * (1 - mix)) + (osamples[c][of] * mix);
            return true;
        }, startFrame, endFrame);

        // Memcpy the rest of the other clip after the fade
        if (padding > 0) {
            for (int c = 0; c < this.channels; c++)
                System.arraycopy(osamples[c], overlap, this.samples[c], endFrame, padding);
        }
    }

    /**
     * Trim this audio track to a new size
     * @param startFrame Start frame to begin crop
     * @param numFrames Number of frames after the start to keep
     */
    public void trim(int startFrame, int numFrames) {
        numFrames = Math.max(0, Math.min(this.numFrames - startFrame, numFrames));
        double[][] buf = new double[this.channels][numFrames];
        if (numFrames > 0) {
            for (int c = 0; c < this.channels; c++)
                System.arraycopy(this.samples[c], startFrame, buf[c], 0, numFrames);
        }
        setNumFrames(numFrames);
        this.samples = buf;
    }

    /**
     * Ensure an audio clip is the same sample rate and has the same number of channels as this clip
     * @param other Audio clip
     * @return Audio clip with matching dimensions
     */
    private WavData matchDepth(WavData other) {
        if (this.channels != other.channels || this.sampleRate != other.sampleRate) {
            other = new WavData(other);
            other.setChannels(this.channels);
            other.setSampleRate(this.sampleRate);
        }
        return other;
    }

    /**
     * Set the number of channels for this audio
     * This will automatically modify the samples to correspond to the new channel count
     * @param numChannels New number of channels
     */
    public void setChannels(int numChannels) {
        if (this.channels == numChannels || numChannels < 1)
            return;

        double[][] newSamples = new double[numChannels][this.numFrames];

        if (numChannels > this.channels) {
            // Copy last channel into additional channels
            for (int c = 0; c < numChannels; c++)
                System.arraycopy(this.samples[this.channels-1], 0, newSamples[c], 0, this.numFrames);
        } else {
            // Mix down last channel into remaining
            int lastChannel = numChannels - 1;
            double ampMulti = 1d / (this.channels - lastChannel);
            for (int c = 0; c < this.channels; c++) {
                if (c < lastChannel)
                    System.arraycopy(this.samples[c], 0, newSamples[c], 0, this.numFrames);
                else {
                    for (int f = 0; f < this.numFrames; f++)
                        newSamples[lastChannel][f] += this.samples[c][f] * ampMulti;
                }
            }
        }

        // Finish
        this.samples = newSamples;
        this.channels = numChannels;
    }

    /**
     * Multiply each sample by a specific factor
     * @param factor Multiplication factor
     */
    public void multiply(final double factor, int startFrame, int endFrame) {
        forEachSample((c, f, amp) -> {
            this.samples[c][f] *= factor;
            return true;
        }, startFrame, endFrame);
    }

    public void multiply(double factor) {
        multiply(factor, 0, this.numFrames);
    }

    /**
     * Get the peak amplitude of a clip of wav data
     * @param startFrame Frame to begin scan
     * @param endFrame Frame to end scan
     * @return Peak amplitude
     */
    public double getPeakAmplitude(int startFrame, int endFrame) {
        final double[] max = {-Double.MAX_VALUE};
        forEachSample((c, f, amp) -> {
            max[0] = Math.max(max[0], Math.abs(amp));
            return true;
        }, startFrame, endFrame);
        return max[0];
    }

    public double getPeakAmplitude() {
        return getPeakAmplitude(0, this.numFrames);
    }

    /**
     * Set the peak amplitude for this clip
     * @param newPeak New peak amplitude
     * @param startFrame Start frame to change
     * @param endFrame End frame to change
     */
    public void setPeakAmplitude(double newPeak, int startFrame, int endFrame) {
        double curPeak = getPeakAmplitude(startFrame, endFrame);
        if (curPeak <= 0)
            return;
        multiply(newPeak / curPeak, startFrame, endFrame);
    }

    public void setPeakAmplitude(double newPeak) {
        setPeakAmplitude(newPeak, 0, this.numFrames);
    }

    /**
     * Clamp amplitude to valid range (-1.0 to 1.0)
     * @param startFrame Start frame
     * @param endFrame End frame
     * @return True if altitude was clamped at some point
     */
    public boolean clampAmplitude(int startFrame, int endFrame) {
        final boolean[] clamped = {false};
        forEachSample((c, f, amp) -> {
            if (amp > 1) {
                samples[c][f] = 1;
                clamped[0] = true;
            } else if (amp < -1) {
                samples[c][f] = -1;
                clamped[0] = true;
            }
            return true;
        }, startFrame, endFrame);
        return clamped[0];
    }

    public boolean clampAmplitude() {
        return clampAmplitude(0, this.numFrames);
    }

    /**
     * Find the first instance where an audio sample hits zero amplitude
     * @param startFrame Frame to start search from
     * @param endFrame Frame to end search (inclusive)
     * @param minAmp Minimum amplitude to consider silence
     * @return Frame where amplitude is zero or -1 if not found
     */
    public int findZeroCrossing(int startFrame, int endFrame, double minAmp) {
        boolean forward = startFrame < endFrame;
        if (forward) {
            startFrame = Math.max(0, startFrame);
            endFrame = Math.min(this.numFrames, endFrame);
        } else {
            endFrame = Math.max(0, endFrame);
            startFrame = Math.min(this.numFrames, startFrame);
        }
        int iter = forward ? 1 : -1;
        outer: for (int f = startFrame; forward ? f < endFrame : f > endFrame; f += iter) {
            for (int c = 0; c < this.channels; c++) {
                if (Math.abs(this.samples[c][f]) > minAmp)
                    continue outer;
            }
            // All samples at zero
            return f;
        }
        return -1;
    }

    public int findZeroCrossing(int startFrame, int endFrame) {
        return findZeroCrossing(startFrame, endFrame, EPSILON);
    }

    /**
     * Trim out the silence in a clip
     * @param minAmp Minimum amplitude to consider silence
     */
    public void trimSilence(double minAmp) {
        final double ma = Math.max(minAmp, 1e-9);
        final int[][] bounds = new int[channels][2];
        for (int c = 0; c < channels; c++)
            bounds[c][0] = bounds[c][1] = -1;

        // Find start trim
        forEachSample((channel, frame, amp) -> {
            if (amp >= ma && bounds[channel][0] == -1)
                bounds[channel][0] = frame;
            return true;
        });

        // Find end trim
        forEachSample((channel, frame, amp) -> {
            if (amp >= ma && bounds[channel][1] == -1)
                bounds[channel][1] = frame;
            return true;
        }, numFrames - 1, 0);

        int minStart = numFrames, maxEnd = 0;
        for (int c = 0; c < channels; c++) {
            minStart = Math.min(minStart, bounds[c][0]);
            maxEnd = Math.max(maxEnd, bounds[c][1]);
        }
        if (minStart == -1) {
            // Complete silence - trim out the whole thing
            this.samples = new double[this.channels][0];
            setNumFrames(0);
        } else if (minStart >= 0 && maxEnd < numFrames - 1)
            trim(minStart, maxEnd - minStart + 1);
    }

    /**
     * Reverse the samples in this clip
     */
    public void reverse() {
        double[][] buf = new double[this.channels][this.numFrames];
        forEachSample((c, f, amp) -> {
            buf[c][numFrames - f - 1] = amp;
            return true;
        });
        this.samples = buf;
    }

    /**
     * Loop through each sample for each channel
     */
    public interface ForEach {

        /**
         * Called for each pixel in the image
         * @param channel Channel number
         * @param frame Frame number
         * @param amp Amplitude value for this sample
         * @return True to continue loop, false to stop
         */
        boolean onSample(int channel, int frame, double amp);
    }

    /**
     * Iterate through each sample in the audio clip in channel-major order
     * @param iterator For-each iterator
     * @param startFrame Start frame
     * @param endFrame End frame
     */
    public void forEachSample(ForEach iterator, int startFrame, int endFrame) {
        startFrame = Math.max(0, startFrame);
        endFrame = Math.min(this.numFrames, endFrame);
        for (int c = 0; c < this.channels; c++) {
            if (startFrame < endFrame) {
                for (int f = startFrame; f < endFrame; f++)
                    iterator.onSample(c, f, this.samples[c][f]);
            } else {
                for (int f = startFrame; f > endFrame; f--)
                    iterator.onSample(c, f, this.samples[c][f]);
            }
        }
    }

    public void forEachSample(ForEach iterator) {
        forEachSample(iterator, 0, this.numFrames);
    }

    public void forEachSample(ForEach iterator, int channelNum, int startFrame, int endFrame) {
        if (channelNum < 0 || channelNum >= this.channels)
            return;
        for (int f = startFrame; f < endFrame; f++)
            iterator.onSample(channelNum, f, this.samples[channelNum][f]);
    }

    public void forEachSample(ForEach iterator, int channelNum) {
        forEachSample(iterator, channelNum, 0, this.numFrames);
    }

    /**
     * Write this WAV data out to a file
     * @param file File to write to
     * @return True if successful
     */
    public boolean writeToFile(File file) {

        // Make sure the file ends in .wav
        String ext = FileUtils.getExtension(file);
        if (!ext.equals("wav")) {
            String fName = FileUtils.stripExtension(file);
            file = new File(file.getParent(), fName + ".wav");
        }

        // Make sure there's no audio clipping since it won't save properly
        if (clampAmplitude())
            Log.w(file.getName() + " has audio clipping!");

        WavFile f = null;
        try {
            f = WavFile.newWavFile(file, this.channels, this.numFrames, 16, this.sampleRate);
            f.writeFrames(this.samples, this.numFrames);
            return true;
        } catch (Exception e) {
            Log.e("Failed to create output file: " + file, e);
            return false;
        } finally {
            try {
                if (f != null)
                    f.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Join a set of audio tracks by assigning each track to a channel
     * Note: Sample rate must be the same between all tracks
     * Any difference in frame length will be padded with silence
     * @param tracks Audio to join
     * @return Single track
     */
    public static WavData joinByChannel(WavData[] tracks) {

        // Nothing to join
        if (tracks == null || tracks.length == 0)
            return null;

        // Get the output stats
        int totalChannels = 0;
        int maxFrames = 0;
        int sampleRate = -1;
        for (WavData d : tracks) {
            totalChannels += d.channels;
            maxFrames = Math.max(maxFrames, d.numFrames);
            if (sampleRate != -1 && sampleRate != d.sampleRate) {
                Log.e("Cannot join audio tracks due to sample rate mismatch: "
                        + sampleRate + " != " + d.sampleRate);
                return null;
            }
            sampleRate = d.sampleRate;
        }

        // Create the output audio
        WavData output = new WavData(totalChannels, maxFrames, sampleRate);
        int outChannel = 0;
        for (WavData d : tracks) {
            for (int c = 0; c < d.channels; c++) {
                System.arraycopy(d.samples[c], 0, output.samples[outChannel], 0, d.numFrames);
                outChannel++;
            }
        }

        return output;
    }
}
