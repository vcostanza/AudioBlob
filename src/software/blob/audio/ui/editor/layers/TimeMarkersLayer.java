package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorMouseEvent;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.ui.view.EditText;
import software.blob.ui.view.layout.LayoutParams;
import software.blob.ui.view.layout.LinearLayout;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Draws time markers over the timeline
 */
public class TimeMarkersLayer extends EditorLayer {

    private static final double[] INTERVALS = {0.1, 0.2, 0.25, 0.5, 1, 10, 60};

    public TimeMarkersLayer(AudioEditor editor) {
        super(editor);
    }

    @Override
    public String getName() {
        return "Time Markers";
    }

    @Override
    public void paint(Graphics2D g) {
        int margin = getEditorMargin();
        int w = getWidth(), h = getHeight();

        if (settings.hasBeatMarkers())
            paintBeatMarkers(g);
        else
            paintTimeMarkers(g);

        // Duration line
        int durX = getX(editor.getDuration());
        if (durX >= 0 && durX < w) {
            g.setColor(Color.GRAY);
            g.drawLine(durX, 0, durX, h);
        }

        // Top line
        g.setColor(Color.WHITE);
        g.drawLine(0, margin, w, margin);
    }

    @Override
    public boolean onMouseClicked(EditorMouseEvent e) {
        int margin = getEditorMargin();
        if (e.button == MouseEvent.BUTTON3 && e.x < margin && e.y < margin) {
            promptSetBPM();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDoubleClicked(EditorMouseEvent e) {
        int margin = getEditorMargin();
        if (e.button == MouseEvent.BUTTON1 && e.x < margin && e.y < margin) {
            promptSetBPM();
            return true;
        }
        return false;
    }

    private int roundToBPM(Track track, double time) {
        return (int) Math.round(time / (60d / track.bpm));
    }

    private void promptSetBPM() {
        Track track = getSelectedTrack();
        if (track == null)
            return;

        LinearLayout ll = new LinearLayout();
        final EditText et = new EditText();
        et.setText(track.bpm);
        et.addActionListener(e -> {
            track.bpm = Math.min(AudioEditor.MAX_BPM, Misc.parseInt(et.getText(), track.bpm));
            editor.repaint();
            editor.hideContextMenu();
        });
        ll.add(et, new LayoutParams(50, LayoutParams.WRAP_CONTENT));
        editor.showContextMenu(ll);
    }

    private void paintBeatMarkers(Graphics2D g) {
        Track track = getSelectedTrack();
        if (track == null)
            return;

        int h = getHeight();
        int margin = getEditorMargin();

        double startTime = editor.getViewport().getTimeOffset();
        double duration = editor.getViewport().getTimeRange();
        double endTime = startTime + duration;
        endTime = Math.min(endTime, editor.getDuration());

        int bpm = track.bpm;
        double bpmInterval = 60d / bpm;
        int startIndex = roundToBPM(track, startTime);
        int endIndex = roundToBPM(track, endTime);

        int minTrack = startIndex;
        for (int i = startIndex; i <= endIndex; i++) {
            double time = i * bpmInterval;
            if (time < startTime || time > endTime)
                continue;
            int x = getX(time);
            g.setColor(minTrack == bpm ? Color.GRAY : Color.DARK_GRAY);
            g.drawLine(x, 0, x, h);
            if (++minTrack == bpm)
                minTrack = 0;
        }

        // BPM text
        float padding = 4;
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(getFont(12));
        g.drawString("BPM", padding, padding + 12);
        g.setColor(Color.WHITE);
        g.setFont(getFont(10));
        g.drawString(String.valueOf(bpm), padding, margin - padding);
    }

    private void paintTimeMarkers(Graphics2D g) {
        int h = getHeight();
        int margin = getEditorMargin();

        float padding = 4;
        double startTime = editor.getViewport().getTimeOffset();
        double duration = editor.getViewport().getTimeRange();
        double endTime = startTime + duration;
        int log10 = (int) Math.floor(Math.log10(duration));
        double majorInterval = Math.pow(10, log10);
        double durationDiv10 = duration / 10;
        double minorInterval = durationDiv10;
        double closestInterval = Double.MAX_VALUE;
        for (double interval : INTERVALS) {
            double diff = Math.abs(durationDiv10 - interval);
            if (diff < closestInterval) {
                minorInterval = interval;
                closestInterval = diff;
            }
        }

        startTime = Math.min(Math.floor(startTime / majorInterval) * majorInterval,
                Math.floor(startTime / minorInterval) * minorInterval);
        duration = endTime - startTime;

        int numMajorMarkers = (int) Math.ceil(duration / majorInterval);
        int numMinorMarkers = (int) Math.ceil(duration / minorInterval);

        g.setColor(Color.DARK_GRAY);
        g.setFont(getFont(12));
        int minorSkip = (int) Math.round(1 / minorInterval);
        int minorSkipCount = minorSkip == 1 ? 10 : minorSkip;
        for (int i = 0; i < numMinorMarkers; i++, minorSkipCount++) {
            if (minorSkipCount == minorSkip) {
                minorSkipCount = 0;
                continue;
            }
            float time = (float) (startTime + (i * minorInterval));
            int x = getX(time);
            g.drawLine(x, 0, x, h);
            g.drawString(String.valueOf(time), x + padding, margin - padding);
        }
        g.setColor(Color.GRAY);
        for (int i = 0; i < numMajorMarkers; i++) {
            float time = (float) (startTime + (i * majorInterval));
            int x = getX(time);
            g.drawLine(x, 0, x, h);
            g.drawString(String.valueOf(time), x + padding, margin - padding);
        }
    }
}
