package software.blob.audio.ui.editor.controllers;

import software.blob.audio.effects.sbsms.SBSMSEffect;
import software.blob.audio.ui.editor.events.*;
import software.blob.audio.ui.editor.instruments.*;
import software.blob.audio.playback.AudioHandle;
import software.blob.audio.thread.callback.FinishCallback;
import software.blob.audio.thread.callback.TaskCallback;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.layers.PianoRollLayer;
import software.blob.audio.ui.editor.midi.MidiDeviceMonitor;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.swing.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MIDI input reader and playback
 */
public class MidiController extends EditorController implements
        EditorInstrumentListener, EditorTrackListener, EditorProjectListener {

    private Instrument instrumentOverride;

    private final Map<String, List<InstrumentSample>> loading = new HashMap<>();
    private final Map<Integer, AudioHandle> noteHandles = new HashMap<>();
    private final Map<Integer, Long> lastNoteTimes = new HashMap<>();
    private final Map<Long, List<AudioHandle>> trackHandles = new HashMap<>();
    private final SampleLoader sampleLoader = new SampleLoader();
    private final MidiDeviceMonitor deviceMonitor = new MidiDeviceMonitor(new MidiInputReceiver());
    private final List<MidiNoteListener> listeners = new ArrayList<>();

    public MidiController(AudioEditor editor) {
        super(editor);
        sampleLoader.start();
    }

    @Override
    public void dispose() {
        stopNotes();
        deviceMonitor.dispose();
        sampleLoader.dispose();
    }

    public MidiDevice getLastConnectedDevice() {
        return deviceMonitor.getLastConnectedDevice();
    }

    private void loadInstrument(Instrument instrument) {
        for (int i = 36; i <= 84; i++)
            loadSample(instrument, i);
    }

    /**
     * Play a note a given velocity
     * @param value Note value
     * @param velocity Velocity (0 to 127)
     * @param time Time code in seconds to place the note on the piano roll
     *             -1 to not add the note
     */
    public void playNote(int value, int velocity, final double time) {
        // No velocity means the note is silent
        if (velocity <= 0)
            return;

        //Log.d(Misc.getNoteName(value, velocity));

        // Track must be selected
        final Track track = getTracks().getSelected();
        if (track == null)
            return;

        // Allow override from other components
        Instrument instrument = track.instrument;
        if (instrumentOverride != null)
            instrument = instrumentOverride;
        if (instrument == null)
            return;

        // Get the appropriate sample given note and velocity
        final InstrumentSample sample = instrument.getSample(value, velocity);
        if (sample == null)
            return;

        // Create handle for the sound we're about to play
        final AudioHandle handle = createPlaybackHandle(track, instrument, sample, velocity);
        if (handle == null)
            return;

        List<AudioHandle> handles;
        synchronized (noteHandles) {

            long ct = getAudioPlayer().curTime();
            long lt = lastNoteTimes.getOrDefault(value, 0L);
            if (ct - lt < AudioEditor.NOTE_EPSILON_MS)
                return;
            lastNoteTimes.put(value, ct);

            // Track looping audio by note
            noteHandles.put(value, handle);

            // Track audio played on the selected track
            handles = trackHandles.computeIfAbsent(track.id, k -> new ArrayList<>());
            handles.add(handle);
        }

        // Remove the sound from tracking when it finished
        handle.setCallback(new AudioHandle.Callback() {
            @Override
            public void onPlayback(double time, int frame) {
            }
            @Override
            public void onPlaybackFinished() {
                synchronized (noteHandles) {
                    handles.remove(handle);
                }
            }
        });

        // Queue the sound
        getAudioPlayer().queue(handle);

        // Add note to the piano roll if we're recording
        final MidiNote note = new MidiNote(value, velocity, time);
        SwingUtilities.invokeLater(() -> {
            for (MidiNoteListener l : listeners)
                l.onMidiNotePlayed(note, sample, handle);
        });
    }

    public void playNote(int value, int velocity) {
        playNote(value, velocity, getRecorder().getTimeCode());
    }

    /**
     * Stop playing a note
     * @param note Note value
     */
    public void stopNote(int note) {
        AudioHandle handle;
        synchronized (noteHandles) {
            handle = noteHandles.remove(note);
        }
        if (handle == null)
            return;

        if (handle.wav.isLoopable())
            getAudioPlayer().remove(handle);

        SwingUtilities.invokeLater(editor::repaint);
    }

    /**
     * Stop all notes being played
     */
    public void stopNotes() {
        List<AudioHandle> handles;
        synchronized (noteHandles) {
            handles = new ArrayList<>(noteHandles.values());
            noteHandles.clear();
            trackHandles.clear();
            lastNoteTimes.clear();
        }
        getAudioPlayer().removeAll(handles);
        SwingUtilities.invokeLater(editor::repaint);
    }

    /**
     * Return array of active notes (held down)
     * @return Active note values
     */
    public Set<Integer> getActiveNotes() {
        synchronized (noteHandles) {
            return new HashSet<>(noteHandles.keySet());
        }
    }

    /**
     * Create an {@link AudioHandle} for a given track and sample
     * @param track Audio track
     * @param inst Instrument to use
     * @param sample Sample to play
     * @param velocity Velocity (0 to 127)
     * @return Audio handle
     */
    public AudioHandle createPlaybackHandle(final Track track, Instrument inst, InstrumentSample sample, int velocity) {
        SampleWav wav = sample.getWav();
        if (wav == null || inst == null)
            return null;

        // Calculate velocity-based volume
        double maxAmp = inst.getMaxAmplitude();
        maxAmp = inst.isVelocityBasedAmp() ? MidiNote.getAmplitude(velocity, maxAmp) : maxAmp;
        final double velVol = maxAmp / wav.getPeakAmplitude();

        // Create handle for the sound we're about to play
        final Track.Layer layer = track.getLayer(PianoRollLayer.ID);
        final AudioHandle handle = getAudioPlayer().createHandle(wav);
        handle.setVolumeCalculator(() -> track.muted || layer.muted ? 0 : track.volume * layer.volume * velVol);
        return handle;
    }

    /**
     * Create an {@link AudioHandle} for a given track and sample
     * @param track Audio track (instrument must be non-null)
     * @param sample Sample to play
     * @param velocity Velocity (0 to 127)
     * @return Audio handle
     */
    public AudioHandle createPlaybackHandle(final Track track, InstrumentSample sample, int velocity) {
        return createPlaybackHandle(track, track.instrument, sample, velocity);
    }

    /**
     * Load sample for a given note (if needed)
     * @param instrument Instrument to load note for
     * @param note Note to load
     */
    public void loadSample(Instrument instrument, final int note) {
        if (note < 0)
            return;

        // Check if instrument supports note interpolation
        // If it doesn't then it'll just play the closest defined note
        if (instrument == null || !instrument.isInterpolationSupported())
            return;

        final String pathUID = instrument.getPathUID();
        final String key = pathUID + "_" + note;
        synchronized (loading) {
            if (loading.containsKey(key))
                return;
        }

        // Get all samples for a given note
        List<InstrumentSample> samples = instrument.getSamples(note);
        if (samples.isEmpty())
            return;

        InstrumentSample first = samples.get(0);

        // Note is already loaded
        if (first.note == note)
            return;

        // Note isn't the same as we requested - need to interpolate using SBSMS
        File instSampleDir = new File(editor.getCacheDirectory(), "instrument_samples");
        final File cacheDir = pathUID != null ? new File(instSampleDir, pathUID) : null;
        if (cacheDir != null && !cacheDir.exists() && !cacheDir.mkdirs())
            Log.e("Failed to make instrument cache dir: " + cacheDir);

        // Check if we have a cached version of these samples
        for (int i = 0; i < samples.size(); i++) {
            InstrumentSample sample = samples.get(i);
            String noteName = Misc.getNoteName(note, sample.velocity);
            File noteFile = new File(cacheDir, noteName + ".wav");
            if (noteFile.exists()) {
                instrument.addSample(noteFile);
                samples.remove(i--);
            }
        }

        // All the samples were cached locally so we're done
        if (samples.isEmpty())
            return;

        // Hold a weak reference to the instrument so that if it's
        // no longer used we can stop generating samples early on
        final WeakReference<Instrument> instWeak = new WeakReference<>(instrument);

        // Begin generating interpolated samples
        synchronized (loading) {
            loading.put(key, samples);
        }
        SBSMSEffect sbsms = new SBSMSEffect();
        sbsms.setPitch(Misc.getNoteFrequency(note) / first.frequency);
        for (InstrumentSample sample : samples) {
            final InstrumentSample s = sample;
            sampleLoader.submit(instrument, s, sbsms, (FinishCallback) results -> {
                Instrument inst = instWeak.get();
                if (inst != null && !results.isEmpty()) {
                    WavData wav = results.get(0);
                    inst.addSample(new InstrumentSample(note, s.velocity, wav));

                    // Cache so we don't have to perform this very slow operation again
                    String noteName = Misc.getNoteName(note, s.velocity);
                    File noteFile = new File(cacheDir, noteName + ".wav");
                    wav.writeToFile(noteFile);
                    Log.d("Cached note " + noteName + " to " + noteFile);
                }
                synchronized (loading) {
                    List<InstrumentSample> sLoading = loading.get(key);
                    if (sLoading == null || !sLoading.remove(s))
                        return;
                    if (sLoading.isEmpty())
                        loading.remove(key);
                }
            });
        }
    }

    /**
     * Add notes to a track and fire listeners
     * @param track Track
     * @param notes MIDI notes
     */
    public void addNotes(Track track, Collection<MidiNote> notes) {
        if (notes == null || notes.isEmpty())
            return;
        if (track.notes == null)
            track.notes = new MidiNoteList(notes.size());
        if (track.notes.addAll(notes)) {
            for (MidiNoteListener l : listeners)
                l.onMidiNotesAdded(track, notes);
        }
    }

    /**
     * Remove notes from a track and fire listeners
     * @param track Track
     * @param notes MIDI notes
     */
    public void removeNotes(Track track, Collection<MidiNote> notes) {
        if (track.notes != null && track.notes.removeAll(notes)) {
            for (MidiNoteListener l : listeners)
                l.onMidiNotesRemoved(track, notes);
        }
    }

    /**
     * Set the instrument override so notes are played using this
     * instrument over the selected track instrument
     * @param instrument Instrument (null to default)
     */
    public void setInstrumentOverride(Instrument instrument) {
        instrumentOverride = instrument;
    }

    public void addListener(MidiNoteListener l) {
        listeners.add(l);
    }

    public void removeListener(MidiNoteListener l) {
        listeners.remove(l);
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(MidiNoteListener.class));
    }

    @Override
    public void onInstrumentChanged(Track track, Instrument instrument) {
        loadInstrument(instrument);
    }

    @Override
    public void onLoadProject(EditorProject project) {
        for (Track track : project.tracks) {
            loadInstrument(track.instrument);
        }
    }

    @Override
    public void onTrackRemoved(Track track) {
        List<AudioHandle> handles;
        synchronized (noteHandles) {
            handles = trackHandles.get(track.id);
        }
        getAudioPlayer().removeAll(handles);
    }

    private static class SampleProcessTask implements Runnable {

        private final WeakReference<Instrument> instrument;
        private final InstrumentSample sample;
        private final SBSMSEffect sbsms;
        private final TaskCallback callback;

        SampleProcessTask(Instrument instrument, InstrumentSample sample,
                          SBSMSEffect sbsms, TaskCallback callback) {
            this.instrument = new WeakReference<>(instrument);
            this.sample = sample;
            this.sbsms = sbsms;
            this.callback = callback;
        }

        @Override
        public void run() {
            WavData wav = null;
            if (instrument.get() != null) {
                wav = sample.getWav();
                try {
                    wav = sbsms.process(wav);
                    wav.setPeakAmplitude(1);
                } catch (Exception e) {
                    Log.e("Failed to execute task", e);
                }
            }
            if (callback != null)
                callback.onFinished(wav != null ? Collections.singletonList(wav) : Collections.emptyList());
        }
    }

    private static class SampleLoader extends Thread {

        private final ExecutorService threadPool = Executors.newFixedThreadPool(4);
        private final List<SampleProcessTask> tasks = new ArrayList<>();
        private boolean running;

        SampleLoader() {
            setDaemon(true);
        }

        @Override
        public synchronized void start() {
            running = true;
            super.start();
        }

        public void dispose() {
            running = false;
        }

        synchronized void submit(Instrument instrument, InstrumentSample sample,
                                 SBSMSEffect sbsms, TaskCallback callback) {
            tasks.add(new SampleProcessTask(instrument, sample, sbsms, callback));
        }

        synchronized void clear() {
            tasks.clear();
        }

        @Override
        public void run() {
            super.run();
            while (running) {
                processTasks();
                try {
                    Thread.sleep(100);
                } catch (Exception ignored) {
                }
            }
        }

        private void processTasks() {
            List<SampleProcessTask> tasks;
            synchronized (this.tasks) {
                if (this.tasks.isEmpty())
                    return;

                tasks = new ArrayList<>(this.tasks);
                this.tasks.clear();
            }
            for (SampleProcessTask t : tasks)
                threadPool.submit(t);
        }
    }

    private class MidiInputReceiver implements Receiver {

        @Override
        public void send(MidiMessage msg, long timeStamp) {
            final double timeSecs = getRecorder().getTimeCode();
            if (msg instanceof ShortMessage) {
                ShortMessage sm = (ShortMessage) msg;
                int command = sm.getCommand();
                switch (command) {
                    // Note presses
                    case ShortMessage.NOTE_ON:
                    case ShortMessage.NOTE_OFF: {
                        int note = sm.getData1();
                        int velocity = sm.getData2();
                        //Log.d("Note: " + Misc.getNoteName(note) + " (" + velocity + ")");
                        if (velocity > 0 && command == ShortMessage.NOTE_ON)
                            playNote(note, velocity, timeSecs);
                        else
                            stopNote(note);
                        break;
                    }
                }
            }
        }

        @Override
        public void close() {
        }
    }
}
