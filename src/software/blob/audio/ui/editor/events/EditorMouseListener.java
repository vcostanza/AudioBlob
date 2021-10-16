package software.blob.audio.ui.editor.events;

/**
 * Mouse listener that returns true if handled
 */
public interface EditorMouseListener {

    default boolean onMouseClicked(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseDoubleClicked(EditorMouseEvent e) {
        return false;
    }

    default boolean onMousePressed(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseReleased(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseEntered(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseExited(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseMoved(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseDragged(EditorMouseEvent e) {
        return false;
    }

    default boolean onMouseWheel(EditorMouseEvent e) {
        return false;
    }
}
