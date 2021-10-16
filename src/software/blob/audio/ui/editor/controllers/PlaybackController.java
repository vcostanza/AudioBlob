package software.blob.audio.ui.editor.controllers;

import software.blob.audio.playback.AudioHandle;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.events.PlaybackListener;
import software.blob.audio.ui.editor.layers.PianoRollLayer;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.ui.editor.track.generator.WavGenerator;
import software.blob.audio.ui.editor.track.generator.WavGeneratorCallback;
import software.blob.audio.ui.editor.track.generator.WavGeneratorParams;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls playback for the editor
 */
public class PlaybackController extends EditorController implements EditorProjectListener, EditorTrackListener {

    // Audio handle for the current audio clip that's playing
    private AudioHandle playing;

    // Audio is busy processing in response to a play request
    private boolean processing;

    // The current time position of playback
    private double timeCode;

    private final List<PlaybackListener> listeners = new ArrayList<>();

    public PlaybackController(AudioEditor editor) {
        super(editor);
    }

    /**
     * Set the current time code
     * @param timeCode Time code in seconds
     */
    public void setTimeCode(double timeCode) {
        this.timeCode = timeCode;
        for (PlaybackListener l : listeners)
            l.onPlaybackTimeChanged(timeCode);
    }

    /**
     * Get the current time code to start playback
     * @return Time code in seconds
     */
    public double getTimeCode() {
        return timeCode;
    }

    /**
     * Start audio playback
     * @param loop True to loop playback
     */
    public void play(final boolean loop) {
        if (processing)
            return;

        pause();

        processing = true;
        SelectionController selection = editor.getSelection();
        double startTime = selection.getStartTime();
        double duration = selection.getDuration();

        WavGenerator generator = new WavGenerator(editor);
        WavGeneratorParams params = new WavGeneratorParams();
        params.startTime = startTime;
        params.endTime = startTime + duration;
        params.loop = loop;

        // Playback for the piano roll is handled separately
        params.excludeLayers.add(getLayer(PianoRollLayer.class));

        generator.generate(params, new WavGeneratorCallback() {
            @Override
            public void onWavGenerated(List<TrackWav> results, WavGeneratorParams params) {
                processing = false;
                queue(results, params);
            }
            @Override
            public void onFailed() {
                Log.e("Failed to generate wav");
                processing = false;
            }
        });
    }

    public void play() {
        play(false);
    }

    /**
     * Queue track wav list at the set times
     * @param wavs Wav data list
     * @param params Wav generator params used to create these tracks
     */
    private void queue(List<TrackWav> wavs, WavGeneratorParams params) {
        // Queue silence while other tracks play alongside
        WavData silence = new WavData(params.channels, params.getDuration(), params.sampleRate);
        if (params.loop)
            silence.setLoopFrames(0, silence.numFrames);
        queue(silence, params.startTime, 0);

        // Queue track wavs
        for (TrackWav wav : wavs) {
            final Track track = wav.track;
            final Track.Layer layer = wav.layer;
            if (track == null || layer == null)
                continue;
            AudioHandle h = getAudioPlayer().createHandle(wav);
            h.setVolumeCalculator(() -> track.muted || layer.muted ? 0 : track.volume * layer.volume);
            getAudioPlayer().queue(h);
        }
    }

    void queue(WavData wav, final double startTime, double delay) {
        boolean wasPlaying = isPlaying();
        final AudioHandle h = playing = getAudioPlayer().createHandle(wav);

        // Honor the current time code
        double timeCode = getTimeCode();
        boolean tcInside = timeCode >= startTime && timeCode < startTime + wav.duration;
        if (tcInside)
            h.setTime(timeCode - startTime);

        // Callback to update playback time code and stop when finished
        h.setCallback(new AudioHandle.Callback() {
            @Override
            public void onPlayback(double time, int frame) {
                if (isPlaying()) {
                    setTimeCode(startTime + time);
                    editor.keepAwake();
                }
            }
            @Override
            public void onPlaybackFinished() {
                if (h == playing)
                    stop();
            }
        });

        // Queue the sound
        getAudioPlayer().queue(h, delay);

        if (!wasPlaying) {
            // Fire listeners
            for (PlaybackListener l : listeners)
                l.onPlaybackStarted(tcInside ? timeCode : startTime);
        }
    }

    void queue(WavData wav, double startTime) {
        queue(wav, startTime, startTime - getTimeCode());
    }

    /**
     * Check if there's audio currently playing
     * @return True if audio playing
     */
    public boolean isPlaying() {
        return playing != null;
    }

    /**
     * Pause playback (if playing)
     */
    public void pause() {
        if (isPlaying()) {
            getAudioPlayer().clear();
            playing = null;
            for (PlaybackListener l : listeners)
                l.onPlaybackStopped(timeCode);
        }
    }

    /**
     * Stop playback and reset time code to zero
     */
    public void stop() {
        pause();
        setTimeCode(editor.getSelection().getStartTime());
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(PlaybackListener.class));
    }

    @Override
    public void onLoadProject(EditorProject project) {
        pause();
        setTimeCode(0);
    }
}
