package software.blob.audio.ui.editor.pitchcurve;

import software.blob.audio.ui.editor.view.DrawBounds;
import software.blob.audio.wave.WavData;

/**
 * Cached WAV data generated from a {@link PitchCurve)}
 */
public class PitchCurveWav extends WavData {

    private final long id;
    private final DrawBounds bounds;
    private final WavData src;
    private final double baseFreq, maxAmp;

    public PitchCurveWav(PitchCurve curve, WavData src, double baseFreq, double maxAmp, WavData dst) {
        super(dst);
        this.id = curve.id;
        this.bounds = new DrawBounds(curve);
        this.src = src;
        this.baseFreq = baseFreq;
        this.maxAmp = maxAmp;
    }

    public boolean equals(PitchCurve curve, WavData src, double baseFreq, double maxAmp) {
        return this.id == curve.id && this.src == src
                && Double.compare(this.baseFreq, baseFreq) == 0
                && Double.compare(this.maxAmp, maxAmp) == 0
                && this.bounds.equals(curve);
    }
}
