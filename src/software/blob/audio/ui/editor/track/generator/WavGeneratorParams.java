package software.blob.audio.ui.editor.track.generator;

import java.util.HashSet;
import java.util.Set;

/**
 * Parameters for generating WAV data
 */
public class WavGeneratorParams {

    // Start time (on the timeline) to begin WAV
    public double startTime;

    // End time (on the timeline) to end WAV
    public double endTime;

    // Number of output channels
    public int channels = 2;

    // Output sample rate
    public int sampleRate = 44100;

    // Flag output as loopable
    public boolean loop;

    // Ignore muted tracks
    public boolean ignoreMuted;

    // Specific layers to exclude
    public final Set<WavGeneratorLayer> excludeLayers = new HashSet<>();

    /**
     * Check that all the parameters are acceptable
     * @return True if valid
     */
    public boolean isValid() {
        return startTime >= 0 && endTime > startTime && channels > 0 && sampleRate > 0;
    }

    /**
     * Get the desired duration of the output WAV
     * @return Duration in seconds
     */
    public double getDuration() {
        return this.endTime - this.startTime;
    }
}
