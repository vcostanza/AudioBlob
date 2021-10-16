package software.blob.audio.ui.editor.dialog;

import org.json.JSONObject;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.dialog.filebrowser.OnFileSelectedListener;
import software.blob.ui.util.FileUtils;
import software.blob.audio.util.JSONUtils;
import software.blob.ui.util.Log;

import java.io.File;

/**
 * Dialog shown when saving a project file
 */
public class SaveProjectDialog extends EditorFileDialog implements OnFileSelectedListener {

    public SaveProjectDialog(AudioEditor editor) {
        super(editor, "Save Project");
        setApproveButtonText("Save");
        setFileExistsCheck(false);
        setOnFileSelectedListener(this);
        setTypeFilter(FILTER_PROJECT);
    }

    @Override
    protected String getLastDirectoryPreference() {
        return "last_directory_projects";
    }

    @Override
    public void onFileSelected(File selected) {
        save(selected, false);
    }

    public void save(File file, boolean forceOverwrite) {
        file = FileUtils.appendExtension(file, EXT_PROJECT);
        if (!forceOverwrite && !canWrite(file))
            return;

        EditorProject project = new EditorProject();
        project.file = file;

        // Let controllers perform any additional save data operations
        for (EditorProjectListener l : editor.getEditorListeners(EditorProjectListener.class))
            l.onSaveProject(project);

        JSONObject json = project.toJSON(file);

        try {
            JSONUtils.writeObject(file, json);
            editor.setProjectFile(file);
        } catch (Exception e) {
            Log.e("Failed to save project", e);
            DialogUtils.errorDialog("Save Project Failed", "Failed to save project");
        }

        dismiss();
    }
}
