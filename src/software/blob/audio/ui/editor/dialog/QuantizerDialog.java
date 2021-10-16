package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.layers.PianoRollLayer;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.midi.quantize.BPMError;
import software.blob.audio.ui.editor.midi.quantize.Quantizer;
import software.blob.audio.ui.editor.midi.quantize.QuantizerScanResult;
import software.blob.audio.ui.editor.track.Pattern;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.EditText;
import software.blob.ui.view.TextView;
import software.blob.ui.view.View;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.layout.LinearLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Quantize notes in the piano roll
 */
public class QuantizerDialog extends LayoutDialog implements EditText.OnTextChangedListener {

    private final AudioEditor editor;
    private final PianoRollLayer pianoRoll;
    private final Quantizer quantizer;
    private final QuantizerScanResult scanRes;
    private final Pattern pattern;

    private final TextView trackName, patternTxt;
    private final EditText srcBPM, dstBPM;
    private final TextView bpmError;
    private final JComboBox<BPMError> bpmCandidates;
    private final JCheckBox patternReplace;

    public QuantizerDialog(AudioEditor editor, Track track, MidiNoteList pattern) {
        super(editor.getFrame());
        this.editor = editor;
        this.pianoRoll = editor.getLayer(PianoRollLayer.class);
        this.quantizer = new Quantizer(editor, track);

        this.pattern = new Pattern();
        this.pattern.notes = pattern;
        this.quantizer.setPattern(this.pattern);
        this.scanRes = this.quantizer.scan();

        InflatedLayout inf = LayoutInflater.inflate("quantizer_dialog");
        this.trackName = inf.findByName("track");
        this.patternTxt = inf.findByName("pattern");
        this.srcBPM = inf.findByName("srcBPM");
        this.dstBPM = inf.findByName("dstBPM");
        this.bpmError = inf.findByName("bpmError");
        this.bpmCandidates = inf.findByName("bpmCandidates");
        this.patternReplace = inf.findByName("patternReplace");
        LinearLayout patternRow = inf.findByName("patternRow");

        this.trackName.setText(track.name);

        if (pattern != null && !pattern.isEmpty()) {
            this.patternTxt.setText(pattern.size() + " notes (x" + scanRes.patternCount + ")");
        } else {
            patternRow.setVisibility(View.GONE);
            patternReplace.setVisible(false);
        }

        this.srcBPM.setText(this.scanRes.bestBPM);
        this.dstBPM.setText(this.scanRes.bestBPM);
        this.dstBPM.addTextChangedListener((et, text) -> updateErrorText());
        //this.srcBPM.addTextChangedListener(this);
        updateErrorText();

        this.bpmCandidates.setModel(new DefaultComboBoxModel<>(scanRes.bestBPMs.toArray(new BPMError[0])));
        this.bpmCandidates.addActionListener(this);

        this.pianoRoll.highlightPattern(scanRes.patternMatches);

        setTitle("Quantizer");
        setView(inf.getRoot());
        //setSize(240, 150);
    }

    @Override
    public void onTextChanged(EditText et, String text) {
        if (et == srcBPM)
            dstBPM.setText(text);
    }

    @Override
    protected void onOK() {
        if (patternReplace.isSelected()) {
            EditTextDialog d = new EditTextDialog(this);
            d.setTitle("Pattern Name");
            d.setHint("Pattern name");
            d.setText("Pattern " + (editor.getPatterns().getCount() + 1));
            d.selectAll();
            d.setEmptyTextAllowed(false);
            d.showDialog(() -> {
                d.dismiss();
                this.pattern.name = d.getText();
                quantizer.setPatternReplace(true);
                quantize();
            });
            return;
        }
        quantize();
    }

    @Override
    public void dispose() {
        super.dispose();
        this.pianoRoll.highlightPattern(null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == bpmCandidates) {
            BPMError be = (BPMError) bpmCandidates.getSelectedItem();
            dstBPM.setText(be.bpm);
            return;
        }
        super.actionPerformed(e);
    }

    private void updateErrorText() {
        int bpm = Misc.parseInt(dstBPM.getText(), 0);
        BPMError err = new BPMError(bpm, scanRes.getIntervalError(bpm));
        this.bpmError.setText(err.getErrorPercentage() + "%");
    }

    private void quantize() {
        //int srcBPM = Misc.parseInt(this.srcBPM.getText(), scanRes.bestBPM);
        int dstBPM = Misc.parseInt(this.dstBPM.getText(), scanRes.bestBPM);
        if (dstBPM > AudioEditor.MAX_BPM) {
            DialogUtils.errorDialog("Quantizer", "Max BPM allowed is " + AudioEditor.MAX_BPM);
            return;
        }
        this.quantizer.quantize(scanRes, dstBPM, dstBPM);
        dismiss();
    }
}
