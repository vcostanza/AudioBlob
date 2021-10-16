package software.blob.audio.ui.editor.view;

import software.blob.audio.ui.editor.pitchcurve.PitchSample;
import software.blob.audio.util.Misc;
import software.blob.ui.view.View;

import javax.swing.*;
import java.awt.*;
import java.awt.font.LineMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Tuner view
 */
public class TunerView extends View {

    private static final double NOTE_WIDTH = 0.25f;
    private static final double NOTE_RANGE = 8;
    private static final double TIME_RANGE = 3;
    private static final double INTERVAL = 0.2d;
    private static final double DRAW_DELAY = 0.9;
    private static final int TARGET_FPS = 120;
    private static final int FPS_DELAY = 1000 / TARGET_FPS;

    private static final Color NOTE_BG_1 = new Color(40, 40, 40);
    private static final Color NOTE_BG_2 = new Color(24, 24, 24);
    private static final Stroke STROKE = new BasicStroke(2);
    private static final Font FONT = new Font("Arial", Font.BOLD, 12);

    private final long startMillis;
    private final Timer drawTimer;
    private double latestNote, drawNote, minDrawNote, pixelsPerNote;
    private final List<PitchSample> samples = new ArrayList<>();

    public TunerView() {
        setBackground(Color.BLACK);
        startMillis = System.currentTimeMillis();
        drawNote = latestNote = 48;

        drawTimer = new Timer(FPS_DELAY, e -> repaint());
        drawTimer.start();
    }

    public void addFrequency(double freq) {
        if (!Double.isNaN(freq)) {
            double time = curTimeDelta();
            latestNote = Misc.getNoteValue(freq);
            samples.add(new PitchSample(time, latestNote, 0));
        }
    }

    @Override
    protected void paint(Graphics2D g) {
        super.paint(g);

        if (drawNote == 0)
            drawNote = latestNote;
        else
            drawNote = (drawNote * DRAW_DELAY) + (latestNote * (1 - DRAW_DELAY));

        double curMax = drawNote + NOTE_RANGE + NOTE_WIDTH;
        double curMin = minDrawNote = drawNote - NOTE_RANGE - NOTE_WIDTH;

        int w = getWidth();
        int minNote = (int) Math.floor(curMin);
        int maxNote = (int) Math.ceil(curMax) + 1;
        pixelsPerNote = (double) getHeight() / (curMax - curMin);
        double pixelsPerSecond = w / TIME_RANGE;

        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics();

        for (int note = minNote; note <= maxNote; note++) {
            int maxY = getY(note - NOTE_WIDTH);
            int minY = getY(note + NOTE_WIDTH);
            int h = maxY - minY;
            Color color = (note % 2) == 0 ? NOTE_BG_1 : NOTE_BG_2;
            g.setColor(color);
            g.fillRect(0, minY, w, h);

            String noteName = Misc.getNoteName(note);
            LineMetrics lm = fm.getLineMetrics(noteName, g);
            float fh = lm.getHeight();
            g.setColor(Color.WHITE);
            g.drawString(noteName, 4, maxY - lm.getDescent() - (h - fh) / 2);
        }

        int lastX = -1, lastY = -1;
        double lastTime = -1;
        double startTime = curTimeDelta() - TIME_RANGE;
        g.setStroke(STROKE);
        g.setColor(Color.RED);
        for (int i = 0; i < samples.size(); i++) {
            PitchSample sample = samples.get(i);
            if (sample.time < startTime - INTERVAL) {
                samples.remove(i--);
                continue;
            }
            int x = (int) Math.round((sample.time - startTime) * pixelsPerSecond);
            int y = getY(sample.note);
            if (lastX != -1 && lastY != -1 && sample.time - lastTime <= INTERVAL) {
                g.drawLine(lastX, lastY, x, y);
            }
            g.fillRect(x - 2, y - 2, 4, 4);
            lastX = x;
            lastY = y;
            lastTime = sample.time;
        }
    }

    private double curTimeDelta() {
        return (System.currentTimeMillis() - startMillis) / 1000d;
    }

    private int getY(double note) {
        return getHeight() - (int) Math.round((note - this.minDrawNote) * pixelsPerNote);
    }
}
