package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.view.DrawBounds;

/**
 * Box selection changed
 */
public interface BoxSelectionListener {

    /**
     * Process box selection
     * @param box Draw box
     * @param e Mouse event
     */
    void onBoxSelect(DrawBounds box, EditorMouseEvent e);

    /**
     * Select all notes
     */
    void onSelectAll();

    /**
     * Deselect all notes
     */
    void onDeselect();
}
