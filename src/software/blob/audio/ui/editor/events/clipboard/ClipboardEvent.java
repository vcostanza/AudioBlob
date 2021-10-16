package software.blob.audio.ui.editor.events.clipboard;

import java.util.Locale;

/**
 * An event for clipboard actions such as cut/copy/paste/delete
 */
public class ClipboardEvent {

    public enum Action {
        CUT, COPY, PASTE, DELETE
    }

    public final Action action;

    public ClipboardEvent(String action) {
        this.action = Action.valueOf(action.toUpperCase(Locale.ROOT));
    }
}
