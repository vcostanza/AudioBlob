package software.blob.audio.ui.editor.dialog;

import software.blob.audio.playback.AudioHandle;
import software.blob.audio.playback.AudioPlayer;
import software.blob.audio.ui.DialogProgressCallback;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.MidiNoteListener;
import software.blob.audio.ui.editor.instruments.GenInstrument;
import software.blob.audio.ui.editor.instruments.GenInstrumentParams;
import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.instruments.InstrumentSample;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate an instrument using a sample from an existing loaded instrument
 */
public class GenInstrumentSampleDialog extends LayoutDialog implements MidiNoteListener {

    private final AudioEditor editor;
    private final AudioPlayer audioPlayer;
    private final JComboBox<String> instrumentBox;
    private final JComboBox<InstrumentSample> sampleBox;
    private final List<Instrument> instruments;
    private Instrument instrument;

    private InstrumentDialog.Callback callback;

    public GenInstrumentSampleDialog(AudioEditor editor, Window window) {
        super(window);
        this.editor = editor;
        this.audioPlayer = editor.getAudioPlayer();

        InflatedLayout inf = LayoutInflater.inflate("gen_instrument_sample_dialog");
        setView(inf.getRoot());
        setTitle("Gen. Instrument");
        setSize(200, DEFAULT_HEIGHT);

        sampleBox = inf.findByName("sample");
        sampleBox.addActionListener(this);

        instruments = editor.getTracks().getInstruments();
        int selectedIndex = 0;
        int index = 0;
        Track selected = editor.getTracks().getSelected();
        List<String> instrumentNames = new ArrayList<>(instruments.size());
        for (Instrument inst : instruments) {
            if (selected.instrument == inst)
                selectedIndex = index;
            instrumentNames.add(inst.getName());
            index++;
        }

        instrumentBox = inf.findByName("instrument");
        instrumentBox.setModel(new DefaultComboBoxModel<>(instrumentNames.toArray(new String[0])));
        instrumentBox.addActionListener(this);
        instrumentBox.setSelectedIndex(selectedIndex);

        editor.getMidi().addListener(this);
    }

    public GenInstrumentSampleDialog(AudioEditor editor) {
        this(editor, editor.getFrame());
    }

    public GenInstrumentSampleDialog setCallback(InstrumentDialog.Callback cb) {
        callback = cb;
        return this;
    }

    @Override
    protected void onOK() {
        super.onOK();

        InstrumentSample sample = (InstrumentSample) sampleBox.getSelectedItem();
        WavData wav = getSampleWav();
        if (sample == null || wav == null)
            return;

        GenInstrument inst = GenInstrument.create(editor);
        if (inst == null)
            return;

        GenInstrumentParams params = new GenInstrumentParams();
        params.minDuration = params.maxDuration = wav.duration;

        // Set the name of the instrument to the name of the sample with the original note in parentheses
        // But only if the name of the sample isn't the same as the note
        String noteName = Misc.getNoteName(sample.note);
        if (!noteName.equals(wav.name))
            inst.setName(wav.name + " (" + noteName + ")");

        inst.setSeedWav(wav);
        inst.setParams(params);
        inst.init(new DialogProgressCallback(window, "Generating instrument",
                (results) -> onInstrumentLoaded(inst)));

        dismiss();
    }

    @Override
    public void dispose() {
        super.dispose();
        editor.getMidi().removeListener(this);
        editor.getMidi().setInstrumentOverride(null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == instrumentBox) {
            int index = instrumentBox.getSelectedIndex();
            setInstrument(instruments.get(index));
        } else if (src == sampleBox) {
            if (instrument != null) {
                WavData wav = getSampleWav();
                if (wav != null) {
                    AudioHandle h = audioPlayer.createHandle(wav);
                    h.setVolume(instrument.getMaxAmplitude());
                    audioPlayer.queue(h);
                }
            }
        } else
            super.actionPerformed(e);
    }

    @Override
    public void onMidiNotePlayed(MidiNote note, InstrumentSample sample, AudioHandle handle) {
        sampleBox.setSelectedItem(sample);
    }

    private void setInstrument(Instrument instrument) {
        this.instrument = instrument;
        List<InstrumentSample> samples = instrument.getSamples();
        sampleBox.setModel(new DefaultComboBoxModel<>(samples.toArray(new InstrumentSample[0])));
        editor.getMidi().setInstrumentOverride(instrument);
    }

    private void onInstrumentLoaded(Instrument instrument) {
        if (callback != null)
            callback.onInstrumentLoaded(instrument);
        else
            editor.setInstrument(instrument);
    }

    private WavData getSampleWav() {
        InstrumentSample sample = (InstrumentSample) sampleBox.getSelectedItem();
        if (sample == null)
            return null;

        WavData wav = sample.getWav();
        if (wav == null) {
            DialogUtils.errorDialog("Generate Instrument", "Failed to load sample for " + sample);
            return null;
        }

        return wav;
    }
}
