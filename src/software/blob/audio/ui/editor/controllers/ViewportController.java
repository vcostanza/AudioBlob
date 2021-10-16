package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.events.EditorMouseEvent;
import software.blob.audio.ui.editor.events.EditorMouseListener;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.pitchcurve.PitchCurve;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.audio.util.Misc;
import software.blob.ui.util.Log;

import java.awt.event.MouseEvent;

/**
 * Handles the panning/zooming of the editor
 */
public class ViewportController extends EditorController implements
        IDrawBounds, EditorMouseListener, EditorProjectListener {

    private static final double HZOOM_SECONDS = 60;
    private static final double MIN_VZOOM = 1.5, MAX_VZOOM = 7, RANGE_VZOOM = MAX_VZOOM - MIN_VZOOM;
    private static final double MIN_HZOOM = 0.5, MAX_HZOOM = 10;

    private double timeOffset = 0, noteOffset = 0;
    public double hZoom = 1, hZoomSq = 1, vZoom = 1, vZoomSq = 1;

    private EditorMouseEvent panEvent;
    private double panTime, panNote;

    public ViewportController(AudioEditor editor) {
        super(editor);
    }

    /**
     * Set time offset (horizontal pan)
     * @param timeOffset Time offset in seconds
     */
    public void setTimeOffset(double timeOffset) {
        this.timeOffset = Math.max(0, timeOffset);
    }

    /**
     * Get time offset (horizontal pan)
     * @return Time offset in seconds
     */
    public double getTimeOffset() {
        return this.timeOffset;
    }

    /**
     * Set timescale zoom
     * @param hZoom Horizontal zoom (1 to 10)
     */
    public void setTimeZoom(double hZoom) {
        this.hZoom = Misc.clamp(hZoom, MIN_HZOOM, MAX_HZOOM);
        this.hZoomSq = hZoom * hZoom;
    }

    /**
     * Set time offset and zoom
     * @param timeOffset Time offset in seconds
     * @param zoom Horizontal zoom (1 to 10)
     */
    public void setTimeZoom(double timeOffset, double zoom) {
        setTimeOffset(timeOffset);
        setTimeZoom(zoom);
        refresh();
    }

    /**
     * Get the displayed time range
     * @return Time range in seconds
     */
    public double getTimeRange() {
        return getBaseTimeRange() / hZoomSq;
    }

    /**
     * Get the time range displayed at h-zoom = 1
     * @return Base time range in seconds
     */
    public double getBaseTimeRange() {
        return HZOOM_SECONDS;
    }

    /**
     * Zoom to the time range
     * @param startTime Start time in seconds
     * @param endTime End time in seconds
     */
    public void zoomToTimeRange(double startTime, double endTime) {
        double timeWindow = endTime - startTime;
        if (timeWindow > Misc.EPSILON)
            setTimeZoom(startTime, Math.sqrt(getBaseTimeRange() / timeWindow));
    }

    /**
     * Zoom relative to a given time
     * @param relTime Time code in seconds
     * @param zoom Horizontal zoom (1 to 10)
     */
    public void zoomRelativeToTime(double relTime, double zoom) {
        zoomRelative(relTime, 0, zoom, 0);
    }

    /**
     * Zoom to a given selection
     * @param startTime Start time
     * @param endTime End time
     */
    public void zoomToSelection(double startTime, double endTime) {
        if (startTime == endTime)
            return;

        double minNote = editor.getMaxValidNote();
        double maxNote = editor.getMinValidNote();
        for (Track track : getTracks()) {
            if (track.notes != null) {
                for (MidiNote note : track.notes) {
                    if (note.time >= startTime && note.time <= endTime) {
                        minNote = Math.min(minNote, note.value);
                        maxNote = Math.max(maxNote, note.value);
                    }
                }
            }
            if (track.curves != null) {
                for (PitchCurve curve : track.curves) {
                    if (endTime < curve.getMinTime() || startTime > curve.getMaxTime())
                        continue;
                    minNote = Math.min(minNote, curve.getMinNote());
                    maxNote = Math.max(maxNote, curve.getMaxNote());
                }
            }
        }
        minNote = Math.floor(minNote) - AudioEditor.NOTE_THRESHOLD;
        maxNote = Math.ceil(maxNote) + AudioEditor.NOTE_THRESHOLD;

        zoomToTimeRange(startTime, endTime);
        zoomToNoteRange(minNote, maxNote);
    }

    /**
     * Zoom to a given selection
     */
    public void zoomToSelection() {
        SelectionController selection = editor.getSelection();
        double startTime = selection.getStartTime();
        double endTime = selection.getEndTime();
        zoomToSelection(startTime, endTime);
    }

    /**
     * Reset zoom to zero
     */
    public void resetZoom() {
        zoomToTimeRange(0, Math.min(30, editor.getDuration()));

        double minNote = Double.MAX_VALUE, maxNote = -Double.MAX_VALUE;
        for (Track track : getTracks()) {
            if (track.notes != null) {
                for (MidiNote note : track.notes) {
                    minNote = Math.min(minNote, note.value);
                    maxNote = Math.max(maxNote, note.value);
                }
            }
            if (track.curves != null && !track.curves.isEmpty()) {
                minNote = Math.min(minNote, track.curves.getMinNote());
                maxNote = Math.max(maxNote, track.curves.getMaxNote());
            }
        }

        if (minNote == Double.MAX_VALUE)
            zoomToNoteRange(48 - AudioEditor.NOTE_THRESHOLD, 60 + AudioEditor.NOTE_THRESHOLD);
        else {
            if (maxNote - minNote < 12) {
                double median = (maxNote + minNote) / 2;
                minNote = median - 6;
                maxNote = median + 6;
            }
            zoomToNoteRange(Math.floor(minNote) - AudioEditor.NOTE_THRESHOLD,
                    Math.ceil(maxNote) + AudioEditor.NOTE_THRESHOLD);
        }
    }

    /**
     * Set note offset (vertical pan)
     * @param noteOffset Note offset
     */
    public void setNoteOffset(double noteOffset) {
        this.noteOffset = Misc.clamp(noteOffset, editor.getMinValidNote(), editor.getMaxValidNote());
    }

    /**
     * Get note offset (vertical pan)
     * @return Note offset
     */
    public double getNoteOffset() {
        return this.noteOffset;
    }

    /**
     * Get the displayed note range
     * @return Note range
     */
    public double getNoteRange() {
        return getBaseNoteRange() / vZoomSq;
    }

    /**
     * Get the base note range at v-zoom = 1
     * @return Note range
     */
    public double getBaseNoteRange() {
        return editor.getValidNoteRange();
    }

    /**
     * Set note zoom level (vertical zoom)
     * Zoom is the full range of valid notes relative to the height of the window
     * @param vZoom Vertical zoom (2 to 7)
     */
    public void setNoteZoom(double vZoom) {
        this.vZoom = Misc.clamp(vZoom, MIN_VZOOM, MAX_VZOOM);
        this.vZoomSq = vZoom * vZoom;
    }

    /**
     * Set note zoom level and pan
     * @param noteOffset Note offset
     * @param zoom Vertical zoom (2 to 7)
     */
    public void setNoteZoom(double noteOffset, double zoom) {
        setNoteOffset(noteOffset);
        setNoteZoom(zoom);
        refresh();
    }

    /**
     * Set the visible note range
     * @param minNote Minimum note
     * @param maxNote Maximum note
     */
    public void zoomToNoteRange(double minNote, double maxNote) {
        setNoteZoom(minNote, Math.sqrt(getBaseNoteRange() / (maxNote - minNote)));
    }

    /**
     * Zoom relative to a note
     * @param relNote Note
     * @param zoom Vertical zoom
     */
    public void zoomRelativeToNote(double relNote, double zoom) {
        zoomRelative(0, relNote, 0, zoom);
    }

    /**
     * Zoom relative to a given time and note
     * @param relTime Time code in seconds
     * @param relNote Note value
     * @param hZoom Horizontal zoom
     * @param vZoom Vertical zoom
     */
    public void zoomRelative(double relTime, double relNote, double hZoom, double vZoom) {
        int x = editor.getX(relTime);
        int y = editor.getY(relNote);
        if (hZoom != 0)
            setTimeZoom(hZoom);
        if (vZoom != 0)
            setNoteZoom(vZoom);
        editor.updateViewport(false);
        if (hZoom != 0)
            setTimeOffset(timeOffset + (relTime - editor.getTime(x)));
        if (vZoom != 0)
            setNoteOffset(noteOffset + (relNote - editor.getNote(y)));
        refresh();
    }

    /**
     * Scroll by a given note amount
     * @param amount Note amount
     * @param scaleByZoom True to scale the amount by the current vertical zoom
     */
    public void scrollVertically(double amount, boolean scaleByZoom) {
        if (scaleByZoom) {
            double scale = (RANGE_VZOOM - (vZoom - MIN_VZOOM)) + 1;
            amount *= scale;
        }
        setNoteOffset(noteOffset + amount);
        refresh();
    }

    public void refresh() {
        editor.updateViewport();
        editor.repaint();
    }

    @Override
    public boolean onMouseWheel(EditorMouseEvent e) {
        /*Log.d("type = " + e.getScrollType()
                + " | rotation = " + e.getWheelRotation()
                + " | amount = " + e.getScrollAmount());*/
        double amount = e.wheelRotation * -0.1d;
        if (e.shift)
            zoomRelativeToTime(e.time, hZoom + amount);
        else if (e.alt)
            zoomRelativeToNote(e.note, vZoom + amount);
        else if (e.ctrl)
            zoomRelative(e.time, e.note, hZoom + amount, vZoom + amount);
        else
            scrollVertically(amount * 2, true);
        return true;
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (getMode() == EditorMode.PAN || e.button == MouseEvent.BUTTON3) {
            panEvent = e;
            panTime = timeOffset;
            panNote = noteOffset;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(EditorMouseEvent e) {
        if (panEvent != null) {
            double timeAmt = editor.getTime(panEvent.x) - editor.getTime(e.x);
            double noteAmt = editor.getNote(panEvent.y) - editor.getNote(e.y);
            setTimeOffset(panTime + timeAmt);
            setNoteOffset(panNote + noteAmt);
            refresh();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        if (panEvent != null && e.button == MouseEvent.BUTTON3)
            panEvent = null;
        return false;
    }

    @Override
    public void onLoadProject(EditorProject project) {
        if (project.viewport != null) {
            setTimeOffset(project.viewport.timeOffset);
            setNoteOffset(project.viewport.noteOffset);
            setTimeZoom(project.viewport.hZoom);
            setNoteZoom(project.viewport.vZoom);
            refresh();
        } else
            resetZoom();
    }

    @Override
    public void onSaveProject(EditorProject project) {
        project.viewport = new EditorProject.Viewport();
        project.viewport.timeOffset = timeOffset;
        project.viewport.noteOffset = noteOffset;
        project.viewport.hZoom = hZoom;
        project.viewport.vZoom = vZoom;
    }

    @Override
    public double getMinTime() {
        return getTimeOffset();
    }

    @Override
    public double getMaxTime() {
        return getMinTime() + getTimeRange();
    }

    @Override
    public double getMinNote() {
        return getNoteOffset();
    }

    @Override
    public double getMaxNote() {
        return getMinNote() + getNoteRange();
    }
}
