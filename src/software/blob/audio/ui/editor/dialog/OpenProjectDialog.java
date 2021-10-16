package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.dialog.filebrowser.OnFileSelectedListener;
import software.blob.ui.util.Log;

import java.io.File;

/**
 * Dialog shown when opening a project file
 */
public class OpenProjectDialog extends EditorFileDialog implements OnFileSelectedListener {

    public OpenProjectDialog(AudioEditor editor) {
        super(editor, "Open Project");
        setOnFileSelectedListener(this);
        setTypeFilter(FILTER_PROJECT);
    }

    @Override
    protected String getLastDirectoryPreference() {
        return "last_directory_projects";
    }

    @Override
    public void onFileSelected(File selected) {
        try {
            EditorProject project = new EditorProject(editor, selected);
            editor.open(project);
            dismiss();
        } catch (Exception e) {
            Log.e("Failed to open project", e);
            DialogUtils.errorDialog("Open Project Failed", "Failed to open project");
        }
    }
}
