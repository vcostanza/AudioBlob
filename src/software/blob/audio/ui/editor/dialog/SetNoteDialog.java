package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.pitchcurve.LegacyPitchCurve;
import software.blob.ui.view.EditText;
import software.blob.ui.view.FilterComboBox;
import software.blob.ui.view.View;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.LayoutParams;
import software.blob.ui.view.layout.LinearLayout;
import software.blob.audio.util.Misc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Set the note range
 */
public class SetNoteDialog extends LayoutDialog implements ActionListener {

    private final AudioEditor editor;
    private final JCheckBox rangeCB;
    private final LinearLayout noteLayout, minLayout, maxLayout;
    private final FilterComboBox note, minNote, maxNote;
    private final int noteRange;

    private Callback callback;

    public SetNoteDialog(AudioEditor editor) {
        super(editor.getFrame());
        this.editor = editor;

        InflatedLayout inf = LayoutInflater.inflate("set_note_dialog");

        rangeCB = inf.findByName("rangeCB");
        rangeCB.addActionListener(this);

        noteRange = editor.getMaxValidNote() - editor.getMinValidNote();
        List<String> notes = new ArrayList<>(noteRange);
        for (int i = 0; i < noteRange; i++)
            notes.add(Misc.getNoteName(i + 12));
        Collections.reverse(notes);

        noteLayout = inf.findByName("note");
        minLayout = inf.findByName("minNote");
        maxLayout = inf.findByName("maxNote");

        note = createBox(inf, "note", notes);
        minNote = createBox(inf, "minNote", notes);
        maxNote = createBox(inf, "maxNote", notes);

        setTitle("Set Note");
        setView(inf.getRoot());
        setSize(180, 190);
    }

    public SetNoteDialog setNoteRange(int minNote, int maxNote, int avgNote) {
        this.minNote.setSelectedItem(Misc.getNoteName(minNote));
        this.maxNote.setSelectedItem(Misc.getNoteName(maxNote));
        this.note.setSelectedItem(Misc.getNoteName(avgNote));
        return this;
    }

    public SetNoteDialog setPitchCurve(LegacyPitchCurve segment) {
        int minNote = (int) Math.floor(segment.minNote);
        int maxNote = (int) Math.ceil(segment.maxNote);
        int medNote = (int) Math.round((segment.minNote + segment.maxNote) / 2);
        return setNoteRange(minNote, maxNote, medNote);
    }

    public SetNoteDialog setCallback(Callback cb) {
        callback = cb;
        return this;
    }

    public void showContextMenu(int x, int y) {
        note.setWindow(editor.getFrame());
        note.setActionListener(e -> onOK());
        note.showMenu(editor.getFrame(), x, y);
    }

    public interface Callback {
        void onNoteSelected(int note);
        void onNoteRangeSelected(int minNote, int maxNote);
    }

    private FilterComboBox createBox(InflatedLayout inf, String name, List<String> notes) {
        LinearLayout layout = inf.findByName(name);
        Component[] children = layout.getComponents();
        for (Component c : children) {
            if (c instanceof EditText) {
                layout.remove(c);
                FilterComboBox cb = new FilterComboBox();
                cb.setWindow(this);
                cb.setChoices(notes);
                layout.add(cb, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
                return cb;
            }
        }
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);

        if (e.getSource() == rangeCB) {
            if (rangeCB.isSelected()) {
                noteLayout.setVisibility(View.GONE);
                minLayout.setVisibility(View.VISIBLE);
                maxLayout.setVisibility(View.VISIBLE);
            } else {
                noteLayout.setVisibility(View.VISIBLE);
                minLayout.setVisibility(View.GONE);
                maxLayout.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onOK() {
        if (callback == null)
            return;

        int note = getNote(this.note);
        int minNote = getNote(this.minNote);
        int maxNote = getNote(this.maxNote);

        if (rangeCB.isSelected())
            callback.onNoteRangeSelected(minNote, maxNote);
        else
            callback.onNoteSelected(note);

        dismiss();
    }

    private int getNote(FilterComboBox cb) {
        return Misc.getNoteValue(cb.getSelectedItem());
    }
}
