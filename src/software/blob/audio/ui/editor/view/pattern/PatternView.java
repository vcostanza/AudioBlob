package software.blob.audio.ui.editor.view.pattern;

import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.util.Misc;
import software.blob.ui.view.AttributeSet;
import software.blob.ui.view.View;

import java.awt.*;

import static software.blob.audio.ui.editor.layers.NoteBarsLayer.*;
import static software.blob.audio.ui.editor.layers.PianoRollLayer.*;

/**
 * Displays pattern content in a small preview thumbnail
 */
public class PatternView extends View {

    private static final Stroke STROKE = new BasicStroke(1f);

    private Pattern pattern;

    public PatternView(AttributeSet attrs) {
        super(attrs);
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
        repaint();
    }

    @Override
    protected void paint(Graphics2D g) {
        super.paint(g);

        if (this.pattern == null)
            return;

        int bSize = Math.round(borderWidth);
        int borderPadding = (int) Math.ceil(borderWidth * 2);
        int width = getWidth() - borderPadding;
        int height = getHeight() - borderPadding;
        double duration = Misc.clamp(this.pattern.duration, 0.5, 5);
        int minNote = this.pattern.minNote;
        int maxNote = this.pattern.maxNote;
        int noteRange = Misc.clamp(maxNote - minNote, 5, height);
        double pixelsPerSecond = width / duration;
        double pixelsPerNote = height / (noteRange + NOTE_WIDTH * 2);
        int noteHeight = (int) Math.max(1, pixelsPerNote * NOTE_WIDTH * 2);

        if (noteRange > maxNote - minNote) {
            float median = (maxNote + minNote) / 2f;
            float nr = noteRange / 2f;
            minNote = (int) (median - nr);
            maxNote = (int) (median + nr);
        }

        g.translate(bSize, bSize);

        // Draw the note bars
        for (int note = minNote; note <= maxNote; note++) {
            int y = (int) Math.round((note - minNote) * pixelsPerNote);
            Color color = (note % 2) == 0 ? NOTE_BG_1 : NOTE_BG_2;
            g.setColor(color);
            g.fillRect(0, y, width, noteHeight);
        }

        // Draw the notes
        int noteWidth = Math.max((int) Math.round(pixelsPerSecond * NOTE_DURATION), 10);
        g.setStroke(STROKE);
        for (MidiNote note : this.pattern.notes) {
            if (note.time >= duration)
                break;

            int x = (int) Math.round(note.time * pixelsPerSecond);
            int y = (int) Math.round(((note.value - minNote) + NOTE_WIDTH * 2) * pixelsPerNote);
            y = height - y;

            g.setColor(Color.RED);
            g.fillRect(x, y, noteWidth, noteHeight);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, noteWidth, noteHeight);
        }

        g.translate(-bSize, -bSize);
    }
}
