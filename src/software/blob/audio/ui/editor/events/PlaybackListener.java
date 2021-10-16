package software.blob.audio.ui.editor.events;

/**
 * Playback event listener
 */
public interface PlaybackListener {

    /**
     * Playback has begun
     * @param timeCode Time code playback was started at
     */
    default void onPlaybackStarted(double timeCode) {
    }

    /**
     * Playback has stopped
     * @param timeCode Time code playback was stopped at
     */
    default void onPlaybackStopped(double timeCode) {
    }

    /**
     * Time code has changed
     * @param timeCode Current time code
     */
    default void onPlaybackTimeChanged(double timeCode) {
    }
}
