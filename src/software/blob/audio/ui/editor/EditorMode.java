package software.blob.audio.ui.editor;

import software.blob.ui.res.Resources;

import java.awt.*;

/**
 * Mode for the {@link AudioEditor}
 */
public enum EditorMode {

    // Click to play notes, select pitch curves
    CLICK(Cursor.DEFAULT_CURSOR),

    // Select segments to play, specific chunks of the pitch curve
    SELECT(Cursor.TEXT_CURSOR),

    // Select chunks of pitch curve
    BOX_SELECT(Cursor.CROSSHAIR_CURSOR),

    // Pan the viewport around
    PAN(Cursor.MOVE_CURSOR),

    // Freeform drawing mode
    DRAW(Resources.getCursor("pencil_cursor", 0, 15));

    public final Cursor cursor;

    EditorMode(Cursor cursor) {
        this.cursor = cursor;
    }

    EditorMode(int cursor) {
        this(new Cursor(cursor));
    }
}
