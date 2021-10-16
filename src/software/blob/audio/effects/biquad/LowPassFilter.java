package software.blob.audio.effects.biquad;

import software.blob.audio.wave.WavData;

/**
 * Filter for removing frequencies above a certain threshold
 */
public class LowPassFilter extends BiQuadFilter {

    public LowPassFilter(double cutoffFrequency, PoleType poleType) {
        setCutoffFrequency(cutoffFrequency);
        setPoleType(poleType);
    }

    @Override
    public WavData process(WavData input) {
        WavData output = new WavData(input);
        applyFilter(output, PassType.LOW_PASS);
        return output;
    }
}
