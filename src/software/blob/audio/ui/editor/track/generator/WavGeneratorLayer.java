package software.blob.audio.ui.editor.track.generator;

import software.blob.audio.ui.editor.track.Track;

import java.util.List;

/**
 * Interface for generating a WAV "layer" that is mixed into the output
 */
public interface WavGeneratorLayer {

    /**
     * Get all sub-tasks to perform wav generation for this layer
     * @param track Track to generate tasks for
     * @param params Parameters for generating wav
     * @return List of tasks or null to skip
     */
    List<WavGeneratorTask> getGeneratorTasks(Track track, WavGeneratorParams params);
}
