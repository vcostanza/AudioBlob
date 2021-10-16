package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.PlaybackListener;
import software.blob.audio.ui.editor.events.RecordListener;
import software.blob.ui.util.DialogUtils;

/**
 * Handles MIDI/Wav recording
 */
public class RecordController extends EditorController implements PlaybackListener {

    private double startTime;
    private boolean pending, recording;

    public RecordController(AudioEditor editor) {
        super(editor);
    }

    public void start() {
        Instrument inst = getSelectedInstrument();
        if (inst == null) {
            DialogUtils.errorDialog("Recording Failed", "The selected track does not have an instrument loaded.");
            return;
        }
        if (!this.recording && !this.pending) {
            this.pending = true;
            this.startTime = editor.getSelection().getStartTime();
            getPlayback().play();
        }
    }

    public void stop() {
        if (this.recording) {
            this.recording = false;
            for (RecordListener l : editor.getEditorListeners(RecordListener.class))
                l.onRecordStopped(getTimeCode());
        }
        this.pending = false;
    }

    public boolean isRecording() {
        return this.recording;
    }

    public double getTimeCode() {
        if (!isRecording())
            return -1;
        return getPlayback().getTimeCode();
    }

    @Override
    public void onPlaybackStarted(double timeCode) {
        if (this.pending) {
            this.pending = false;
            this.recording = true;
            for (RecordListener l : editor.getEditorListeners(RecordListener.class))
                l.onRecordStarted(startTime);
        } else
            stop();
    }

    @Override
    public void onPlaybackStopped(double timeCode) {
        stop();
    }

    @Override
    public void onPlaybackTimeChanged(double timeCode) {
        if (!isRecording() || !getPlayback().isPlaying())
            return;
        double time = getTimeCode();
        double duration = editor.getDuration();
        if (time >= duration - 2) {
            double add = AudioEditor.DURATION_DEFAULT;
            editor.setDuration(duration + add);
            getPlayback().queue(getAudioPlayer().createClip(add), duration);
        }
        double timeRange = editor.getViewport().getTimeRange() / 2;
        editor.getViewport().setTimeOffset(time - timeRange);
    }
}
