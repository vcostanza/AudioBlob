package software.blob.audio.ui.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.JSONUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Struct that contains project data
 */
public class EditorProject {

    // Project file
    public File file;

    // General display settings
    public Settings settings;

    // Viewport settings
    public Viewport viewport;

    // Open sidebars
    public List<String> sidebars;

    // Selection bounds
    public Selection selection;

    // Tracks
    public List<Track> tracks;

    // Patterns
    public List<Pattern> patterns;

    public EditorProject() {
    }

    public EditorProject(Track... tracks) {
        this.tracks = new ArrayList<>(Arrays.asList(tracks));
    }

    public EditorProject(AudioEditor editor, File file) throws Exception {
        this(editor, file.getParentFile(), JSONUtils.readObject(file));
        this.file = file;
    }

    public EditorProject(AudioEditor editor, File dir, JSONObject json) throws Exception {

        // Display settings
        if (json.has("settings"))
            this.settings = new Settings(json.getJSONObject("settings"));

        // Viewport state
        if (json.has("viewport"))
            this.viewport = new Viewport(json.getJSONObject("viewport"));

        // Open sidebars
        if (json.has("sidebars")) {
            JSONArray arr = json.getJSONArray("sidebars");
            this.sidebars = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++)
                this.sidebars.add(arr.getString(i));
        }

        // Selected time range
        if (json.has("selection"))
            this.selection = new Selection(json.getJSONObject("selection"));

        if (json.has("tracks")) {
            // Load tracks list
            JSONArray tracks = json.getJSONArray("tracks");
            this.tracks = new ArrayList<>(tracks.length());
            for (int i = 0; i < tracks.length(); i++)
                this.tracks.add(new Track(editor, dir, tracks.getJSONObject(i)));
        } else {
            // Legacy single track project
            this.tracks = new ArrayList<>(1);
            this.tracks.add(new Track(editor, dir, json));
        }

        // Project-level BPM (legacy)
        if (json.has("bpm")) {
            if (this.settings == null)
                this.settings = new Settings();
            this.settings.bpm = json.getInt("bpm");
        }
        if (this.settings != null && this.settings.bpm > -1) {
            for (Track track : this.tracks)
                track.bpm = settings.bpm;
        }

        // Re-usable patterns
        if (json.has("patterns")) {
            JSONArray patterns = json.getJSONArray("patterns");
            this.patterns = new ArrayList<>(patterns.length());
            for (int i = 0; i < patterns.length(); i++)
                this.patterns.add(new Pattern(patterns.getJSONObject(i)));
        }
    }

    public JSONObject toJSON(File saveFile) {
        JSONObject json = new JSONObject();

        // Display settings
        if (this.settings != null)
            json.put("settings", this.settings.toJSON());

        // Viewport state
        if (this.viewport != null)
            json.put("viewport", this.viewport.toJSON());

        // Open sidebars
        if (this.sidebars != null) {
            JSONArray arr = new JSONArray();
            for (String sidebar : this.sidebars)
                arr.put(sidebar);
            json.put("sidebars", arr);
        }

        // Selection state
        if (this.selection != null)
            json.put("selection", this.selection.toJSON());

        // Track list
        if (this.tracks != null) {
            JSONArray tracks = new JSONArray();
            for (Track track : this.tracks)
                tracks.put(track.toJSON(saveFile));
            json.put("tracks", tracks);
        }

        // Patterns
        if (this.patterns != null) {
            JSONArray patterns = new JSONArray();
            for (Pattern pattern : this.patterns)
                patterns.put(pattern.toJSON());
            json.put("patterns", patterns);
        }

        return json;
    }

    /**
     * Get the max duration of all project tracks
     * @return Duration in seconds (minimum = 10)
     */
    public double getDuration() {
        double duration = 10; // 10 seconds default
        if (this.tracks != null) {
            for (Track track : this.tracks)
                duration = Math.max(duration, track.duration);
        }
        return duration;
    }

    /**
     * General project settings
     */
    public static class Settings {
        public int bpm = -1;
        public boolean bpmMarkers;
        public boolean bpmMisalignment;
        public boolean ampShading;

        public Settings() {
        }

        private Settings(JSONObject json) {
            this.bpm = json.optInt("bpm", -1);
            this.bpmMarkers = json.optBoolean("bpmMarkers");
            this.bpmMisalignment = json.optBoolean("bpmMisalignment");
            this.ampShading = json.optBoolean("ampShading");
        }

        private JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("bpmMarkers", this.bpmMarkers);
            json.put("bpmMisalignment", this.bpmMisalignment);
            json.put("ampShading", this.ampShading);
            return json;
        }
    }

    /**
     * Viewport settings
     */
    public static class Viewport {
        public double timeOffset;
        public double noteOffset;
        public double hZoom;
        public double vZoom;

        public Viewport() {
        }

        private Viewport(JSONObject json) {
            timeOffset = json.optDouble("timeOffset", 0);
            noteOffset = json.optDouble("noteOffset", 0);
            hZoom = json.optDouble("hZoom", 1);
            vZoom = json.optDouble("vZoom", 1);
        }

        private JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("timeOffset", timeOffset);
            json.put("noteOffset", noteOffset);
            json.put("hZoom", hZoom);
            json.put("vZoom", vZoom);
            return json;
        }
    }

    /**
     * Selection bounds
     */
    public static class Selection {
        public double startTime, endTime;

        public Selection(double startTime, double endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public Selection(JSONObject json) {
            this(json.optDouble("startTime", 0), json.optDouble("endTime", 0));
        }

        private JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("startTime", startTime);
            json.put("endTime", endTime);
            return json;
        }
    }
}
