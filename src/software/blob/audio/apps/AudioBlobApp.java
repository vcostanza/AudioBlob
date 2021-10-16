package software.blob.audio.apps;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.AudioEditorFrame;
import software.blob.audio.ui.editor.res.AudioEditorResources;
import software.blob.ui.listener.ComponentHiddenListener;
import software.blob.ui.res.Resources;
import software.blob.ui.theme.DarkTheme;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.util.Log;
import software.blob.ui.view.TextView;
import software.blob.ui.view.dialog.filebrowser.FileBrowserDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.LayoutParams;
import software.blob.ui.view.layout.LinearLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Random;

/**
 * The main application class for AudioBlob
 */
public class AudioBlobApp {

    public static final String APP_NAME = "AudioBlob";
    public static final String APP_VERSION = "0.1";

    protected static final Random random = new Random();

    public static void main(String[] args) {

        // Instantiate BlobView dark theme
        DarkTheme.apply();

        // Initialize resource roots
        AudioEditorResources.init();

        // Make sure the base directory is set
        File baseDir = getBaseDirectory();

        // Inflate main layout
        InflatedLayout inf = LayoutInflater.inflate("editor");
        final LinearLayout content = inf.getRoot();
        LinearLayout container = inf.findByName("editorContainer");

        // Set version number
        TextView version = inf.findByName("version");
        version.setText("v" + APP_VERSION);

        // Create the audio editor
        AudioEditorFrame frame = new AudioEditorFrame();
        final AudioEditor editor = new AudioEditor(frame, inf);
        editor.setBaseDirectory(baseDir);

        // Shutdown operations
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(new RuntimePermission("shutdownHooks"));
        Runtime.getRuntime().addShutdownHook(new Thread(editor::dispose, "ShutdownThread"));

        // Show the frame dialog
        container.add(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        frame.setIconImage(Resources.getImage("audioblob"));
        frame.setSize(1280, 720);
        frame.setContentPane(content);
        frame.setLocationRelativeTo(null);
        frame.addComponentListener((ComponentHiddenListener) e -> System.exit(0));
        frame.setVisible(true);
    }

    /**
     * Get the default base directory for this system
     * If one isn't specified than prompt the user to set one
     * @return Base directory
     */
    private static File getBaseDirectory() {
        final File[] ret = { null };
        try {
            String path = System.getProperty("user.home");
            if (path != null)
                ret[0] = new File(path);
        } catch (Exception e) {
            Log.e("Failed to read user.home", e);
        }
        // If not set, prompt for one
        // XXX - This should never happen but just in case
        if (ret[0] == null || !ret[0].exists()) {
            DialogUtils.errorDialog("Home Directory Missing", "Failed to find home directory. Please select.");
            final FileBrowserDialog dialog = new FileBrowserDialog(null);
            dialog.setTitle("Set Home Directory");
            dialog.setSelectMode(FileBrowserDialog.SELECT_DIRS_ONLY);
            dialog.setHomeDirectory(new File(System.getProperty("user.dir")));
            dialog.setOnFileSelectedListener(file -> {
                ret[0] = file;
                dialog.dismiss();
            });
            dialog.showDialog();
        }
        return ret[0];
    }
}
