package software.blob.audio.ui.editor.track.generator;

import software.blob.audio.thread.WavProcessorTask;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.wave.WavData;

/**
 * Task used for generating layers in {@link WavGenerator}
 */
public abstract class WavGeneratorTask extends WavProcessorTask {

    /**
     * Generate snippet with the start time it should be placed in the output wav
     * @return WAV snippet (only {@link TrackWav#time} is read)
     */
    protected abstract TrackWav generate();

    @Override
    public final WavData process() {
        return generate();
    }
}
