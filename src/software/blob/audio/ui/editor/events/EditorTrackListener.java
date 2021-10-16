package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.track.Track;

/**
 * Events related to tracks
 */
public interface EditorTrackListener {

    /**
     * A track has been added
     * @param track Track that was added
     */
    default void onTrackAdded(Track track) {
    }

    /**
     * A track has been removed
     * @param track Track that was removed
     */
    default void onTrackRemoved(Track track) {
    }

    /**
     * A track has been selected
     * @param track The track that was selected
     */
    default void onTrackSelected(Track track) {
    }

    /**
     * Track volume has been changed
     * @param track Track
     */
    default void onTrackVolumeChanged(Track track) {
    }

    /**
     * Track visibility has been changed
     * @param track Track
     */
    default void onTrackVisibilityChanged(Track track) {
    }
}
