package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorMouseEvent;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Layer that shows the current time code
 */
public class TimeCursorLayer extends EditorLayer {

    private static final Stroke STROKE = new BasicStroke(1.5f);
    private static final Color COLOR_LINE = new Color(192, 192, 192);
    private static final Color COLOR_HANDLE = new Color(255, 192, 128);
    private static final int HANDLE_RADIUS = 12;

    private final Point handlePos = new Point();
    private boolean hovering, dragging;

    public TimeCursorLayer(AudioEditor editor) {
        super(editor);
    }

    @Override
    public String getName() {
        return "Time Cursor";
    }

    @Override
    public void paint(Graphics2D g) {
        double time = editor.getPlayback().getTimeCode();
        int x = getX(time);
        int y = HANDLE_RADIUS;
        handlePos.x = x;
        handlePos.y = y;

        // Draw the white marker line
        g.setStroke(STROKE);
        g.setColor(COLOR_LINE);
        g.drawLine(x, y, x, getHeight());

        // Draw the top handle
        int r2 = HANDLE_RADIUS * 2;
        g.setColor(COLOR_HANDLE);
        g.fillArc(x - HANDLE_RADIUS, y - HANDLE_RADIUS, r2, r2, 45, 90);
    }

    @Override
    public void onPlaybackTimeChanged(double timeCode) {
        repaint();
    }

    @Override
    public boolean onMouseMoved(EditorMouseEvent e) {
        boolean hover = testHit(e);
        if (hovering != hover) {
            toggleCursor(Cursor.HAND_CURSOR, hover);
            hovering = hover;
        }
        return false;
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (e.button == MouseEvent.BUTTON1 && testHit(e)) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(EditorMouseEvent e) {
        if (dragging) {
            editor.getPlayback().setTimeCode(e.time);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        if (dragging)
            dragging = false;
        return false;
    }

    private boolean testHit(EditorMouseEvent e) {
        return withinRadius(e.x, e.y, handlePos, HANDLE_RADIUS);
    }
}
