package software.blob.audio.ui.editor.events;

/**
 * Listener for viewport change events
 */
public interface EditorViewportListener {

    /**
     * The editor viewport has changed in some way
     * i.e. zoom, pan, width, height
     */
    void onViewportChanged();
}
