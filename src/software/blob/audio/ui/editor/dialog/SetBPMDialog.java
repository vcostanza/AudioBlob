package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.midi.MidiNoteList;
import software.blob.audio.ui.editor.midi.quantize.Quantizer;
import software.blob.audio.ui.editor.midi.quantize.QuantizerScanResult;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.EditText;
import software.blob.ui.view.TextView;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;

/**
 * Set the BPM of the notes in the track
 */
public class SetBPMDialog extends LayoutDialog implements EditText.OnTextChangedListener {

    private final AudioEditor editor;
    private final Track track;
    private final Quantizer quantizer;

    private final TextView trackName;
    private final EditText srcBPM, dstBPM;

    public SetBPMDialog(AudioEditor editor, Track track, MidiNoteList notes) {
        super(editor.getFrame());
        this.editor = editor;
        this.track = track;
        this.quantizer = new Quantizer(editor, track, notes);
        QuantizerScanResult scanRes = quantizer.scan();

        InflatedLayout inf = LayoutInflater.inflate("set_bpm_dialog");
        this.trackName = inf.findByName("track");
        this.srcBPM = inf.findByName("srcBPM");
        this.dstBPM = inf.findByName("dstBPM");

        this.trackName.setText(track.name);
        this.srcBPM.setText(scanRes.bestBPM);
        this.dstBPM.setText(scanRes.bestBPM);

        setTitle("Set BPM");
        setView(inf.getRoot());
        setSize(230, 150);
    }

    @Override
    public void onTextChanged(EditText et, String text) {
        if (et == srcBPM)
            dstBPM.setText(text);
    }

    @Override
    protected void onOK() {
        int srcBPM = Misc.parseInt(this.srcBPM.getText(), 1);
        int dstBPM = Misc.parseInt(this.dstBPM.getText(), 1);
        if (srcBPM == dstBPM) {
            DialogUtils.errorDialog("Set BPM", "Input BPM is the same as the output BPM!");
            return;
        }
        quantizer.setBPM(srcBPM, dstBPM);
        dismiss();
    }
}
