package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.layers.WaveformLayer;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.ui.editor.track.generator.WavGenerator;
import software.blob.audio.ui.editor.track.generator.WavGeneratorCallback;
import software.blob.audio.ui.editor.track.generator.WavGeneratorParams;
import software.blob.audio.wave.WavData;
import software.blob.ui.view.dialog.filebrowser.OnFileSelectedListener;
import software.blob.ui.util.FileUtils;

import java.io.File;
import java.util.List;

/**
 * Dialog shown when importing a wav file
 */
public class ExportWavDialog extends EditorFileDialog implements OnFileSelectedListener {

    public ExportWavDialog(AudioEditor editor) {
        super(editor, "Export WAV");
        setApproveButtonText("Export");
        setFileExistsCheck(false);
        setOnFileSelectedListener(this);
        setTypeFilter(FILTER_WAV);

        File file = editor.getProjectFile();
        if (file != null)
            fileNameTxt.setText(FileUtils.stripExtension(file) + ".wav");
    }

    @Override
    protected String getLastDirectoryPreference() {
        return "last_directory_export_wav";
    }

    @Override
    public void onFileSelected(File selected) {
        final File file = FileUtils.appendExtension(selected, EXT_WAV);
        if (canWrite(file)) {
            dismiss();
            WavGenerator generator = new WavGenerator(editor);
            WavGeneratorParams params = new WavGeneratorParams();
            params.endTime = editor.getDuration();
            params.channels = 2;
            params.sampleRate = 44100;
            params.ignoreMuted = true;
            params.excludeLayers.add(editor.getLayer(WaveformLayer.class));
            generator.generate(params, new WavGeneratorCallback() {
                @Override
                public void onWavGenerated(List<TrackWav> results, WavGeneratorParams params) {
                    WavData wav = new WavData(params.channels, params.getDuration(), params.sampleRate);
                    for (TrackWav tw : results) {
                        if (tw.track != null)
                            tw.multiply(tw.track.volume);
                        wav.mix(tw, tw.time);
                    }
                    wav.trimSilence(0);
                    wav.writeToFile(file);
                }
                @Override
                public void onFailed() {
                }
            });
        }
    }
}
