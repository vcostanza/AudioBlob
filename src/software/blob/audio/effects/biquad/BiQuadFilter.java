package software.blob.audio.effects.biquad;

import software.blob.audio.effects.AudioEffect;
import software.blob.audio.wave.WavData;

/**
 * High/low pass filter base class
 * Converted from https://github.com/naudio/NAudio/blob/master/NAudio.Core/Dsp/BiQuadFilter.cs
 */
public abstract class BiQuadFilter extends AudioEffect {

    protected enum PassType {
        LOW_PASS,
        HIGH_PASS
    }

    // Default bandwidth values per roll-off
    public enum PoleType {
        TWO(1, 0.7071),
        FOUR(2, 0.60492333, 1.33722126),
        SIX(3, 0.58338080, 0.75932572, 1.95302407),
        EIGHT(4, 0.57622191, 0.66045510, 0.94276399, 2.57900101);

        final int passes;
        final double[] bandwidth;

        PoleType(int passes, double... bandwidth) {
            this.passes = passes;
            this.bandwidth = bandwidth;
        }
    }

    // Parameters
    protected double cutoffFrequency;
    protected PoleType pole;

    /**
     * Set the cutoff frequency
     * @param frequency Frequency
     */
    public void setCutoffFrequency(double frequency) {
        this.cutoffFrequency = frequency;
    }

    /**
     * Set the pole type for this filter
     * @param pole Pole type
     */
    public void setPoleType(PoleType pole) {
        this.pole = pole;
    }

    protected void applyFilter(WavData wav, PassType type) {
        for (int pass = 0; pass < pole.passes; pass++) {
            FilterInstance filter = setupFilter(type, wav.sampleRate, cutoffFrequency, pole.bandwidth[pass]);
            for (int c = 0; c < wav.channels; c++) {
                filter.reset();
                for (int f = 0; f < wav.numFrames; f++)
                    wav.samples[c][f] = filter.transform(wav.samples[c][f]);
            }
        }
    }

    protected FilterInstance setupFilter(PassType type, int sampleRate, double cutoffFrequency, double q) {
        double w0 = 2 * Math.PI * cutoffFrequency / sampleRate;
        double cosw0 = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * q);

        double b0, b1, b2;
        double aa0 = 1 + alpha;
        double aa1 = -2 * cosw0;
        double aa2 = 1 - alpha;

        switch (type) {
            case LOW_PASS:
                b0 = (1 - cosw0) / 2;
                b1 = 1 - cosw0;
                b2 = (1 - cosw0) / 2;
                break;
            case HIGH_PASS:
                b0 = (1 + cosw0) / 2;
                b1 = -(1 + cosw0);
                b2 = (1 + cosw0) / 2;
                break;
            default:
                return null;
        }

        return new FilterInstance(aa0, aa1, aa2, b0, b1, b2);
    }

    protected static class FilterInstance {

        // coefficients
        private final double a0;
        private final double a1;
        private final double a2;
        private final double a3;
        private final double a4;

        // state
        private double x1;
        private double x2;
        private double y1;
        private double y2;

        protected FilterInstance(double aa0, double aa1, double aa2, double b0, double b1, double b2) {
            a0 = b0/aa0;
            a1 = b1/aa0;
            a2 = b2/aa0;
            a3 = aa1/aa0;
            a4 = aa2/aa0;
        }

        protected void reset() {
            x1 = x2 = y1 = y2 = 0;
        }

        protected double transform(double inSample) {
            // compute result
            double result = a0 * inSample + a1 * x1 + a2 * x2 - a3 * y1 - a4 * y2;

            // shift x1 to x2, sample to x1
            x2 = x1;
            x1 = inSample;

            // shift y1 to y2, result to y1
            y2 = y1;
            y1 = result;

            return y1;
        }
    }
}
