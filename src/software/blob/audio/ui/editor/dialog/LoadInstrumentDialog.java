package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.ui.util.DialogUtils;

import java.io.File;

/**
 * Dialog shown when loading instrument samples
 */
public class LoadInstrumentDialog extends InstrumentDialog {

    public LoadInstrumentDialog(AudioEditor editor) {
        super(editor, "Load Instrument");
        setApproveButtonText("Load");
        setSelectMode(SELECT_ALL);
        setOnFileSelectedListener(this);
        setTypeFilter(FILTER_INSTRUMENT);
    }

    @Override
    protected String getLastDirectoryPreference() {
        return "last_directory_load_instrument";
    }

    @Override
    public void onFileSelected(final File file) {
        Instrument inst = editor.loadInstrument(file);
        if (inst != null) {
            dismiss();
            onInstrumentLoaded(inst);
        } else
            DialogUtils.errorDialog("Load Instrument Failed", "Failed to load instrument: " + file.getName());
    }
}
