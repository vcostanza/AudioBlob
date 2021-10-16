package software.blob.audio.ui.editor.midi;

/**
 * The bounds of a set of MIDI notes
 */
public class MidiNoteBounds {

    public double minTime, maxTime;
    public int minNote, maxNote;

    public MidiNoteBounds(double minTime, double maxTime, int minNote, int maxNote) {
        this.minTime = minTime;
        this.maxTime = maxTime;
        this.minNote = minNote;
        this.maxNote = maxNote;
    }

    /**
     * Add a note to the bounds
     * @param note Note to add
     */
    void add(MidiNote note) {
        minTime = Math.min(minTime, note.time);
        maxTime = Math.max(maxTime, note.time);
        minNote = Math.min(minNote, note.value);
        maxNote = Math.max(maxNote, note.value);
    }
}
