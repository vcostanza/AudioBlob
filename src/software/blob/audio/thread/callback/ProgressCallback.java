package software.blob.audio.thread.callback;

import software.blob.audio.wave.WavData;

import java.util.List;

/**
 * Lambda-friendly callback for {@link #onProgress(int, int)}
 */
public interface ProgressCallback extends TaskCallback {

    default void onFinished(List<WavData> results) {
    }
}
