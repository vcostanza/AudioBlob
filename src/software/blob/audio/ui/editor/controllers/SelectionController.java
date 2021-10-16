package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.*;
import software.blob.audio.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles selection mode
 */
public class SelectionController extends EditorController implements EditorProjectListener {

    private double startTime, endTime;
    private final List<EditorSelectionListener> listeners = new ArrayList<>();

    public SelectionController(AudioEditor editor) {
        super(editor);
    }

    public void set(double startTime, double endTime) {
        double start = Misc.clamp(Math.min(startTime, endTime), 0, editor.getDuration());
        double end = Misc.clamp(Math.max(startTime, endTime), 0, editor.getDuration());
        this.startTime = start;
        this.endTime = end;
        for (EditorSelectionListener l : listeners)
            l.onSelectionChanged(startTime, endTime);
    }

    public void set(double time) {
        set(time, time);
    }

    public double getStartTime() {
        return this.startTime;
    }

    public double getEndTime() {
        return this.endTime;
    }

    public double getMedianTime() {
        return (this.startTime + this.endTime) / 2;
    }

    public double getDuration() {
        double duration = getEndTime() - getStartTime();
        return duration > 0 ? duration : (editor.getDuration() - getStartTime());
    }

    public boolean isRangeSelected() {
        return Double.compare(startTime, endTime) != 0;
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(EditorSelectionListener.class));
    }

    @Override
    public void onLoadProject(EditorProject project) {
        if (project.selection != null)
            set(project.selection.startTime, project.selection.endTime);
        else
            set(0);
    }

    @Override
    public void onSaveProject(EditorProject project) {
        project.selection = new EditorProject.Selection(startTime, endTime);
    }
}
