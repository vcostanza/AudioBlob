package software.blob.audio.effects;

import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

/**
 * An audio effect that can be performed on a track with a variable number of channels
 * Automatically handles channel join after processing is finished for each channel
 */
public abstract class MultiChannelEffect extends AudioEffect {

    @Override
    public WavData process(WavData input) {

        // Process each track separately
        WavData[] tracks = new WavData[input.channels];
        for (int c = 0; c < input.channels; c++) {
            tracks[c] = processChannel(input, c);
            if (tracks[c] == null) {
                Log.e("Failed to process null track #" + c);
                return null;
            }
        }

        // Mix tracks into single output
        return WavData.joinByChannel(tracks);
    }

    /**
     * Process a specific channel of a WAV
     * @param input Input WAV
     * @param channel Channel to process
     * @return Processed channel (as its own separate WAV data)
     */
    protected abstract WavData processChannel(WavData input, int channel);
}
