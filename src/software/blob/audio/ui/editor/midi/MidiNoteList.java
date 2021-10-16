package software.blob.audio.ui.editor.midi;

import org.json.JSONArray;
import software.blob.ui.util.Log;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A list of notes that supported the {@link Transferable} interface for easy copy/pasting
 */
public class MidiNoteList extends ArrayList<MidiNote> implements Transferable {

    // The primary data flavor for this content
    public static final DataFlavor DATA_FLAVOR = new DataFlavor("audioblob/midi", "AudioBlob MIDI notes");

    public MidiNoteList() {
        super();
    }

    public MidiNoteList(Collection<? extends MidiNote> c) {
        super(c);
    }

    public MidiNoteList(int initialCapacity) {
        super(initialCapacity);
    }

    public MidiNoteList(JSONArray noteArr) {
        this(noteArr.length());
        for (int i = 0; i < noteArr.length(); i++)
            add(new MidiNote(noteArr.getJSONObject(i)));
    }

    public JSONArray toJSON() {
        JSONArray noteArr = new JSONArray();
        for (MidiNote n : this)
            noteArr.put(n.toJSON());
        return noteArr;
    }

    /**
     * Get the bounds of a list of MIDI notes
     * @return Bounds
     */
    public MidiNoteBounds getBounds() {
        MidiNoteBounds b = new MidiNoteBounds(Double.MAX_VALUE, -Double.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE);
        for (MidiNote note : this)
            b.add(note);
        return b;
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
     * Reads MIDI note list from clipboard data
     * @param clipboard Clipboard instance
     * @return MIDI note list or null if not found
     */
    public static MidiNoteList fromClipboard(Clipboard clipboard) {
        try {
            DataFlavor[] flavors = clipboard.getAvailableDataFlavors();
            if (flavors.length == 0 || !flavors[0].equals(DATA_FLAVOR))
                return null;

            // Look for JSON data string
            Object content = clipboard.getData(DATA_FLAVOR);
            if (!(content instanceof JSONArray))
                return null;

            // Convert JSON back to MIDI note list
            return new MidiNoteList((JSONArray) content);
        } catch (Exception e) {
            Log.e("Failed to read MIDI notes from clipboard", e);
            return null;
        }
    }
}
