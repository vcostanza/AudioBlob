package software.blob.audio.ui.editor.track;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.ui.util.Log;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * List of track patterns
 */
public class TrackPatternList extends ArrayList<TrackPattern> implements Transferable, IDrawBounds {

    private static final DataFlavor DATA_FLAVOR = new DataFlavor("audioblob/pattern", "AudioBlob track patterns");

    private double minTime, maxTime, minNote, maxNote;

    public TrackPatternList(Collection<TrackPattern> patterns) {
        super(patterns);
    }

    public TrackPatternList(int initialCapacity) {
        super(initialCapacity);
    }

    public void updateBounds() {
        minTime = Double.MAX_VALUE;
        maxTime = -Double.MAX_VALUE;
        minNote = Integer.MAX_VALUE;
        maxNote = -Integer.MAX_VALUE;
        for (TrackPattern tp : this) {
            minTime = Math.min(minTime, tp.getMinTime());
            maxTime = Math.max(maxTime, tp.getMaxTime());
            minNote = Math.min(minNote, tp.getMinNote());
            maxNote = Math.max(maxNote, tp.getMaxNote());
        }
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
    public Object getTransferData(DataFlavor flavor) throws JSONException {
        JSONArray arr = new JSONArray();
        for (TrackPattern pattern : this)
            arr.put(pattern.toJSON());
        return arr;
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

    /**
     * Reads track pattern list from clipboard data
     * @param editor Audio editor instance
     * @param clipboard Clipboard instance
     * @return Track pattern list or null if not found
     */
    public static TrackPatternList fromClipboard(AudioEditor editor, Clipboard clipboard) {
        try {
            DataFlavor[] flavors = clipboard.getAvailableDataFlavors();
            if (flavors.length == 0 || !flavors[0].equals(DATA_FLAVOR))
                return null;

            // Look for JSON data string
            Object content = clipboard.getData(DATA_FLAVOR);
            if (!(content instanceof JSONArray))
                return null;

            // Convert JSON back to MIDI note list
            JSONArray array = (JSONArray) content;
            TrackPatternList ret = new TrackPatternList(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                TrackPattern tp = new TrackPattern(obj);
                tp.pattern = editor.getPatterns().findByID(tp.patternID);
                ret.add(tp);
            }
            ret.updateBounds();
            return ret;
        } catch (Exception e) {
            Log.e("Failed to read patterns from clipboard", e);
            return null;
        }
    }
}
