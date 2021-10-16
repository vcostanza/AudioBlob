package software.blob.audio.thread;

import software.blob.audio.wave.WavData;

import java.util.concurrent.Callable;

/**
 * Generic task interface for processing audio
 */
public abstract class WavProcessorTask implements Callable<WavData> {

    private boolean _canceled;
    private OnTaskFinished _finishCallback;

    /**
     * Method that does the processing - sub-classes are required to override this
     * @return Processed audio clip
     */
    public abstract WavData process();

    /**
     * Signal this task to be canceled
     * Note: It's up to the sub-class to handle cancellation
     */
    public final void cancel() {
        _canceled = true;
    }

    public boolean isCanceled() {
        return _canceled;
    }

    /* PRIVATE */

    interface OnTaskFinished {
        void onFinish(WavProcessorTask task);
    }

    final void setFinishedCallback(OnTaskFinished callback) {
        _finishCallback = callback;
    }

    @Override
    public final WavData call() throws Exception {
        WavData wav = process();
        if (_finishCallback != null)
            _finishCallback.onFinish(this);
        return wav;
    }
}
