package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.SelectionController;
import software.blob.audio.ui.editor.events.*;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.DrawBounds;
import software.blob.audio.util.Misc;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Draws selection bounds
 */
public class SelectionLayer extends EditorLayer implements EditorMouseListener,
        EditorSelectionListener, EditorViewportListener {

    private static final int CLICK_THRESH = 4;
    private static final Color COLOR_FILL = new Color(128, 128, 128, 64);
    private static final Color COLOR_STROKE = new Color(192, 192, 192, 128);
    private static final Stroke STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,
            new float[] {2f, 0f, 2f}, 2f);

    private double startDragTime = -1, startDragNote = -1, endDragTime = -1, endDragNote = -1;
    private double clickTimeThresh;

    public SelectionLayer(AudioEditor editor) {
        super(editor);
    }

    @Override
    public String getName() {
        return "Selection";
    }

    @Override
    public void paint(Graphics2D g) {
        SelectionController ctrl = editor.getSelection();
        int startX = getX(ctrl.getStartTime());
        int endX = getX(ctrl.getEndTime());
        int height = getHeight();

        g.setColor(COLOR_FILL);
        g.fillRect(startX, 0, endX - startX, height);

        g.setColor(COLOR_STROKE);
        g.setStroke(STROKE);
        g.drawLine(startX, 0, startX, height);
        g.drawLine(endX, 0, endX, height);

        // Box select
        if (getMode() == EditorMode.BOX_SELECT && endDragTime > -1) {
            int x1 = getX(Math.min(startDragTime, endDragTime));
            int x2 = getX(Math.max(startDragTime, endDragTime));
            int y1 = getY(Math.max(startDragNote, endDragNote));
            int y2 = getY(Math.min(startDragNote, endDragNote));
            g.setColor(COLOR_FILL);
            g.fillRect(x1, y1, x2 - x1, y2 - y1);
            g.setColor(COLOR_STROKE);
            g.drawRect(x1, y1, x2 - x1, y2 - y1);
        }
    }

    @Override
    public void onSelectionChanged(double startTime, double endTime) {
        repaint();
    }

    /**
     * Get the selected handle
     * @param e Mouse event
     * @return -1 = left handle, 1 = right handle, 0 = neither
     */
    public int hitTestHandle(EditorMouseEvent e) {
        if (getMode() != EditorMode.SELECT)
            return 0;
        SelectionController selection = editor.getSelection();
        if (Math.abs(e.time - selection.getStartTime()) <= clickTimeThresh)
            return -1;
        else if (Math.abs(e.time - selection.getEndTime()) <= clickTimeThresh)
            return 1;
        return 0;
    }

    @Override
    public boolean onMouseMoved(EditorMouseEvent e) {
        int hit = hitTestHandle(e);
        toggleCursor(hit == -1 ? Cursor.W_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR, hit != 0);
        return false;
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (e.button != MouseEvent.BUTTON1)
            return false;
        EditorMode mode = getMode();
        if (mode == EditorMode.SELECT) {
            int hit = hitTestHandle(e);
            if (hit == -1)
                startDragTime = editor.getSelection().getEndTime();
            else if (hit == 1)
                startDragTime = editor.getSelection().getStartTime();
            else {
                setSelection(e.time, e.time);
                startDragTime = e.time;
            }
            return true;
        } else if (mode == EditorMode.BOX_SELECT) {
            startDragTime = endDragTime = e.time;
            startDragNote = endDragNote = e.note;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(EditorMouseEvent e) {
        if (e.button != MouseEvent.BUTTON1)
            return false;
        EditorMode mode = getMode();
        if (mode == EditorMode.SELECT) {
            setSelection(startDragTime, e.time);
            return true;
        } else if (mode == EditorMode.BOX_SELECT) {
            endDragTime = e.time;
            endDragNote = e.note;
            repaint();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        if (e.button != MouseEvent.BUTTON1)
            return false;
        if (getMode() == EditorMode.BOX_SELECT) {
            DrawBounds bounds = new DrawBounds();
            bounds.minTime = Math.min(startDragTime, endDragTime);
            bounds.maxTime = Math.max(startDragTime, endDragTime);
            bounds.minNote = Math.min(startDragNote, endDragNote);
            bounds.maxNote = Math.max(startDragNote, endDragNote);
            for (BoxSelectionListener l : editor.getEditorListeners(BoxSelectionListener.class))
                l.onBoxSelect(bounds, e);
            if (!e.shift && !e.ctrl)
                editor.setMode(EditorMode.CLICK);
            endDragTime = -1;
            repaint();
        }
        return false;
    }

    @Override
    public void onViewportChanged() {
        int centerX = editor.getWidth() / 2;
        clickTimeThresh = Math.abs(editor.getTime(centerX) - editor.getTime(centerX + CLICK_THRESH));
    }

    private void setSelection(double startTime, double endTime) {
        if (settings.hasBeatMarkers()) {
            Track track = getSelectedTrack();
            if (track != null) {
                startTime = Misc.roundToBPM(startTime, track.bpm);
                endTime = Misc.roundToBPM(endTime, track.bpm);
            }
        }
        editor.getSelection().set(startTime, endTime);
    }
}
