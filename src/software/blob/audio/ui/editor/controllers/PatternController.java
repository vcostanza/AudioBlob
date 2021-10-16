package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.dialog.EditTextDialog;
import software.blob.audio.ui.editor.events.EditorPatternListener;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.layers.PianoRollLayer;
import software.blob.audio.ui.editor.layers.PitchCurvesLayer;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.pitchcurve.PitchCurve;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;
import software.blob.audio.ui.editor.pitchcurve.PitchSample;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackPattern;
import software.blob.audio.util.IDGenerator;
import software.blob.ui.util.DialogUtils;

import java.util.*;

/**
 * Manages note and curve patterns
 */
public class PatternController extends EditorController implements
        Iterable<Pattern>, EditorProjectListener {

    private final IDGenerator idGen = new IDGenerator();
    private final MidiController midi;
    private final List<Pattern> patterns = new ArrayList<>();
    private final Map<Long, Pattern> patternMap = new HashMap<>();
    private final List<EditorPatternListener> listeners = new ArrayList<>();

    public PatternController(AudioEditor editor) {
        super(editor);
        midi = editor.getMidi();
    }

    /**
     * Add patterns to the list
     * @param patterns Pattern to add
     */
    public void add(Collection<Pattern> patterns) {
        // Make sure patterns have valid IDs
        List<Pattern> toAdd = new ArrayList<>(patterns.size());
        for (Pattern p : patterns) {
            if (p.id == -1)
                p = new Pattern(idGen.createID(), p);
            toAdd.add(p);
        }
        if (this.patterns.addAll(toAdd)) {
            for (Pattern pattern : toAdd)
                patternMap.put(pattern.id, pattern);
            for (EditorPatternListener l : listeners)
                l.onPatternsAdded(toAdd);
        }
    }

    public void add(Pattern pattern) {
        add(Collections.singleton(pattern));
    }

    /**
     * Remove patterns from the list
     * @param patterns Patterns to remove
     */
    public void remove(Collection<Pattern> patterns) {
        if (this.patterns.removeAll(patterns)) {

            Set<Long> removedIDs = new HashSet<>();
            for (Pattern pattern : patterns) {
                patternMap.remove(pattern.id);
                removedIDs.add(pattern.id);
            }

            for (EditorPatternListener l : listeners)
                l.onPatternsRemoved(patterns);

            // Remove all track pattern instances
            for (Track track : editor.getTracks()) {
                List<TrackPattern> removed = new ArrayList<>();
                if (track.patterns != null) {
                    for (int i = 0; i < track.patterns.size(); i++) {
                        TrackPattern tp = track.patterns.get(i);
                        if (removedIDs.contains(tp.patternID))
                            removed.add(track.patterns.remove(i--));
                    }
                }
                for (EditorPatternListener l : listeners)
                    l.onTrackPatternsRemoved(track, removed);
            }
        }
    }

    public void remove(Pattern pattern) {
        remove(Collections.singleton(pattern));
    }

    /**
     * Get the pattern at the given position index
     * @param index Index
     * @return Pattern or null if not found
     */
    public Pattern get(int index) {
        return index >= 0 && index < patterns.size() ? patterns.get(index) : null;
    }

    /**
     * Get all patterns
     * @return Pattern list
     */
    public List<Pattern> getAll() {
        return this.patterns;
    }

    /**
     * Get the number of patterns
     * @return Pattern count
     */
    public int getCount() {
        return this.patterns.size();
    }

    public int getUsages(Pattern pattern) {
        int usages = 0;
        for (Track track : editor.getTracks()) {
            if (track.patterns != null) {
                for (int i = 0; i < track.patterns.size(); i++) {
                    TrackPattern tp = track.patterns.get(i);
                    if (tp.patternID == pattern.id)
                        usages++;
                }
            }
        }
        return usages;
    }

    /**
     * Find a pattern by ID
     * @param id ID number
     * @return Pattern or null if not found
     */
    public Pattern findByID(long id) {
        return patternMap.get(id);
    }

    /**
     * Create a new pattern
     * @param name Pattern name
     */
    public Pattern create(String name) {
        Pattern pattern = new Pattern(idGen.createID());
        pattern.name = name;
        return pattern;
    }

    /**
     * Create a new pattern
     * @param name Pattern name
     * @param notes MIDI notes
     * @param curves Pitch curves
     * @return New pattern
     */
    public Pattern create(String name, MidiNoteList notes, PitchCurveList curves) {

        Pattern pattern = create(name);

        // Get minimum time code
        double minTime = getMinTime(notes, curves);

        // Copy data with normalized time codes
        if (notes != null && !notes.isEmpty()) {
            pattern.notes = new MidiNoteList(notes.size());
            for (MidiNote note : notes)
                pattern.notes.add(new MidiNote(note.value, note.velocity, note.time - minTime));
        }
        if (curves != null && !curves.isEmpty()) {
            pattern.curves = new PitchCurveList(curves.size());
            for (PitchCurve curve : curves) {
                PitchCurve copy = new PitchCurve(curve.size());
                for (PitchSample sample : curve)
                    copy.add(new PitchSample(sample));
                pattern.curves.add(copy);
            }
        }

        // Update pattern stats
        pattern.update();

        return pattern;
    }

    /**
     * Create a new pattern from the currently selected content
     */
    public void createFromSelected() {
        PianoRollLayer pianoRoll = editor.getLayer(PianoRollLayer.class);
        PitchCurvesLayer pitchCurves = editor.getLayer(PitchCurvesLayer.class);

        MidiNoteList notes = pianoRoll.getSelected();
        PitchCurveList curves = pitchCurves.getSelected();

        if (notes.isEmpty() && curves.isEmpty()) {
            DialogUtils.errorDialog("New Pattern", "No notes or curves selected.");
            return;
        }

        final EditTextDialog d = new EditTextDialog(editor.getFrame());
        d.setTitle("New Pattern");
        d.setHint("Pattern name");
        d.setText("Pattern " + (patterns.size() + 1));
        d.selectAll();
        d.setEmptyTextAllowed(false);
        d.showDialog(() -> {
            Pattern pattern = create(d.getText(), notes, curves);
            add(pattern, notes, curves);
            d.dismiss();
        });
    }

    /**
     * Add a new pattern while replacing existing notes and pitch curves
     * @param pattern Pattern to add
     * @param replacedNotes Notes to replace
     * @param replacedCurve Curve to replace
     */
    public void add(Pattern pattern, MidiNoteList replacedNotes, PitchCurveList replacedCurve) {
        AddPatternChange change = new AddPatternChange(pattern);
        change.track = editor.getTracks().getSelected();
        change.notes = replacedNotes;
        change.curves = replacedCurve;

        TrackPattern tp = new TrackPattern(pattern.id);
        tp.startTime = getMinTime(replacedNotes, replacedCurve);
        tp.startNote = pattern.minNote;
        tp.pattern = pattern;
        change.trPattern = tp;

        editor.getChanges().execute(change);
    }

    /**
     * Add track-based pattern instances to a track
     * @param track Track
     * @param patterns Track patterns
     */
    public void add(Track track, Collection<TrackPattern> patterns) {
        if (track.patterns == null)
            track.patterns = new ArrayList<>(patterns.size());
        if (track.patterns.addAll(patterns)) {
            for (TrackPattern tp : patterns) {
                if (tp.pattern == null)
                    tp.pattern = findByID(tp.id);
            }
            for (EditorPatternListener l : listeners)
                l.onTrackPatternsAdded(track, patterns);
        }
    }

    public void add(Track track, TrackPattern pattern) {
        add(track, Collections.singleton(pattern));
    }

    /**
     * Remove track-based pattern instances from a track
     * @param track Track
     * @param patterns Track patterns
     */
    public void remove(Track track, Collection<TrackPattern> patterns) {
        if (track.patterns != null && track.patterns.removeAll(patterns)) {
            for (EditorPatternListener l : listeners)
                l.onTrackPatternsRemoved(track, patterns);
        }
    }

    public void remove(Track track, TrackPattern pattern) {
        remove(track, Collections.singleton(pattern));
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(EditorPatternListener.class));
    }

    @Override
    public void onLoadProject(EditorProject project) {
        patterns.clear();

        // Add project patterns
        if (project.patterns != null) {
            patterns.addAll(project.patterns);

            // Update pattern stats and get the newest ID
            long id = 0;
            for (Pattern pattern : this) {
                patternMap.put(pattern.id, pattern);
                id = Math.max(pattern.id + 1, id);
            }
            idGen.setID(id);

            for (EditorPatternListener l : listeners)
                l.onPatternsAdded(patterns);
        }

        // Need to hook up track patterns
        for (Track track : project.tracks) {
            if (track.patterns != null) {
                for (TrackPattern tp : track.patterns)
                    tp.pattern = editor.getPatterns().findByID(tp.patternID);
            }
        }
    }

    @Override
    public void onSaveProject(EditorProject project) {
        project.patterns = new ArrayList<>(this.patterns);
    }

    @Override
    public Iterator<Pattern> iterator() {
        return patterns.iterator();
    }

    private double getMinTime(MidiNoteList notes, PitchCurveList curves) {
        double minTime = Double.MAX_VALUE;
        if (notes != null && !notes.isEmpty())
            minTime = Math.min(minTime, notes.getBounds().minTime);
        if (curves != null & !curves.isEmpty())
            minTime = Math.min(minTime, curves.getMinTime());
        return minTime;
    }

    private class AddPatternChange implements ChangeController.Change {

        private final Pattern pattern;

        private Track track;
        private TrackPattern trPattern;
        private MidiNoteList notes;
        private PitchCurveList curves;

        AddPatternChange(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public void execute() {
            // Add the pattern
            add(pattern);

            // Place an instance of the pattern at the position it was created
            if (track != null) {
                if (notes != null)
                    midi.removeNotes(track, notes);
                if (track.curves != null && curves != null)
                    track.curves.removeAll(curves);
                add(track, trPattern);
            }
        }

        @Override
        public void undo() {
            remove(pattern);

            // Add old data back
            if (track != null) {
                if (notes != null)
                    midi.addNotes(track, notes);
                if (curves != null && !curves.isEmpty()) {
                    if (track.curves == null)
                        track.curves = new PitchCurveList(curves);
                    else
                        track.curves.addAll(curves);
                }
            }
        }
    }
}
