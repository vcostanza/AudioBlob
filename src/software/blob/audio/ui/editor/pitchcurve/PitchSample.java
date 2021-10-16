package software.blob.audio.ui.editor.pitchcurve;

import org.json.JSONObject;
import software.blob.audio.audacity.frequency.FrequencySample;
import software.blob.audio.ui.editor.EditorPoint;
import software.blob.audio.util.Misc;

/**
 * A sample in a {@link PitchCurve}
 */
public class PitchSample extends EditorPoint {

    public double amplitude;

    public PitchSample() {
    }

    public PitchSample(double time, double note, double amplitude) {
        set(time, note, amplitude);
    }

    public PitchSample(FrequencySample sample) {
        this(sample.time, Misc.getNoteValue(sample.frequency), sample.amplitude);
    }

    public PitchSample(PitchSample other) {
        set(other);
    }

    public void set(double time, double note, double amplitude) {
        super.set(time, note);
        this.amplitude = amplitude;
    }

    @Override
    public void set(double time, double note) {
        set(time, note, amplitude);
    }

    public void set(PitchSample other) {
        set(other.time, other.note, other.amplitude);
    }

    public PitchSample(JSONObject json) {
        this.note = json.getDouble("note");
        this.amplitude = json.getDouble("amplitude");
        this.time = json.getDouble("time");
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("note", this.note);
        json.put("amplitude", this.amplitude);
        json.put("time", this.time);
        return json;
    }

    @Override
    public String toString() {
        int midRounded = (int) Math.round(this.note);
        int note = midRounded % 12;
        String noteName = Misc.indexToNoteName(note);
        int octave = (midRounded / 12) - 1;
        int rem = (int) Math.round((this.note - midRounded) * 200);
        String remStr = (rem > 0 ? "+" : "") + rem + "%";
        return noteName + octave + " " + remStr + " @ " + time;
    }
}
