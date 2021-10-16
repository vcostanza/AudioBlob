package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.dialog.*;
import software.blob.audio.ui.editor.events.BoxSelectionListener;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.audio.ui.editor.events.clipboard.ClipboardEvent;
import software.blob.audio.ui.editor.events.clipboard.ClipboardListener;
import software.blob.audio.ui.editor.layers.PatternsLayer;
import software.blob.audio.ui.editor.layers.PianoRollLayer;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.layers.PitchCurvesLayer;
import software.blob.audio.ui.editor.midi.quantize.Quantizer;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.view.RecentFilesMenuManager;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.menu.MenuItemView;
import software.blob.ui.view.menu.MenuView;
import software.blob.ui.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls for the menu at the top of the window
 */
public class MenuController extends EditorController implements ActionListener, EditorProjectListener {

    private static final String PREF_RECENT_PROJECTS = "open_recent_projects";
    private static final int MAX_RECENT_FILES = 5;

    private final RecentFilesMenuManager recentProjects;
    private final List<BoxSelectionListener> listeners = new ArrayList<>();

    public MenuController(AudioEditor editor) {
        super(editor);

        JMenuBar menuBar = editor.getRoot().findChildByName("menu");
        menuBar.setBorder(null);

        // Initialize menus
        MenuView recentMenu = null;
        for (Component c : menuBar.getComponents()) {
            if (!(c instanceof MenuView))
                continue;
            MenuView menu = (MenuView) c;
            for (Component c2 : menu.getMenuComponents()) {
                if (c2 instanceof MenuItemView)
                    ((MenuItemView) c2).addActionListener(this);
                else if (c2 instanceof MenuView && c2.getName().equals("open_recent"))
                    recentMenu = (MenuView) c2;
            }
        }

        // Populate recent projects
        recentProjects = new RecentFilesMenuManager(recentMenu, PREF_RECENT_PROJECTS);
        recentProjects.setMaxFiles(MAX_RECENT_FILES);
        recentProjects.setActionListener(this);
        recentProjects.init();
    }

    @Override
    public void onLoadProject(EditorProject project) {
        recentProjects.addFile(project.file);
    }

    @Override
    public void onSaveProject(EditorProject project) {
        // Need to invoke later since this event is called right before the project is saved
        final File file = project.file;
        SwingUtilities.invokeLater(() -> recentProjects.addFile(file));
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(BoxSelectionListener.class));
    }

    private void deselect() {
        for (BoxSelectionListener l : listeners)
            l.onDeselect();
    }

    private static boolean trackHasNotes(String title, Track track) {
        if (track == null) {
            DialogUtils.errorDialog(title, "No track selected");
            return false;
        } else if (track.notes == null || track.notes.isEmpty()) {
            DialogUtils.errorDialog(title, "Track has no notes");
            return false;
        }
        return true;
    }

    /* All menu actions below */

    @Override
    public void actionPerformed(ActionEvent evt) {

        SettingsController settings = editor.getSettings();
        SelectionController selection = editor.getSelection();
        ViewportController viewport = editor.getViewport();
        PlaybackController playback = getPlayback();
        RecordController recorder = getRecorder();
        PitchCurvesLayer curves = getLayer(PitchCurvesLayer.class);
        PianoRollLayer pianoRoll = getLayer(PianoRollLayer.class);

        Track track = getTracks().getSelected();

        MenuItemView m = (MenuItemView) evt.getSource();
        String name = m.getName();
        switch (name) {

            /* File menu */

            // New project
            case "new_project":
                editor.createNewProject();
                break;

            // Open project
            case "open_project":
                new OpenProjectDialog(editor).showDialog();
                break;

            // Open recent project
            case "open_recent": {
                String path = m.getActionCommand();
                File file = new File(path);
                try {
                    EditorProject project = new EditorProject(editor, file);
                    editor.open(project);
                } catch (Exception e) {
                    Log.e("Failed to open project", e);
                    DialogUtils.errorDialog("Open Project Failed", "Failed to open project");
                }
                break;
            }

            // Save project
            case "save_project":
            case "save_project_as": {
                SaveProjectDialog d = new SaveProjectDialog(editor);
                File projectFile = editor.getProjectFile();
                if (name.equals("save_project") && projectFile != null && projectFile.exists())
                    d.save(projectFile, true);
                else
                    d.showDialog();
                break;
            }

            // Import WAV file
            case "import_wav":
                new ImportWavDialog(editor).showDialog();
                break;

            // Export WAV file
            case "export_wav":
                new ExportWavDialog(editor).showDialog();
                break;

            // Record WAV/MIDI
            case "record":
                if (recorder.isRecording())
                    recorder.stop();
                else
                    recorder.start();
                break;

            // Load instrument to use for output generation
            case "load_instrument":
                new LoadInstrumentDialog(editor).showDialog();
                break;

            // Instrument made up of random samples
            case "generate_instrument":
                new GenInstrumentDialog(editor).showDialog();
                break;

            /* Edit menu */

            // Undo
            case "undo":
                editor.getChanges().undo();
                break;

            // Redo
            case "redo":
                editor.getChanges().redo();
                break;

            // Clipboard events
            case "cut":
            case "copy":
            case "paste":
            case "delete": {
                ClipboardEvent event = new ClipboardEvent(name);
                for (ClipboardListener l : editor.getEditorListeners(ClipboardListener.class))
                    l.onClipboardEvent(event);
                break;
            }

            // TODO: Pitch curve editor dialog
            // Apply the transform on the selected curve and deselect
            /*case "apply_edit":
                deselect();
                break;

            // Set note based on selection
            case "set_note":
                //curves.promptSetNote(false);
                break;

            // Quick version of set note
            case "set_note_quick":
                //curves.promptSetNote(true);
                break;*/

            // Quantize notes
            case "quantize": {
                if (trackHasNotes("Quantize Notes", track))
                    new QuantizerDialog(editor, track, pianoRoll.getSelected()).showDialog();
                break;
            }

            // Quick quantize based on current BPM
            case "quantize_quick": {
                if (trackHasNotes("Quick Quantize", track)) {
                    Quantizer quantizer = new Quantizer(editor, track);
                    quantizer.quantize(track.bpm);
                }
                break;
            }

            // Change BPM of the notes
            case "set_bpm": {
                if (trackHasNotes("Set BPM", track)) {
                    MidiNoteList selected = pianoRoll.getSelected();
                    new SetBPMDialog(editor, track, selected.isEmpty() ? track.notes : selected).showDialog();
                }
                break;
            }

            /* Select menu */

            // Select all
            case "select_all":
                for (BoxSelectionListener l : listeners)
                    l.onSelectAll();
                break;

            // Select none
            case "select_none":
                deselect();
                selection.set(selection.getStartTime());
                break;

            // Save notes to pattern
            case "create_pattern":
                editor.getPatterns().createFromSelected();
                break;

            // Convert a pattern instance back to notes
            case "break_pattern":
                editor.getLayer(PatternsLayer.class).breakApartSelected();
                break;

            /* Mode menu */

            // Select time range
            case "mode_select":
                editor.setMode(EditorMode.SELECT);
                break;

            // Select curves using a box
            case "mode_box_select":
                editor.setMode(EditorMode.BOX_SELECT);
                break;

            // Click mode
            case "mode_click":
                editor.setMode(EditorMode.CLICK);
                break;

            // Pan mode
            case "mode_pan":
                editor.setMode(EditorMode.PAN);
                break;

            // Set mode
            case "mode_draw":
                editor.setMode(EditorMode.DRAW);
                break;

            /* Playback menu */

            // Toggle playback
            case "toggle_playback":
            case "toggle_playback_loop":
                if (playback.isPlaying())
                    playback.pause();
                else
                    playback.play(name.equals("toggle_playback_loop"));
                break;

            /* Zoom menu */

            // Zoom time in
            case "zoom_in":
                viewport.zoomRelativeToTime(selection.getMedianTime(), viewport.hZoom + 0.2);
                break;

            // Zoom time out
            case "zoom_out":
                viewport.zoomRelativeToTime(selection.getMedianTime(), viewport.hZoom - 0.2);
                break;

            // Zoom to fit
            case "zoom_fit":
                if (selection.isRangeSelected())
                    viewport.zoomToSelection();
                else {
                    PitchCurveList selected = curves.getSelected();
                    List<MidiNote> notes = pianoRoll.getSelected();

                    double minTime = Double.MAX_VALUE, maxTime = -Double.MAX_VALUE;
                    if (!selected.isEmpty()) {
                        minTime = selected.getMinTime();
                        maxTime = selected.getMaxTime();
                    }
                    for (MidiNote note : notes) {
                        minTime = Math.min(minTime, note.time);
                        maxTime = Math.max(maxTime, note.time + PianoRollLayer.NOTE_DURATION);
                    }
                    if (minTime != Double.MAX_VALUE)
                        viewport.zoomToSelection(minTime, maxTime);
                }
                break;

            // Reset zoom to default
            case "zoom_reset":
                viewport.resetZoom();
                break;

            // Toggle amplitude shading on pitch curves
            case "amplitude_shading":
                settings.setAmplitudeShading(!settings.hasAmplitudeShading());
                break;

            // Toggle BPM markers display
            case "bpm_markers":
                settings.setBeatMarkers(!settings.hasBeatMarkers());
                break;

            // Toggle misaligned note highlighting
            case "bpm_misalignment":
                settings.setBeatMisalignment(!settings.hasBeatMisalignment());
                break;
        }
    }
}
