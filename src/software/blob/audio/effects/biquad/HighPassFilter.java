package software.blob.audio.effects.biquad;

import software.blob.audio.wave.WavData;

/**
 * Filter for removing frequencies below a certain threshold
 */
public class HighPassFilter extends BiQuadFilter {

    public HighPassFilter(double cutoffFrequency, PoleType poleType) {
        setCutoffFrequency(cutoffFrequency);
        setPoleType(poleType);
    }

    public HighPassFilter() {
        this(100, PoleType.TWO);
    }

    @Override
    public WavData process(WavData input) {
        WavData output = new WavData(input);
        applyFilter(output, PassType.HIGH_PASS);
        return output;
    }
}
