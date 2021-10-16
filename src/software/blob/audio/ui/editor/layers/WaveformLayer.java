package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.events.EditorViewportListener;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.ui.editor.track.generator.*;
import software.blob.audio.util.Misc;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Draws the seed waveform under the pitch curves
 */
public class WaveformLayer extends EditorLayer implements
        EditorViewportListener, EditorTrackListener, WavGeneratorLayer {

    private static final Color COLOR_STROKE = new Color(192, 192, 192, 128);
    private static final Stroke STROKE = new BasicStroke(1);

    private int[] waveLines;
    private int waveX = 0;

    public WaveformLayer(AudioEditor editor) {
        super(editor);
    }

    @Override
    public String getName() {
        return "Waveform";
    }

    @Override
    public String getID() {
        return "waveform";
    }

    @Override
    public void paint(Graphics2D g) {
        if (waveLines == null)
            return;

        Shape oldClip = clipMargin(g);

        int margin = getEditorMargin();
        int h = getHeight() - margin;
        int h2 = (h / 2) + margin;
        g.setColor(COLOR_STROKE);
        g.setStroke(STROKE);
        int x = waveX;
        for (int w = 0; w < waveLines.length; w += 2) {
            int l1 = waveLines[w], l2 = waveLines[w + 1];
            g.drawLine(x, h2 - l1, x, h2 - l2);
            x++;
        }

        g.setClip(oldClip);
    }

    @Override
    public void onViewportChanged() {
        refresh();
    }

    @Override
    public void onTrackSelected(Track track) {
        refresh();
    }

    @Override
    public void onTrackVisibilityChanged(Track track) {
        refresh();
    }

    @Override
    public List<WavGeneratorTask> getGeneratorTasks(Track track, final WavGeneratorParams params) {
        final TrackWav seed = track.seed;
        if (seed == null || track != getSelectedTrack())
            return null;

        // Bounds check
        if (params.startTime >= seed.time + seed.duration || params.endTime < seed.time)
            return null;

        return Collections.singletonList(new WavGeneratorTask() {
            @Override
            protected TrackWav generate() {
                return seed;
            }
        });
    }

    /**
     * Refreshes the rendering of the waveform
     */
    private void refresh() {
        waveLines = null;

        Track track = getSelectedTrack();
        TrackWav wav = track != null ? track.seed : null;
        if (wav == null || getWidth() == 0 || !track.isLayerVisible(getID()))
            return;

        waveX = Math.max(0, getX(wav.time));

        int height = (getHeight() - getEditorMargin()) / 4;
        double samplesPerPixel = (1d / editor.getPixelsPerSecond()) * wav.sampleRate;
        double startTime = getTime(0) - wav.time;
        double endTime = getTime(getWidth());
        int startFrame = (int) Math.floor(startTime * wav.sampleRate);
        int endFrame = (int) Math.ceil(endTime * wav.sampleRate);
        startFrame = Misc.clamp(startFrame, 0, wav.numFrames);
        endFrame = Misc.clamp(endFrame, 0, wav.numFrames);
        int numFrames = endFrame - startFrame;
        int iters = (int) Math.ceil(numFrames / samplesPerPixel);
        int s = startFrame, e;
        double[] minMax = new double[2];
        waveLines = new int[iters * 2];
        int w = 0;
        for (int i = 0; i < iters; i++) {
            e = startFrame + Math.min((int) Math.round(samplesPerPixel * (i + 1)), numFrames);
            if (s >= e)
                break;
            minMax[0] = 2;
            minMax[1] = -2;
            wav.forEachSample((channel, frame, amp) -> {
                minMax[0] = Math.min(minMax[0], amp);
                minMax[1] = Math.max(minMax[1], amp);
                return true;
            }, s, e);
            waveLines[w] = (int) Math.round(minMax[0] * height);
            waveLines[w + 1] = (int) Math.round(minMax[1] * height);
            s = e;
            w += 2;
        }
    }
}
