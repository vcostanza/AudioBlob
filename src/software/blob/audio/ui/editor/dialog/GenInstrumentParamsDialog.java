package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.instruments.GenInstrumentParams;
import software.blob.audio.ui.editor.instruments.GenInstrumentParams.Velocity;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.util.Misc;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.util.FileUtils;
import software.blob.ui.util.Log;
import software.blob.ui.view.EditText;
import software.blob.ui.view.TextView;
import software.blob.ui.view.View;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.*;
import software.blob.audio.util.JSONUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Parameters for generating an instrument
 */
public class GenInstrumentParamsDialog extends LayoutDialog {

    private static final String PREF_PREFIX = "geninst_";
    private static final String PREF_PRESET = "preset";
    private static final String PREF_SAMPLE = "sample";
    private static final String PREF_TIME_OFFSET = "time_offset";
    private static final String PREF_PITCH = "pitch";
    private static final String PREF_FADE = "fade";
    private static final String PREF_VEL_AMP = "velocity_amp";
    private static final String PREF_VEL_PITCH = "velocity_pitch";
    private static final String PREF_VEL_OFFSET = "velocity_offset";
    private static final String PREF_VEL_SAMPLE = "velocity_sample";
    private static final String PREF_MIN_DURATION = "min_duration";
    private static final String PREF_MAX_DURATION = "max_duration";
    private static final String PREF_RANDOM_DURATION = "random_duration";

    private static final String PRESET_EXT = "gip";
    private static final GenInstrumentParams PRESET_NEW = new GenInstrumentParams("Create New");
    private static final GenInstrumentParams PRESET_NONE = new GenInstrumentParams("<None>");

    private final Preferences prefs;
    private final File presetDir;
    private final TextView sampleType, sampleName;
    private final RadioGroup eachSample, timeOffset, samplePitch, sampleFade;
    private final JCheckBox velAmplitude, velPitch, velOffset, velSample;
    private final JCheckBox randomDur;
    private final EditText minDur, maxDur;
    private final JCheckBox quantize;
    private final EditText bpm;
    private final JComboBox<GenInstrumentParams> presetBox;
    private DefaultComboBoxModel<GenInstrumentParams> presetModel;

    private Callback callback;

    public GenInstrumentParamsDialog(AudioEditor editor, File file) {
        super(editor.getFrame());
        this.prefs = Preferences.userNodeForPackage(getClass());
        this.presetDir = new File(editor.getCacheDirectory(), "presets");

        boolean directory = file.isDirectory();

        InflatedLayout inf = LayoutInflater.inflate("gen_instrument_params_dialog");
        sampleType = inf.findByName("sampleType");
        sampleName = inf.findByName("sampleName");
        presetBox = inf.findByName("preset");
        eachSample = inf.findByName("eachSample");
        timeOffset = inf.findByName("timeOffset");
        samplePitch = inf.findByName("samplePitch");
        sampleFade = inf.findByName("sampleFade");
        velAmplitude = inf.findByName("velocityAmplitude");
        velPitch = inf.findByName("velocityPitch");
        velOffset = inf.findByName("velocityOffset");
        velSample = inf.findByName("velocitySample");
        minDur = inf.findByName("minDur");
        maxDur = inf.findByName("maxDur");
        randomDur = inf.findByName("randomDur");
        quantize = inf.findByName("quantize");
        bpm = inf.findByName("bpm");

        sampleType.setText("Sample " + (directory ? "directory" : "file") + ":");
        sampleName.setText(file.getName());

        if (!directory) {
            eachSample.setVisibility(View.GONE);
            velSample.setVisible(false);
        }

        eachSample.check(getPref(PREF_SAMPLE, "sameSample"));
        timeOffset.check(getPref(PREF_TIME_OFFSET, "startOffset"));
        samplePitch.check(getPref(PREF_PITCH, "defaultPitch"));
        sampleFade.check(getPref(PREF_FADE, "fadeOut"));
        velAmplitude.setSelected(getPref(PREF_VEL_AMP, true));
        velPitch.setSelected(getPref(PREF_VEL_PITCH, false));
        velOffset.setSelected(getPref(PREF_VEL_OFFSET, false));
        velSample.setSelected(getPref(PREF_VEL_SAMPLE, false));
        randomDur.setSelected(getPref(PREF_RANDOM_DURATION, false));
        minDur.setText(getPref(PREF_MIN_DURATION, 0.25f));
        maxDur.setText(getPref(PREF_MAX_DURATION, 1.0f));

        Track track = editor.getTracks().getSelected();
        bpm.setText(track != null ? track.bpm : Track.BPM_DEFAULT);

        setupPresets();

        setTitle("Generate Instrument");
        setView(inf.getRoot());
    }

    /**
     * Callback interface
     */
    public interface Callback {

        /**
         * Dialog confirmed
         * @param params Instrument parameters
         */
        void onConfirmed(GenInstrumentParams params);
    }

    public GenInstrumentParamsDialog setCallback(Callback callback) {
        this.callback = callback;
        return this;
    }

    @Override
    protected void onOK() {
        GenInstrumentParams params = createParams();
        if (params == null) {
            DialogUtils.errorDialog("Generate Instrument", "Failed to set instrument parameters");
            return;
        }

        GenInstrumentParams selected = (GenInstrumentParams) presetBox.getSelectedItem();
        if (selected != PRESET_NONE && selected != PRESET_NEW) {
            params.name = selected.name;
            params.file = selected.file;
            if (params.file == null)
                promptSavePreset(params);
            else
                savePreset(params);
        }

        setPref(PREF_SAMPLE, eachSample.getCheckedName());
        setPref(PREF_TIME_OFFSET, timeOffset.getCheckedName());
        setPref(PREF_PITCH, samplePitch.getCheckedName());
        setPref(PREF_FADE, sampleFade.getCheckedName());
        setPref(PREF_VEL_AMP, velAmplitude.isSelected());
        setPref(PREF_VEL_PITCH, velPitch.isSelected());
        setPref(PREF_VEL_OFFSET, velOffset.isSelected());
        setPref(PREF_MIN_DURATION, minDur.getText());
        setPref(PREF_MAX_DURATION, maxDur.getText());
        setPref(PREF_RANDOM_DURATION, randomDur.isSelected());
        setPref(PREF_PRESET, params.file != null ? params.file.getAbsolutePath() : "");

        if (callback != null)
            callback.onConfirmed(params);

        dismiss();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == presetBox) {
            GenInstrumentParams preset = (GenInstrumentParams) presetBox.getSelectedItem();
            setPreset(preset);
        } else
            super.actionPerformed(e);
    }

    private GenInstrumentParams createParams() {
        String sample = this.eachSample.getCheckedName();
        String timeOffset = this.timeOffset.getCheckedName();
        String pitch = this.samplePitch.getCheckedName();
        String fade = this.sampleFade.getCheckedName();

        GenInstrumentParams params = new GenInstrumentParams();
        if (!params.setEnumValue("sample", sample)
                || !params.setEnumValue("offset", timeOffset)
                || !params.setEnumValue("pitch", pitch)
                || !params.setEnumValue("fade", fade))
            return null;

        if (velAmplitude.isSelected())
            params.velocityControls.add(Velocity.AMPLITUDE);
        if (velPitch.isSelected())
            params.velocityControls.add(Velocity.PITCH);
        if (velOffset.isSelected())
            params.velocityControls.add(Velocity.OFFSET);
        if (velSample.isSelected())
            params.velocityControls.add(Velocity.SAMPLE);

        params.minDuration = Misc.parseDouble(minDur.getText(), 0.25);
        params.maxDuration = Misc.parseDouble(maxDur.getText(), 1.0);
        params.randomDuration = randomDur.isSelected();
        if (quantize.isSelected())
            params.bpm = Misc.parseInt(bpm.getText(), Track.BPM_DEFAULT);
        return params;
    }

    private void setupPresets() {
        String presetPath = getPref(PREF_PRESET, null);
        GenInstrumentParams selected = null;
        List<GenInstrumentParams> presetList = new ArrayList<>();
        File[] presetFiles = FileUtils.listFiles(presetDir, PRESET_EXT);
        if (presetFiles != null) {
            for (File f : presetFiles) {
                try {
                    GenInstrumentParams preset = new GenInstrumentParams(f);
                    if (f.getAbsolutePath().equals(presetPath))
                        selected = preset;
                    presetList.add(preset);
                } catch (Exception e) {
                    Log.e("Failed to read preset: " + f);
                }
            }
        }
        presetList.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        presetList.add(PRESET_NONE);
        presetList.add(PRESET_NEW);
        presetModel = new DefaultComboBoxModel<>(presetList.toArray(new GenInstrumentParams[0]));
        presetBox.setModel(presetModel);
        presetBox.addActionListener(this);
        setPresetBox(selected != null ? selected : PRESET_NONE);
    }

    private void setPresetBox(GenInstrumentParams preset) {
        presetBox.removeActionListener(this);
        presetBox.setSelectedItem(preset);
        presetBox.addActionListener(this);
    }

    private void setPreset(GenInstrumentParams params) {
        if (params == null || params == PRESET_NONE)
            return;
        if (params == PRESET_NEW)
            promptNewPreset();
        else {
            checkButton(eachSample, params.sample);
            checkButton(timeOffset, params.offset);
            checkButton(samplePitch, params.pitch);
            checkButton(sampleFade, params.fade);
            velAmplitude.setSelected(params.velocityControls.contains(Velocity.AMPLITUDE));
            velPitch.setSelected(params.velocityControls.contains(Velocity.PITCH));
            velOffset.setSelected(params.velocityControls.contains(Velocity.OFFSET));
            velSample.setSelected(params.velocityControls.contains(Velocity.SAMPLE));
            randomDur.setSelected(params.randomDuration);
            minDur.setText(params.minDuration);
            maxDur.setText(params.maxDuration);
        }
    }

    private void promptNewPreset() {
        EditTextDialog d = new EditTextDialog(this);
        d.setTitle("New Preset");
        d.setHint("Preset name");
        d.setText("Preset " + (presetBox.getModel().getSize() - 1));
        d.selectAll();
        d.setEmptyTextAllowed(false);
        d.setOnCancel(() -> setPresetBox(PRESET_NONE));
        d.showDialog(() -> {
            GenInstrumentParams params = createParams();
            if (params == null) {
                DialogUtils.errorDialog("Generate Instrument", "Failed to create instrument parameters");
                return;
            }
            params.name = d.getText();
            presetModel.insertElementAt(params, 0);
            setPresetBox(params);
            d.dismiss();
        });
    }

    private void promptSavePreset(final GenInstrumentParams params) {
        if (!presetDir.exists() && !presetDir.mkdirs())
            return;
        String fPrefix = params.name.toLowerCase(Locale.ROOT).replace(" ", "_");
        int i = 1;
        File f;
        do {
            String fName = fPrefix;
            if (i > 1)
                fName += "_" + i;
            f = new File(presetDir, fName + "." + PRESET_EXT);
            i++;
        } while (f.exists());
        params.file = f;
        savePreset(params);
    }

    private void savePreset(GenInstrumentParams params) {
        try {
            JSONUtils.writeObject(params.file, params.toJSON());
        } catch (Exception e) {
            DialogUtils.errorDialog("Generate Instrument", "Failed to save instrument parameters");
        }
    }

    private void checkButton(RadioGroup group, Enum<?> value) {
        String type = value.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String name = value.name().replace("_", " ").toLowerCase(Locale.ROOT);
        if (type.equals("fade"))
            group.check(type + Misc.upperWords(name).replace(" ", ""));
        else
            group.check(name + Misc.upperFirst(type));
    }

    private String getPref(String keySuffix, String defValue) {
        return prefs.get(PREF_PREFIX + keySuffix, defValue);
    }

    private boolean getPref(String keySuffix, boolean defValue) {
        return Boolean.parseBoolean(getPref(keySuffix, String.valueOf(defValue)));
    }

    private float getPref(String keySuffix, float defValue) {
        return Misc.parseFloat(getPref(keySuffix, String.valueOf(defValue)), defValue);
    }

    private void setPref(String keySuffix, String value) {
        prefs.put(PREF_PREFIX + keySuffix, value);
    }

    private void setPref(String keySuffix, boolean value) {
        setPref(keySuffix, String.valueOf(value));
    }

    private void setPref(String keySuffix, float value) {
        setPref(keySuffix, String.valueOf(value));
    }
}
