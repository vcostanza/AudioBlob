package software.blob.audio.ui.editor.midi;

import org.json.JSONObject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.audio.util.IDGenerator;
import software.blob.audio.util.Misc;

import java.util.Comparator;
import java.util.Objects;

/**
 * A MIDI note with an associated time code and velocity
 */
public class MidiNote implements IDrawBounds {

    private static final IDGenerator ID_GEN = new IDGenerator();

    // Maximum velocity value
    public static final int MAX_VELOCITY = 127;

    // Sort notes by time
    public static final Comparator<MidiNote> SORT_TIME = Comparator.comparingDouble(o -> o.time);

    // Core values
    public int value;
    public double time;
    public byte velocity;

    // Optional velocity range
    public byte minVelocity, maxVelocity;

    // Used for editing
    public final transient long id = ID_GEN.createID();
    public final transient int hash = Objects.hash(id);

    public MidiNote() {
    }

    public MidiNote(int value, int velocity, double time) {
        set(value, velocity, time);
    }

    public MidiNote(MidiNote other) {
        this(other.value, other.velocity, other.time);
        this.minVelocity = other.minVelocity;
        this.maxVelocity = other.maxVelocity;
    }

    public MidiNote(JSONObject json) {
        this(json.getInt("value"), json.optInt("velocity", MAX_VELOCITY), json.getDouble("time"));
        setVelocityRange(json.optInt("minVelocity", 0), json.optInt("maxVelocity", 0));
    }

    /**
     * Convert this note to JSON
     * @return JSON object
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("value", this.value);
        json.put("time", this.time);
        if (hasVelocityRange()) {
            json.put("minVelocity", this.minVelocity);
            json.put("maxVelocity", this.maxVelocity);
        } else
            json.put("velocity", this.velocity);
        return json;
    }

    /**
     * Set the data for this note
     * @param value Note value
     * @param velocity Velocity (0 to 127)
     * @param time Time code (seconds)
     */
    public void set(int value, int velocity, double time) {
        this.value = value;
        this.velocity = (byte) velocity;
        this.time = time;
    }

    /**
     * Set the velocity range
     * @param minVelocity Minimum velocity
     * @param maxVelocity Maximum velocity
     */
    public void setVelocityRange(int minVelocity, int maxVelocity) {
        this.minVelocity = (byte) minVelocity;
        this.maxVelocity = (byte) maxVelocity;
        if (hasVelocityRange())
            this.velocity = (byte) ((minVelocity + maxVelocity) / 2);
    }

    /**
     * Check if this note has a velocity range
     * @return True if this note has a velocity range
     */
    public boolean hasVelocityRange() {
        return this.minVelocity < this.maxVelocity;
    }

    /**
     * Get a random velocity in the range
     * If a range is not defined then just use the default velocity
     * @return Velocity (0 to 127)
     */
    public int getRandomVelocity() {
        return hasVelocityRange() ? Misc.random(this.minVelocity, this.maxVelocity) : this.velocity;
    }

    @Override
    public double getMinTime() {
        return this.time;
    }

    @Override
    public double getMaxTime() {
        return this.time + AudioEditor.NOTE_DURATION;
    }

    @Override
    public double getMinNote() {
        return this.value - AudioEditor.NOTE_THRESHOLD;
    }

    @Override
    public double getMaxNote() {
        return this.value + AudioEditor.NOTE_THRESHOLD;
    }

    @Override
    public String toString() {
        return Misc.getNoteName(this.value) + " " + this.velocity + " (" + this.time + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MidiNote that = (MidiNote) o;
        return this.id == that.id;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * Calculate amplitude given a velocity and max amplitude
     * @param velocity Velocity (0 to 127)
     * @param maxAmp Max amplitude (0 to 1)
     * @return Amplitude (0 to 1)
     */
    public static double getAmplitude(int velocity, double maxAmp) {
        return ((double) velocity / MAX_VELOCITY) * maxAmp;
    }

    /**
     * Calculate velocity given an amplitude and max
     * @param amplitude Amplitude (0 to 1)
     * @param maxAmp Max amplitude (0 to 1)
     * @return Velocity (0 to 127)
     */
    public static int getVelocity(double amplitude, double maxAmp) {
        return (int) Math.floor((amplitude / maxAmp) * MAX_VELOCITY);
    }
}
