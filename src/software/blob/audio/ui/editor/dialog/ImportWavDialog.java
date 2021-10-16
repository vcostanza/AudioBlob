package software.blob.audio.ui.editor.dialog;

import software.blob.audio.audacity.frequency.FrequencyReader;
import software.blob.audio.audacity.frequency.FrequencyStats;
import software.blob.audio.effects.biquad.BiQuadFilter;
import software.blob.audio.effects.biquad.HighPassFilter;
import software.blob.audio.effects.biquad.LowPassFilter;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.pitchcurve.PitchCurve;
import software.blob.audio.ui.editor.pitchcurve.PitchCurveList;
import software.blob.audio.ui.editor.track.Track;
import software.blob.audio.ui.editor.track.TrackWav;
import software.blob.audio.wave.SnippetExtractor;
import software.blob.audio.wave.WavData;
import software.blob.ui.view.View;
import software.blob.ui.view.dialog.ProgressDialog;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.EditText;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.dialog.filebrowser.OnFileSelectedListener;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.util.FileUtils;
import software.blob.ui.util.Log;
import software.blob.audio.util.Misc;
import software.blob.ui.view.layout.LinearLayout;
import software.blob.ui.view.layout.table.TableLayout;

import javax.swing.*;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Dialog shown when importing a wav file
 */
public class ImportWavDialog extends EditorFileDialog implements OnFileSelectedListener {

    // Preferences
    private static final String PREF_HIGH_PASS = "highPass", PREF_LOW_PASS = "lowPass",
            PREF_MIN_AMP = "minAmp", PREF_SCAN_WINDOW = "scanWindow";

    public ImportWavDialog(AudioEditor editor) {
        super(editor, "Import WAV");
        setOnFileSelectedListener(this);
        setTypeFilter(FILTER_WAV);
    }

    @Override
    protected String getLastDirectoryPreference() {
        return "last_directory_import_wav";
    }

    @Override
    public void onFileSelected(File selected) {
        selected = FileUtils.appendExtension(selected, EXT_WAV);
        TrackWav wav;
        try {
            wav = new TrackWav(null, selected, 0);
        } catch (Exception e) {
            DialogUtils.errorDialog("Error", "Failed to load file: " + selected.getName());
            Log.e("Failed to load .wav file: " + selected, e);
            return;
        }
        dismiss();
        showParamsDialog(wav);
    }

    private void showParamsDialog(final TrackWav wav) {
        InflatedLayout inf = LayoutInflater.inflate("import_wav_dialog");

        final LinearLayout root = inf.getRoot();
        final JCheckBox insertTime = inf.findByName("insertTimeCode");
        final JCheckBox scanToggle = inf.findByName("scanPitch");
        final TableLayout scanParams = inf.findByName("pitchScanParams");
        final EditText highPass = inf.findByName("highPass");
        final EditText lowPass = inf.findByName("lowPass");
        final EditText minAmp = inf.findByName("minAmp");
        final EditText scanWindow = inf.findByName("scanWindow");

        final Preferences prefs = Preferences.userNodeForPackage(getClass());

        scanToggle.addChangeListener(e -> scanParams.setVisibility(scanToggle.isSelected() ? View.VISIBLE : View.GONE));
        highPass.setText(prefs.get(PREF_HIGH_PASS, ""));
        lowPass.setText(prefs.get(PREF_LOW_PASS, "1000"));
        minAmp.setText(prefs.get(PREF_MIN_AMP, "0.01"));
        scanWindow.setText(prefs.get(PREF_SCAN_WINDOW, "0.1"));

        final LayoutDialog d = new LayoutDialog(editor.getFrame());
        d.setTitle("Import WAV");
        d.setView(root);
        d.showDialog(() -> {

            // Set track wav to the current selection start time
            if (insertTime.isSelected())
                wav.time = editor.getSelection().getStartTime();

            if (scanToggle.isSelected()) {
                // Set pitch scan parameters and process
                prefs.put(PREF_HIGH_PASS, highPass.getText());
                prefs.put(PREF_LOW_PASS, lowPass.getText());
                prefs.put(PREF_MIN_AMP, minAmp.getText());
                prefs.put(PREF_SCAN_WINDOW, scanWindow.getText());

                double hp = Misc.parseFloat(highPass.getText(), 0);
                double lp = Misc.parseFloat(lowPass.getText(), 0);
                double ma = Misc.parseFloat(minAmp.getText(), 0);
                double sw = Misc.parseFloat(scanWindow.getText(), 0.1f);

                // Minimum scan window size
                if (sw < 0.1f) {
                    DialogUtils.errorDialog("Error", "Scan window size must be at least 0.1 seconds");
                    return;
                }

                processImport(wav, hp, lp, ma, sw);
            } else {
                // Just import the wav
                onFinished(wav, null);
            }

            d.dismiss();
        });
    }

    private void processImport(final TrackWav wav, double highPass, double lowPass, double minAmp, double scanWindow) {

        WavData processed = wav;

        // Apply pass filters
        if (lowPass > 0)
            processed = new LowPassFilter(lowPass, BiQuadFilter.PoleType.EIGHT).process(processed);
        if (highPass > 0)
            processed = new HighPassFilter(highPass, BiQuadFilter.PoleType.EIGHT).process(processed);

        final SnippetExtractor extractor = new SnippetExtractor();
        extractor.setMinimumAmplitude(minAmp);
        extractor.setMinimumSnippetDuration(scanWindow);
        extractor.setMinimumSilenceDuration(scanWindow / 10);

        final FrequencyReader inputReader = new FrequencyReader();
        inputReader.setMinimumAmplitude(minAmp);
        inputReader.setFrequencyRange(highPass, lowPass);
        inputReader.setScanWindow(scanWindow);
        inputReader.setNumScans(16);
        inputReader.setMultiThreaded(true);

        // Read frequencies on a separate thread
        final WavData toScan = processed;
        final ProgressDialog prog = new ProgressDialog(editor.getFrame());
        prog.setTitle("Processing " + wav.name);
        prog.showDialog();
        new Thread(() -> {
            FrequencyStats stats = inputReader.read(toScan, extractor, (p, max) -> {
                SwingUtilities.invokeLater(() -> prog.setProgress(p, max));
                return !prog.isCancelled();
            });
            if (stats == null)
                return;
            final PitchCurveList curves = new PitchCurveList(stats);
            for (PitchCurve curve : curves)
                curve.pos.time += wav.time;
            if (prog.isCancelled())
                return;
            SwingUtilities.invokeLater(() -> {
                prog.dismiss();
                onFinished(wav, curves);
            });
        }).start();
    }

    private void onFinished(TrackWav wav, PitchCurveList curves) {
        Track track = new Track(editor, wav.name);
        track.curves = curves;
        track.seed = wav;
        wav.track = track;
        wav.layer = track.getLayer("waveform");
        editor.getTracks().add(track);
        editor.getTracks().select(track);
        editor.updateDuration();
    }
}
