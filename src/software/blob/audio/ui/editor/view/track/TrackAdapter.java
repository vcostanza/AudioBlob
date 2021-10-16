package software.blob.audio.ui.editor.view.track;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.TrackController;
import software.blob.audio.ui.editor.track.Track;
import software.blob.ui.view.View;
import software.blob.ui.view.drag.DragEvent;
import software.blob.ui.view.drag.DragListener;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.list.ListAdapter;
import software.blob.ui.view.layout.list.ListView;

import java.awt.*;

/**
 * List adapter for track buttons
 */
public class TrackAdapter extends ListAdapter implements DragListener {

    private final AudioEditor editor;
    private final TrackController tracks;

    public TrackAdapter(AudioEditor editor) {
        this.editor = editor;
        this.tracks = editor.getTracks();
    }

    @Override
    public int getCount() {
        return tracks.getCount() + 1;
    }

    @Override
    public Track getItem(int position) {
        return tracks.get(position);
    }

    @Override
    public View getView(int position, View existing, ListView list) {
        Track track = getItem(position);
        TrackButton button = existing instanceof TrackButton ? (TrackButton) existing : null;
        if (button == null) {
            button = LayoutInflater.inflate("track_button").getRoot();
            editor.getRoot().addOnDragListener(button, this);
        }
        button.refresh(editor, track, position);
        return button;
    }

    @Override
    public boolean onDrag(Component d, DragEvent event) {
        if (event.drop == d || !(event.drop instanceof TrackButton))
            return false;

        TrackButton drag = (TrackButton) d;
        TrackButton drop = (TrackButton) event.drop;
        if (drag.track == null)
            return false;

        int highlight = 0;
        if (event.point != null)
            highlight = event.point.y > drop.getHeight() / 2 ? 1 : -1;
        if (drop.track == null && highlight > 0)
            highlight = 0;

        switch (event.type) {
            case ENTER:
                drop.dragHighlight = highlight;
                break;
            case EXIT:
                drop.dragHighlight = 0;
                break;
            case DROP: {
                drop.dragHighlight = 0;
                int newPos = drop.position;
                if (newPos > drag.position && highlight < 0)
                    newPos--;
                else if (newPos < drag.position && highlight > 0)
                    newPos++;
                editor.getTracks().move(drag.track, newPos);
                notifyDatasetChanged();
                break;
            }
        }
        return true;
    }

    @Override
    public boolean acceptComponent(Component c) {
        return c instanceof TrackButton;
    }
}
