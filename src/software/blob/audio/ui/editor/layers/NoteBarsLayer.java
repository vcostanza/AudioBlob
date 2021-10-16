package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.MidiController;
import software.blob.audio.ui.editor.events.EditorMouseEvent;
import software.blob.audio.ui.editor.track.Track;
import software.blob.ui.util.ColorUtils;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;

/**
 * Draws the horizontal note bars
 */
public class NoteBarsLayer extends EditorLayer {

    // Colors
    public static final Color NOTE_BG_1 = new Color(42, 42, 42);
    public static final Color NOTE_BG_2 = new Color(30, 30, 30);

    public static final float NOTE_WIDTH = AudioEditor.NOTE_THRESHOLD;

    private final MidiController midi;
    private int noteIdx = -1;
    private int hoverNote = -1;

    public NoteBarsLayer(AudioEditor editor) {
        super(editor);
        this.midi = editor.getMidi();
    }

    @Override
    public String getName() {
        return "Note Bars";
    }

    @Override
    public void paint(Graphics2D g) {
        int w = getWidth(), margin = getEditorMargin();
        int minNote = (int) Math.floor(editor.getViewport().getNoteOffset());
        int maxNote = (int) Math.min(editor.getMaxValidNote(), Math.ceil(minNote + editor.getViewport().getNoteRange()) + 1);
        Shape oldClip = g.getClip();
        g.setClip(0, margin, w, getHeight() - margin);

        Track track = getSelectedTrack();
        Color heldColor = track != null ? ColorUtils.getColor(track.color, 128) : Color.DARK_GRAY;
        Set<Integer> held = editor.getMidi().getActiveNotes();
        for (int note = minNote; note <= maxNote; note++) {
            int maxY = getY(note - NOTE_WIDTH);
            int minY = getY(note + NOTE_WIDTH);
            int h = maxY - minY;
            Color color;
            if (hoverNote == note || held.contains(note))
                color = heldColor;
            else
                color = (note % 2) == 0 ? NOTE_BG_1 : NOTE_BG_2;
            g.setColor(color);
            g.fillRect(0, minY, w, h);
        }

        g.setClip(oldClip);
    }

    private int getMouseNote(EditorMouseEvent e) {
        if (e.y < getEditorMargin())
            return -1;
        int rounded = (int) Math.round(e.note);
        if (Math.abs(e.note - rounded) > NOTE_WIDTH)
            return -1;
        return rounded;
    }

    private void onHover(int note) {
        if (note != hoverNote) {
            hoverNote = note;
            if (note > -1) {
                Track selected = getSelectedTrack();
                if (selected == null || selected.instrument == null)
                    return;
                midi.loadSample(selected.instrument, note);
            }
            toggleCursor(Cursor.HAND_CURSOR, hoverNote != -1);
            repaint();
        }
    }

    @Override
    public boolean onMouseMoved(EditorMouseEvent e) {
        if (getMode() != EditorMode.CLICK)
            return false;
        onHover(getMouseNote(e));
        return false;
    }

    @Override
    public boolean onMousePressed(EditorMouseEvent e) {
        if (getMode() != EditorMode.CLICK || e.button != MouseEvent.BUTTON1)
            return false;
        noteIdx = getMouseNote(e);
        if (noteIdx > -1)
            midi.playNote(noteIdx, 64);
        return true;
    }

    @Override
    public boolean onMouseReleased(EditorMouseEvent e) {
        if (noteIdx != -1)
            midi.stopNote(noteIdx);
        noteIdx = -1;
        return false;
    }

    @Override
    public boolean onMouseExited(EditorMouseEvent e) {
        onHover(-1);
        return false;
    }
}
