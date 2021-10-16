package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.playback.AudioPlayer;
import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.layers.EditorLayer;
import software.blob.audio.ui.editor.track.Track;

/**
 * Generic controller module for the editor
 */
public abstract class EditorController {

    protected final AudioEditor editor;

    protected EditorController(AudioEditor editor) {
        this.editor = editor;
    }

    protected EditorMode getMode() {
        return editor.getMode();
    }

    protected AudioPlayer getAudioPlayer() {
        return editor.getAudioPlayer();
    }

    protected Instrument getSelectedInstrument() {
        Track track = getTracks().getSelected();
        return track != null ? track.instrument : null;
    }

    protected TrackController getTracks() {
        return editor.getTracks();
    }

    protected PlaybackController getPlayback() {
        return editor.getPlayback();
    }

    protected RecordController getRecorder() {
        return editor.getRecorder();
    }

    protected <T extends EditorLayer> T getLayer(Class<T> clazz) {
        return editor.getLayer(clazz);
    }

    public void refreshListeners() {
        // Refresh listeners
    }

    public void dispose() {
        // Program shutdown hook
    }
}
