package software.blob.audio.ui.editor.view.pattern;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.ChangeController;
import software.blob.audio.ui.editor.dialog.EditTextDialog;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackPattern;
import software.blob.audio.util.Misc;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.AttributeSet;
import software.blob.ui.view.ImageButton;
import software.blob.ui.view.TextView;
import software.blob.ui.view.View;
import software.blob.ui.view.drag.DragEvent;
import software.blob.ui.view.drag.DragEventType;
import software.blob.ui.view.drag.DragListener;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.LinearLayout;
import software.blob.ui.view.listener.ClickListener;
import software.blob.ui.view.listener.DoubleClickListener;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Displays a pattern with its name on top in a {@link PatternAdapter}
 */
public class PatternLayout extends LinearLayout implements DragListener,
        ClickListener, DoubleClickListener {

    private AudioEditor editor;
    private Pattern pattern;
    private TextView nameTxt;
    private PatternView preview;

    public PatternLayout(AttributeSet attrs) {
        super(attrs);
    }

    @Override
    public void onFinishInflate(InflatedLayout inf) {
        super.onFinishInflate(inf);
        this.nameTxt = inf.findByName("name");
        this.preview = inf.findByName("pattern");

        setOnClickListener(this);
        setOnDoubleClickListener(this);
    }

    public void init(AudioEditor editor) {
        this.editor = editor;
        editor.getRoot().addOnDragListener(this, this);
    }

    /**
     * Set the pattern this layout is displaying
     * @param pattern Pattern
     */
    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
        this.nameTxt.setText(pattern.name);
        this.preview.setPattern(pattern);
    }

    @Override
    public void onClick(View view, MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3)
            showOptions();
    }

    @Override
    public void onDoubleClick(View view, MouseEvent e) {
        if (view == this && e.getButton() == MouseEvent.BUTTON1)
            promptEdit();
    }

    @Override
    public boolean onDrag(Component drag, DragEvent event) {
        if (event.type != DragEventType.DROP || event.drop != editor)
            return false;

        Track selected = editor.getTracks().getSelected();
        if (selected == null)
            return false;

        TrackPattern tp = new TrackPattern(pattern);
        tp.startTime = editor.getTime(event.point.x);
        if (editor.getSettings().hasBeatMarkers())
            tp.startTime = Misc.roundToBPM(tp.startTime, selected.bpm);
        tp.startNote = (int) Math.round(editor.getNote(event.point.y) - (pattern.noteRange / 2d));
        editor.getChanges().execute(new AddPatternChange(selected, tp));
        return true;
    }

    @Override
    public boolean acceptComponent(Component c) {
        return c == editor;
    }

    private void showOptions() {
        if (pattern == null)
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
            int usages = editor.getPatterns().getUsages(pattern);
            if (DialogUtils.confirmDialog("Delete Pattern",
                    "Are you sure you want to delete " + pattern.name + "?\n" + (usages > 0
                            ? usages + " instances of this pattern will be removed from the project."
                            : "This pattern is not currently in use.")))
                editor.getPatterns().remove(pattern);
        });

        editor.showContextMenu(inf.getRoot());
    }

    private void promptEdit() {
        if (pattern == null)
            return;

        final EditTextDialog d = new EditTextDialog(editor.getFrame());
        d.setTitle("Pattern Name");
        d.setHint("Pattern name");
        d.setText(pattern.name);
        d.selectAll();
        d.setEmptyTextAllowed(false);
        d.showDialog(() -> {
            pattern.name = d.getText();
            setPattern(pattern);
            editor.repaint();
            d.dismiss();
        });
    }

    private class AddPatternChange implements ChangeController.Change {

        private final Track track;
        private final TrackPattern pattern;

        AddPatternChange(Track track, TrackPattern tp) {
            this.track = track;
            this.pattern = tp;
        }

        @Override
        public void execute() {
            editor.getPatterns().add(track, pattern);
        }

        @Override
        public void undo() {
            editor.getPatterns().remove(track, pattern);
        }
    }
}
