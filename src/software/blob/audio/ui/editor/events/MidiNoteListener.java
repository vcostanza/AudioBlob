package software.blob.audio.ui.editor.events;

import software.blob.audio.playback.AudioHandle;
import software.blob.audio.ui.editor.instruments.InstrumentSample;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.track.Track;

import java.util.Collection;

/**
 * Event listener interface for MIDI notes
 */
public interface MidiNoteListener {

    /**
     * MIDI notes have been added to a track
     * @param track Track
     * @param notes MIDI notes
     */
    default void onMidiNotesAdded(Track track, Collection<MidiNote> notes) {
    }

    /**
     * MIDI notes have been removed from a track
     * @param track Track
     * @param notes MIDI notes
     */
    default void onMidiNotesRemoved(Track track, Collection<MidiNote> notes) {
    }

    /**
     * MIDI note was played
     * @param note Note that was played
     * @param sample Instrument sample that was used
     * @param handle Queued audio handle
     */
    default void onMidiNotePlayed(MidiNote note, InstrumentSample sample, AudioHandle handle) {
    }
}
