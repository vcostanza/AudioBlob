package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.track.Track;

/**
 * Listener for when an instrument on a track has been changed
 */
public interface EditorInstrumentListener {

    /**
     * The instrument for a given track has been changed
     * @param track Track
     * @param instrument Instrument
     */
    void onInstrumentChanged(Track track, Instrument instrument);
}
