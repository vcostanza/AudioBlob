package software.blob.audio.ui.editor.instruments;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.instruments.GenInstrumentParams.*;

/**
 * A piano keyboard with random samples
 */
public class GenKeyboardInstrument extends GenInstrument {

    private static final int MIN_NOTE = 36, MAX_NOTE = 84, NOTE_COUNT = (MAX_NOTE - MIN_NOTE) + 1;
    private static final int[] VELOCITIES = {0, 64, 92, 120};

    public GenKeyboardInstrument(AudioEditor editor) {
        super(editor);
        this.maxAmplitude = 0.3;
    }

    @Override
    protected void forEachNote(NoteIterator noteIter) {
        int[] velocities = params.velocityControls.isEmpty()
                || params.velocityControls.size() == 1
                && params.velocityControls.contains(Velocity.AMPLITUDE)
                ? new int[1] : VELOCITIES;
        int i = 0, total = NOTE_COUNT * velocities.length;
        for (int n = MIN_NOTE; n <= MAX_NOTE; n++) {
            for (int v : velocities)
                noteIter.forEachNote(n, v, i++, total);
        }
    }

    /*private void createPresets() {
        addPreset(new Preset("Hypersamples per octave", false, (instrument, seed, cb) -> {
            WavData c3, c4;
            try {
                File dir = SoundApp.HYPERSAMPLE_DIR;
                c3 = new WavData(new File(dir, "130_05_25.wav"));
                c4 = new WavData(new File(dir, "C4_05_25.wav"));
                c3.padLoop(c3.numFrames);
                c4.padLoop(c4.numFrames);
            } catch (Exception ignore) {
                return;
            }

            setProgressMessage(cb, "Fetching random samples");
            List<SBSMSTask> tasks = new ArrayList<>();
            int lowC = Misc.getNoteValue("C3");
            int middleC = Misc.getNoteValue("C4");
            for (int note = MIN_NOTE; note <= MAX_NOTE; note++) {
                int srcNote;
                WavData srcWav;
                if (note < middleC) {
                    srcNote = lowC;
                    srcWav = c3;
                } else {
                    srcNote = middleC;
                    srcWav = c4;
                }
                WavData wav = trimSample(srcWav, false);
                wav.setPeakAmplitude(1);
                SBSMSEffect sbsms = new SBSMSEffect();
                sbsms.setPitch(Misc.getNoteFrequency(note) / Misc.getNoteFrequency(srcNote));
                tasks.add(new SBSMSTask(wav, sbsms));
            }

            List<WavData> processed = new WavProcessorService().execute(tasks,
                    new LogProgressCallback("RandomInstrument", "Type H"));

            for (int n = MIN_NOTE, i = 0; n <= MAX_NOTE; n++, i++) {
                WavData wav = processed.get(i);
                wav = processSample(wav);
                addSample(new InstrumentSample(n, wav));
            }
        }));
    }*/
}
