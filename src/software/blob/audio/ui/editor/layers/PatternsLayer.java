package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.controllers.ChangeController;
import software.blob.audio.ui.editor.controllers.ViewportController;
import software.blob.audio.ui.editor.events.*;
import software.blob.audio.ui.editor.events.clipboard.ClipboardEvent;
import software.blob.audio.ui.editor.events.clipboard.ClipboardListener;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.pitchcurve.PitchCurve;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackPattern;
import software.blob.audio.ui.editor.track.TrackPatternList;
import software.blob.audio.ui.editor.view.DrawBounds;
import software.blob.audio.ui.editor.view.pattern.PatternAdapter;
import software.blob.audio.util.Misc;
import software.blob.ui.view.layout.list.ListView;
import software.blob.ui.util.ColorUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Pattern listing and draw handles
 */
public class PatternsLayer extends EditorLayer implements EditorProjectListener, EditorPatternListener,
        EditorTrackListener, EditorMouseListener, BoxSelectionListener,
        ClipboardListener, ClipboardOwner {

    private static final Color SELECTED_STROKE = new Color(255, 255, 92);
    private static final Color SELECTED_FILL = ColorUtils.getColor(SELECTED_STROKE, 64);
    private static final Stroke STROKE = new BasicStroke(1f);

    private final ViewportController viewport;

    // View layout for the buttons
    private final ListView patternList;
    private final PatternAdapter patternAdapter;

    // Interactivity
    private EditorMouseEvent dragEvent;
    private TrackPattern dragPattern;
    private MidiNote dragNote;
    private final Map<Long, PatternPosition> dragInitialPos = new HashMap<>();
    private boolean dragDeselect;
    private HitTestResult hover;
    private final Set<TrackPattern> selected = new HashSet<>();

    public PatternsLayer(AudioEditor editor) {
        super(editor);
        viewport = editor.getViewport();
        patternList = editor.getInflatedLayout().findByName("patterns");
        patternList.setAdapter(patternAdapter = new PatternAdapter(editor));
    }

    @Override
    public String getName() {
        return "Patterns";
    }

    /**
     * Break apart selected pattern instances into individual notes/curves
     */
    public void breakApartSelected() {
        Track track = getSelectedTrack();
        if (track == null || track.patterns == null || selected.isEmpty())
            return;

        MidiNoteList notes = new MidiNoteList();
        PitchCurveList curves = new PitchCurveList(false);
        for (TrackPattern p : selected) {
            if (p.pattern == null)
                continue;

            if (p.pattern.notes != null) {
                for (MidiNote note : p.pattern.notes) {
                    notes.add(new MidiNote(p.startNote + (note.value - p.pattern.minNote),
                            note.getRandomVelocity(), note.time + p.startTime));
                }
            }

            if (p.pattern.curves != null) {
                for (PitchCurve curve : p.pattern.curves) {
                    PitchCurve copy = new PitchCurve(curve);
                    copy.pos.time += p.startTime;
                    copy.pos.note += p.startNote;
                    curves.add(copy);
                }
            }
        }

        editor.getChanges().execute(new BreakPatternChange(track, selected, notes, curves));
        selected.clear();
    }

    @Override
    public void paint(Graphics2D g) {
        Track track = getSelectedTrack();
        if (track == null || track.patterns == null || !track.visible)
            return;

        Shape oldClip = clipMargin(g);
        g.setStroke(STROKE);

        Color hoverColor = track.color;
        Color defColor = ColorUtils.getColor(track.color, 128);

        TrackPattern tpHover = hover != null ? hover.pattern : null;

        for (TrackPattern tp : track.patterns) {
            if (selected.contains(tp))
                continue;
            if (tpHover == tp)
                drawBox(g, tp, hoverColor, null, true);
            else
                drawBox(g, tp, defColor, null, false);
        }

        for (TrackPattern tp : selected)
            drawBox(g, tp, SELECTED_STROKE, SELECTED_FILL, true);

        g.setClip(oldClip);
    }

    private void drawBox(Graphics2D g, TrackPattern tp, Color stroke, Color fill, boolean showName) {
        if (tp.pattern == null || !tp.intersects(viewport))
            return;

        int x1 = getX(tp.getMinTime());
        int x2 = getX(tp.getMaxTime());
        int y1 = getY(tp.getMinNote());
        int y2 = getY(tp.getMaxNote());

        // Draw rectangle around pattern
        if (fill != null) {
            g.setColor(fill);
            g.fillRect(x1, y2, x2 - x1, y1 - y2);
        }
        g.setColor(stroke);
        g.drawRect(x1, y2, x2 - x1, y1 - y2);

        // Draw name tab above rectangle
        if (showName) {
            g.setFont(getFont(12));
            FontMetrics metrics = g.getFontMetrics();
            int padding = 2;
            int tWidth = metrics.stringWidth(tp.pattern.name) + padding * 2;
            int tHeight = metrics.getHeight();
            if (tWidth > x2 - x1)
                return;

            int tx = x1, ty = y2 - tHeight;
            if (tp.getMinTime() < viewport.getMinTime()) {
                if (tp.getMaxTime() < viewport.getMaxTime())
                    tx = x2 - tWidth;
                else
                    tx = (x1 + x2) / 2;
            }
            tx = Misc.clamp(tx, x1, x2 - tWidth);
            if (tp.getMaxNote() >= viewport.getMaxNote())
                ty = y1;

            g.fillRect(tx, ty, tWidth, tHeight);

            g.setColor(Color.BLACK);
            g.drawString(tp.pattern.name, tx + padding, ty + tHeight - metrics.getDescent());
        }
    }

    @Override
    public void onLoadProject(EditorProject project) {
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override
    public void onPatternsAdded(Collection<Pattern> patterns) {
        refresh();
    }

    @Override
    public void onPatternsRemoved(Collection<Pattern> patterns) {
        refresh();
    }

    @Override
    public void onTrackPatternsAdded(Track track, Collection<TrackPattern> patterns) {
        repaint();
    }

    @Override
    public void onTrackPatternsRemoved(Track track, Collection<TrackPattern> patterns) {
        selected.removeAll(patterns);
        repaint();
    }

    @Override
    public boolean onMouseMoved(EditorMouseEvent e) {
        HitTestResult hit = hitTest(e);

        // Repaint if the hover has changed
        if (this.hover != hit) {
            this.hover = hit;
            toggleCursor(Cursor.HAND_CURSOR, hit != null);
            repaint();
            return hit != null;
        }

        return false;
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (e.button != MouseEvent.BUTTON1)
            return false;

        HitTestResult hit = hitTest(e);
        if (hit == null) {
            onDeselect();
            return false;
        }

        dragPattern = hit.pattern;

        if (e.shift && selected.remove(dragPattern)) {
            // Deselect single
            dragPattern = null;
            repaint();
            return true;
        }
        if (!selected.contains(dragPattern)) {
            if (!e.shift)
                selected.clear();
            selected.add(dragPattern);
            dragDeselect = true;
        }

        for (TrackPattern tp : selected)
            dragInitialPos.put(tp.id, new PatternPosition(tp));

        dragNote = new MidiNote();
        dragPattern.transformNote(hit.note, dragNote);
        dragEvent = e;
        repaint();
        return true;
    }

    @Override
    public boolean onMouseDragged(EditorMouseEvent e) {
        if (dragEvent == null)
            return false;

        Track track = getSelectedTrack();
        if (track == null)
            return false;

        // Move notes by delta
        double tDelta = e.time - dragEvent.time;
        int vDelta = (int) Math.round(e.note - dragEvent.note);

        MidiNote dragNote = new MidiNote();
        dragNote.time = this.dragNote.time + tDelta;
        dragNote.value = this.dragNote.value + vDelta;
        if (settings.hasBeatMarkers())
            dragNote.time = Misc.roundToBPM(dragNote.time, track.bpm);

        tDelta = dragNote.time - this.dragNote.time;
        vDelta = dragNote.value - this.dragNote.value;

        for (TrackPattern tp : selected) {
            PatternPosition pos = dragInitialPos.get(tp.id);
            if (pos != null) {
                tp.startTime = pos.time + tDelta;
                tp.startNote = pos.note + vDelta;
            }
        }

        repaint();
        return true;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        if (dragEvent == null)
            return false;

        PatternPosition pos = dragInitialPos.get(dragPattern.id);
        if (Double.compare(dragPattern.startTime, pos.time) != 0 || dragPattern.startNote != pos.note) {

            // Add movement change
            editor.getChanges().add(new PatternPositionChange(selected, dragInitialPos));

            if (dragDeselect)
                onDeselect();
        }

        dragEvent = null;
        dragPattern = null;
        dragNote = null;
        dragInitialPos.clear();
        dragDeselect = false;
        return true;
    }

    @Override
    public void onTrackSelected(Track track) {
        hover = null;
        selected.clear();
        repaint();
    }

    @Override
    public void onBoxSelect(DrawBounds box, EditorMouseEvent e) {
        Track track = getSelectedTrack();
        if (track == null || track.patterns == null)
            return;

        if (!e.shift && !e.ctrl)
            selected.clear();

        boolean notesVisible = track.isLayerVisible(PianoRollLayer.ID);

        MidiNote scratch = new MidiNote();
        for (TrackPattern tp : track.patterns) {
            if (tp.pattern == null || !tp.intersects(box))
                continue;

            if (notesVisible && tp.pattern.notes != null) {
                for (MidiNote note : tp.pattern.notes) {
                    tp.transformNote(note, scratch);
                    if (box.contains(scratch.time, scratch.value)) {
                        if (e.ctrl)
                            selected.remove(tp);
                        else
                            selected.add(tp);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onSelectAll() {
        Track track = getSelectedTrack();
        selected.clear();
        if (track != null && track.patterns != null)
            selected.addAll(track.patterns);
        repaint();
    }

    @Override
    public void onDeselect() {
        selected.clear();
        repaint();
    }

    @Override
    public void onClipboardEvent(ClipboardEvent event) {
        Track track = getSelectedTrack();
        if (track == null)
            return;

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // Paste notes from clipboard
        if (event.action == ClipboardEvent.Action.PASTE) {
            TrackPatternList paste = TrackPatternList.fromClipboard(editor, clipboard);
            if (paste == null)
                return;

            // Select the pasted content
            selected.clear();
            selected.addAll(paste);

            // Offset the notes relative to the mouse position (or center if mouse is off-screen)
            Point mouse = editor.getMousePosition();
            if (mouse == null) {
                Dimension eSize = editor.getSize();
                mouse = new Point(eSize.width / 2, eSize.height / 2);
            }
            double mouseTime = getTime(mouse.x);
            double mouseNote = getNote(mouse.y);
            if (settings.hasBeatMarkers())
                mouseTime = Misc.roundToBPM(mouseTime, track.bpm);
            if (mouseNote >= paste.getMinNote() && mouseNote <= paste.getMaxNote())
                mouseNote = paste.getMinNote();
            mouseTime -= paste.getMinTime();
            mouseNote -= paste.getMinNote();
            for (TrackPattern tr : paste) {
                tr.startTime += mouseTime;
                tr.startNote += mouseNote;
            }
            editor.getChanges().execute(new PatternListChange(track, paste, false, true));
            return;
        }

        // Nothing to do from this point forward if there's no selection
        if (selected.isEmpty())
            return;

        // Add selected notes to the clipboard
        if (event.action == ClipboardEvent.Action.CUT || event.action == ClipboardEvent.Action.COPY)
            clipboard.setContents(new TrackPatternList(selected), this);

        // Remove selected notes and push to the change stack
        if (event.action == ClipboardEvent.Action.CUT || event.action == ClipboardEvent.Action.DELETE) {
            editor.getChanges().execute(new PatternListChange(track, selected, true, true));
            onDeselect();
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

    private void refresh() {
        patternAdapter.notifyDatasetChanged();
        repaint();
    }

    private HitTestResult hitTest(EditorMouseEvent e) {
        if (e.x < getEditorMargin() || getMode() != EditorMode.CLICK)
            return null;

        Track track = getSelectedTrack();
        if (track == null || track.patterns == null)
            return null;

        boolean notesVisible = track.isLayerVisible(PianoRollLayer.ID);

        MidiNote scratch = new MidiNote();
        for (TrackPattern tp : track.patterns) {
            if (tp.pattern == null || !tp.contains(e.time, e.note))
                continue;

            if (notesVisible && tp.pattern.notes != null) {
                for (MidiNote note : tp.pattern.notes) {
                    tp.transformNote(note, scratch);
                    if (scratch.contains(e.time, e.note))
                        return new HitTestResult(tp, note);
                }
            }

            // TODO: Pitch curve hit testing
        }

        return null;
    }

    private static class HitTestResult {

        TrackPattern pattern;
        MidiNote note;

        HitTestResult(TrackPattern pattern, MidiNote note) {
            this.pattern = pattern;
            this.note = note;
        }
    }

    private static class PatternPosition {
        double time;
        int note;

        PatternPosition(TrackPattern tp) {
            this.time = tp.startTime;
            this.note = tp.startNote;
        }
    }

    private class PatternListChange implements ChangeController.Change {

        private final Track track;
        private final List<TrackPattern> patterns;
        private final boolean remove;
        private final boolean select;

        PatternListChange(Track track, Collection<TrackPattern> patterns, boolean remove, boolean select) {
            this.track = track;
            this.patterns = new ArrayList<>(patterns);
            this.remove = remove;
            this.select = select;
        }

        private void execute(boolean removal) {
            if (removal) {
                editor.getPatterns().remove(track, patterns);
            } else {
                editor.getPatterns().add(track, patterns);
                if (select) {
                    selected.clear();
                    selected.addAll(this.patterns);
                }
            }
        }

        @Override
        public void execute() {
            execute(remove);
        }

        @Override
        public void undo() {
            execute(!remove);
        }
    }

    private class PatternPositionChange implements ChangeController.Change {

        private final List<TrackPattern> patterns;
        private final Map<Long, PatternPosition> oldPos;
        private final Map<Long, PatternPosition> newPos;

        PatternPositionChange(Collection<TrackPattern> patterns, Map<Long, PatternPosition> oldPos) {
            this.patterns = new ArrayList<>(patterns);
            this.oldPos = new HashMap<>(oldPos);
            this.newPos = new HashMap<>(patterns.size());
            for (TrackPattern pattern : patterns)
                this.newPos.put(pattern.id, new PatternPosition(pattern));
        }

        @Override
        public void execute() {
            apply(newPos);
        }

        @Override
        public void undo() {
            apply(oldPos);
        }

        private void apply(Map<Long, PatternPosition> map) {
            for (TrackPattern tp : this.patterns) {
                PatternPosition pos = map.get(tp.id);
                if (pos != null) {
                    tp.startTime = pos.time;
                    tp.startNote = pos.note;
                }
            }
            selected.clear();
            selected.addAll(this.patterns);
            repaint();
        }
    }

    private class BreakPatternChange implements ChangeController.Change {

        private final Track track;
        private final List<TrackPattern> patterns;
        private final MidiNoteList notes;
        private final PitchCurveList curves;

        BreakPatternChange(Track track, Collection<TrackPattern> patterns, MidiNoteList notes, PitchCurveList curves) {
            this.track = track;
            this.patterns = new ArrayList<>(patterns);
            this.notes = notes;
            this.curves = curves;
        }

        @Override
        public void execute() {
            // Remove existing patterns
            editor.getPatterns().remove(track, patterns);

            // Replace with notes and curves
            editor.getMidi().addNotes(track, notes);
            if (track.curves == null)
                track.curves = new PitchCurveList(curves);
            else
                track.curves.addAll(curves);

            editor.getLayer(PianoRollLayer.class).select(notes);
            editor.getLayer(PitchCurvesLayer.class).select(curves);
        }

        @Override
        public void undo() {

            // Remove notes and curves
            editor.getMidi().removeNotes(track, notes);
            if (track.curves != null)
                track.curves.removeAll(curves);

            // Replace with patterns
            editor.getPatterns().add(track, patterns);

            selected.addAll(patterns);
        }
    }
}
