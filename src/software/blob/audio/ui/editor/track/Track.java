package software.blob.audio.ui.editor.track;

import org.json.JSONArray;
import org.json.JSONObject;
import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.layers.EditorLayer;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.pitchcurve.LegacyPitchCurve;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;
import software.blob.audio.util.IDGenerator;
import software.blob.audio.util.Misc;
import software.blob.ui.util.ColorUtils;
import software.blob.ui.util.FileUtils;
import software.blob.audio.util.JSONUtils;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Track containing notes and pitch curves
 */
public class Track {

    // Default track colors
    public static final Color[] DEFAULT_COLORS = {
            new Color(0xff8080),
            new Color(0xffC080),
            //new Color(0xffff80),
            //new Color(0xC0ff80),
            new Color(0x80ff80),
            new Color(0x80ffC0),
            new Color(0x80ffff),
            new Color(0x80C0ff),
            new Color(0x8080ff),
            new Color(0xC080ff),
            new Color(0xff80ff),
            new Color(0xff80C0)
    };

    public static final int BPM_DEFAULT = 120;

    private static final IDGenerator ID_GEN = new IDGenerator();

    // UID
    public final transient long id;

    // Track name
    public String name;

    // Color
    public Color color;

    // BPM
    public int bpm;

    // Playback instrument
    public Instrument instrument;

    // Input reference wav
    public TrackWav seed;

    // Pitch curves
    public PitchCurveList curves;

    // MIDI notes (piano roll)
    public MidiNoteList notes;

    // Patterns containing repeatable notes and curves
    public List<TrackPattern> patterns;

    // Volume of this track (0 to 1)
    public double volume;

    // Whether this track is muted or not
    // This is separate from volume because we want to remember
    // the existing volume level after toggling a track off then on
    public boolean muted;

    // Visibility of the track
    public boolean visible;

    // Track is selected (only used during save/load)
    public boolean selected;

    // Layers mapped by name
    private final Map<String, Layer> layers = new HashMap<>();

    // Duration of the project track in seconds
    public transient double duration;

    public Track(AudioEditor editor, String name) {
        this.name = name;
        this.id = ID_GEN.createID();
        this.color = getRandomColor();
        this.bpm = BPM_DEFAULT;
        this.volume = 1.0;
        this.muted = false;
        this.visible = true;
        for (EditorLayer layer : editor.getLayers()) {
            String id = layer.getID();
            if (id != null)
                this.layers.put(id, new Layer(layer));
        }
    }

    public Track(AudioEditor editor, File dir, JSONObject json) throws Exception {
        this(editor, json.optString("name", "Untitled"));

        this.bpm = json.optInt("bpm", BPM_DEFAULT);
        this.volume = Misc.clamp(json.optDouble("volume", 1.0), 0.0, 1.0);
        this.muted = json.optBoolean("muted");
        this.visible = json.optBoolean("visible", true);
        this.selected = json.optBoolean("selected", false);

        if (json.has("color"))
            this.color = ColorUtils.fromHexString(json.getString("color"));

        // Layer controls
        JSONObject layers = json.optJSONObject("layers");
        if (layers != null) {
            for (String key : layers.keySet()) {
                JSONObject layerJSON = layers.optJSONObject(key);
                if (layerJSON == null)
                    continue;
                Layer layer = this.layers.get(layerJSON.optString("name"));
                if (layer != null)
                    layer.init(layerJSON);
            }
        }

        if (json.has("instrument")) {
            this.instrument = new Instrument(FileUtils.getFile(dir, json.getString("instrument")));
            if (this.name.equals("Untitled"))
                this.name = this.instrument.getName();
        }

        if (json.has("seed")) {
            Object seed = json.get("seed");
            if (seed instanceof String)
                this.seed = new TrackWav(this, FileUtils.getFile(dir, (String) seed), 0);
            else if (seed instanceof JSONObject)
                this.seed = new TrackWav(this, dir, (JSONObject) seed);
            if (this.seed != null)
                this.seed.layer = getLayer("waveform");
        }

        if (json.has("curves")) {
            Object o = json.get("curves");
            if (o instanceof JSONArray)
                this.curves = new PitchCurveList((JSONArray) o);
            else if (o instanceof JSONObject)
                this.curves = new LegacyPitchCurve((JSONObject) o).upgrade();
        }

        if (json.has("notes"))
            this.notes = new MidiNoteList(json.getJSONArray("notes"));

        if (json.has("patterns")) {
            JSONArray arr = json.getJSONArray("patterns");
            this.patterns = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                TrackPattern tp = new TrackPattern(arr.getJSONObject(i));
                this.patterns.add(tp);
            }
        }

        updateDuration();
    }

    public JSONObject toJSON(File saveFile) {
        JSONObject json = new JSONObject();

        String name = this.name != null ? this.name : "Untitled";
        json.put("name", name);
        json.put("bpm", this.bpm);
        json.put("volume", this.volume);
        json.put("muted", this.muted);
        json.put("visible", this.visible);

        if (this.selected)
            json.put("selected", true);

        if (this.color != null)
            json.put("color", "#" + Integer.toHexString(this.color.getRGB()));

        // Layer controls
        JSONObject layers = new JSONObject();
        for (Layer layer : this.layers.values())
            JSONUtils.putIfNonEmpty(layers, layer.name, layer.toJSON());
        JSONUtils.putIfNonEmpty(json, "layers", layers);

        // Instrument
        if (instrument != null) {
            String path = instrument.getPath();
            if (path == null) {
                String baseName = FileUtils.stripExtension(saveFile);
                String instName = instrument.getName().replace(" ", "_").toLowerCase();
                File dir = saveFile.getParentFile();
                File projectDir = new File(dir, baseName + "_data");
                File sampleDir = new File(projectDir, instName);
                int suffix = 1;
                while (sampleDir.exists())
                    sampleDir = new File(projectDir, instName + "_" + (++suffix));
                File instFile = new File(sampleDir, instName + ".inst");
                if (instrument.writeToFile(instFile, sampleDir, true))
                    path = instFile.getAbsolutePath();
            }
            if (path != null)
                json.put("instrument", FileUtils.getRelativePath(
                        saveFile.getParentFile(), new File(path)));
        }

        // Seed WAV
        if (seed != null)
            json.put("seed", seed.toJSON());

        // Pitch curves
        if (curves != null && !curves.isEmpty())
            json.put("curves", curves.toJSON());

        // MIDI notes
        if (notes != null && !notes.isEmpty())
            json.put("notes", notes.toJSON());

        // Pattern instances
        if (patterns != null && !patterns.isEmpty()) {
            JSONArray arr = new JSONArray(patterns.size());
            for (TrackPattern pattern : patterns)
                arr.put(pattern.toJSON());
            json.put("patterns", arr);
        }

        return json;
    }

    /**
     * Update the duration of this track based on its contents
     */
    public void updateDuration() {
        double nDur = notes != null ? notes.getBounds().maxTime + AudioEditor.NOTE_DURATION : 0;
        double wDur = seed != null ? seed.time + seed.duration : 0;
        double cDur = 0;
        if (curves != null) {
            curves.updateBounds();
            cDur = curves.getMaxTime();
        }
        double pDur = 0;
        if (patterns != null) {
            for (TrackPattern tp : patterns)
                pDur = Math.max(pDur, tp.getMaxTime());
        }
        duration = Misc.max(cDur, wDur, nDur, pDur) + 1;
    }

    /**
     * Check if this track is effectively muted (either toggled off or volume is zero)
     * @return True if muted
     */
    public boolean isMuted() {
        return this.muted || this.volume <= 0;
    }

    /**
     * Get a layer control given its ID
     * @param id ID string
     * @return Layer
     */
    public Layer getLayer(String id) {
        return this.layers.get(id);
    }

    /**
     * Get all track layers
     * @return Track layers
     */
    public List<Layer> getLayers() {
        return new ArrayList<>(this.layers.values());
    }

    /**
     * Check if a specific layer in this track is currently visible
     * Note: If the track itself isn't visible then the layer is not considered visible either
     * @param id Layer ID
     * @return True if visible
     */
    public boolean isLayerVisible(String id) {
        if (!this.visible)
            return false;
        Layer layer = getLayer(id);
        return layer != null && layer.visible;
    }

    @Override
    public String toString() {
        return name;
    }

    /* Static helper methods */

    /**
     * Layer control
     */
    public static class Layer {
        public final String name;
        public boolean visible;
        public double volume;
        public boolean muted;

        public Layer(EditorLayer layer) {
            this.name = layer.getName();
            this.volume = 1.0;
            this.visible = true;
        }

        public void init(JSONObject json) {
            this.visible = json.optBoolean("visible", true);
            this.volume = json.optDouble("volume", 1.0);
            this.muted = json.optBoolean("muted");
        }

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            if (!this.visible)
                json.put("visible", this.visible);
            if (this.volume < 1.0)
                json.put("volume", this.volume);
            if (this.muted)
                json.put("muted", this.muted);
            return json;
        }
    }

    /**
     * Get a random track color
     * @return Random color from list of default track colors
     */
    public static Color getRandomColor() {
        return Misc.random(DEFAULT_COLORS);
    }
}
