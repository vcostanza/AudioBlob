package software.blob.audio.ui.editor.track.generator;

import software.blob.audio.ui.editor.track.TrackWav;

import java.util.List;

/**
 * Callback fired by {@link WavGenerator} when WAV data has finished being generated
 */
public interface WavGeneratorCallback {

    /**
     * Wav data has been generated
     * @param results List of generated wav data
     * @param params Parameters used to generate this wav data
     */
    void onWavGenerated(List<TrackWav> results, WavGeneratorParams params);

    /**
     * Generator failed for whatever reason
     */
    void onFailed();
}
