package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.events.EditorInstrumentListener;
import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.ui.view.EditText;
import software.blob.ui.view.ImageButton;
import software.blob.ui.view.View;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.LayoutParams;
import software.blob.ui.view.layout.LinearLayout;
import software.blob.ui.view.listener.ClickListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Edit track details
 */
public class TrackDetailsDialog extends LayoutDialog implements ClickListener, ActionListener {

    private final AudioEditor editor;
    private final Track track;

    private final EditText nameTxt;
    private final ImageButton colorBtn;
    private final EditText bpmTxt;
    private final JComboBox<String> instrumentBox;

    private final List<Instrument> instruments = new ArrayList<>();
    private Instrument instrument;

    private Runnable callback;

    public TrackDetailsDialog(AudioEditor editor, Track track) {
        super(editor.getFrame());
        this.editor = editor;
        this.track = track;
        setInstrument(track.instrument);

        InflatedLayout inf = LayoutInflater.inflate("track_details_dialog");

        nameTxt = inf.findByName("name");
        nameTxt.setText(track.name);

        colorBtn = inf.findByName("color");
        colorBtn.setBackground(track.color);
        colorBtn.setOnClickListener(this);

        bpmTxt = inf.findByName("bpm");
        bpmTxt.setText(track.bpm);

        instrumentBox = inf.findByName("instrument");
        instrumentBox.addActionListener(this);

        setTitle("Track Details");
        setView(inf.getRoot());
        setSize(220, 150);
    }

    public TrackDetailsDialog setCallback(Runnable callback) {
        this.callback = callback;
        return this;
    }

    @Override
    public void dispose() {
        super.dispose();
        editor.getMidi().setInstrumentOverride(null);
    }

    @Override
    public LayoutDialog showDialog(Runnable onOk) {
        setupInstrumentBox();
        return super.showDialog(onOk);
    }

    @Override
    protected void onOK() {
        track.name = nameTxt.getText();
        track.color = colorBtn.getDefaultBackground();
        track.bpm = Math.min(AudioEditor.MAX_BPM, Misc.parseInt(bpmTxt.getText(), Track.BPM_DEFAULT));
        track.instrument = instrument;
        dismiss();
        for (EditorInstrumentListener l : editor.getEditorListeners(EditorInstrumentListener.class))
            l.onInstrumentChanged(track, instrument);
        if (callback != null)
            callback.run();
    }

    @Override
    public void onClick(View view, MouseEvent event) {
        if (view == colorBtn)
            promptSetColor();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();

        // Select track instrument
        if (src == instrumentBox) {
            int count = instruments.size();
            int idx = instrumentBox.getSelectedIndex();
            if (idx >= 0 && idx < count)
                setInstrument(instruments.get(idx));
            else if (idx >= count && idx < count + 3) {
                idx -= count;
                if (idx == 0)
                    setInstrument(null);
                else {
                    instrumentBox.setSelectedIndex(instruments.indexOf(instrument));
                    if (idx == 1) {
                        LoadInstrumentDialog d = new LoadInstrumentDialog(editor);
                        d.setWindow(this);
                        d.setCallback((inst) -> {
                            setInstrument(inst);
                            onOK();
                        });
                        d.showDialogLater();
                    } else {
                        GenInstrumentDialog d = new GenInstrumentDialog(editor, this);
                        d.setCallback((inst) -> {
                            setInstrument(inst);
                            onOK();
                        });
                        d.showDialogLater();
                    }
                }
            }
            return;
        }

        super.actionPerformed(e);
    }

    private void setupInstrumentBox() {
        instruments.clear();
        instruments.addAll(editor.getTracks().getInstruments());

        // Convert strings to array
        int idx = 0, count = instruments.size();
        int selectedIdx = -1;
        List<String> labels = new ArrayList<>(count + 3);
        for (Instrument inst : instruments) {
            labels.add(inst.getName());
            if (inst.equals(instrument))
                selectedIdx = idx;
            idx++;
        }
        labels.add("<None>");
        labels.add("Load Instrument");
        labels.add("Generate Instrument");
        if (selectedIdx == -1)
            selectedIdx = count;

        // Instantiate combo box
        instrumentBox.setModel(new DefaultComboBoxModel<>(labels.toArray(new String[0])));
        instrumentBox.setSelectedIndex(selectedIdx);
    }

    private void setInstrument(Instrument inst) {
        this.instrument = inst;
        editor.getMidi().setInstrumentOverride(inst);
    }

    private void promptSetColor() {
        final int div = 2;
        final int cols = Track.DEFAULT_COLORS.length / div;

        InflatedLayout inf = LayoutInflater.inflate("track_color_dialog");
        LinearLayout defaults = inf.findByName("default_colors");
        ImageButton pickerBtn = inf.findByName("color_picker");
        pickerBtn.setOnClickListener((view, event) -> showColorChooser());

        LinearLayout layout = null;

        int i = 0;
        for (final Color color : Track.DEFAULT_COLORS) {
            if (i == 0)
                layout = new LinearLayout(LinearLayout.HORIZONTAL);
            ImageButton btn = new ImageButton();
            btn.setBackground(color);
            btn.setOnClickListener((view, event) -> {
                editor.hideContextMenu();
                colorBtn.setBackground(color);
            });
            layout.add(btn, new LayoutParams(24, 24));
            if (++i == cols) {
                defaults.add(layout);
                i = 0;
            }
        }

        editor.showContextMenu(TrackDetailsDialog.this, inf.getRoot());
    }

    private void showColorChooser() {
        editor.hideContextMenu();
        final JColorChooser chooser = new JColorChooser(colorBtn.getDefaultBackground());
        final LayoutDialog dialog = new LayoutDialog(window);
        dialog.setTitle("Choose Track Color");
        dialog.setView(chooser);
        dialog.showDialog(() -> {
            dialog.dismiss();
            colorBtn.setBackground(chooser.getColor());
        });
    }
}
