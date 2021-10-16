package software.blob.audio.ui.editor;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.playback.AudioPlayer;
import software.blob.audio.ui.editor.controllers.*;
import software.blob.audio.ui.editor.events.*;
import software.blob.audio.ui.editor.layers.*;
import software.blob.audio.ui.editor.track.Track;
import software.blob.ui.util.FileUtils;
import software.blob.ui.view.View;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.util.Log;
import software.blob.audio.util.Misc;
import software.blob.ui.view.layout.LinearLayout;

import java.awt.*;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * The main class for the AudioBlob editor
 */
@SuppressWarnings("unused")
public class AudioEditor extends View {

    private static final String TAG = "PitchEditor";

    private static final String PREF_LAST_INSTRUMENT = "last_instrument";

    // The maximum amount of error allowed around a note's exact frequency
    public static final float NOTE_THRESHOLD = 0.25f;

    // The default visual duration of a note in seconds
    public static final float NOTE_DURATION = 0.1f;

    // The lowest valid note (C0)
    public static final int MIN_NOTE = 12;
    public static final int MAX_NOTE = (int) Math.floor(Misc.getNoteValue(22050));

    // Default duration (seconds)
    public static final int DURATION_DEFAULT = 10;

    // The minimum amount of time allowed between notes before they're considered a chord
    public static final long NOTE_EPSILON_MS = 50;

    // The maximum allowed BPM value
    public static final int MAX_BPM = (int) Math.floor(60000d / NOTE_EPSILON_MS);

    // Window frame and its layout
    private final AudioEditorFrame frame;
    private final LinearLayout body;
    private final InflatedLayout layout;
    private final Preferences prefs;

    // Edit mode
    private EditorMode mode = EditorMode.CLICK;

    // Controllers
    private MenuController menu;
    private ButtonController buttons;
    private SidebarController sidebar;
    private MouseController mouse;
    private ViewportController viewport;
    private PlaybackController playback;
    private RecordController recorder;
    private SelectionController selection;
    private ChangeController changes;
    private MidiController midi;
    private TrackController tracks;
    private PatternController patterns;
    private TrackLayerController layerCtrl;
    private ContextMenuController contextMenu;
    private SettingsController settings;
    private final List<EditorController> controllers = new ArrayList<>();
    private final Map<Class<? extends EditorController>, EditorController> controllerMap = new HashMap<>();

    // Layers (in draw order)
    private NoteBarsLayer noteBars;
    private TimeMarkersLayer timeMarkersLayer;
    private WaveformLayer waveformLayer;
    private PitchCurvesLayer pitchCurves;
    private PianoRollLayer pianoRoll;
    private SelectionLayer selectionLayer;
    private TimeCursorLayer timeCursor;
    private TracksLayer tracksLayer;
    private PatternsLayer patternsLayer;
    private NoteMarkersLayer noteMarkers;
    private final List<EditorLayer> layers = new ArrayList<>();
    private final Map<Class<? extends EditorLayer>, EditorLayer> layerMap = new HashMap<>();

    // Set by user
    private File baseDir, projectFile;
    private double duration;
    private AudioPlayer audioPlayer;

    // Draw variables
    private int margin, widthPd, heightPd;
    private double pixelsPerNote, pixelsPerSecond;

    // For keeping the PC awake while we're working
    private Robot robot;
    private long lastKeepAwake;

    public AudioEditor(AudioEditorFrame frame, InflatedLayout layout) {
        this.frame = frame;
        this.layout = layout;
        this.prefs = Preferences.userNodeForPackage(getClass());
        this.body = layout.findByName("body");

        setBackground(Color.BLACK);

        try {
            setAudioPlayer(new AudioPlayer());
        } catch (Exception e) {
            Log.e("Failed to initialize audio player", e);
            return;
        }

        try {
            this.robot = new Robot();
        } catch (Exception e) {
            Log.e("Failed to make insomnia bot - keep-awake function disabled", e);
        }

        // Automatically instantiate all controllers and layers
        initComponents();

        // Create a new project
        createNewProject();

        // Read preferences
        String lastInstPath = prefs.get(PREF_LAST_INSTRUMENT, null);
        if (lastInstPath != null)
            setInstrument(loadInstrument(new File(lastInstPath)));
    }

    /**
     * Shutdown operation
     */
    public void dispose() {
        File tmp = getTempDirectory();
        if (tmp.exists())
            FileUtils.deleteDirectory(tmp);
    }

    /**
     * Get the main editor frame
     * @return Frame
     */
    public AudioEditorFrame getFrame() {
        return this.frame;
    }

    /**
     * Get the root layout
     * @return Layout view
     */
    public LinearLayout getRoot() {
        return this.layout.getRoot();
    }

    /**
     * Get the inflated layout instance
     * @return Inflated layout data
     */
    public InflatedLayout getInflatedLayout() {
        return layout;
    }

    /**
     * Get the playback controller
     * @return Playback controller
     */
    public PlaybackController getPlayback() {
        return this.playback;
    }

    /**
     * Get the record controller
     * @return Record controller
     */
    public RecordController getRecorder() {
        return this.recorder;
    }

    /**
     * Get the viewport controller
     * @return Viewport controller
     */
    public ViewportController getViewport() {
        return this.viewport;
    }

    /**
     * Get the time selection controller
     * @return selection controller
     */
    public SelectionController getSelection() {
        return this.selection;
    }

    /**
     * Get the undo/redo change controller
     * @return Change controller
     */
    public ChangeController getChanges() {
        return this.changes;
    }

    /**
     * Get the MIDI input controller
     * @return MIDI controller
     */
    public MidiController getMidi() {
        return this.midi;
    }

    /**
     * Get the tracks manager
     * @return Tracks controller
     */
    public TrackController getTracks() {
        return this.tracks;
    }

    /**
     * Get the patterns manager
     * @return Patterns controller
     */
    public PatternController getPatterns() {
        return this.patterns;
    }

    /**
     * Get the editor settings
     * @return Settings controller
     */
    public SettingsController getSettings() {
        return this.settings;
    }

    /**
     * Open a project file
     * @param project Project to open
     */
    public void open(EditorProject project) {
        // Dispose existing project data
        close();

        // Update project file
        setProjectFile(project.file);

        // Read metadata
        duration = project.getDuration();

        // Notify components that a new project has been opened
        for (EditorProjectListener l : getEditorListeners(EditorProjectListener.class))
            l.onLoadProject(project);
    }

    /**
     * Create a new empty project
     */
    public void createNewProject() {
        Track track = new Track(this, "Track 1");
        track.duration = DURATION_DEFAULT;
        open(new EditorProject(track));
    }

    /**
     * Dispose project data
     */
    public void close() {
        this.tracks.clear();
        repaint();
    }

    /**
     * Set the instrument to use for sample output
     * @param instrument Sample instrument
     */
    public void setInstrument(Instrument instrument) {
        Track selected = this.tracks.getSelected();
        if (selected != null && !Objects.equals(selected.instrument, instrument)) {
            boolean useInstName = selected.name.startsWith("Track ");
            if (selected.instrument != null)
                useInstName = selected.instrument.getName().equals(selected.name);
            if (useInstName && instrument != null)
                selected.name = instrument.getName();
            selected.instrument = instrument;
            String path = instrument != null ? instrument.getPath() : null;
            if (path != null)
                this.prefs.put(PREF_LAST_INSTRUMENT, path);
            for (EditorInstrumentListener l : getEditorListeners(EditorInstrumentListener.class))
                l.onInstrumentChanged(selected, instrument);
        }
    }

    /**
     * Load an instrument from a file
     * @param instFile Instrument metadata file or directory containing samples
     * @return Instrument or null if failed to load
     */
    public Instrument loadInstrument(File instFile) {
        Instrument inst = null;
        try {
            inst = new Instrument(instFile);
            if (inst.getSampleCount() == 0)
                return null;
            if (audioPlayer != null) {
                inst.setSampleRate(audioPlayer.getSampleRate());
                inst.setChannels(audioPlayer.getChannels());
            }
        } catch (Exception e) {
            Log.e("Failed to load instrument", e);
        }
        return inst;
    }

    /**
     * Set the audio player instance
     * @param audioPlayer Audio player
     */
    public void setAudioPlayer(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
    }

    /**
     * Get the audio player instance used for sound playback
     * @return Audio player
     */
    public AudioPlayer getAudioPlayer() {
        return this.audioPlayer;
    }

    /**
     * Set the base directory to start import/export from
     * @param baseDir Base directory
     */
    public void setBaseDirectory(File baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Get the base/home directory
     * @return Base directory
     */
    public File getBaseDirectory() {
        return this.baseDir;
    }

    /**
     * Get the cache directory
     * @return Cache directory
     */
    public File getCacheDirectory() {
        return new File(this.baseDir, ".cache" + File.separator + "AudioBlob");
    }

    /**
     * Get the temporary files directory (cleared on shutdown)
     * @return Temporary files directory
     */
    public File getTempDirectory() {
        return new File(getCacheDirectory(), "tmp");
    }

    /**
     * Set the active project file
     * @param projectFile Project file
     */
    public void setProjectFile(File projectFile) {
        this.projectFile = projectFile;
        frame.setTitle(projectFile != null ? projectFile.getName() : "New Project");
    }

    /**
     * Get the active project file
     * @return Project file or null if a new project is open
     */
    public File getProjectFile() {
        return this.projectFile;
    }

    /**
     * Set context menu layout
     * @param window Window to attach context menu to
     * @param view Layout view
     */
    public void showContextMenu(Window window, Component view) {
        contextMenu.show(window, view);
    }

    public void showContextMenu(Component view) {
        showContextMenu(frame, view);
    }

    public void hideContextMenu() {
        contextMenu.hide();
    }

    /**
     * Get the total duration of the viewport
     * @return Duration in seconds
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Set the total duration of the viewport
     * @param duration Duration in seconds
     */
    public void setDuration(double duration) {
        if (Double.compare(this.duration, duration) != 0) {
            this.duration = duration;
            repaint();
        }
    }

    /**
     * Update the total duration based on the maximum track duration
     */
    public void updateDuration() {
        double maxDur = DURATION_DEFAULT;
        for (Track track : getTracks()) {
            track.updateDuration();
            maxDur = Math.max(maxDur, track.duration);
        }
        setDuration(maxDur);
    }

    /**
     * Get the current mode
     * @return Editor mode
     */
    public EditorMode getMode() {
        return this.mode;
    }

    /**
     * Set the current mode
     * @param mode Editor mode
     */
    public void setMode(EditorMode mode) {
        this.mode = mode;
        setCursor(mode.cursor);
        for (EditorModeListener l : getEditorListeners(EditorModeListener.class))
            l.onEditorModeChanged(mode);
    }

    /**
     * Get the controller instance given a class that extends {@link EditorController}
     * @param clazz Editor controller class
     * @return Controller or null if not found
     */
    public <T extends EditorController> T getController(Class<T> clazz) {
        EditorController ctrl = controllerMap.get(clazz);
        return clazz.isInstance(ctrl) ? clazz.cast(ctrl) : null;
    }

    /**
     * Get the layer instance given a class that extends {@link EditorLayer}
     * @param clazz Editor layer class
     * @return Layer or null if not found
     */
    public <T extends EditorLayer> T getLayer(Class<T> clazz) {
        EditorLayer layer = layerMap.get(clazz);
        return clazz.isInstance(layer) ? clazz.cast(layer) : null;
    }

    /**
     * Get the list of layers (in draw order)
     * @return Layers list
     */
    public List<EditorLayer> getLayers() {
        return this.layers;
    }

    /**
     * Get all interfaces of a certain class
     * The order is layers (reverse draw order) -> controllers
     * @param clazz Interface class
     * @param <T> Class
     * @return Listeners
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getEditorListeners(Class<T> clazz) {
        List<T> ret = new ArrayList<>();
        for (int i = layers.size() - 1; i >= 0; i--) {
            EditorLayer w = layers.get(i);
            if (clazz.isInstance(w))
                ret.add((T) layers.get(i));
        }
        for (EditorController ctrl : controllers) {
            if (clazz.isInstance(ctrl))
                ret.add((T) ctrl);
        }
        return ret;
    }

    /**
     * Get the default drawing margin
     * @return Margin in pixels
     */
    public int getEditorMargin() {
        return margin;
    }

    /**
     * Convert the given time code to an X-value on the timeline
     * @param time Time in seconds
     * @return X component
     */
    public int getX(double time) {
        return (int) Math.round((time - viewport.getTimeOffset()) * pixelsPerSecond) + margin;
    }

    /**
     * Convert the given note value to a Y-value on the timeline
     * @param note Note value
     * @return Y component
     */
    public int getY(double note) {
        return getHeight() - (int) Math.round((note - viewport.getNoteOffset()) * pixelsPerNote);
    }

    /**
     * Convert the given X-value to a time code on the timeline
     * @param x X component
     * @return Time in seconds
     */
    public double getTime(int x) {
        double time = ((x - margin) / pixelsPerSecond) + viewport.getTimeOffset();
        return Math.max(time, 0);
    }

    /**
     * Convert the given Y-value to a note value on the timeline
     * @param y Y component
     * @return Note value
     */
    public double getNote(int y) {
        double note = ((getHeight() - y) / pixelsPerNote) + viewport.getNoteOffset();
        return Misc.clamp(note, MIN_NOTE, MAX_NOTE);
    }

    /**
     * Convert the given Y-value to a frequency
     * @param y Y component
     * @return Frequency in hertz
     */
    public double getFrequency(int y) {
        return Misc.getNoteFrequency(getNote(y));
    }

    /**
     * Get the minimum valid note value
     * @return Minimum note value
     */
    public int getMinValidNote() {
        return MIN_NOTE;
    }

    /**
     * Get the maximum valid note value
     * @return Maximum note value
     */
    public int getMaxValidNote() {
        return MAX_NOTE;
    }

    /**
     * Calculate the range of valid notes
     * @return Valid note range
     */
    public int getValidNoteRange() {
        return (MAX_NOTE - MIN_NOTE) + 1;
    }

    /**
     * Get the number of pixels per second displayed
     * @return Pixels per second
     */
    public double getPixelsPerSecond() {
        return this.pixelsPerSecond;
    }

    /**
     * Get the number of pixels per note displayed
     * @return Pixels per note
     */
    public double getPixelsPerNote() {
        return this.pixelsPerNote;
    }

    /**
     * Update viewport parameters
     * @param fireListeners True to call {@link EditorViewportListener#onViewportChanged()}
     */
    public void updateViewport(boolean fireListeners) {
        // Conversion values
        pixelsPerNote = (heightPd / viewport.getBaseNoteRange()) * viewport.vZoomSq;
        pixelsPerSecond = (widthPd / viewport.getBaseTimeRange()) * viewport.hZoomSq;
        if (fireListeners) {
            for (EditorViewportListener l : getEditorListeners(EditorViewportListener.class))
                l.onViewportChanged();
        }
    }

    public void updateViewport() {
        updateViewport(true);
    }

    /**
     * Update the current display cursor based on the layers
     */
    public void updateCursor() {
        Cursor cursor = null;
        for (EditorLayer layer : layers) {
            Cursor wCursor = layer.getCursor();
            if (wCursor != null)
                cursor = wCursor;
        }

        // Update cursor if needed
        setCursor(cursor != null ? cursor : mode.cursor);
    }

    /**
     * Request to keep the PC awake
     */
    public void keepAwake() {
        if (robot == null)
            return;
        long curTime = System.currentTimeMillis();
        if (curTime - lastKeepAwake > 10000) {
            // XXX - This is sort of hacky, but it does the job without interrupting user input
            Point p = MouseInfo.getPointerInfo().getLocation();
            robot.mouseMove(p.x, p.y);
            lastKeepAwake = curTime;
        }
    }

    /**
     * Main drawing method for layers
     * @param g 2D graphics
     */
    @Override
    protected void paint(Graphics2D g) {
        super.paint(g);

        //long start = System.currentTimeMillis();

        int w = body.getWidth();
        int h = body.getHeight();
        int oldWidth = widthPd, oldHeight = heightPd;
        //margin = Math.min(w, h) / 16;
        margin = 40;
        widthPd = w - margin;
        heightPd = h - margin;

        // Notify layers of size change
        if (oldWidth != widthPd || oldHeight != heightPd)
            updateViewport();

        // Draw layers
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (EditorLayer layer : layers)
            layer.paint(g);

        //Log.d("Took " + (System.currentTimeMillis() - start) + " ms to draw");
    }

    /* PRIVATE */

    /**
     * Lombok-style initialization of all {@link EditorController} and {@link EditorLayer} fields
     */
    private void initComponents() {
        Field[] fields = getClass().getDeclaredFields();
        for (Field f : fields) {
            Class<?> type = f.getType();
            if (EditorController.class.isAssignableFrom(type) || EditorLayer.class.isAssignableFrom(type)) {
                try {
                    Constructor<?> ctor = type.getConstructor(AudioEditor.class);
                    Object component = ctor.newInstance(this);
                    f.set(this, component);
                    if (component instanceof EditorController) {
                        EditorController ctrl = (EditorController) component;
                        controllers.add(ctrl);
                        controllerMap.put(ctrl.getClass(), ctrl);
                    } else {
                        EditorLayer layer = (EditorLayer) component;
                        layers.add(layer);
                        layerMap.put(layer.getClass(), layer);
                    }
                } catch (Exception e) {
                    Log.e("Failed to instantiate controller: " + type, e);
                }
            }
        }

        // Refresh listeners for each controller
        for (EditorController controller : controllers)
            controller.refreshListeners();
    }
}
