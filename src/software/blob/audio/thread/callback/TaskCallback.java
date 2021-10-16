package software.blob.audio.thread.callback;

import software.blob.audio.thread.WavProcessorService;
import software.blob.audio.wave.WavData;

import java.util.List;

/**
 * Callback fired for tasks executed via {@link WavProcessorService}
 */
public interface TaskCallback {

    /**
     * Progress has incremented
     * @param prog Progress value
     * @param max Maximum progress value
     * @return True to continue processing other tasks, false to stop
     */
    boolean onProgress(int prog, int max);

    /**
     * Tasks have finished
     * @param results Results
     */
    void onFinished(List<WavData> results);
}
