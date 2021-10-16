package software.blob.audio.ui.editor.track.generator;

import software.blob.audio.thread.WavProcessorService;
import software.blob.audio.ui.DialogProgressCallback;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.layers.EditorLayer;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate WAV data from the editor timeline
 */
public class WavGenerator {

    private final AudioEditor editor;

    public WavGenerator(AudioEditor editor) {
        this.editor = editor;
    }

    /**
     * Begin generating WAV
     * @param params Parameters
     * @param callback Callback once WAV is finished or failed
     */
    public void generate(final WavGeneratorParams params, final WavGeneratorCallback callback) {

        // Make sure parameters are valid
        if (!params.isValid()) {
            fail(callback);
            return;
        }

        // Get all layers that implement WavGeneratorLayer
        List<WavGeneratorLayer> layers = new ArrayList<>();
        for (EditorLayer layer : editor.getLayers()) {
            if (layer instanceof WavGeneratorLayer && !params.excludeLayers.contains(layer))
                layers.add((WavGeneratorLayer) layer);
        }

        List<WavGeneratorTask> tasks = new ArrayList<>();
        //final TrackWav output = new TrackWav(params.channels, params.getDuration(), params.sampleRate);

        // Begin gathering processor tasks
        for (WavGeneratorLayer layer : layers) {
            for (Track track : editor.getTracks()) {

                // Ignore muted track/layer
                if (params.ignoreMuted) {
                    if (track.isMuted())
                        continue;
                    if (layer instanceof EditorLayer) {
                        Track.Layer l = ((EditorLayer) layer).getTrackLayer(track);
                        if (l != null && (l.muted || l.volume <= 0))
                            continue;
                    }
                }

                List<WavGeneratorTask> layerTasks = layer.getGeneratorTasks(track, params);
                if (layerTasks != null)
                    tasks.addAll(layerTasks);
            }
        }

        // Nothing to play - Return empty wav
        if (tasks.isEmpty()) {
            if (callback != null)
                callback.onWavGenerated(new ArrayList<>(), params);
            return;
        }

        DialogProgressCallback pcb = new DialogProgressCallback(editor.getFrame(), "Generating output", results -> {
            try {
                onFinish(params, results, callback);
            } catch (Exception e) {
                Log.e("Failed to process results", e);
                SwingUtilities.invokeLater(() -> fail(callback));
            }
        });
        pcb.setOnCancel(() -> fail(callback));
        new WavProcessorService().executeAsync(tasks, pcb);
    }

    private void onFinish(final WavGeneratorParams params, List<WavData> results, final WavGeneratorCallback callback) {
        // Nothing to play
        if (results.isEmpty()) {
            SwingUtilities.invokeLater(() -> fail(callback));
            return;
        }

        // Output track wav mapped by track UID and layer name
        final Map<String, TrackWav> output = new HashMap<>();

        // Mix results into a single wave
        for (WavData w : results) {
            if (!(w instanceof TrackWav)) {
                Log.w("Ignoring non-track wav: " + w);
                continue;
            }

            TrackWav tw = (TrackWav) w;
            if (tw.track == null) {
                Log.w("Ignoring wav with missing track: " + tw);
                continue;
            }

            String uid = String.valueOf(tw.track.id);
            if (tw.layer != null)
                uid += "_" + tw.layer.name;

            TrackWav outputWav = output.computeIfAbsent(uid,
                    k -> new TrackWav(tw.track, tw.layer, params.channels, params.getDuration(), params.sampleRate));
            outputWav.mix(tw, tw.time - params.startTime);
        }

        // Trim to desired duration
        for (TrackWav wav : output.values()) {
            wav.trim(0, wav.getFrame(params.getDuration()));
            if (params.loop)
                wav.setLoopFrames(0, wav.numFrames);
            wav.time = params.startTime;
        }

        // Start playback
        SwingUtilities.invokeLater(() -> {
            if (callback != null)
                callback.onWavGenerated(new ArrayList<>(output.values()), params);
        });
    }

    private void fail(WavGeneratorCallback cb) {
        if (cb != null)
            cb.onFailed();
    }
}
