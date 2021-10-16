package software.blob.audio.ui.editor.midi.quantize;

import java.util.Comparator;

/**
 * Beats per minute value with associated error
 */
public class BPMError {

    public static final Comparator<BPMError> SORT_ERROR = Comparator.comparingDouble(e -> e.error);
    public static final Comparator<BPMError> SORT_BPM = Comparator.comparingInt(e -> e.bpm);

    public int bpm;
    public double error;

    public BPMError(int bpm, double error) {
        this.bpm = bpm;
        this.error = error;
    }

    public BPMError(BPMError other) {
        this(other.bpm, other.error);
    }

    /**
     * Get the error in percentage form
     * 100% = No error (exactly the same)
     * @return Error percentage
     */
    public int getErrorPercentage() {
        return (int) Math.floor((1 - this.error) * 100);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return this.bpm + " (" + getErrorPercentage() + "%)";
    }
}
