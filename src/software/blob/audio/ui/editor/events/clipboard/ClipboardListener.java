package software.blob.audio.ui.editor.events.clipboard;

/**
 * Listener for clipboard-based events such as cut/copy/paste/delete
 */
public interface ClipboardListener {

    /**
     * A cut/copy/paste/delete event has been invoked
     * @param event Clipboard event
     */
    void onClipboardEvent(ClipboardEvent event);
}
