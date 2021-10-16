package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.ui.view.dialog.filebrowser.OnFileSelectedListener;

import java.awt.*;

/**
 * Dialog for loading or generating instruments
 */
public abstract class InstrumentDialog extends EditorFileDialog implements OnFileSelectedListener {

    /**
     * Callback when an instrument has been loaded
     */
    public interface Callback {

        /**
         * Instrument has been loaded
         * @param instrument Instrument
         */
        void onInstrumentLoaded(Instrument instrument);
    }

    protected Callback callback;
    protected Window window;

    public InstrumentDialog(AudioEditor editor, String title) {
        super(editor, title);
    }

    public InstrumentDialog setWindow(Window window) {
        this.window = window;
        return this;
    }

    public InstrumentDialog setCallback(Callback callback) {
        this.callback = callback;
        return this;
    }

    protected Window getWindow() {
        return this.window != null ? this.window : editor.getFrame();
    }

    protected void onInstrumentLoaded(Instrument instrument) {
        if (callback != null)
            callback.onInstrumentLoaded(instrument);
        else
            editor.setInstrument(instrument);
    }
}
