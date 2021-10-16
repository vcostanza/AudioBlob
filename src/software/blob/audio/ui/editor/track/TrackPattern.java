package software.blob.audio.ui.editor.track;

import org.json.JSONObject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.audio.util.IDGenerator;

import java.util.Objects;

/**
 * Pattern instance within a track
 * For the "master" version of a pattern see {@link Pattern}
 */
public class TrackPattern implements IDrawBounds {

    private static final IDGenerator ID_GEN = new IDGenerator();

    // Instance ID
    public transient final long id = ID_GEN.createID();

    // Pattern ID
    public final long patternID;

    // Note value where the pattern starts
    public int startNote;

    // Time the pattern starts (in seconds)
    public double startTime;

    // Master pattern instance
    public transient Pattern pattern;

    private final transient int hash;

    public TrackPattern(long patternID) {
        this.patternID = patternID;
        this.hash = Objects.hash(this.patternID, this.id);
    }

    public TrackPattern(Pattern pattern) {
        this(pattern.id);
        this.pattern = pattern;
    }

    public TrackPattern(JSONObject json) {
        this(json.getLong("patternID"));
        this.startNote = json.getInt("startNote");
        this.startTime = json.getDouble("startTime");
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("patternID", this.patternID);
        json.put("startNote", this.startNote);
        json.put("startTime", this.startTime);
        return json;
    }

    /**
     * Transform a note to the proper offset
     * Assumes the note is part of {@link #pattern}
     * @param src Note to transform
     * @param dst Note to store transform result
     */
    public void transformNote(MidiNote src, MidiNote dst) {
        if (pattern == null)
            dst.set(src.value, src.velocity, src.time);
        dst.set(startNote + (src.value - pattern.minNote), src.velocity, startTime + src.time);
        dst.setVelocityRange(src.minVelocity, src.maxVelocity);
    }

    @Override
    public double getMinTime() {
        return startTime;
    }

    @Override
    public double getMaxTime() {
        return startTime + (pattern != null ? pattern.duration : 0);
    }

    @Override
    public double getMinNote() {
        return startNote - AudioEditor.NOTE_THRESHOLD;
    }

    @Override
    public double getMaxNote() {
        return startNote + (pattern != null ? pattern.noteRange : 0) + AudioEditor.NOTE_THRESHOLD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackPattern that = (TrackPattern) o;
        return id == that.id && patternID == that.patternID;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }
}
