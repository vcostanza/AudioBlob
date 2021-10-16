package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackPattern;

import java.util.Collection;

/**
 * Listener for pattern events
 */
public interface EditorPatternListener {

    /**
     * Patterns have been added
     * @param patterns Patterns were added
     */
    default void onPatternsAdded(Collection<Pattern> patterns) {
    }

    /**
     * Pattern have been removed
     * @param patterns Patterns were removed
     */
    default void onPatternsRemoved(Collection<Pattern> patterns) {
    }

    /**
     * A pattern instance has been added to a track
     * @param track Track
     * @param patterns Track patterns
     */
    default void onTrackPatternsAdded(Track track, Collection<TrackPattern> patterns) {
    }

    /**
     * A pattern instance has been removed from a track
     * @param track Track
     * @param patterns Track patterns
     */
    default void onTrackPatternsRemoved(Track track, Collection<TrackPattern> patterns) {
    }
}
