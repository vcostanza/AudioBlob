package software.blob.audio.ui.editor.view.track;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.TrackController;
import software.blob.audio.ui.editor.dialog.TrackDetailsDialog;
import software.blob.audio.ui.editor.events.EditorTrackListener;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.ControlButton;
import software.blob.ui.theme.DarkTheme;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.*;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.LinearLayout;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Button representing a track in the player
 */
public class TrackButton extends ControlButton {

    private static final Font FONT_INSTRUMENT = new Font(DarkTheme.FONT_DEFAULT_NAME, Font.PLAIN, 10);
    private static final Color DRAG_COLOR = new Color(255, 255, 255);
    private static final int DRAG_STROKE = 5;

    // Views
    private TextView instTxt;
    private LinearLayout buttonLayout, textLayout;

    // Modified by adapter
    int position;
    Track track;
    int dragHighlight;

    public TrackButton(AttributeSet attrs) {
        super(attrs);
    }

    @Override
    public void onFinishInflate(InflatedLayout inf) {
        buttonLayout = findChildByName("buttonLayout");
        textLayout = findChildByName("textLayout");
        instTxt = findChildByName("instrument");
        instTxt.setFont(FONT_INSTRUMENT);
        super.onFinishInflate(inf);
    }

    public void refresh(AudioEditor editor, Track track, int position) {
        super.refresh(editor, track == null ? new NewTrack() : new TrackControl(editor, track));
        this.track = track;
        this.position = position;
        refresh();
    }

    @Override
    public void onClick(View view, MouseEvent e) {
        int btn = e.getButton();
        if (view == this) {
            if (btn == MouseEvent.BUTTON1) {
                if (track == null)
                    promptNewTrack();
                else
                    ctrl.setSelected(true);
            } else if (btn == MouseEvent.BUTTON3)
                showOptions();
        } else
            super.onClick(view, e);
    }

    @Override
    public void onDoubleClick(View view, MouseEvent e) {
        if (view == this && e.getButton() == MouseEvent.BUTTON1)
            promptEdit();
        else
            super.onDoubleClick(view, e);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        if (dragHighlight != 0) {
            int width = getWidth();
            int height = getHeight();
            g.setColor(DRAG_COLOR);
            if (dragHighlight < 0)
                g.fillRect(0, 0, width, DRAG_STROKE);
            else
                g.fillRect(0, height - DRAG_STROKE, width, DRAG_STROKE);
        }
    }

    @Override
    protected void refresh() {
        super.refresh();
        if (track != null) {
            setDefaultBackground(track.color);
            setBackgroundBorder(null, 2);
            textLayout.setGravity(Gravity.START);
            buttonLayout.setVisibility(View.VISIBLE);
            String instName = track.instrument != null ? track.instrument.getName() : "<None>";
            instTxt.setText(instName);
            instTxt.setVisible(true);
        } else {
            setDefaultBackground(DarkTheme.GRAY_96);
            setBackgroundBorder(DarkTheme.GRAY_85, 2);
            textLayout.setGravity(Gravity.CENTER);
            buttonLayout.setVisibility(View.GONE);
            instTxt.setVisible(false);
        }
    }

    private void showOptions() {
        if (track == null)
            return;

        InflatedLayout inf = LayoutInflater.inflate("track_options");

        ImageButton edit = inf.findByName("edit");
        edit.setOnClickListener((view, event) -> {
            editor.hideContextMenu();
            promptEdit();
        });

        ImageButton delete = inf.findByName("delete");
        delete.setOnClickListener((view, event) -> {
            editor.hideContextMenu();
            if (DialogUtils.confirmDialog("Delete Track", "Are you sure you want to delete " + track.name + "?"))
                editor.getTracks().remove(track);
        });

        editor.showContextMenu(inf.getRoot());
    }

    private void promptEdit() {
        if (track == null)
            return;

        new TrackDetailsDialog(editor, track).setCallback(this::refresh).showDialog();
    }

    private static class TrackControl implements Controller {

        final AudioEditor editor;
        final Track track;
        final List<EditorTrackListener> listeners;

        TrackControl(AudioEditor editor, Track track) {
            this.editor = editor;
            this.track = track;
            this.listeners = editor.getEditorListeners(EditorTrackListener.class);
        }

        @Override
        public String getName() {
            return track.name;
        }

        @Override
        public void setSelected(boolean selected) {
            editor.getTracks().select(track);
        }

        @Override
        public boolean isSelected() {
            return editor.getTracks().getSelected() == track;
        }

        @Override
        public void setVolume(double volume) {
            track.volume = volume;
            for (EditorTrackListener l : listeners)
                l.onTrackVolumeChanged(track);
        }

        @Override
        public double getVolume() {
            return track.volume;
        }

        @Override
        public void setMuted(boolean mute) {
            track.muted = mute;
            for (EditorTrackListener l : listeners)
                l.onTrackVolumeChanged(track);
        }

        @Override
        public boolean isMuted() {
            return track.muted;
        }

        @Override
        public void setVisible(boolean visible) {
            track.visible = visible;
            for (EditorTrackListener l : listeners)
                l.onTrackVisibilityChanged(track);
        }

        @Override
        public boolean isVisible() {
            return track.visible;
        }
    }

    private static class NewTrack implements Controller {

        @Override
        public String getName() {
            return "New Track";
        }

        @Override
        public void setSelected(boolean selected) {
        }

        @Override
        public void setVolume(double volume) {
        }

        @Override
        public void setMuted(boolean mute) {
        }

        @Override
        public void setVisible(boolean visible) {
        }
    }

    private void promptNewTrack() {

        // Create track with name based on its index number
        final TrackController tracks = editor.getTracks();
        final Track track = new Track(editor, "Track " + (tracks.getCount() + 1));

        // Choose a color that isn't the same or too similar to the last track color
        // in order to avoid clashing
        Track first = tracks.getFirst();
        Track last = tracks.getLast();
        if (first != null && last != null) {
            int lastRGB = last.color.getRGB();
            Color[] colors = Track.DEFAULT_COLORS;
            for (int i = 0; i < colors.length; i++) {
                if (colors[i].getRGB() == lastRGB) {
                    track.color = colors[(i + 1) % colors.length];
                    break;
                }
            }
        }

        // Use the same BPM as the current track
        Track selected = tracks.getSelected();
        if (selected != null)
            track.bpm = selected.bpm;

        // Open details for new track
        TrackDetailsDialog d = new TrackDetailsDialog(editor, track);
        d.setTitle("New Track");
        d.setCallback(() -> {
            // Add and select the new track
            tracks.add(track);
            tracks.select(track);
        }).showDialog();
    }
}
