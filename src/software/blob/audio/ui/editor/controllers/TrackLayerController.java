package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.track.TrackLayerAdapter;
import software.blob.ui.view.layout.list.ListView;

/**
 * Handles the right-side layer listing per track
 */
public class TrackLayerController extends EditorController implements EditorTrackListener {

    // View layout for the buttons
    private final ListView layersList;
    private final TrackLayerAdapter layerAdapter;

    public TrackLayerController(AudioEditor editor) {
        super(editor);
        layersList = editor.getInflatedLayout().findByName("trackLayers");
        layersList.setAdapter(layerAdapter = new TrackLayerAdapter(editor));
    }

    @Override
    public void onTrackSelected(Track track) {
        layerAdapter.refresh(track);
    }
}
