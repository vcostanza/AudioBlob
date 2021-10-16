package software.blob.audio.ui.editor.events;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.util.Misc;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class EditorMouseEvent {

    public final int button, x, y;
    public final boolean ctrl, alt, shift;
    public final double time, note;

    // Mouse-wheel specific
    public final int scrollType, scrollAmount, wheelRotation;
    public final double preciseWheelRotation;

    public EditorMouseEvent(AudioEditor editor, MouseEvent e) {
        this.button = getButton(e);
        this.x = e.getX();
        this.y = e.getY();
        this.ctrl = e.isControlDown();
        this.alt = e.isAltDown();
        this.shift = e.isShiftDown();
        this.time = editor.getTime(x);
        this.note = editor.getNote(y);
        if (e instanceof MouseWheelEvent) {
            MouseWheelEvent mw = (MouseWheelEvent) e;
            this.scrollType = mw.getScrollType();
            this.scrollAmount = mw.getScrollAmount();
            this.wheelRotation = mw.getWheelRotation();
            this.preciseWheelRotation = mw.getPreciseWheelRotation();
        } else {
            this.scrollType = this.scrollAmount = this.wheelRotation = 0;
            this.preciseWheelRotation = 0;
        }
    }

    private static int getButton(MouseEvent e) {
        int button = e.getButton();
        if (button != MouseEvent.NOBUTTON)
            return button;

        int mod = e.getModifiers();
        if (Misc.hasBits(mod, MouseEvent.BUTTON1_DOWN_MASK) || Misc.hasBits(mod, MouseEvent.BUTTON1_MASK))
            return MouseEvent.BUTTON1;
        if (Misc.hasBits(mod, MouseEvent.BUTTON2_DOWN_MASK) || Misc.hasBits(mod, MouseEvent.BUTTON2_MASK))
            return MouseEvent.BUTTON2;
        if (Misc.hasBits(mod, MouseEvent.BUTTON3_DOWN_MASK) || Misc.hasBits(mod, MouseEvent.BUTTON3_MASK))
            return MouseEvent.BUTTON3;

        return button;
    }
}
