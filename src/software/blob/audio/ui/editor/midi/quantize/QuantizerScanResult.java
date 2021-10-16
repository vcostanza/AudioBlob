package software.blob.audio.ui.editor.midi.quantize;

import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Results of {@link Quantizer#scan()}
 */
public class QuantizerScanResult {

    public double[] intervals;
    public double minInterval = Double.MAX_VALUE;
    public double maxInterval = -Double.MAX_VALUE;
    public int maxBPM, minBPM, bestBPM;
    public List<BPMError> bestBPMs = new ArrayList<>();
    public Set<MidiNote> patternMatches;
    public int patternCount;

    /**
     * Get the total timing error of the note intervals given a BPM
     * @param bpm BPM value
     * @return Error value
     */
    public double getIntervalError(int bpm) {
        if (intervals == null)
            return Double.NaN;
        double bpmInterval = 60d / bpm;
        double error = 0;
        for (double interval : intervals) {
            double rounded = Misc.roundToNearest(interval, bpmInterval);
            error += Math.abs(rounded - interval);
        }
        return error;
    }
}
