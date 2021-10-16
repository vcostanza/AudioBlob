package software.blob.audio.ui.editor.view.track;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.track.Track;
import software.blob.ui.view.View;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.list.ListAdapter;
import software.blob.ui.view.layout.list.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * List adapter for layer buttons
 */
public class TrackLayerAdapter extends ListAdapter {

    private final AudioEditor editor;

    private Track track;
    private final List<Track.Layer> layers = new ArrayList<>();

    public TrackLayerAdapter(AudioEditor editor) {
        this.editor = editor;
    }

    public void refresh(Track track) {
        this.track = track;
        this.layers.clear();
        if (track != null)
            this.layers.addAll(track.getLayers());
        notifyDatasetChanged();
    }

    @Override
    public int getCount() {
        return layers.size();
    }

    @Override
    public Track.Layer getItem(int position) {
        return layers.get(position);
    }

    @Override
    public View getView(int position, View existing, ListView list) {
        Track.Layer layer = getItem(position);
        TrackLayerButton button = existing instanceof TrackLayerButton ? (TrackLayerButton) existing : null;
        if (button == null)
            button = LayoutInflater.inflate("layer_button").getRoot();
        button.refresh(editor, track, layer);
        return button;
    }
}
