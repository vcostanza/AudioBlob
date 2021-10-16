package software.blob.audio.ui.editor.pitchcurve;

import org.json.JSONArray;
import software.blob.audio.audacity.frequency.FrequencySample;
import software.blob.audio.audacity.frequency.FrequencyStats;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.audio.util.Misc;
import software.blob.audio.util.SortedList;
import software.blob.ui.util.Log;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A list of {@link PitchCurve}s
 */
public class PitchCurveList extends SortedList<PitchCurve> implements IDrawBounds, Transferable {

    // The primary data flavor for this content
    public static final DataFlavor DATA_FLAVOR = new DataFlavor("audioblob/curves", "AudioBlob pitch curves");

    private static final Comparator<PitchCurve> SORT_TIME = Comparator.comparingDouble(PitchCurve::getMinTime);

    private double minNote, maxNote;
    private double minTime, maxTime;

    public PitchCurveList() {
        super(SORT_TIME);
    }

    public PitchCurveList(boolean autoSort) {
        this();
        setAutoSort(autoSort);
    }

    public PitchCurveList(Collection<? extends PitchCurve> c) {
        super(c, SORT_TIME, true);
    }

    public PitchCurveList(int initialCapacity) {
        super(initialCapacity, SORT_TIME);
    }

    public PitchCurveList(FrequencyStats frequencyStats) {
        this();
        List<FrequencyStats> sList = frequencyStats.splitBySilence();
        for (FrequencyStats stats : sList) {
            PitchCurve curve = new PitchCurve(stats.getSampleCount());
            curve.pos.time = stats.getStartTime();
            curve.pos.note = Misc.getNoteValue(stats.minFreq);
            curve.setAutoSort(false);
            for (FrequencySample fs : stats) {
                PitchSample sample = new PitchSample(fs.time - curve.pos.time,
                        Misc.getNoteValue(fs.frequency) - curve.pos.note, fs.amplitude);
                curve.add(sample);
            }
            curve.setAutoSort(true);
            add(curve);
        }
        updateBounds();
    }

    public PitchCurveList(JSONArray arr) {
        this(arr.length());
        setAutoSort(false);
        for (int i = 0; i < arr.length(); i++)
            add(new PitchCurve(arr.getJSONObject(i)));
        setAutoSort(true);
    }

    public JSONArray toJSON() {
        JSONArray arr = new JSONArray();
        for (PitchCurve curve : this)
            arr.put(curve.toJSON());
        return arr;
    }

    /**
     * Update the bounds of this pitch curve
     */
    public void updateBounds() {
        minTime = minNote = Double.MAX_VALUE;
        maxTime = maxNote = -Double.MAX_VALUE;
        for (PitchCurve curve : this) {
            minNote = Math.min(minNote, curve.getMinNote());
            maxNote = Math.max(maxNote, curve.getMaxNote());
            minTime = Math.min(minTime, curve.getMinTime());
            maxTime = Math.max(maxTime, curve.getMaxTime());
        }
    }

    @Override
    public boolean sort() {
        if (super.sort()) {
            updateBounds();
            return true;
        }
        return false;
    }

    @Override
    public double getMinTime() {
        return minTime;
    }

    @Override
    public double getMaxTime() {
        return maxTime;
    }

    @Override
    public double getMinNote() {
        return minNote;
    }

    @Override
    public double getMaxNote() {
        return maxNote;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {
                DATA_FLAVOR, DataFlavor.getTextPlainUnicodeFlavor()
        };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DATA_FLAVOR);
    }

    @Override
    public JSONArray getTransferData(DataFlavor flavor) {
        return toJSON();
    }

    /**
     * Reads pitch curves from clipboard data
     * @param clipboard Clipboard instance
     * @return MIDI note list or null if not found
     */
    public static PitchCurveList fromClipboard(Clipboard clipboard) {
        try {
            DataFlavor[] flavors = clipboard.getAvailableDataFlavors();
            if (flavors.length == 0 || !flavors[0].equals(DATA_FLAVOR))
                return null;

            // Look for JSON data string
            Object content = clipboard.getData(DATA_FLAVOR);
            if (!(content instanceof JSONArray))
                return null;

            // Convert JSON back to pitch curve list
            return new PitchCurveList((JSONArray) content);
        } catch (Exception e) {
            Log.e("Failed to read pitch curves from clipboard", e);
            return null;
        }
    }
}
