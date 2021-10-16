package software.blob.audio.ui.editor.events;

/**
 * Selection changed
 */
public interface EditorSelectionListener {

    /**
     * Editor selection has been changed
     * @param startTime Start time
     * @param endTime End time
     */
    void onSelectionChanged(double startTime, double endTime);
}
