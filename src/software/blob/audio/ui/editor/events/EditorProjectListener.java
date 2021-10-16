package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.EditorProject;

/**
 * Called when a project has been created, loaded, closed, etc.
 */
public interface EditorProjectListener {

    /**
     * A new project has been loaded
     * @param project Project
     */
    default void onLoadProject(EditorProject project) {
    }

    /**
     * A project is in the process of being saved
     * @param project Project
     */
    default void onSaveProject(EditorProject project) {
    }
}
