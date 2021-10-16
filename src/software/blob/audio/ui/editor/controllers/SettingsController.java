package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.events.EditorProjectListener;

/**
 * Miscellaneous settings are stored here such as BPM, amplitude shading, etc.
 */
public class SettingsController extends EditorController implements EditorProjectListener {

    private boolean bpmMarkers;
    private boolean bpmMisalignment;
    private boolean ampShading;

    public SettingsController(AudioEditor editor) {
        super(editor);
    }

    /**
     * Check if BPM marker display is active (vs. regular time display)
     * @return True if BPM markers are active
     */
    public boolean hasBeatMarkers() {
        return bpmMarkers;
    }

    /**
     * Set whether BPM marker display should be active
     * @param bpmMarkers True to enable BPM marker display
     */
    public void setBeatMarkers(boolean bpmMarkers) {
        if (this.bpmMarkers != bpmMarkers) {
            this.bpmMarkers = bpmMarkers;
            editor.repaint();
        }
    }

    /**
     * Check if misaligned notes (not aligned to BPM) should be highlighted
     * @return True to highlight misaligned notes
     */
    public boolean hasBeatMisalignment() {
        return bpmMisalignment;
    }

    /**
     * Whether to highlight notes that are misaligned relative to the BPM
     * @param show True to show
     */
    public void setBeatMisalignment(boolean show) {
        if (this.bpmMisalignment != show) {
            this.bpmMisalignment = show;
            editor.repaint();
        }
    }

    /**
     * Check if the editor should perform amplitude shading
     * @return True if amplitude shading enabled
     */
    public boolean hasAmplitudeShading() {
        return ampShading;
    }

    /**
     * Toggle amplitude shading on the curves
     * @param shading True to enable amplitude shading
     */
    public void setAmplitudeShading(boolean shading) {
        if (this.ampShading != shading) {
            this.ampShading = shading;
            editor.repaint();
        }
    }

    @Override
    public void onLoadProject(EditorProject project) {
        if (project.settings != null) {
            this.bpmMarkers = project.settings.bpmMarkers;
            this.bpmMisalignment = project.settings.bpmMisalignment;
            this.ampShading = project.settings.ampShading;
        }
    }

    @Override
    public void onSaveProject(EditorProject project) {
        project.settings = new EditorProject.Settings();
        project.settings.bpmMarkers = bpmMarkers;
        project.settings.bpmMisalignment = bpmMisalignment;
        project.settings.ampShading = ampShading;
    }
}
