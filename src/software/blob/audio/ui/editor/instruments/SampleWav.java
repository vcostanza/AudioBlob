package software.blob.audio.ui.editor.instruments;

import software.blob.audio.wave.LockableWavData;
import software.blob.audio.wave.WavData;

/**
 * Read-only wav data that has cached peak amplitude
 */
public class SampleWav extends LockableWavData {

    private final double peakAmplitude;

    public SampleWav(WavData src) {
        super(src);
        this.peakAmplitude = src.getPeakAmplitude();
        lock();
    }

    @Override
    public double getPeakAmplitude(int startFrame, int endFrame) {
        return peakAmplitude;
    }
}
