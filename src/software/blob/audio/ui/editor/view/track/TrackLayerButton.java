package software.blob.audio.ui.editor.view.track;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.layers.EditorLayer;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.ControlButton;
import software.blob.ui.theme.DarkTheme;
import software.blob.ui.view.AttributeSet;
import software.blob.ui.view.layout.InflatedLayout;

import java.util.List;

/**
 * Button for controlling visibility and volume of an {@link EditorLayer} for a given {@link Track}
 */
public class TrackLayerButton extends ControlButton {

    public TrackLayerButton(AttributeSet attrs) {
        super(attrs);
    }

    @Override
    public void onFinishInflate(InflatedLayout inf) {
        super.onFinishInflate(inf);
        setDefaultBackground(DarkTheme.GRAY_85);
        setCursor(null);
    }

    public void refresh(AudioEditor editor, Track track, Track.Layer layer) {
        super.refresh(editor, new LayerControl(editor, track, layer));
    }

    private static class LayerControl implements Controller {

        final AudioEditor editor;
        final Track track;
        final Track.Layer layer;
        final List<EditorTrackListener> listeners;

        LayerControl(AudioEditor editor, Track track, Track.Layer layer) {
            this.editor = editor;
            this.track = track;
            this.layer = layer;
            this.listeners = editor.getEditorListeners(EditorTrackListener.class);
        }

        @Override
        public String getName() {
            return layer.name;
        }

        @Override
        public void setSelected(boolean selected) {
        }

        @Override
        public boolean isSelected() {
            return true;
        }

        @Override
        public void setVolume(double volume) {
            layer.volume = volume;
            for (EditorTrackListener l : listeners)
                l.onTrackVolumeChanged(track);
        }

        @Override
        public double getVolume() {
            return layer.volume;
        }

        @Override
        public void setMuted(boolean mute) {
            layer.muted = mute;
            for (EditorTrackListener l : listeners)
                l.onTrackVolumeChanged(track);
        }

        @Override
        public boolean isMuted() {
            return layer.muted;
        }

        @Override
        public void setVisible(boolean visible) {
            layer.visible = visible;
            for (EditorTrackListener l : listeners)
                l.onTrackVisibilityChanged(track);
        }

        @Override
        public boolean isVisible() {
            return layer.visible;
        }
    }
}
