package software.blob.audio.ui.editor.events;

/**
 * Recording event listener
 */
public interface RecordListener {

    /**
     * Recording has begun
     * @param timeCode Time code recording was started at
     */
    default void onRecordStarted(double timeCode) {
    }

    /**
     * Recording has stopped
     * @param timeCode Time code recording was stopped at
     */
    default void onRecordStopped(double timeCode) {
    }
}
