package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.EditorMode;

/**
 * Listener for when the editor's mode is changed (i.e. click mode, select mode, etc.)
 */
public interface EditorModeListener {

    /**
     * The editor mode has been changed
     * @param mode New editing mode
     */
    void onEditorModeChanged(EditorMode mode);
}
