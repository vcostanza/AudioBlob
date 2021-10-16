package software.blob.audio.thread.callback;

/**
 * A task callback with attached progress message
 */
public interface MessageCallback extends TaskCallback {

    /**
     * Set the message to be displayed during progress
     * @param message Message
     */
    void setMessage(String message);
}
