package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.EditorPoint;
import software.blob.audio.ui.editor.controllers.ChangeController;
import software.blob.audio.ui.editor.controllers.ViewportController;
import software.blob.audio.ui.editor.events.BoxSelectionListener;
import software.blob.audio.ui.editor.events.EditorMouseEvent;
import software.blob.audio.ui.editor.events.EditorViewportListener;
import software.blob.audio.ui.editor.events.clipboard.ClipboardEvent;
import software.blob.audio.ui.editor.events.clipboard.ClipboardListener;
import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.instruments.InstrumentSample;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.pitchcurve.PitchCurve;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;
import software.blob.audio.ui.editor.pitchcurve.PitchSample;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackPattern;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.ui.editor.track.generator.WavGeneratorLayer;
import software.blob.audio.ui.editor.track.generator.WavGeneratorParams;
import software.blob.audio.ui.editor.track.generator.WavGeneratorTask;
import software.blob.audio.ui.editor.view.DrawBounds;
import software.blob.audio.util.Misc;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.ColorUtils;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Displays {@link PitchCurve}s within the {@link AudioEditor}
 */
public class PitchCurvesLayer extends EditorLayer implements BoxSelectionListener,
        ClipboardListener, ClipboardOwner,
        EditorViewportListener, WavGeneratorLayer {

    // Stroke constants
    private static final int STROKE_WIDTH = 2;
    private static final BasicStroke STROKE_DEFAULT = new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);

    // Colors
    private static final Color COLOR_SELECTED = new Color(255, 255, 128);

    // Click threshold
    private static final int CLICK_THRESH = 5;

    // Selected curves
    private final Set<PitchCurve> selected = new HashSet<>();

    // Curve we're hovering over
    private PitchCurve hoverCurve;

    // Drag event
    private PitchCurve dragCurve;
    private EditorMouseEvent dragEvent;
    private final Map<Long, EditorPoint> dragInitialPos = new HashMap<>();
    private boolean dragDeselect;

    // Segment that's being drawn with the freehand tool
    private PitchCurve drawnCurve;

    // Click threshold in note range and time
    private double clickNoteThresh, clickTimeThresh;

    // Paint vars
    private final List<PitchCurve> paintSelected = new ArrayList<>();

    public PitchCurvesLayer(AudioEditor editor) {
        super(editor);
    }

    /**
     * Select a list of pitch curves
     * @param curves Pitch curves
     */
    public void select(PitchCurveList curves) {
        selected.clear();
        if (curves != null)
            selected.addAll(curves);
        repaint();
    }

    /**
     * Get selected pitch curves
     * @return Curves list
     */
    public PitchCurveList getSelected() {
        return new PitchCurveList(selected);
    }

    @Override
    public String getName() {
        return "Pitch Curves";
    }

    @Override
    public String getID() {
        return "pitchCurves";
    }

    @Override
    public void paint(Graphics2D g) {
        // Clip to the usable bounds
        Shape oldClip = clipMargin(g);

        ViewportController viewport = editor.getViewport();
        Track selTrack = getSelectedTrack();
        List<Track> tracks = editor.getTracks().getAll();
        int numTracks = tracks.size();

        paintSelected.clear();

        for (int i = 0; i <= numTracks; i++) {

            Track track = i == numTracks ? selTrack : tracks.get(i);
            boolean mainTrack = selTrack == track;

            // Focused track should be drawn on top
            if (mainTrack && i < numTracks)
                continue;

            // Nothing to draw
            if (track == null || !track.isLayerVisible(getID()))
                continue;

            Color defColor = mainTrack ? track.color : ColorUtils.getColor(track.color, 128);
            Color hoverColor = mainTrack ? track.color.brighter() : null;

            if (track.curves != null && track.curves.intersects(viewport)) {
                for (PitchCurve curve : track.curves) {
                    if (selected.contains(curve))
                        paintSelected.add(curve);
                    else
                        drawCurve(g, curve, hoverCurve == curve ? hoverColor : defColor, mainTrack);
                }
            }

            if (track.patterns != null) {
                for (TrackPattern tp : track.patterns) {
                    if (tp.pattern == null || tp.pattern.curves == null || !tp.intersects(viewport))
                        continue;
                    for (PitchCurve curve : tp.pattern.curves)
                        drawCurve(g, curve, defColor, tp.startTime, tp.startNote, mainTrack);
                }
            }
        }

        // Draw selected curves
        for (PitchCurve curve : paintSelected)
            drawCurve(g, curve, COLOR_SELECTED, true);

        // Drawing
        if (drawnCurve != null) {
            int lastX = -1, lastY = -1;
            g.setColor(COLOR_SELECTED);
            for (PitchSample sample : drawnCurve) {
                int x = getX(sample.time);
                int y = getY(sample.note);
                if (lastX != -1 && lastY != -1)
                    g.drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }
        }

        g.setClip(oldClip);
    }

    private void drawCurve(Graphics2D g, PitchCurve curve, Color color,
            double startTime, double startNote, boolean mainTrack) {
        ViewportController viewport = editor.getViewport();

        startTime += curve.pos.time;
        startNote += curve.pos.note;

        // Draw the pitch curves
        g.setStroke(STROKE_DEFAULT);
        int lastX = -1, lastY = -1;
        PitchSample last = null;
        boolean lastInside = false;
        for (PitchSample sample : curve) {

            double time = sample.time + startTime;
            double note = sample.note + startNote;
            boolean inside = viewport.contains(time, note);
            if (!inside && !lastInside) {
                last = sample;
                continue;
            }

            int x = getX(time);
            int y = getY(note);

            if (last != null && !lastInside) {
                lastX = getX(last.time + startTime);
                lastY = getY(last.note + startNote);
            }

            if (last != null && lastX != -1 && lastY != -1 && (lastX != x || lastY != y)) {

                if (mainTrack) {
                    // Modulate alpha based on amplitude
                    if (settings.hasAmplitudeShading()) {
                        double amp = ((sample.amplitude + last.amplitude) / 2) * 0.9 + 0.1;
                        color = ColorUtils.getColor(color, (int) Math.round(amp * 255));
                    }
                }

                g.setColor(color);
                g.drawLine(lastX, lastY, x, y);
            }

            lastX = x;
            lastY = y;
            last = sample;
            lastInside = inside;
        }
    }

    private void drawCurve(Graphics2D g, PitchCurve curve, Color color, boolean mainTrack) {
        drawCurve(g, curve, color, 0, 0, mainTrack);
    }

    @Override
    public void onViewportChanged() {
        clickNoteThresh = CLICK_THRESH / editor.getPixelsPerNote();
        clickTimeThresh = CLICK_THRESH / editor.getPixelsPerSecond();
    }

    @Override
    public void onBoxSelect(DrawBounds box, EditorMouseEvent e) {
        Track track = getSelectedTrack();
        if (track == null || track.curves == null)
            return;

        if (!e.shift && !e.ctrl)
            selected.clear();
        for (PitchCurve curve : track.curves) {
            if (box.intersects(curve)) {
                PitchSample sample = testHitSample(curve, box);
                if (sample != null) {
                    if (e.ctrl)
                        selected.remove(curve);
                    else
                        selected.add(curve);
                }
            }
        }
        repaint();
    }

    @Override
    public void onSelectAll() {
        Track track = getSelectedTrack();
        if (track == null || track.curves == null)
            return;

        selected.clear();
        selected.addAll(track.curves);
        repaint();
    }

    @Override
    public void onDeselect() {
        if (isSelectActive()) {
            selected.clear();
            repaint();
        }
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (drawSegment(e))
            return true;

        if (e.button != MouseEvent.BUTTON1)
            return false;

        HitTestResult hit = testHitCurve(e);
        if (hit != null) {
            if (e.shift) {
                if (selected.remove(hit.curve)) {
                    repaint();
                    return true;
                }
            }
            dragEvent = e;
            dragCurve = hit.curve;
            if (!selected.contains(hit.curve)) {
                if (!e.shift)
                    selected.clear();
                selected.add(hit.curve);
                dragDeselect = true;
            }
            for (PitchCurve curve : selected)
                dragInitialPos.put(curve.id, new EditorPoint(curve.pos));
            repaint();
            return true;
        }

        onDeselect();
        return false;
    }

    @Override
    public boolean onMouseDragged(EditorMouseEvent e) {
        if (drawSegment(e))
            return true;

        if (dragEvent != null) {
            Track track = getSelectedTrack();
            if (track == null)
                return false;

            // Move curves by delta
            double tDelta = e.time - dragEvent.time;
            double vDelta = e.note - dragEvent.note;

            // Move the dragged note by the supplied
            EditorPoint dragPos = dragInitialPos.get(dragCurve.id);
            dragCurve.setPos(dragPos.time + tDelta, dragPos.note + vDelta);
            if (settings.hasBeatMarkers())
                dragCurve.pos.time = Misc.roundToBPM(dragCurve.pos.time, track.bpm);

            // Move the other notes relative to the dragged note
            for (PitchCurve curve : selected) {
                if (curve == dragCurve)
                    continue;
                EditorPoint pos = dragInitialPos.get(curve.id);
                if (pos != null) {
                    curve.setPos((pos.time - dragPos.time) + dragCurve.pos.time,
                            (pos.note - dragPos.note) + dragCurve.pos.note);
                }
            }
            repaint();
        }

        return false;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        Track track = getSelectedTrack();
        if (track == null)
            return false;

        // Finish drawn segment
        if (drawSegment(e)) {
            // Split segment up by direction to keep the shape intact
            PitchCurveList curves = drawnCurve.splitByTimeDirection();
            drawnCurve = null;
            editor.getChanges().execute(new EditCurvesChange(track, curves, null, false));
            return true;
        }

        if (dragEvent != null) {

            // Check if the note was actually moved
            EditorPoint pos = dragInitialPos.get(dragCurve.id);
            if (Double.compare(dragCurve.pos.time, pos.time) != 0 || dragCurve.pos.note != pos.note) {

                // Add movement change
                editor.getChanges().add(new CurvePositionChange(selected, dragInitialPos, !dragDeselect));

                // Deselect if the note was newly selected when we started dragging
                if (dragDeselect)
                    onDeselect();
            }

            // Clear drag vars
            dragEvent = null;
            dragCurve = null;
            dragInitialPos.clear();
            dragDeselect = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseMoved(EditorMouseEvent e) {
        HitTestResult result = testHitCurve(e);
        setHoverCurve(result != null ? result.curve : null);
        return hoverCurve != null;
    }

    @Override
    public boolean onMouseExited(EditorMouseEvent e) {
        setHoverCurve(null);
        return false;
    }

    @Override
    public void onClipboardEvent(ClipboardEvent event) {
        Track track = getSelectedTrack();
        if (track == null)
            return;

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // Paste notes from clipboard
        if (event.action == ClipboardEvent.Action.PASTE) {
            PitchCurveList paste = PitchCurveList.fromClipboard(clipboard);
            if (paste != null) {
                // Offset the notes relative to the mouse position (or center if mouse is off-screen)
                Point mouse = editor.getMousePosition();
                if (mouse == null) {
                    Dimension eSize = editor.getSize();
                    mouse = new Point(eSize.width / 2, eSize.height / 2);
                }
                double mouseNote = (getNote(mouse.y) - paste.getMinNote())
                        - (paste.getMaxNote() - paste.getMinNote()) / 2;
                double mouseTime = getTime(mouse.x);
                if (settings.hasBeatMarkers())
                    mouseTime = Misc.roundToBPM(mouseTime, track.bpm);
                mouseTime -= paste.getMinTime();
                for (PitchCurve curve : paste) {
                    curve.pos.time += mouseTime;
                    curve.pos.note += mouseNote;
                }
                editor.getChanges().execute(new EditCurvesChange(track, paste, null, true));
            }
            return;
        }

        // Nothing to do from this point forward if there's no selection
        if (selected.isEmpty())
            return;

        // Add selected notes to the clipboard
        if (event.action == ClipboardEvent.Action.CUT || event.action == ClipboardEvent.Action.COPY)
            clipboard.setContents(new PitchCurveList(selected), this);

        // Remove selected notes and push to the change stack
        if (event.action == ClipboardEvent.Action.CUT || event.action == ClipboardEvent.Action.DELETE) {
            editor.getChanges().execute(new EditCurvesChange(track, null, new PitchCurveList(selected), true));
            onDeselect();
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // For now, we don't care
    }

    @Override
    public List<WavGeneratorTask> getGeneratorTasks(final Track track, WavGeneratorParams params) {
        List<WavGeneratorTask> tasks = new ArrayList<>();
        final Instrument instrument = track.instrument;
        PitchCurveList curves = track.curves;

        // Nothing to do
        if (instrument == null || curves == null)
            return null;

        final Track.Layer layer = getTrackLayer(track);
        for (final PitchCurve curve : curves) {
            if (curve.isEmpty() || params.startTime > curve.getMaxTime()
                    || params.endTime < curve.getMinTime())
                continue;
            tasks.add(new WavGeneratorTask() {
                @Override
                public TrackWav generate() {
                    PitchSample first = curve.get(0);
                    InstrumentSample instSample = instrument.getSample(
                            (int) Math.round(curve.pos.note + first.note),
                            MidiNote.getVelocity(first.amplitude, 1));
                    WavData processed = curve.apply(instSample, instrument.getMaxAmplitude());
                    TrackWav wav = new TrackWav(track, layer, processed, curve.pos.time);
                    wav.setSampleRate(params.sampleRate);
                    return wav;
                }
            });
        }
        return tasks;
    }

    private boolean isSelectActive() {
        return !selected.isEmpty();
    }

    private void setHoverCurve(PitchCurve curve) {
        if (hoverCurve != curve) {
            hoverCurve = curve;
            toggleCursor(Cursor.HAND_CURSOR, curve != null);
            repaint();
        }
    }

    private boolean drawSegment(EditorMouseEvent e) {
        Track track = getSelectedTrack();
        if (getMode() == EditorMode.DRAW && e.button == MouseEvent.BUTTON1
                && track != null && track.isLayerVisible(getID())) {
            if (drawnCurve == null) {
                drawnCurve = new PitchCurve();
                drawnCurve.setAutoSort(false);
            }
            drawnCurve.add(new PitchSample(e.time, e.note, 1));
            repaint();
            return true;
        }
        return false;
    }

    private HitTestResult testHitCurve(EditorMouseEvent e) {
        if (getMode() != EditorMode.CLICK)
            return null;

        Track track = getSelectedTrack();
        if (track == null || track.curves == null || !track.isLayerVisible(getID()))
            return null;

        DrawBounds hitBox = new DrawBounds();
        hitBox.minTime = e.time - clickTimeThresh;
        hitBox.maxTime = e.time + clickTimeThresh;
        hitBox.minNote = e.note - clickNoteThresh;
        hitBox.maxNote = e.note + clickNoteThresh;

        HitTestResult ret1 = null;
        HitTestResult ret2 = null;
        for (PitchCurve curve : track.curves) {
            if (curve.intersects(hitBox)) {
                PitchSample sample = testHitSample(curve, hitBox);
                if (sample != null) {
                    HitTestResult res = new HitTestResult(curve, sample);
                    if (selected.contains(curve))
                        ret1 = res;
                    else
                        ret2 = res;
                }
            }
        }
        return ret1 != null ? ret1 : ret2;
    }

    private PitchSample testHitSample(PitchCurve curve, DrawBounds hitBox) {
        DrawBounds db = new DrawBounds();
        db.minTime = hitBox.getMinTime() - curve.pos.time;
        db.maxTime = hitBox.getMaxTime() - curve.pos.time;
        db.minNote = hitBox.getMinNote() - curve.pos.note;
        db.maxNote = hitBox.getMaxNote() - curve.pos.note;
        for (PitchSample sample : curve) {
            if (db.contains(sample.time, sample.note))
                return sample;
        }
        return null;
    }

    private static class HitTestResult {

        PitchCurve curve;
        PitchSample sample;

        HitTestResult(PitchCurve curve, PitchSample sample) {
            this.curve = curve;
            this.sample = sample;
        }
    }

    private class EditCurvesChange implements ChangeController.Change {

        private final Track track;
        private final PitchCurveList added;
        private final PitchCurveList removed;
        private final boolean select;

        EditCurvesChange(Track track, PitchCurveList added, PitchCurveList removed, boolean select) {
            this.track = track;
            this.added = added;
            this.removed = removed;
            this.select = select;
        }

        @Override
        public void execute() {
            add(added);
            remove(removed);
            repaint();
        }

        @Override
        public void undo() {
            remove(added);
            add(removed);
            repaint();
        }

        private void add(PitchCurveList curves) {
            if (curves != null) {
                if (track.curves == null)
                    track.curves = new PitchCurveList(curves);
                else
                    track.curves.addAll(curves);
                if (select) {
                    selected.clear();
                    selected.addAll(curves);
                }
            }
        }

        private void remove(PitchCurveList curves) {
            if (curves != null) {
                if (track.curves != null)
                    track.curves.removeAll(curves);
            }
        }
    }

    private class CurvePositionChange implements ChangeController.Change {

        private final List<PitchCurve> curves;
        private final Map<Long, EditorPoint> oldPos;
        private final Map<Long, EditorPoint> newPos;
        private final boolean select;

        CurvePositionChange(Collection<PitchCurve> notes, Map<Long, EditorPoint> oldPos, boolean select) {
            this.curves = new ArrayList<>(notes);
            this.oldPos = new HashMap<>(oldPos);
            this.newPos = new HashMap<>(notes.size());
            for (PitchCurve curve : curves)
                this.newPos.put(curve.id, new EditorPoint(curve.pos));
            this.select = select;
        }

        @Override
        public void execute() {
            apply(newPos);
        }

        @Override
        public void undo() {
            apply(oldPos);
        }

        private void apply(Map<Long, EditorPoint> map) {
            for (PitchCurve curve : curves) {
                EditorPoint pos = map.get(curve.id);
                if (pos != null)
                    curve.setPos(pos);
            }
            if (select) {
                selected.clear();
                selected.addAll(curves);
            }
            repaint();
        }
    }
}
