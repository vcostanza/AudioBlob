package software.blob.audio.thread.callback;

import java.util.List;

/**
 * Lambda-friendly callback for {@link #onFinished(List)}
 */
public interface FinishCallback extends TaskCallback {

    default boolean onProgress(int prog, int max) {
        return true;
    }
}
