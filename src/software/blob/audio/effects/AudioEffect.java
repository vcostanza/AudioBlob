package software.blob.audio.effects;

import software.blob.audio.wave.WavData;

/**
 * Generic audio track effect
 */
public abstract class AudioEffect {

    /**
     * Process the given WAV input
     * @param input Input data
     * @return Output data or null if failed or N/A
     */
    public abstract WavData process(WavData input);
}
