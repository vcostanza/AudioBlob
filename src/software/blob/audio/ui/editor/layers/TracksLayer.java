package software.blob.audio.ui.editor.layers;

import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.controllers.ChangeController;
import software.blob.audio.ui.editor.controllers.TrackController;
import software.blob.audio.ui.editor.events.EditorInstrumentListener;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.track.TrackAdapter;
import software.blob.ui.view.layout.list.ListView;

import java.awt.*;

/**
 * Handles the track listing
 */
public class TracksLayer extends EditorLayer implements EditorProjectListener, EditorTrackListener,
        EditorInstrumentListener {

    private final TrackController tracks;

    // View layout for the buttons
    private final ListView trackList;
    private final TrackAdapter trackAdapter;

    public TracksLayer(AudioEditor editor) {
        super(editor);
        tracks = editor.getTracks();
        trackList = editor.getInflatedLayout().findByName("tracks");
        trackList.setAdapter(trackAdapter = new TrackAdapter(editor));
    }

    @Override
    public String getName() {
        return "Tracks";
    }

    @Override
    public void paint(Graphics2D g) {
        // Nothing to do here - handled by layout
    }

    @Override
    public void onLoadProject(EditorProject project) {
        trackAdapter.notifyDatasetChanged();
    }

    @Override
    public void onTrackAdded(final Track track) {
        trackAdapter.notifyDatasetChanged();
    }

    @Override
    public void onTrackRemoved(Track track) {
        trackAdapter.notifyDatasetChanged();
    }

    @Override
    public void onTrackSelected(Track track) {
        trackAdapter.notifyDatasetChanged();
    }

    @Override
    public void onTrackVisibilityChanged(Track track) {
        editor.repaint();
    }

    @Override
    public void onInstrumentChanged(Track track, Instrument instrument) {
        trackAdapter.notifyDatasetChanged();
    }

    private class TrackAdditionChange implements ChangeController.Change {

        private final Track track;

        TrackAdditionChange(Track track) {
            this.track = track;
        }

        @Override
        public void execute() {
            tracks.add(this.track);
        }

        @Override
        public void undo() {
            tracks.remove(this.track);
        }
    }

    private class TrackRemovalChange implements ChangeController.Change {

        private final Track track;

        TrackRemovalChange(Track track) {
            this.track = track;
        }

        @Override
        public void execute() {
            tracks.remove(this.track);
        }

        @Override
        public void undo() {
            tracks.add(this.track);
        }
    }
}
