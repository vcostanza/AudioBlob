package software.blob.audio.effects.volume;

import software.blob.audio.effects.AudioEffect;
import software.blob.audio.wave.WavData;

/**
 * Modulate amplitude given amplitude multipliers
 */
public class AmplitudeModulator extends AudioEffect {

    private double[][] ampFactors;

    /**
     * Set the array of amplitude factors
     * Each sample in the input is multiplied by the matching factor
     * @param ampFactors Amplitude factors[channel][frame]
     */
    public void setAmplitudeFactors(double[][] ampFactors) {
        this.ampFactors = ampFactors;
    }

    public void setAmplitudeFactors(double[] ampFactors) {
        double[][] channelAmpFactors = new double[1][ampFactors.length];
        System.arraycopy(ampFactors, 0, channelAmpFactors[0], 0, ampFactors.length);
        setAmplitudeFactors(channelAmpFactors);
    }

    @Override
    public WavData process(WavData input) {
        final WavData output = new WavData(input);
        input.forEachSample((c, f, amp) -> {
            int ac = Math.min(c, ampFactors.length - 1);
            int af = Math.min(f, ampFactors[ac].length - 1);
            output.samples[c][f] *= ampFactors[ac][af];
            return true;
        });
        return output;
    }
}
