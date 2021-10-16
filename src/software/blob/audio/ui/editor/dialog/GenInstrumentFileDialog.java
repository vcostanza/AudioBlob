package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.instruments.GenInstrument;
import software.blob.audio.ui.DialogProgressCallback;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.util.Misc;
import software.blob.ui.view.dialog.filebrowser.FileTypeFilter;

import java.io.File;

/**
 * Allows the user to generate an instrument from audio files
 */
public class GenInstrumentFileDialog extends InstrumentDialog {

    public GenInstrumentFileDialog(AudioEditor editor) {
        super(editor, "Load Instrument Seed");
        setSelectMode(SELECT_ALL);
        setOnFileSelectedListener(this);
        setTypeFilter(new FileTypeFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || Misc.MEDIA_FILTER.accept(null, f.getName());
            }
            @Override
            public String getDescription() {
                return "Any type of media file";
            }
        });
    }

    @Override
    protected String getLastDirectoryPreference() {
        return "last_directory_generated_instrument";
    }

    @Override
    public void onFileSelected(final File file) {
        GenInstrument instrument = GenInstrument.create(editor);
        if (instrument == null)
            return;

        dismiss();

        instrument.setSeedFile(file);

        new GenInstrumentParamsDialog(editor, file).setCallback((params) -> {
            instrument.setParams(params);
            initInstrument(instrument);
        }).showDialog();
    }

    private void initInstrument(final GenInstrument inst) {
        inst.init(new DialogProgressCallback(getWindow(), "Generating instrument",
                (results) -> onInstrumentLoaded(inst)));
    }
}
