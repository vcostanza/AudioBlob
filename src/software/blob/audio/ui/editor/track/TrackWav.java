package software.blob.audio.ui.editor.track;

import org.json.JSONObject;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.FileUtils;

import java.io.File;

/**
 * Waveform that can be placed somewhere in the editor timeline
 */
public class TrackWav extends WavData {

    // The associated track/layer for this wav
    public Track track;
    public Track.Layer layer;

    // The time offset for this wav in seconds
    public double time;

    /**
     * Create a track wav "pointer" for existing wav data
     * This will NOT perform a deep copy of the source wav data
     * @param track Associated track
     * @param wav Wav data
     * @param time Time in seconds
     */
    public TrackWav(Track track, Track.Layer layer, WavData wav, double time) {
        super(wav.samples, wav.sampleRate);
        this.track = track;
        this.layer = layer;
        this.file = wav.file;
        this.name = wav.name;
        this.time = time;
    }

    public TrackWav(Track track, Track.Layer layer, WavData wav) {
        this(track, layer, wav, 0);
    }

    public TrackWav(Track track, Track.Layer layer, int channels, double duration, int sampleRate) {
        super(channels, duration, sampleRate);
        this.track = track;
        this.layer = layer;
    }

    public TrackWav(Track track, File file, double time) throws Exception {
        super(file);
        this.track = track;
        this.time = time;
    }

    public TrackWav(Track track, File dir, JSONObject json) throws Exception {
        this(track, FileUtils.getFile(dir, json.getString("path")), json.getDouble("time"));
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("path", file.getAbsolutePath());
        json.put("time", time);
        return json;
    }
}
