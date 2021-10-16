package software.blob.audio.ui.editor.track;

import org.json.JSONObject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.midi.MidiNoteBounds;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;

/**
 * A pattern is a series of notes and pitch curves that are
 * commonly used more than once in one or more tracks
 * See {@link TrackPattern}
 */
public class Pattern {

    // Name of this pattern
    public String name;

    // Pattern ID
    public final long id;

    // Pitch curves
    public PitchCurveList curves;

    // MIDI notes (piano roll)
    public MidiNoteList notes;

    // Duration of the pattern in seconds
    // Patterns may be longer than their content
    public double duration;

    // Min and max note value in this pattern
    public transient int minNote, maxNote, noteRange;

    public Pattern() {
        this(-1);
    }

    public Pattern(long id) {
        this.id = id;
    }

    public Pattern(long id, Pattern other) {
        this(id);
        this.name = other.name;
        this.notes = other.notes;
        this.curves = other.curves;
        update();
    }

    public Pattern(JSONObject json) {
        this(json.optLong("id", 0L));

        this.name = json.optString("name", "Untitled");

        if (json.has("notes"))
            this.notes = new MidiNoteList(json.getJSONArray("notes"));

        if (json.has("curves"))
            this.curves = new PitchCurveList(json.getJSONArray("curves"));

        if (json.has("duration"))
            this.duration = json.optDouble("duration", 0);

        update();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        json.put("id", this.id);
        json.putOpt("name", this.name);
        json.put("duration", this.duration);

        if (this.notes != null && !this.notes.isEmpty())
            json.put("notes", this.notes.toJSON());

        if (this.curves != null && !this.curves.isEmpty())
            json.put("curves", this.curves.toJSON());

        return json;
    }

    /**
     * Update the duration and note range of this pattern based on its contents
     */
    public void update() {
        double duration = 0;
        minNote = AudioEditor.MAX_NOTE;
        maxNote = AudioEditor.MIN_NOTE;
        if (notes != null && !notes.isEmpty()) {
            MidiNoteBounds bounds = notes.getBounds();
            duration = Math.max(duration, bounds.maxTime + AudioEditor.NOTE_DURATION);
            minNote = Math.min(minNote, bounds.minNote);
            maxNote = Math.max(maxNote, bounds.maxNote);
        }
        if (curves != null && !curves.isEmpty()) {
            duration = Math.max(duration, curves.getMaxTime());
            minNote = Math.min(minNote, (int) Math.floor(curves.getMinNote()));
            maxNote = Math.max(maxNote, (int) Math.ceil(curves.getMinNote()));
        }
        if (duration > this.duration)
            this.duration = duration;
        noteRange = maxNote - minNote;
    }
}
