package software.blob.audio.effects.sbsms;

import software.blob.audio.thread.WavProcessorTask;
import software.blob.audio.wave.WavData;

/**
 * Processor task that hooks up to {@link SBSMSEffect}
 */
public class SBSMSTask extends WavProcessorTask {

    private final WavData input;
    private final SBSMSEffect effect;

    public SBSMSTask(WavData input, SBSMSEffect effect) {
        this.input = input;
        this.effect = effect;
    }

    @Override
    public WavData process() {
        return this.effect.process(this.input);
    }
}
