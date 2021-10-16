package software.blob.audio.audacity.frequency;

import software.blob.audio.audacity.fft.FFT;
import software.blob.audio.thread.WavProcessorService;
import software.blob.audio.thread.WavProcessorTask;
import software.blob.audio.thread.callback.ProgressCallback;
import software.blob.audio.wave.SnippetExtractor;
import software.blob.audio.wave.WavData;
import software.blob.audio.wave.WavSnippet;
import software.blob.audio.util.Misc;
import software.blob.ui.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Detect frequency range of an audio clip
 * Partially adapted from https://github.com/audacity/audacity/blob/master/src/effects/ChangePitch.cpp
 */
public class FrequencyReader {

    // Scan window in seconds
    private double scanWindow;

    // Minimum amplitude cutoff
    private double minAmp;

    // Frequency cutoff
    private double minFreq, maxFreq;

    // Frequency multiplier
    private double freqMulti;

    // Number of scans to perform
    private int numScans;

    // Fill empty spots with the last valid sample
    private boolean fillGaps;

    // Run multi-threaded
    private boolean multiThread;

    public FrequencyReader() {
        // Set defaults
        setMinimumAmplitude(0.025);
        setFrequencyRange(0, Double.MAX_VALUE);
        setFrequencyMultiplier(1);
        setScanWindow(0.2);
        setNumScans(8);
        setFillGaps(false);
        setMultiThreaded(false);
    }

    /**
     * Set the size of the scan window
     * @param scanWindow Scan window in seconds
     */
    public void setScanWindow(double scanWindow) {
        this.scanWindow = scanWindow;
    }

    /**
     * Set the minimum amplitude cutoff
     * Any audio quieter than this amplitude will be ignored
     * @param minAmp Minimum amplitude
     */
    public void setMinimumAmplitude(double minAmp) {
        this.minAmp = minAmp;
    }

    /**
     * Set the frequency range to consider valid during scan
     * @param minFreq Minimum frequency (default = 0)
     * @param maxFreq Maximum frequency (default = {@link Double#MAX_VALUE})
     */
    public void setFrequencyRange(double minFreq, double maxFreq) {
        this.minFreq = minFreq;
        this.maxFreq = maxFreq;
    }

    /**
     * Set multiplication factor for output frequencies
     * @param multiplier Multiplier factor (default = 1)
     */
    public void setFrequencyMultiplier(double multiplier) {
        this.freqMulti = multiplier;
    }

    /**
     * Set the number of scans to perform
     * Higher values means smoother results
     * @param numScans Number of scans to perform (minimum = 1)
     */
    public void setNumScans(int numScans) {
        this.numScans = Math.max(1, numScans);
    }

    /**
     * Whether to fill empty gaps with the last valid sample
     * @param fillGaps True to fill gaps
     */
    public void setFillGaps(boolean fillGaps) {
        this.fillGaps = fillGaps;
    }

    /**
     * Whether to use multiple threads to read frequencies
     * @param multiThread True to enable multi-threaded reading
     */
    public void setMultiThreaded(boolean multiThread) {
        this.multiThread = multiThread;
    }

    /**
     * Process audio data for frequency statistics
     * @param data Audio to process
     * @param channel Channel to process
     * @return Frequency statistics
     */
    public FrequencyStats read(WavData data, int channel) {
        FrequencyStats stats = new FrequencyStats();
        double interval = stats.interval = scanWindow / numScans;

        double endTime = data.duration - scanWindow;
        if (endTime < 0)
            return stats;

        List<FrequencySample> results;
        if (multiThread) {
            WavProcessorService service = new WavProcessorService();
            double timePerThread = endTime / service.getNumThreads();
            int loopsPerThread = (int) Math.ceil(timePerThread / interval);
            timePerThread = loopsPerThread * interval;
            int numTasks = (int) Math.ceil(endTime / timePerThread);
            List<ReadTask> tasks = new ArrayList<>();
            for (int i = 0; i < numTasks; i++) {
                double start = i * timePerThread;
                double end = (i + 1) * timePerThread;
                end = Math.min(end, endTime);
                if (endTime - start < stats.interval)
                    break;
                tasks.add(new ReadTask(data, channel, start, end, interval));
            }
            service.execute(tasks);
            results = new ArrayList<>();
            for (ReadTask t : tasks)
                results.addAll(t.results);
        } else {
            // Single-threaded task
            ReadTask task = new ReadTask(data, channel, 0, endTime + interval, interval);
            task.process();
            results = task.results;
        }

        // Duplicate last sample to fill up scan window
        if (!results.isEmpty()) {
            FrequencySample last = results.get(results.size() - 1);
            for (int i = 1; i < numScans; i++)
                results.add(new FrequencySample(last.frequency, last.amplitude, last.time + i * interval));
        }

        stats.addAll(results);
        stats.update();
        return stats;
    }

    public FrequencyStats read(WavData data) {
        return read(data, 0);
    }

    public FrequencyStats read(WavData data, SnippetExtractor extractor, ProgressCallback cb) {
        List<WavSnippet> snippets = extractor.splitBySilence(data);
        FrequencyStats stats = new FrequencyStats();
        stats.interval = scanWindow / numScans;
        int prog = 0;
        for (WavSnippet snippet : snippets) {
            //Log.d("Reading stats for snippet " + (prog + 1) + "/" + snippets.size());
            stats.add(read(snippet), snippet.startTime);
            prog++;
            if (cb != null && !cb.onProgress(prog, snippets.size()))
                return null;
        }
        stats.update();
        return stats;
    }

    public FrequencyStats read(WavData data, SnippetExtractor extractor) {
        return read(data, extractor, null);
    }

    /**
     * Read several audio clips for frequency stats
     * This method is always multi-threaded, with one task used per audio clip
     * @param clips Audio clips
     * @return List of matching frequency stats
     */
    public List<FrequencyStats> read(List<? extends WavData> clips) {
        double interval = scanWindow / numScans;
        List<ReadTask> tasks = new ArrayList<>(clips.size());
        for (WavData wav : clips)
            tasks.add(new ReadTask(wav, interval));
        new WavProcessorService().execute(tasks);
        List<FrequencyStats> results = new ArrayList<>();
        for (ReadTask t : tasks) {
            FrequencyStats stats = new FrequencyStats();
            stats.interval = interval;
            stats.addAll(t.results);
            stats.update();
            results.add(stats);
        }
        return results;
    }

    private class ReadTask extends WavProcessorTask {

        final List<FrequencySample> results = new ArrayList<>();
        final WavData data;
        final int channel;
        final double startTime, endTime, interval;

        ReadTask(WavData data, int channel, double startTime, double endTime, double interval) {
            this.data = data;
            this.channel = channel;
            this.startTime = startTime;
            this.endTime = endTime;
            this.interval = interval;
        }

        ReadTask(WavData data, double interval) {
            this(data, 0, 0, data.duration, interval);
        }

        @Override
        public WavData process() {
            double duration = this.endTime - this.startTime;
            int iterations = (int) Math.round(duration / interval);
            FrequencySample lastSample = null;
            for (int i = 0; i < iterations; i++) {
                double startTime = this.startTime + i * this.interval;
                double peakAmp = data.getPeakAmplitude(data.getFrame(startTime), data.getFrame(startTime + interval));
                double freq = peakAmp >= minAmp ? getFrequency(data, channel, startTime, scanWindow) : Double.NaN;
                if (Double.isNaN(freq) || freq < minFreq || freq > maxFreq) {
                    if (fillGaps && lastSample != null) {
                        lastSample = new FrequencySample(lastSample);
                        lastSample.time = startTime;
                        results.add(lastSample);
                    }
                    continue;
                }
                FrequencySample sample = new FrequencySample(freq * freqMulti, peakAmp, startTime);
                results.add(sample);
                lastSample = sample;
            }
            return null;
        }
    }

    private double getFrequency(WavData wav, int channel, double startTime, double scanTime) {
        if (startTime >= wav.duration)
            return Double.NaN;

        double rate = wav.sampleRate;

        // Auto-size window -- high sample rates require larger windowSize.
        // Aim for around 2048 samples at 44.1 kHz (good down to about 100 Hz).
        // To detect single notes, analysis period should be about 0.2 seconds.
        // windowSize must be a power of 2.

        int windowSize;
        if (rate == 44100) // Most common sample rate - save some calc time
            windowSize = 4096;
        else
            windowSize = Math.max(256, (int) Math.round(Math.pow(2.0, Math.floor(Misc.log2(rate / 20.0) + 0.5))));
        int windowSizeH = windowSize / 2;

        // Default to 0.2 seconds
        if (Double.isNaN(scanTime))
            scanTime = 0.2;

        // Number of windows based on scan time (4 by default)
        int numWindows = Math.max(1, (int) Math.round((scanTime * rate) / windowSize));
        //int numWindows = Math.max(1, (int) Math.round(rate / (5.0f * windowSize)));

        startTime = Math.min(Math.max(0, startTime), wav.duration);
        int startFrame = (int) (startTime * rate);

        double[] freq = new double[windowSizeH];
        double[] freqa = new double[windowSizeH];

        FFT fft = FFT.get(windowSize);

        int srcPos = startFrame;
        int windowsUsed = 0;
        for(int i = 0; i < numWindows && srcPos + windowSize < wav.numFrames; i++) {
            if (computeSpectrum(wav, channel, fft, srcPos, windowSize, freq, true)) {
                for (int j = 0; j < windowSizeH; j++)
                    freqa[j] += freq[j];
                windowsUsed++;
            }
            srcPos += windowSize;
        }

        if (windowsUsed < 1)
            return Double.NaN;

        int argmax = 0;
        for(int j = 1; j < windowSizeH; j++)
            if (freqa[j] > freqa[argmax])
                argmax = j;

        int lag = (windowSizeH - 1) - argmax;
        return rate / lag;
    }

    private static boolean computeSpectrum(WavData wav, int channel, FFT fft, int wavStart, int width, double[] output, boolean autocorrelation) {
        int windowSize = fft.length;
        if (width < windowSize)
            return false;

        double[] processed = new double[windowSize];

        int half = windowSize / 2;

        double[] in = new double[windowSize];
        double[] out = new double[windowSize];
        double[] out2 = new double[windowSize];

        int start = 0;
        int windows = 0;
        while (start + windowSize <= width) {
            System.arraycopy(wav.samples[channel], wavStart + start, in, 0, windowSize);

            //WindowFunc(windowFunc, windowSize, in);
            fft.hannWindowFunc(true, in);

            if (autocorrelation) {
                // Take FFT
                fft.apply(in, out, out2);
                // Compute power
                for (int i = 0; i < windowSize; i++)
                    in[i] = (out[i] * out[i]) + (out2[i] * out2[i]);

                // Tolonen and Karjalainen recommend taking the cube root
                // of the power, instead of the square root

                for (int i = 0; i < windowSize; i++)
                    in[i] = Math.pow(in[i], 1.0f / 3.0f);

                // Take FFT
                fft.apply(in, out, out2);
            }
            /*else
                PowerSpectrum(windowSize, in, out);*/

            // Take real part of result
            for (int i = 0; i < half; i++)
                processed[i] += out[i];

            start += half;
            windows++;
        }

        if (windows < 1)
            return false;

        if (autocorrelation) {

            // Peak Pruning as described by Tolonen and Karjalainen, 2000
              /*
               Combine most of the calculations in a single for loop.
               It should be safe, as indexes refer only to current and previous elements,
               that have already been clipped, etc...
              */
            for (int i = 0; i < half; i++) {
                // Clip at zero, copy to temp array
                if (processed[i] < 0.0)
                    processed[i] = 0f;
                out[i] = processed[i];
                // Subtract a time-doubled signal (linearly interp.) from the original
                // (clipped) signal
                if ((i % 2) == 0)
                    processed[i] -= out[i / 2];
                else
                    processed[i] -= ((out[i / 2] + out[i / 2 + 1]) / 2);

                // Clip at zero again
                if (processed[i] < 0.0)
                    processed[i] = 0f;
            }

            // Reverse and scale
            for (int i = 0; i < half; i++)
                in[i] = processed[i] / (windowSize / 4f);
            for (int i = 0; i < half; i++)
                processed[half - 1 - i] = in[i];
        } else {
            // Convert to decibels
            // But do it safely; -Inf is nobody's friend
            for (int i = 0; i < half; i++){
                double temp = (processed[i] / windowSize / windows);
                if (temp > 0.0)
                    processed[i] = 10 * Math.log10(temp);
                else
                    processed[i] = 0;
            }
        }

        System.arraycopy(processed, 0, output, 0, half);

        return true;
    }
}
