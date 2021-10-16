package software.blob.audio.effects.volume;

import software.blob.audio.effects.AudioEffect;
import software.blob.audio.wave.WavData;

/**
 * Fades audio from one peak amplitude to another
 */
public class FadeEffect extends AudioEffect {

    // The "shape" of the fade curve
    public enum Shape {
        LINEAR, QUADRATIC, SINE
    }

    private double startTime, endTime, duration;
    private double startFactor, endFactor;
    private Shape shape;

    public FadeEffect(double startFactor, double endFactor, double duration, Shape shape) {
        setFadeFactors(startFactor, endFactor);
        setFadePositions(Double.NaN, Double.NaN);
        setFadeDuration(duration);
        setFadeShape(shape);
    }

    public FadeEffect(double startFactor, double endFactor, double duration) {
        this(startFactor, endFactor, duration, Shape.LINEAR);
    }

    public FadeEffect() {
        // Fade out by default
        this(1, 0, 1);
    }

    /**
     * Set the range of time this fade effects
     * Note: This overrides {@link #setFadeDuration(double)}
     * @param startTime Start time in seconds (NaN to unset)
     * @param endTime End time in seconds (NaN to unset)
     */
    public void setFadePositions(double startTime, double endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void setFadePosition(double startTime) {
        setFadePositions(startTime, Double.NaN);
    }

    /**
     * Set the duration of the fade
     * The position of the fade is automatic based on the values of {@link #setFadeFactors(double, double)}
     * Note: This only works when {@link #setFadePositions(double, double)} is unset
     * @param duration Duration of the fade in seconds
     */
    public void setFadeDuration(double duration) {
        this.duration = duration;
    }

    /**
     * Set fade start and end factor
     * @param startFactor Start multiplicative factor
     * @param endFactor End multiplicative factor
     */
    public void setFadeFactors(double startFactor, double endFactor) {
        this.startFactor = startFactor;
        this.endFactor = endFactor;
    }

    /**
     * Set the shape of the fade (linear by default)
     * @param shape Fade {@link Shape}
     */
    public void setFadeShape(Shape shape) {
        this.shape = shape;
    }

    @Override
    public WavData process(WavData input) {

        // Check if fade position needs to be set
        double startTime = this.startTime, endTime = this.endTime;
        if (Double.isNaN(startTime)) {
            if (Double.isNaN(endTime)) {
                // Both start and end are invalid - default to the start/end of the clip
                // based on the direction of the fade
                if (startFactor < endFactor) {
                    // Fade in clip
                    startTime = 0;
                    endTime = duration;
                } else {
                    // Fade out clip
                    startTime = input.duration - duration;
                    endTime = input.duration;
                }
            } else
                startTime = endTime - duration;
        } else if (Double.isNaN(endTime))
            endTime = startTime + duration;

        // Nothing to do
        if (startTime >= input.duration || endTime <= 0)
            return input;

        // Copy input data
        WavData output = new WavData(input);

        int startFrame = output.getFrame(startTime);
        int endFrame = output.getFrame(endTime);
        int numFrames = endFrame - startFrame;

        int loopStart = Math.max(0, startFrame);
        int loopEnd = Math.min(output.numFrames, endFrame);

        // Multiply amplitude by fade factor at each frame
        for (int i = loopStart; i < loopEnd; i++) {
            double framePct = (double) (i - startFrame) / numFrames;
            switch (shape) {
                case LINEAR:
                    // Percentage is already linear; nothing to do
                    break;
                case QUADRATIC:
                    if (startFactor < endFactor)
                        framePct = 1 - ((1 - framePct) * (1 - framePct));
                    else
                        framePct *= framePct;
                    break;
                case SINE:
                    framePct = Math.sin(framePct * (Math.PI / 2));
                    break;
            }
            for (int c = 0; c < output.channels; c++)
                output.samples[c][i] *= (startFactor * (1 - framePct)) + (endFactor * framePct);
        }

        return output;
    }
}
