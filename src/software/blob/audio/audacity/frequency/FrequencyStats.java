package software.blob.audio.audacity.frequency;

import software.blob.ui.util.Log;
import software.blob.audio.util.Misc;
import software.blob.audio.util.SerializableInputStream;
import software.blob.audio.util.SerializableOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Frequency statistics for an audio clip
 */
public class FrequencyStats implements Iterable<FrequencySample>, Serializable {

    // Sort frequency samples by time
    private final Comparator<FrequencySample> SORT_TIME = Comparator.comparingDouble(o -> o.time);

    private static final int MAX_USHORT = 65536;
    private static final int MAX_UBYTE = 256;

    // Duration of the audio clip
    public double duration;

    // Time interval between samples
    public double interval;

    // Minimum and maximum frequency
    public double minFreq, maxFreq;

    // Average frequency and tuned variant
    public double avgFreq, stdFreq, avgFreqStd, tunedFreq;

    // Amplitude data
    public double minAmp, maxAmp, avgAmp, stdAmp;

    // Frequency samples
    public List<FrequencySample> samples;

    // Whether or not the samples need to be sorted by time
    // This is set to false each time samples are added
    private transient boolean needsTimeSort;

    // For reading/writing
    private transient int minFreqFloor = -1, maxFreqCeil, freqRange;

    public FrequencyStats() {
        this.samples = new ArrayList<>();
        reset();
    }

    public FrequencyStats(FrequencyStats other) {
        this.duration = other.duration;
        this.interval = other.interval;
        this.minFreq = other.minFreq;
        this.maxFreq = other.maxFreq;
        this.avgFreq = other.avgFreq;
        this.stdFreq = other.stdFreq;
        this.avgFreqStd = other.avgFreqStd;
        this.tunedFreq = other.tunedFreq;
        this.minAmp = other.minAmp;
        this.maxAmp = other.maxAmp;
        this.avgAmp = other.avgAmp;
        this.stdAmp = other.stdAmp;
        this.samples = new ArrayList<>(other.samples.size());
        this.needsTimeSort = other.needsTimeSort;
        for (FrequencySample s : other.samples)
            this.samples.add(new FrequencySample(s));
    }

    /**
     * Reset statistics
     */
    public void reset() {
        this.duration = 0;
        this.minFreq = Double.MAX_VALUE;
        this.maxFreq = -Double.MAX_VALUE;
        this.avgFreq = this.tunedFreq = 0;
        clear();
    }

    /**
     * Add a frequency to these statistics
     * @param freq Frequency
     * @param amp Amplitude
     * @param time Time this frequency was scanned
     */
    public FrequencySample add(double freq, double amp, double time) {
        FrequencySample sample = new FrequencySample(freq, amp, time);
        add(sample);
        return sample;
    }

    public void add(FrequencySample sample) {
        this.samples.add(sample);
        this.needsTimeSort = true;
    }

    public void add(FrequencyStats stats, double startTime) {
        for (FrequencySample s : stats)
            add(s.frequency, s.amplitude, s.time + startTime);
    }

    public void add(FrequencyStats stats) {
        add(stats, 0);
    }

    public void addAll(List<FrequencySample> samples) {
        this.samples.addAll(samples);
        this.needsTimeSort = true;
    }

    /**
     * Get the frequency sample at a given index
     * @param index Sample index
     * @return Frequency sample or null if out of bounds
     */
    public FrequencySample getSample(int index) {
        return index >= 0 && index < getSampleCount() ? this.samples.get(index) : null;
    }

    /**
     * Get the number of samples in this statistics
     * @return Sample count
     */
    public int getSampleCount() {
        return this.samples.size();
    }

    public boolean isEmpty() {
        return getSampleCount() == 0;
    }

    public double getStartTime() {
        return isEmpty() ? 0 : getSample(0).time;
    }

    public void clear() {
        if (this.samples != null)
            this.samples.clear();
        this.needsTimeSort = false;
    }

    /**
     * Update statistics based on samples
     */
    public void update() {

        // Sort samples by time code
        sortByTime();

        // Calculate min, max, and average
        this.avgFreq = 0;
        this.avgAmp = 0;
        this.duration = 0;
        FrequencySample first = null;
        FrequencySample last = null;
        for (FrequencySample sample : this.samples) {
            this.minFreq = Math.min(this.minFreq, sample.frequency);
            this.maxFreq = Math.max(this.maxFreq, sample.frequency);
            this.minAmp = Math.min(this.minAmp, sample.amplitude);
            this.maxAmp = Math.max(this.maxAmp, sample.amplitude);
            this.avgFreq += sample.frequency;
            this.avgAmp += sample.amplitude;
            if (first == null)
                first = sample;
            last = sample;
        }
        this.avgFreq /= getSampleCount();
        this.avgAmp /= getSampleCount();
        if (first != null && last != null)
            this.duration = (last.time - first.time) + this.interval;

        // Calculate standard deviation
        this.stdFreq = 0;
        this.stdAmp = 0;
        for (FrequencySample sample : this.samples) {
            this.stdFreq += Math.pow(sample.frequency - this.avgFreq, 2);
            this.stdAmp += Math.pow(sample.amplitude - this.avgAmp, 2);
        }
        this.stdFreq = Math.sqrt(this.stdFreq / getSampleCount());
        this.stdAmp = Math.sqrt(this.stdAmp / getSampleCount());

        // Recalculate the average while ignoring any values outside 1 standard deviation
        // If the min and max fall within this range, we can skip this step
        double minFreq = this.avgFreq - this.stdFreq;
        double maxFreq = this.avgFreq + this.stdFreq;
        if (this.minFreq < minFreq || this.maxFreq > maxFreq) {
            this.avgFreqStd = 0;
            int count = 0;
            for (FrequencySample sample : this.samples) {
                if (sample.frequency >= minFreq && sample.frequency <= maxFreq) {
                    this.avgFreqStd += sample.frequency;
                    count++;
                }
            }
            this.avgFreqStd /= count;
        } else
            this.avgFreqStd = this.avgFreq;

        // Tune to nearest MIDI note
        this.tunedFreq = Misc.autoTune(this.avgFreqStd);
    }

    /**
     * Split frequency stats by periods of silence based on the interval
     * @return Frequency stats list
     */
    public List<FrequencyStats> splitBySilence() {
        List<FrequencyStats> ret = new ArrayList<>();
        FrequencyStats stats = new FrequencyStats();
        FrequencySample last = null;
        double interval = this.interval + Misc.EPSILON;
        for (int i = 0; i <= getSampleCount(); i++) {
            FrequencySample sample = getSample(i);
            if (last != null && (sample == null || sample.time - last.time > interval)) {
                stats.interval = this.interval;
                stats.update();
                ret.add(stats);
                stats = new FrequencyStats();
            }
            if (sample != null)
                stats.add(sample);
            last = sample;
        }
        return ret;
    }

    public List<FrequencySample> getAmplitudePeaks() {
        List<FrequencySample> peaks = new ArrayList<>();
        FrequencySample peakMax = null;
        for (FrequencySample s : this) {
            if (s.amplitude > avgAmp + stdAmp) {
                if (peakMax == null || s.amplitude > peakMax.amplitude)
                    peakMax = s;
            } else if (peakMax != null) {
                peaks.add(peakMax);
                peakMax = null;
            }
        }
        if (peakMax != null)
            peaks.add(peakMax);
        return peaks;
    }

    /**
     * Get frequency per wav sample
     * Frequencies in between stat samples are interpolated linearly
     * @param sampleRate Sample rate (frames per second)
     * @param startFrame Start frame
     * @param endFrame End frame
     * @return The frequency at each frame
     */
    public double[] getFrequencyPerFrame(int sampleRate, int startFrame, int endFrame) {
        return frameInterpolation(true, sampleRate, startFrame, endFrame);
    }

    public double[] getFrequencyPerFrame(int sampleRate) {
        return frameInterpolation(true, sampleRate);
    }

    /**
     * Get amplitude per wav sample
     * Amplitudes in between stat samples are interpolated linearly
     * @param sampleRate Sample rate (frames per second)
     * @param startFrame Start frame
     * @param endFrame End frame
     * @return The amplitude at each frame
     */
    public double[] getAmplitudePerFrame(int sampleRate, int startFrame, int endFrame) {
        return frameInterpolation(false, sampleRate, startFrame, endFrame);
    }

    public double[] getAmplitudePerFrame(int sampleRate) {
        return frameInterpolation(false, sampleRate);
    }

    private double[] frameInterpolation(boolean interpFreq, int sampleRate, int startFrame, int endFrame) {
        if (isEmpty())
            return new double[0];

        // Make sure samples are sorted by time
        sortByTime();

        int numFrames = (int) Math.round(sampleRate * this.duration);
        endFrame = startFrame + Math.min(endFrame - startFrame, numFrames);
        double[] ret = new double[endFrame - startFrame];

        double interval = this.interval + Misc.EPSILON;
        for (int i = 0; i < getSampleCount(); i++) {
            FrequencySample cur = getSample(i);
            FrequencySample next = getSample(i + 1);
            int frame1 = (int) Math.floor(cur.time * sampleRate) - startFrame;
            int frame2 = (int) Math.floor((cur.time + this.interval) * sampleRate) - startFrame;
            double value1 = interpFreq ? cur.frequency : cur.amplitude;
            double value2 = next != null && next.time <= cur.time + interval
                    ? (interpFreq ? next.frequency : next.amplitude)
                    : value1;
            int f1 = Math.max(frame1, 0);
            int f2 = Math.min(frame2, ret.length);
            if (value1 == value2 && f2 > f1)
                Arrays.fill(ret, f1, f2, value1);
            else {
                int frameLen = frame2 - frame1;
                for (int f = f1; f < f2; f++) {
                    double interp = (double) (f - frame1) / frameLen;
                    ret[f] = (value1 * (1 - interp)) + (value2 * interp);
                }
            }
        }

        return ret;
    }

    private double[] frameInterpolation(boolean interpFreq, int sampleRate) {
        if (isEmpty())
            return new double[0];
        sortByTime();
        FrequencySample first = this.samples.get(0);
        int startFrame = (int) Math.round(first.time * sampleRate);
        int numFrames = (int) Math.round(this.duration * sampleRate);
        return frameInterpolation(interpFreq, sampleRate, startFrame, startFrame + numFrames);
    }

    /**
     * Sort frequency samples by their time code if necessary
     */
    private void sortByTime() {
        if (this.needsTimeSort) {
            this.samples.sort(SORT_TIME);
            this.needsTimeSort = false;
        }
    }

    @Override
    public String toString() {
        return "Min = " + Math.round(this.minFreq)
                + " | Max = " + Math.round(this.maxFreq)
                + " | Average = " + Math.round(this.avgFreq)
                + (this.avgFreqStd != this.avgFreq ? " (STD: " + Math.round(this.avgFreqStd) + ")" : "")
                + " | Tuned = " + (float) tunedFreq
                + " | Samples = " + getSampleCount();
    }

    @Override
    public Iterator<FrequencySample> iterator() {
        return this.samples.iterator();
    }

    public FrequencyStats(SerializableInputStream is) throws IOException {
        this.duration = is.readDouble();
        this.interval = is.readDouble();
        this.minFreq = is.readShort();
        this.maxFreq = is.readShort();
        this.avgFreq = readFrequency(is);
        this.avgFreqStd = readFrequency(is);
        this.stdFreq = readFrequency(is);
        this.minAmp = readAmplitude(is);
        this.maxAmp = readAmplitude(is);
        this.avgAmp = readAmplitude(is);
        this.stdAmp = readAmplitude(is);
        int numSamples = is.readInt();
        this.samples = new ArrayList<>(numSamples);
        for (int i = 0; i < numSamples; i++) {
            double frequency = readFrequency(is);
            double amplitude = readAmplitude(is);
            double time = is.readInt() * this.interval;
            this.samples.add(new FrequencySample(frequency, amplitude, time));
        }
    }

    /**
     * Write these frequency stats to a byte stream
     * @param os Serializable output stream
     * @throws IOException Something went wrong
     */
    public void writeToStream(SerializableOutputStream os) throws IOException {
        os.writeDouble(this.duration);
        os.writeDouble(this.interval);
        os.writeShort((short) Math.floor(this.minFreq));
        os.writeShort((short) Math.ceil(this.maxFreq));
        writeFrequency(os, this.avgFreq);
        writeFrequency(os, this.avgFreqStd);
        writeFrequency(os, this.stdFreq);
        writeAmplitude(os, this.minAmp);
        writeAmplitude(os, this.maxAmp);
        writeAmplitude(os, this.avgAmp);
        writeAmplitude(os, this.stdAmp);
        os.writeInt(this.samples.size());
        for (FrequencySample s : this.samples) {
            writeFrequency(os, s.frequency);
            writeAmplitude(os, s.amplitude);
            os.writeInt((int) Math.round(s.time / this.interval));
        }
    }

    /**
     * Write these frequency stats to a binary file for later use
     * @param file File
     */
    public void writeToFile(File file) {
        try (SerializableOutputStream os = new SerializableOutputStream(new FileOutputStream(file))) {
            writeToStream(os);
        } catch (Exception e) {
            Log.e("Failed to write stats to " + file, e);
        }
    }

    private void writeFrequency(SerializableOutputStream os, double freq) throws IOException {
        if (minFreqFloor == -1) {
            minFreqFloor = (int) Math.floor(minFreq);
            maxFreqCeil = (int) Math.ceil(maxFreq);
            freqRange = maxFreqCeil - minFreqFloor;
        }
        int sh = (int) Math.round(((freq - minFreqFloor) / freqRange) * MAX_USHORT);
        if (sh > Short.MAX_VALUE)
            sh -= MAX_USHORT;
        os.writeShort((short) sh);
    }

    private double readFrequency(SerializableInputStream is) throws IOException {
        if (minFreqFloor == -1) {
            minFreqFloor = (int) Math.floor(minFreq);
            maxFreqCeil = (int) Math.ceil(maxFreq);
            freqRange = maxFreqCeil - minFreqFloor;
        }
        int sh = is.readShort();
        if (sh < 0)
            sh += MAX_USHORT;
        return minFreqFloor + (((double) sh / MAX_USHORT) * freqRange);
    }

    private void writeAmplitude(SerializableOutputStream os, double amp) throws IOException {
        int b = (int) Math.round(amp * MAX_UBYTE);
        if (b > Byte.MAX_VALUE)
            b -= MAX_UBYTE;
        os.write(b);
    }

    private double readAmplitude(SerializableInputStream is) throws IOException {
        int b = is.read();
        if (b < 0)
            b += MAX_UBYTE;
        return (double) b / MAX_UBYTE;
    }
}
