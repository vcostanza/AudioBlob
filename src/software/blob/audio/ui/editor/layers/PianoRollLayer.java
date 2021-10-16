package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.controllers.TrackController;
import software.blob.audio.ui.editor.instruments.InstrumentSample;
import software.blob.audio.ui.editor.instruments.SampleWav;
import software.blob.audio.playback.AudioHandle;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.ChangeController;
import software.blob.audio.ui.editor.controllers.ViewportController;
import software.blob.audio.ui.editor.events.*;
import software.blob.audio.ui.editor.events.clipboard.ClipboardEvent;
import software.blob.audio.ui.editor.events.clipboard.ClipboardEvent.Action;
import software.blob.audio.ui.editor.events.clipboard.ClipboardListener;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.midi.MidiNoteBounds;
import software.blob.audio.ui.editor.midi.MidiNoteList;
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
import java.awt.datatransfer.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * A piano roll with wasabi and soy sauce
 */
public class PianoRollLayer extends EditorLayer implements EditorTrackListener,
        BoxSelectionListener, EditorViewportListener, MidiNoteListener, RecordListener,
        WavGeneratorLayer, ClipboardListener, ClipboardOwner {

    public static final String ID = "pianoRoll";

    private static final Color SELECTED_STROKE = new Color(255, 255, 92);
    private static final Color SELECTED_FILL = ColorUtils.getColor(SELECTED_STROKE, 128);

    private static final Color PATTERN_STROKE = new Color(92, 255, 255);
    private static final Color PATTERN_FILL = ColorUtils.getColor(PATTERN_STROKE, 128);

    private static final Color MISALIGNED_STROKE = new Color(255, 32, 32);
    private static final Color MISALIGNED_FILL = ColorUtils.getColor(MISALIGNED_STROKE, 128);

    private static final Stroke STROKE = new BasicStroke(1.5f);
    public static final double NOTE_DURATION = AudioEditor.NOTE_DURATION;

    // Selected notes
    private final Set<MidiNote> selected = new HashSet<>();

    // Notes in a pattern (used alongside quantizer dialog)
    private final Set<MidiNote> patternHighlight = new HashSet<>();

    // Note hover
    private MidiNote hoverNote;

    // Note dragging vars
    private EditorMouseEvent dragEvent;
    private MidiNote dragNote;
    private final Map<Long, NotePosition> dragInitialPos = new HashMap<>();
    private boolean dragDeselect;

    // Playback tracking
    // Handles are mapped by track ID and note ID (including pattern ID if applicable)
    private final Map<Long, Map<NoteKey, AudioHandle>> playbackHandles = new HashMap<>();
    private double lastPlaybackTime = -1;

    // Recording undo tracking
    private final Map<Long, Set<MidiNote>> recordMap = new HashMap<>();
    private Set<MidiNote> recorded;

    // Paint vars
    private final ViewportController viewport;
    private int noteWidth, noteHeight;
    private double clickThresh;
    private final List<MidiNote> drawSelected = new ArrayList<>();
    private final List<MidiNote> drawPattern = new ArrayList<>();
    private final List<MidiNote> misaligned = new ArrayList<>();

    public PianoRollLayer(AudioEditor editor) {
        super(editor);
        this.viewport = editor.getViewport();
    }

    @Override
    public String getName() {
        return "Piano Roll";
    }

    @Override
    public String getID() {
        return ID;
    }

    /**
     * Add a note to the current track
     * @param note Note
     */
    public void addNote(MidiNote note) {
        Track track = getSelectedTrack();
        if (track == null)
            return;

        // Add note to track
        if (track.notes == null)
            track.notes = new MidiNoteList();
        track.notes.add(note);

        // Track note for undo-record
        if (editor.getRecorder().isRecording()) {
            if (recorded == null)
                recorded = recordMap.computeIfAbsent(track.id, k -> new HashSet<>());
            recorded.add(note);
        }

        repaint();
    }

    /**
     * Select the list of notes
     * @param notes Notes to select
     */
    public void select(MidiNoteList notes) {
        selected.clear();
        if (notes != null)
            selected.addAll(notes);
        repaint();
    }

    /**
     * Get selected notes sorted by time
     * @return List of selected notes
     */
    public MidiNoteList getSelected() {
        MidiNoteList notes = new MidiNoteList(this.selected);
        notes.sort(MidiNote.SORT_TIME);
        return notes;
    }

    /**
     * Set the pattern highlight
     * @param pattern Note pattern
     */
    public void highlightPattern(Set<MidiNote> pattern) {
        patternHighlight.clear();
        if (pattern != null)
            patternHighlight.addAll(pattern);
        repaint();
    }

    @Override
    public void paint(Graphics2D g) {

        List<Track> tracks = editor.getTracks().getAll();
        Track selTrack = getSelectedTrack();
        if (tracks.isEmpty())
            return;

        // Clip to the usable bounds
        Shape oldClip = clipMargin(g);
        g.setStroke(STROKE);

        drawSelected.clear();
        drawPattern.clear();
        misaligned.clear();

        int numTracks = tracks.size();
        for (int i = 0; i <= numTracks; i++) {
            boolean isSelected = i == numTracks;
            Track track = isSelected ? selTrack : tracks.get(i);
            if (track == null || !track.isLayerVisible(getID()) || !isSelected && track == selTrack)
                continue;

            Color tFill = ColorUtils.getColor(track.color, isSelected ? 255 : 128);
            //Color tStroke = ColorUtils.getColor(track.color, isSelected ? 128 : 64);
            Color tStroke = Color.BLACK;

            // Draw loose track notes
            if (track.notes != null) {
                for (MidiNote note : track.notes) {
                    if (note.intersects(viewport))
                        drawNote(g, note, tStroke, tFill, isSelected, false);
                }
            }

            // Draw patterns
            if (track.patterns != null) {
                MidiNote scratch = new MidiNote();
                for (TrackPattern tp : track.patterns) {
                    if (tp.pattern == null || tp.pattern.notes == null || !tp.intersects(viewport))
                        continue;

                    for (MidiNote note : tp.pattern.notes) {
                        tp.transformNote(note, scratch);
                        if (scratch.intersects(viewport)) {
                            if (isSelected && isNoteMisaligned(scratch))
                                drawNote(g, scratch, MISALIGNED_STROKE, MISALIGNED_FILL, true);
                            else
                                drawNote(g, scratch, tStroke, tFill, false, false);
                        }
                    }
                }
            }
        }

        // Draw selected and pattern notes last
        for (MidiNote note : misaligned)
            drawNote(g, note, MISALIGNED_STROKE, MISALIGNED_FILL, true);
        for (MidiNote note : drawPattern)
            drawNote(g, note, PATTERN_STROKE, PATTERN_FILL, true);
        for (MidiNote note : drawSelected)
            drawNote(g, note, SELECTED_STROKE, SELECTED_FILL, true);

        g.setClip(oldClip);
    }

    private void drawNote(Graphics g, MidiNote note, Color stroke, Color fill, boolean trackSelected, boolean strokeOnTop) {
        // Track selected/pattern notes to draw on top last
        if (trackSelected) {
            if (selected.contains(note)) {
                drawSelected.add(note);
                return;
            } else if (patternHighlight.contains(note)) {
                drawPattern.add(note);
                return;
            } else if (isNoteMisaligned(note)) {
                misaligned.add(note);
                return;
            }
        }

        // Amplitude shading
        if (settings.hasAmplitudeShading()) {
            double amp = MidiNote.getAmplitude(note.velocity, 0.9) + 0.1;
            fill = ColorUtils.getColor(fill, (int) (fill.getAlpha() * amp));
            //stroke = ColorUtils.getColor(stroke, (int) (stroke.getAlpha() * amp));
        }

        // Hover highlight
        if (hoverNote == note) {
            stroke = ColorUtils.getColor(fill, 255).darker();
            strokeOnTop = true;
        }

        drawNote(g, note, stroke, fill, strokeOnTop);
    }

    private void drawNote(Graphics g, MidiNote note, Color stroke, Color fill, boolean strokeOnTop) {
        int x = getX(note.time);
        int y = getY(note.value + AudioEditor.NOTE_THRESHOLD);
        if (strokeOnTop) {
            drawNote(g, fill, x, y, false);
            drawNote(g, stroke, x, y, true);
        } else {
            drawNote(g, stroke, x, y, true);
            drawNote(g, fill, x, y, false);
        }
    }

    private void drawNote(Graphics g, Color color, int x, int y, boolean stroke) {
        g.setColor(color);
        if (stroke)
            g.drawRect(x, y, noteWidth, noteHeight);
        else
            g.fillRect(x, y, noteWidth, noteHeight);
    }

    private boolean isNoteMisaligned(MidiNote note) {
        if (!settings.hasBeatMisalignment())
            return false;

        Track track = getSelectedTrack();
        if (track == null)
            return false;

        double noteInterval = note.time / (60d / track.bpm);
        int rounded = (int) Math.round(noteInterval);
        return Math.abs(noteInterval - rounded) > Misc.EPSILON;
    }

    @Override
    public void onTrackSelected(Track track) {
        calculateNoteWidth();
        recorded = track != null ? recordMap.get(track.id) : null;
        selected.clear();
        repaint();
    }

    @Override
    public void onTrackRemoved(Track track) {
        repaint();
    }

    @Override
    public void onBoxSelect(DrawBounds box, EditorMouseEvent e) {
        if (!e.shift && !e.ctrl)
            selected.clear();

        Track track = getSelectedTrack();
        if (track == null || track.notes == null || !track.isLayerVisible(getID()))
            return;

        for (MidiNote note : track.notes) {
            if (box.intersects(note)) {
                if (e.ctrl)
                    selected.remove(note);
                else
                    selected.add(note);
            }
        }
    }

    @Override
    public void onSelectAll() {
        Track track = getSelectedTrack();
        selected.clear();
        if (track != null && track.notes != null)
            selected.addAll(track.notes);
        repaint();
    }

    @Override
    public void onDeselect() {
        if (!selected.isEmpty()) {
            selected.clear();
            repaint();
        }
    }

    @Override
    public void onRecordStarted(double timeCode) {
        recordMap.clear();
        recorded = null;
    }

    @Override
    public void onRecordStopped(double timeCode) {
        // Add recorded notes to undo stack
        for (Map.Entry<Long, Set<MidiNote>> e : recordMap.entrySet()) {
            Track track = editor.getTracks().findByID(e.getKey());
            Set<MidiNote> notes = e.getValue();
            if (notes != null && !notes.isEmpty())
                editor.getChanges().add(new NoteListChange(track, notes, false, false));
        }
        recordMap.clear();
        recorded = null;
    }

    @Override
    public void onClipboardEvent(ClipboardEvent event) {
        Track track = getSelectedTrack();
        if (track == null)
            return;

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // Paste notes from clipboard
        if (event.action == Action.PASTE) {
            MidiNoteList paste = MidiNoteList.fromClipboard(clipboard);
            if (paste != null) {
                // Select the pasted content
                selected.clear();
                selected.addAll(paste);

                // Offset the notes relative to the mouse position (or center if mouse is off-screen)
                MidiNoteBounds bounds = paste.getBounds();
                Point mouse = editor.getMousePosition();
                if (mouse == null) {
                    Dimension eSize = editor.getSize();
                    mouse = new Point(eSize.width / 2, eSize.height / 2);
                }
                int mouseNote = (int) Math.round((getNote(mouse.y) - bounds.minNote)
                        - (bounds.maxNote - bounds.minNote) / 2f);
                double mouseTime = getTime(mouse.x);
                if (settings.hasBeatMarkers())
                    mouseTime = Misc.roundToBPM(mouseTime, track.bpm);
                mouseTime -= bounds.minTime;
                for (MidiNote note : paste) {
                    note.time += mouseTime;
                    note.value += mouseNote;
                }
                editor.getChanges().execute(new NoteListChange(track, paste, false, true));
            }
            return;
        }

        // Nothing to do from this point forward if there's no selection
        if (selected.isEmpty())
            return;

        // Add selected notes to the clipboard
        if (event.action == Action.CUT || event.action == Action.COPY)
            clipboard.setContents(new MidiNoteList(selected), this);

        // Remove selected notes and push to the change stack
        if (event.action == Action.CUT || event.action == Action.DELETE) {
            editor.getChanges().execute(new NoteListChange(track, selected, true, true));
            onDeselect();
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // For now, we don't care
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (e.button != MouseEvent.BUTTON1)
            return false;

        // Begin dragging note(s)
        MidiNote hit = hitTest(e);
        if (hit != null) {
            if (e.shift) {
                if (selected.remove(hit)) {
                    repaint();
                    return true;
                }
            }
            dragEvent = e;
            dragNote = hit;
            if (!selected.contains(hit)) {
                if (!e.shift)
                    selected.clear();
                selected.add(hit);
                dragDeselect = true;
            }
            for (MidiNote n : selected)
                dragInitialPos.put(n.id, new NotePosition(n));
            repaint();
            return true;
        }

        // Nothing selected - deselect any notes
        onDeselect();
        return false;
    }

    @Override
    public boolean onMouseMoved(EditorMouseEvent e) {
        setHoverNote(hitTest(e));
        return hoverNote != null;
    }

    @Override
    public boolean onMouseExited(EditorMouseEvent e) {
        setHoverNote(null);
        return false;
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

        // Move the dragged note by the supplied
        NotePosition dragPos = dragInitialPos.get(dragNote.id);
        dragNote.time = dragPos.time + tDelta;
        dragNote.value = dragPos.value + vDelta;
        if (settings.hasBeatMarkers())
            dragNote.time = Misc.roundToBPM(dragNote.time, track.bpm);

        // Move the other notes relative to the dragged note
        for (MidiNote note : selected) {
            if (note == dragNote)
                continue;
            NotePosition pos = dragInitialPos.get(note.id);
            if (pos != null) {
                note.time = (pos.time - dragPos.time) + dragNote.time;
                note.value = (pos.value - dragPos.value) + dragNote.value;
            }
        }

        repaint();
        return true;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        if (dragEvent != null) {

            // Check if the note was actually moved
            NotePosition pos = dragInitialPos.get(dragNote.id);
            if (Double.compare(dragNote.time, pos.time) != 0 || dragNote.value != pos.value) {

                // Add movement change
                editor.getChanges().add(new NotePositionChange(selected, dragInitialPos, !dragDeselect));

                // Deselect if the note was newly selected when we started dragging
                if (dragDeselect)
                    onDeselect();
            }

            // Clear drag vars
            dragEvent = null;
            dragNote = null;
            dragInitialPos.clear();
            dragDeselect = false;
            return true;
        }
        return false;
    }

    @Override
    public void onViewportChanged() {
        calculateNoteWidth();
        noteHeight = (int) Math.round(editor.getPixelsPerNote() * AudioEditor.NOTE_THRESHOLD * 2);
        clickThresh = 2 / editor.getPixelsPerSecond();
    }

    @Override
    public List<WavGeneratorTask> getGeneratorTasks(final Track track, final WavGeneratorParams params) {
        List<WavGeneratorTask> tasks = new ArrayList<>();

        // Nothing to do
        if (track.instrument == null)
            return null;

        final Track.Layer layer = getTrackLayer(track);

        if (track.notes != null) {
            for (MidiNote n : track.notes) {
                if (n.time < params.startTime || n.time >= params.endTime)
                    continue;
                tasks.add(new NoteGeneratorTask(track, layer, n, params));
            }
        }

        if (track.patterns != null) {
            for (TrackPattern tp : track.patterns) {

                // Pattern is outside parameters
                if (tp.pattern.notes == null || params.endTime < tp.getMinTime()
                        || params.startTime >= tp.getMaxTime())
                    continue;

                for (MidiNote note : tp.pattern.notes) {

                    // Transform note to proper offset
                    MidiNote transformed = new MidiNote(note);
                    tp.transformNote(note, transformed);

                    // Note is outside parameters
                    if (transformed.time < params.startTime || transformed.time >= params.endTime)
                        continue;

                    tasks.add(new NoteGeneratorTask(track, layer, transformed, params));
                }
            }
        }

        return tasks;
    }

    @Override
    public void onPlaybackStarted(double timeCode) {
        notePlayback(timeCode);
    }

    @Override
    public void onPlaybackTimeChanged(double timeCode) {
        if (editor.getPlayback().isPlaying())
            notePlayback(timeCode);
    }

    @Override
    public void onPlaybackStopped(double timeCode) {
        playbackHandles.clear();
        lastPlaybackTime = -1;
    }

    @Override
    public void onMidiNotePlayed(MidiNote note, InstrumentSample sample, AudioHandle handle) {
        if (note.time >= 0)
            addNote(note);
        editor.repaint();
        editor.keepAwake();
    }

    @Override
    public void onMidiNotesAdded(Track track, Collection<MidiNote> notes) {
        repaint();
    }

    @Override
    public void onMidiNotesRemoved(Track track, Collection<MidiNote> notes) {
        selected.removeAll(notes);
        repaint();
    }

    private void setHoverNote(MidiNote note) {
        if (hoverNote != note) {
            hoverNote = note;
            toggleCursor(Cursor.HAND_CURSOR, note != null);
            repaint();
        }
    }

    private MidiNote hitTest(EditorMouseEvent e) {
        if (e.x < getEditorMargin() || getMode() != EditorMode.CLICK)
            return null;

        Track track = getSelectedTrack();
        if (track == null || track.notes == null || !track.isLayerVisible(getID()))
            return null;

        MidiNote hit = null;
        for (MidiNote note : track.notes) {
            if (e.time >= note.time - clickThresh
                    && e.time < note.time + NOTE_DURATION + clickThresh
                    && e.note >= note.value - AudioEditor.NOTE_THRESHOLD
                    && e.note < note.value + AudioEditor.NOTE_THRESHOLD) {
                hit = note;
                if (selected.contains(hit))
                    break;
            }
        }
        return hit;
    }

    private void notePlayback(double timeCode) {
        // Clear playback handles when another loop has started
        if (timeCode < lastPlaybackTime)
            playbackHandles.clear();
        lastPlaybackTime = timeCode;

        // Play incoming notes
        TrackController tracks = editor.getTracks();
        for (int i = 0; i < tracks.getCount(); i++) {

            Track track = tracks.get(i);

            // Nothing to play
            if (track.instrument == null)
                continue;

            // Handles that are currently playing
            Map<NoteKey, AudioHandle> handles = playbackHandles.computeIfAbsent(track.id, k -> new HashMap<>());

            if (track.notes != null) {
                for (MidiNote note : track.notes) {

                    // Note is not near the cursor
                    if (timeCode < note.getMinTime() || timeCode >= note.getMaxTime())
                        continue;

                    // Don't play notes we just recorded
                    if (recorded != null && recorded.contains(note))
                        continue;

                    // Queue and play the note
                    NoteKey key = new NoteKey(note);
                    queueNote(track, note, timeCode, key, handles);
                }
            }

            if (track.patterns != null) {
                MidiNote scratch = new MidiNote();
                for (TrackPattern tp : track.patterns) {

                    // Pattern is not near the cursor
                    if (timeCode < tp.getMinTime() || timeCode >= tp.getMaxTime())
                        continue;

                    for (MidiNote note : tp.pattern.notes) {

                        // Transform note to proper offset
                        tp.transformNote(note, scratch);

                        // Note is not near the cursor
                        if (timeCode < scratch.getMinTime() || timeCode >= scratch.getMaxTime())
                            continue;

                        // Queue and play the note
                        NoteKey key = new NoteKey(tp, note);
                        queueNote(track, scratch, timeCode, key, handles);
                    }
                }
            }
        }
    }

    private void queueNote(Track track, MidiNote note, double timeCode, NoteKey key, Map<NoteKey, AudioHandle> handles) {
        // Check if note is still playing
        AudioHandle h = handles.get(key);
        if (h != null) {
            if (!h.isFinished())
                return;
            handles.remove(key);
        }

        // Get velocity (might be randomized)
        int velocity = note.getRandomVelocity();

        // Get the sample
        InstrumentSample sample = track.instrument.getSample(note.value, velocity);
        if (sample == null)
            return;

        // Create the playback handle and queue it up
        h = editor.getMidi().createPlaybackHandle(track, sample, velocity);
        h.setTime(timeCode - note.time);
        handles.put(key, h);
        getAudioPlayer().queue(h);
    }

    private void calculateNoteWidth() {
        double noteDuration = NOTE_DURATION;
        if (settings.hasBeatMarkers()) {
            Track track = getSelectedTrack();
            if (track != null && track.bpm > 600)
                noteDuration = 60d / track.bpm;
        }
        noteWidth = (int) Math.round(editor.getPixelsPerSecond() * noteDuration);
    }

    /* SUB-CLASSES */

    private static class NoteGeneratorTask extends WavGeneratorTask {

        private final Track track;
        private final Track.Layer layer;
        private final MidiNote note;
        private final WavGeneratorParams params;

        NoteGeneratorTask(Track track, Track.Layer layer, MidiNote note, WavGeneratorParams params) {
            this.track = track;
            this.layer = layer;
            this.note = note;
            this.params = params;
        }

        @Override
        public TrackWav generate() {
            if (track.instrument == null)
                return null;

            int velocity = note.getRandomVelocity();

            InstrumentSample sample = track.instrument.getSample(note.value, velocity);
            if (sample == null)
                return null;

            SampleWav samWav = sample.getWav();
            if (samWav == null)
                return null;

            TrackWav wav = new TrackWav(track, layer, new WavData(samWav), note.time);
            wav.setPeakAmplitude(MidiNote.getAmplitude(velocity, track.instrument.getMaxAmplitude()));
            wav.setSampleRate(params.sampleRate);
            return wav;
        }
    }

    private static class NoteKey {

        private final long tpID, noteID;
        private final int hash;

        public NoteKey(TrackPattern pattern, MidiNote note) {
            this.tpID = pattern != null ? pattern.id : -1;
            this.noteID = note.id;
            this.hash = Objects.hash(tpID, noteID);
        }

        public NoteKey(MidiNote note) {
            this(null, note);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NoteKey noteKey = (NoteKey) o;
            return tpID == noteKey.tpID && noteID == noteKey.noteID;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private class NoteListChange implements ChangeController.Change {

        private final Track track;
        private final List<MidiNote> notes;
        private final boolean remove;
        private final boolean select;

        NoteListChange(Track track, Collection<MidiNote> notes, boolean remove, boolean select) {
            this.track = track;
            this.notes = new ArrayList<>(notes);
            this.remove = remove;
            this.select = select;
        }

        private void execute(boolean removal) {
            if (removal) {
                editor.getMidi().removeNotes(track, notes);
            } else {
                editor.getMidi().addNotes(track, notes);
                if (select) {
                    selected.clear();
                    selected.addAll(this.notes);
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

    private class NotePositionChange implements ChangeController.Change {

        private final List<MidiNote> notes;
        private final Map<Long, NotePosition> oldPos;
        private final Map<Long, NotePosition> newPos;
        private final boolean select;

        NotePositionChange(Collection<MidiNote> notes, Map<Long, NotePosition> oldPos, boolean select) {
            this.notes = new ArrayList<>(notes);
            this.oldPos = new HashMap<>(oldPos);
            this.newPos = new HashMap<>(notes.size());
            for (MidiNote note : notes)
                this.newPos.put(note.id, new NotePosition(note));
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

        private void apply(Map<Long, NotePosition> map) {
            for (MidiNote note : this.notes) {
                NotePosition pos = map.get(note.id);
                if (pos != null) {
                    note.time = pos.time;
                    note.value = pos.value;
                }
            }
            if (select) {
                selected.clear();
                selected.addAll(this.notes);
            }
            repaint();
        }
    }

    private static class NotePosition {
        double time;
        int value;

        NotePosition(MidiNote note) {
            this.time = note.time;
            this.value = note.value;
        }
    }
}
