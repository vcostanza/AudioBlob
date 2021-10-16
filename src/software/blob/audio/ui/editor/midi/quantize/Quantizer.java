package software.blob.audio.ui.editor.midi.quantize;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.ChangeController;
import software.blob.audio.ui.editor.controllers.SettingsController;
import software.blob.audio.ui.editor.layers.PianoRollLayer;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackPattern;
import software.blob.audio.util.Misc;
import software.blob.audio.util.StandardDeviation;

import java.util.*;

/**
 * Quantize MIDI notes based on a given timescale
 */
public class Quantizer {

    // Chord epsilon = 50ms
    private static final double EPSILON = AudioEditor.NOTE_EPSILON_MS / 1000d;

    // Sort notes by time while using a consistent sort order for chords
    private static final Comparator<MidiNote> SORT_TIME_EPSILON = (n1, n2) -> {
        if (Math.abs(n1.time - n2.time) < EPSILON)
            return Integer.compare(n1.value, n2.value);
        return Double.compare(n1.time, n2.time);
    };

    private final AudioEditor editor;
    private final SettingsController settings;
    private final Track track;
    private final List<MidiNote> notes;
    private Pattern pattern;
    private boolean patternReplace;

    public Quantizer(AudioEditor editor, Track track, MidiNoteList notes) {
        this.editor = editor;
        this.track = track;
        this.settings = editor.getSettings();
        this.notes = new ArrayList<>(notes);
        this.notes.sort(SORT_TIME_EPSILON);
    }

    public Quantizer(AudioEditor editor, Track track) {
        this(editor, track, track.notes);
    }

    /**
     * Set a pattern to use for quantization scanning and execution
     * @param pattern Note pattern
     */
    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
        if (hasPattern())
            pattern.notes.sort(SORT_TIME_EPSILON);
    }

    /**
     * Set whether the quantized notes should be replaced
     * with instances of the given pattern
     * @param patternReplace True to replace notes with {@link TrackPattern} where applicable
     */
    public void setPatternReplace(boolean patternReplace) {
        this.patternReplace = patternReplace;
    }

    /**
     * Check if the quantization pattern is set
     * @return True if set
     */
    public boolean hasPattern() {
        return pattern != null && pattern.notes != null && !pattern.notes.isEmpty();
    }

    /**
     * Perform a statistics scan on the notes in the track
     * @return Scan results
     */
    public QuantizerScanResult scan() {
        if (notes == null || notes.size() < 2)
            return null;

        QuantizerScanResult res = new QuantizerScanResult();

        if (hasPattern()) {
            MidiNoteList pattern = this.pattern.notes;
            res.intervals = new double[pattern.size()];

            int p = 0;
            MidiNote last = null;
            int[] timeCounts = new int[pattern.size()];
            double[] averages = new double[pattern.size()];
            List<MidiNote> patternMatch = new ArrayList<>(pattern.size());
            for (int i = 0; i < notes.size(); i++) {
                MidiNote note = notes.get(i);
                MidiNote pNote = pattern.get(p);
                if (pNote.value == note.value) {
                    patternMatch.add(note);
                    double interval = last != null ? note.time - last.time : 0;
                    if (interval < EPSILON)
                        interval = 0;
                    res.intervals[p] = interval;
                    if (++p == pattern.size()) {
                        for (int j = 0; j < averages.length; j++) {
                            interval = res.intervals[j];
                            if (interval > 0) {
                                averages[j] += interval;
                                timeCounts[j]++;
                            }
                        }
                        p = 0;
                        res.patternCount++;
                        if (res.patternMatches == null)
                            res.patternMatches = new HashSet<>();
                        res.patternMatches.addAll(patternMatch);
                        patternMatch.clear();
                    }
                    last = note;
                } else {
                    last = note;
                    if (p > 0) {
                        // We only hit part of the pattern - rewind
                        i -= p;
                        last = notes.get(i);
                    }
                    p = 0;
                    patternMatch.clear();
                }
            }

            for (int i = 0; i < averages.length; i++) {
                int count = timeCounts[i];
                double avg = averages[i];
                if (count > 0 && avg > 0) {
                    avg /= count;
                    res.minInterval = Math.min(res.minInterval, avg);
                    res.maxInterval = Math.max(res.maxInterval, avg);
                    res.intervals[i] = avg;
                } else
                    res.intervals[i] = 0;
            }
        } else {
            res.intervals = new double[notes.size()];

            int p = 0;
            MidiNote last = null;
            for (MidiNote note : notes) {
                double interval = last != null ? note.time - last.time : 0;
                if (interval < EPSILON)
                    interval = 0;
                res.intervals[p++] = interval;
                if (interval > 0) {
                    res.minInterval = Math.min(res.minInterval, interval);
                    res.maxInterval = Math.max(res.maxInterval, interval);
                }
                last = note;
            }
        }

        res.maxBPM = (int) Math.round(60 / res.minInterval);
        res.minBPM = (int) Math.round(60 / res.maxInterval);

        // Determine the closest BPM approximation
        double minError = Double.MAX_VALUE;
        double lastError = Double.MAX_VALUE;
        boolean decreasing = false;
        for (int bpm = 1; bpm <= AudioEditor.MAX_BPM; bpm++) {
            double error = res.getIntervalError(bpm);
            if (error < minError) {
                minError = error;
                res.bestBPM = bpm;
            }
            if (decreasing && error > lastError)
                res.bestBPMs.add(new BPMError(bpm, error));
            decreasing = error <= lastError;
            lastError = error;
        }

        res.bestBPMs.sort(BPMError.SORT_BPM);

        return res;
    }

    /**
     * Quantize notes by the given BPM
     * @param scan Scan results from {@link #scan()}; only required for pattern-based quantization
     * @param srcBPM Source (detected) BPM
     * @param dstBPM Output (desired) BPM quantization
     */
    public void quantize(QuantizerScanResult scan, int srcBPM, int dstBPM) {
        if (notes == null || notes.size() < 2)
            return;

        double srcRounded = 60d / srcBPM;
        double dstRounded = 60d / dstBPM;

        QuantizeChange change = new QuantizeChange(track, dstBPM);
        if (hasPattern() && scan != null) {

            MidiNoteList pattern = this.pattern.notes;

            // Grab the first note in each pattern instance
            // Also grab min and max velocity per note
            List<MidiNote> pNotes = new ArrayList<>(scan.patternCount);
            List<StandardDeviation> velStds = new ArrayList<>(pattern.size());
            MidiNote firstPNote = null;
            int p = 0;
            for (int i = 0; i < notes.size(); i++) {
                MidiNote note = notes.get(i);
                MidiNote pNote = pattern.get(p);
                if (pNote.value == note.value) {
                    // Remember first note in the pattern
                    if (p == 0)
                        firstPNote = note;

                    // Add velocity to standard deviation calculator
                    StandardDeviation std;
                    if (p >= velStds.size())
                        velStds.add(std = new StandardDeviation());
                    else
                        std = velStds.get(p);
                    std.add(note.velocity);

                    // Check if the pattern is finished
                    if (++p == pattern.size()) {
                        pNotes.add(firstPNote);
                        p = 0;
                    }
                } else {
                    i -= p;
                    p = 0;
                }
            }

            // Nothing to do if there's no patterns
            if (pNotes.isEmpty())
                return;

            // Copy and quantize the original pattern notes
            if (patternReplace) {
                this.pattern = editor.getPatterns().create(this.pattern.name);
                this.pattern.notes = new MidiNoteList(pattern.size());
                MidiNote last = null;
                double pDuration = 0;
                for (int i = 0; i < pattern.size(); i++) {

                    // Get min and max velocity
                    StandardDeviation vs = velStds.get(i);
                    double avg = vs.getAverage();
                    double std = vs.getSTD();
                    int minVelocity = (int) Math.round(avg - std);
                    int maxVelocity = (int) Math.round(avg + std);

                    // Copy note so we don't mess with the original
                    MidiNote pNote = new MidiNote(pattern.get(i));
                    pNote.setVelocityRange(minVelocity, maxVelocity);

                    // Get multiplication factors
                    int srcMulti = (int) Math.round(scan.intervals[i] / srcRounded);
                    double dstMulti = srcMulti * dstRounded;

                    // Quantize note
                    if (last == null) {
                        pNote.time = 0;
                        pDuration = dstMulti;
                    } else
                        pNote.time = Misc.roundToNearest(last.time + dstMulti, dstRounded);

                    last = pNote;
                    this.pattern.notes.add(pNote);
                }
                this.pattern.duration = Misc.roundToNearest(last.time + pDuration, dstRounded);
                this.pattern.update();
                change.setPattern(this.pattern);
                change.setPatternReplace(true);
            }

            // Remember all intervals so non-pattern notes aren't disturbed
            MidiNote last = null;
            double[] origIntervals = new double[notes.size()];
            for (int i = 0; i < notes.size(); i++) {
                MidiNote note = notes.get(i);
                origIntervals[i] = note.time - (last != null ? last.time : 0);
                last = note;
            }

            // Quantize based on pattern
            int p2 = 0;
            last = null;
            firstPNote = pNotes.get(0);
            for (int i = 0; i < notes.size(); i++) {

                MidiNote note = notes.get(i);

                // Check if we hit the first note in a pattern
                if (note == firstPNote) {

                    // Quantize pattern
                    for (p = 0; p < pattern.size(); p++) {
                        MidiNote pNote = notes.get(p + i);
                        double time;
                        if (last != null) {
                            int srcMulti = (int) Math.round(scan.intervals[p] / srcRounded);
                            double dstMulti = srcMulti * dstRounded;
                            time = Misc.roundToNearest(last.time + dstMulti, dstRounded);
                        } else
                            time = Misc.roundToNearest(pNote.time, dstRounded);
                        change.execute(pNote, time, true);
                        last = pNote;
                    }

                    // Create track pattern
                    if (patternReplace) {
                        TrackPattern tp = new TrackPattern(this.pattern);
                        tp.startNote = this.pattern.minNote;
                        tp.startTime = note.time;
                        change.addTrackPattern(tp);
                    }

                    // Move to next pattern if there is one and continue
                    p2++;
                    firstPNote = p2 < pNotes.size() ? pNotes.get(p2) : null;
                    i += pattern.size() - 1;
                    continue;
                }

                // Make sure the non-quantized notes have the same relative timings
                if (last != null)
                    change.execute(note, last.time + origIntervals[i]);
                last = note;
            }
        } else {
            // Simple quantization on all notes
            MidiNote last = null;
            for (MidiNote note : notes) {
                if (last != null) {
                    double interval = note.time - last.time;
                    if (interval < EPSILON)
                        interval = 0;
                    int srcMulti = (int) Math.round(interval / srcRounded);
                    double dstMulti = srcMulti * dstRounded;
                    change.execute(note, last.time + dstMulti);
                } else {
                    double startTime = Misc.roundToNearest(note.time, dstRounded);
                    change.execute(note, startTime);
                }
                last = note;
            }
        }
        editor.getChanges().add(change);
        change.executeNoQuantize();
    }

    public void quantize(int bpm) {
        quantize(null, bpm, bpm);
    }

    /**
     * Set the BPM of the track notes
     * Note: This does NOT perform quantization
     * @param srcBPM Current BPM
     * @param dstBPM Output BPM
     */
    public void setBPM(int srcBPM, int dstBPM) {
        if (notes == null || notes.size() < 2)
            return;

        double changeFactor = (double) dstBPM / srcBPM;
        QuantizeChange change = new QuantizeChange(track, dstBPM);
        double startOffset = notes.get(0).time;
        for (MidiNote note : notes)
            change.execute(note, startOffset + ((note.time - startOffset) / changeFactor));
        editor.getChanges().add(change);
        track.bpm = dstBPM;
        editor.repaint();
    }

    private class QuantizeChange implements ChangeController.Change {

        private final Track track;
        private final int oldBPM, newBPM;
        private final boolean hadBeatMarkers;
        private Pattern pattern;
        private boolean patternReplace;
        private final Map<Long, Double> oldTimings = new HashMap<>();
        private final Map<Long, Double> newTimings = new HashMap<>();
        private final List<MidiNote> notes = new ArrayList<>();
        private final List<MidiNote> pNotes = new ArrayList<>();
        private final List<TrackPattern> trackPatterns = new ArrayList<>();

        public QuantizeChange(Track track, int newBPM) {
            this.track = track;
            this.oldBPM = track.bpm;
            this.hadBeatMarkers = settings.hasBeatMarkers();
            this.newBPM = newBPM;
        }

        public void setPattern(Pattern pattern) {
            this.pattern = pattern;
        }

        public void setPatternReplace(boolean patternReplace) {
            this.patternReplace = patternReplace;
        }

        public void addTrackPattern(TrackPattern tp) {
            trackPatterns.add(tp);
        }

        public void execute(MidiNote note, double newTime, boolean inPattern) {
            oldTimings.put(note.id, note.time);
            newTimings.put(note.id, newTime);
            notes.add(note);
            note.time = newTime;
            if (inPattern && patternReplace)
                pNotes.add(note);
        }

        public void execute(MidiNote note, double newTime) {
            execute(note, newTime, false);
        }

        public void executeNoQuantize() {
            if (pattern != null && patternReplace) {
                editor.getPatterns().add(pattern);
                editor.getPatterns().add(track, trackPatterns);
                editor.getMidi().removeNotes(track, pNotes);
            }
            track.bpm = newBPM;
            settings.setBeatMarkers(true);
        }

        @Override
        public void execute() {
            executeNoQuantize();
            applyTimings(newTimings);
        }

        @Override
        public void undo() {
            applyTimings(oldTimings);
            track.bpm = oldBPM;
            settings.setBeatMarkers(hadBeatMarkers);
            if (pattern != null && patternReplace) {
                editor.getPatterns().remove(pattern);
                editor.getMidi().addNotes(track, pNotes);
                editor.getLayer(PianoRollLayer.class).select(pattern.notes);
            }
        }

        private void applyTimings(Map<Long, Double> timings) {
            for (MidiNote note : notes) {
                Double time = timings.get(note.id);
                if (time != null)
                    note.time = time;
            }
            editor.repaint();
        }
    }
}
