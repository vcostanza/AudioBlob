package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.util.Misc;
import software.blob.ui.listener.ComponentHiddenListener;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.dialog.filebrowser.FileBrowserDialog;
import software.blob.ui.util.FileUtils;
import software.blob.ui.view.dialog.filebrowser.FileExtensionFilter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * File chooser dialog specifically meant for the editor
 */
public class EditorFileDialog extends FileBrowserDialog {

    // File extensions
    protected static final String EXT_PROJECT = "pep";
    protected static final String EXT_WAV = "wav";
    protected static final String EXT_INSTRUMENT = "inst";

    // Extension filters
    protected static final FileExtensionFilter FILTER_PROJECT
            = new FileExtensionFilter(EXT_PROJECT, "AudioBlob project");
    protected static final FileExtensionFilter FILTER_WAV
            = new FileExtensionFilter(EXT_WAV, "Waveform audio");
    protected static final FileExtensionFilter FILTER_INSTRUMENT
            = new FileExtensionFilter(EXT_INSTRUMENT, "Instrument directory or metadata");

    // File extension icons
    private static final Map<String, String> EXT_ICONS = new HashMap<>();
    static {
        EXT_ICONS.put(EXT_PROJECT, "file_project");
        EXT_ICONS.put(EXT_INSTRUMENT, "file_instrument");
        for (String ext : Misc.MEDIA_EXTS)
            EXT_ICONS.put(ext, "file_audio");
    }

    protected final AudioEditor editor;

    protected Process playProcess;

    public EditorFileDialog(AudioEditor editor, String title) {
        super(editor.getFrame());
        this.editor = editor;
        setTitle(title);

        // Stop any playing sounds when the dialog is closed
        addComponentListener((ComponentHiddenListener) e -> stopAudioPreview());
    }

    @Override
    public void dispose() {
        super.dispose();
        stopAudioPreview();
    }

    @Override
    protected File getHomeDirectory() {
        return editor.getBaseDirectory();
    }

    @Override
    protected String getFileIcon(File file, boolean isDir) {
        if (!isDir) {
            String icon = EXT_ICONS.get(FileUtils.getExtension(file));
            if (icon != null)
                return icon;
        }
        return super.getFileIcon(file, isDir);
    }

    @Override
    protected void highlight(File file) {
        super.highlight(file);

        // If the file is a sound file then start playing it
        if (Misc.MEDIA_EXTS.contains(FileUtils.getExtension(file)))
            previewAudio(file);
        else
            stopAudioPreview();
    }

    @Override
    protected void setCurrentDir(File dir) {
        super.setCurrentDir(dir);
        stopAudioPreview();
    }

    /**
     * Play an audio file using ffplay
     * @param file File to play
     */
    protected void previewAudio(File file) {
        try {
            stopAudioPreview();
            playProcess = Runtime.getRuntime().exec(new String[]{
                    "ffplay", "file:" + file.getAbsolutePath(), "-t", "10", "-autoexit", "-nodisp"
            });
        } catch (Exception e) {
            //Log.e("Failed to play audio " + file, e);
        }
    }

    /**
     * Stop playing audio file
     */
    protected void stopAudioPreview() {
        if (playProcess != null)
            playProcess.destroy();
    }

    protected boolean canWrite(File file) {
        return !file.exists() || DialogUtils.confirmDialog("Overwrite " + file.getName() + "?",
                file.getName() + " already exists. Overwrite?");
    }
}
