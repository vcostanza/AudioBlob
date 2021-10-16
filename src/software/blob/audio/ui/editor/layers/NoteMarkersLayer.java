package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorViewportListener;
import software.blob.audio.util.Misc;

import java.awt.*;
import java.awt.font.LineMetrics;

/**
 * Draws note names on top of the corresponding bars
 */
public class NoteMarkersLayer extends EditorLayer implements EditorViewportListener {

    private static final Color GRAY_OVERLAY = new Color(0, 0, 0, 64);
    private static final Stroke STROKE = new BasicStroke(1f);

    private int fontSize;

    public NoteMarkersLayer(AudioEditor editor) {
        super(editor);
    }

    @Override
    public String getName() {
        return "Note Markers";
    }

    @Override
    public void paint(Graphics2D g) {
        int w = getWidth();
        int margin = getEditorMargin();
        int minNote = (int) Math.floor(editor.getViewport().getNoteOffset());
        int maxNote = (int) Math.min(editor.getMaxValidNote(), Math.ceil(minNote + editor.getViewport().getNoteRange()) + 1);

        FontMetrics fm = g.getFontMetrics();
        Shape oldClip = g.getClip();
        g.setClip(0, margin + 1, w, getHeight() - margin);
        g.setFont(getFont(fontSize));
        g.setStroke(STROKE);

        g.setColor(GRAY_OVERLAY);
        g.fillRect(0, margin, margin, getHeight());

        // Margin line
        g.setColor(Color.DARK_GRAY);
        g.drawLine(margin, margin, margin, getHeight());

        g.setColor(Color.WHITE);
        for (int note = minNote; note <= maxNote; note++) {
            int maxY = getY(note - AudioEditor.NOTE_THRESHOLD);
            int minY = getY(note + AudioEditor.NOTE_THRESHOLD);
            int h = maxY - minY;

            String noteName = Misc.getNoteName(note);
            LineMetrics lm = fm.getLineMetrics(noteName, g);
            float fh = lm.getHeight();
            g.drawString(noteName, 4, maxY - (h / 2f) + (fh / 2) - lm.getDescent());
        }

        g.setClip(oldClip);
    }

    @Override
    public void onViewportChanged() {
        int barHeight = (int) (editor.getPixelsPerNote() * AudioEditor.NOTE_THRESHOLD * 2);
        if (barHeight <= 10)
            fontSize = 7;
        else
            fontSize = 12;
    }
}
