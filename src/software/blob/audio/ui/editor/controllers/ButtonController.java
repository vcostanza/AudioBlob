package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorModeListener;
import software.blob.audio.ui.editor.events.PlaybackListener;
import software.blob.audio.ui.editor.view.EditorModeButton;
import software.blob.audio.util.Misc;
import software.blob.ui.view.listener.ClickListener;
import software.blob.ui.view.ImageButton;
import software.blob.ui.view.View;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Handles on-screen buttons in the toolbar
 */
public class ButtonController extends EditorController implements
        ClickListener, PlaybackListener, EditorModeListener {

    private final ImageButton play, pause, stop, skipStart, skipEnd, record;
    private final List<EditorModeButton> modeBtns;
    private boolean shiftDown;

    public ButtonController(AudioEditor editor) {
        super(editor);

        // Playback
        play = findButton("play");
        pause = findButton("pause");
        stop = findButton("stop");
        skipStart = findButton("skip_start");
        skipEnd = findButton("skip_end");
        record = findButton("record");

        // Hook up mode buttons to the editor instance
        modeBtns = editor.getInflatedLayout().findByClass(EditorModeButton.class);
        for (EditorModeButton btn : modeBtns)
            btn.setEditor(editor);

        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof KeyEvent) {
                KeyEvent ke = (KeyEvent) e;
                boolean shift = ke.isShiftDown();
                if (shift != shiftDown) {
                    shiftDown = shift;
                    play.setImage(shiftDown ? "play_loop" : "play");
                }
            }
        }, AWTEvent.KEY_EVENT_MASK);
    }

    private ImageButton findButton(String name) {
        ImageButton btn = editor.getInflatedLayout().findByName(name);
        btn.setOnClickListener(this);
        return btn;
    }

    @Override
    public void onClick(View v, MouseEvent event) {

        boolean shiftHeld = Misc.hasBits(event.getModifiers(), MouseEvent.SHIFT_MASK);

        // Record controls
        RecordController recorder = editor.getRecorder();
        if (v == record && !recorder.isRecording())
            recorder.start();
        else
            recorder.stop();

        // Playback controls
        PlaybackController playback = editor.getPlayback();
        if (v == play)
            playback.play(shiftHeld);
        else if (v == pause)
            playback.pause();
        else if (v == stop)
            playback.stop();
        else if (v == skipStart || v == skipEnd) {
            editor.getSelection().set(v == skipStart ? 0 : editor.getDuration());
            editor.getViewport().setTimeOffset(v == skipStart ? 0
                    : editor.getDuration() - editor.getViewport().getTimeRange() / 2);
            editor.getViewport().refresh();
            playback.stop();
        }

        // Mode buttons
        Object tag = v.getTag();
        if (tag instanceof EditorMode)
            editor.setMode((EditorMode) tag);
    }

    @Override
    public void onPlaybackStarted(double timeCode) {
        play.setVisibility(View.GONE);
        pause.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPlaybackStopped(double timeCode) {
        play.setVisibility(View.VISIBLE);
        pause.setVisibility(View.GONE);
    }

    @Override
    public void onEditorModeChanged(EditorMode mode) {
        for (EditorModeButton btn : modeBtns)
            btn.onEditorModeChanged(mode);
    }
}
