package software.blob.audio.ui.editor.instruments;

import software.blob.audio.effects.sbsms.SBSMSEffect;
import software.blob.audio.effects.sbsms.SBSMSTask;
import software.blob.audio.effects.volume.AmplitudeModulator;
import software.blob.audio.effects.volume.FadeEffect;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.instruments.GenInstrumentParams.*;
import software.blob.audio.thread.WavProcessorService;
import software.blob.audio.thread.callback.MessageCallback;
import software.blob.audio.thread.callback.MultiTaskCallback;
import software.blob.audio.thread.callback.TaskCallback;
import software.blob.audio.ui.editor.midi.MidiDeviceType;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.audio.util.Misc;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.util.FileUtils;
import software.blob.ui.util.Log;
import software.blob.audio.util.MutableInt;

import javax.sound.midi.MidiDevice;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Instrument with random samples
 */
public abstract class GenInstrument extends Instrument {

    protected static final double MIDDLE_C = Misc.getNoteFrequency(Misc.getNoteValue("C4"));

    public interface NoteIterator {

        /**
         * Called for each note this instrument uses
         * @param note Note value
         * @param velocity Minimum velocity
         * @param index Note index (starting at zero)
         * @param total Total iterations
         */
        void forEachNote(int note, int velocity, int index, int total);
    }

    protected final AudioEditor editor;
    protected File[] samples;
    protected File seedFile;
    protected WavData seedWav;
    protected double minDuration = 0.25;
    protected GenInstrumentParams params;

    protected GenInstrument(AudioEditor editor) {
        this.editor = editor;
        this.interpolate = false;
    }

    @Override
    public String getName() {
        if (seedWav != null)
            return seedWav.name;
        else if (seedFile != null)
            return FileUtils.stripExtension(seedFile) + " (G)";
        return "Generated";
    }

    public void setParams(GenInstrumentParams params) {
        this.params = params;
        this.minDuration = params.minDuration;
        this.maxDuration = params.maxDuration;
        this.velocityBasedAmp = params.velocityControls.contains(Velocity.AMPLITUDE);
    }

    public void setSeedFile(File seed) {
        this.seedFile = seed;
        if (seed.isDirectory())
            this.samples = FileUtils.listFilesRecursive(seed, Misc.MEDIA_FILTER).toArray(new File[0]);
    }

    public void setSeedWav(WavData wav) {
        this.seedWav = wav;
    }

    public void init(final TaskCallback cb) {
        new Thread(() -> {
            initImpl(cb);
            if (cb != null)
                cb.onFinished(null);
        }).start();
    }

    protected void initImpl(final TaskCallback cb) {
        final NoteWavMap wavs = new NoteWavMap();
        final MutableInt taskNum = new MutableInt();
        int taskCount = 2;
        if (params.pitch != Pitch.CONSTANT)
            taskCount++;

        final boolean randomSample = params.sample != Sample.SAME && samples != null;
        final MultiTaskCallback mcb = new MultiTaskCallback(cb, taskCount);

        // Load samples
        setProgressMessage(cb, "Generating samples");
        final WavData src = randomSample ? null : (samples != null ? getRandomSample() : getSeedSample());
        forEachNote((note, vel, index, total) -> {
            WavData wav = src;
            if (randomSample) {
                if (vel == 0 || params.velocityControls.contains(Velocity.SAMPLE))
                    wav = getRandomSample();
                else
                    wav = wavs.get(note, 0);
            }
            if (vel == 0 || params.velocityControls.contains(Velocity.OFFSET))
                wav = trimSample(wav);
            wavs.put(note, vel, wav);
            mcb.onProgress(taskNum.get(), index + 1, total);
        });

        // Pitch changes
        if (params.pitch != Pitch.CONSTANT || params.velocityControls.contains(Velocity.PITCH)) {
            taskNum.increment();
            setProgressMessage(cb, "Adjusting sample pitch");

            final List<SBSMSTask> tasks = new ArrayList<>();

            forEachNote((note, vel, index, total) -> {
                WavData wav = wavs.get(note, vel);
                SBSMSEffect sbsms;
                if (params.pitch == Pitch.DEFAULT) {
                    sbsms = new SBSMSEffect();
                    if (vel < 120 && params.velocityControls.contains(Velocity.PITCH)) {
                        double velPcnt = (double) vel / MidiNote.MAX_VELOCITY;
                        note -= 1 - velPcnt;
                    }
                    double pitch = Misc.getNoteFrequency(note) / MIDDLE_C;
                    sbsms.setPitch(pitch);
                } else
                    sbsms = getRandomPitchEffect(wav);
                tasks.add(new SBSMSTask(wav, sbsms));
            });

            final List<WavData> processed = new WavProcessorService().execute(tasks, mcb.getCallback(taskNum.get()));
            wavs.clear();
            forEachNote((note, vel, index, total) -> wavs.put(note, vel, processed.get(index)));
        }

        setProgressMessage(cb, "Finalizing samples");
        taskNum.increment();
        forEachNote((note, vel, index, total) -> {
            WavData wav = wavs.get(note, vel);
            wav = processSample(wav);
            addSample(new InstrumentSample(note, vel, wav));
            mcb.onProgress(taskNum.get(), index + 1, total);
        });
    }

    protected abstract void forEachNote(NoteIterator noteIter);

    protected WavData getSample(File file, double minDuration) {
        if (file.isDirectory()) {
            if (file.equals(this.seedFile))
                return getRandomSample();
            // XXX - Avoid calling this repeatedly since File.listFiles can be very slow
            return getRandomSample(file.listFiles());
        }

        // Make sure the file exists
        if (!file.exists())
            return null;

        // Need to convert to wav first
        if (!FileUtils.getExtension(file).equals("wav"))
            file = convertToWav(file);

        try {
            WavData src = new WavData(file);
            src.trimSilence(0);
            if (src.duration >= minDuration)
                return src;
        } catch (Exception ignored) {
        }
        return null;
    }

    protected WavData getSample(File file) {
        return getSample(file, 0);
    }

    protected WavData getSeedSample() {
        if (seedWav != null)
            return seedWav;
        return getSample(seedFile);
    }

    protected WavData getRandomSample() {
        return getRandomSample(this.samples);
    }

    protected WavData getRandomSample(File[] samples) {
        if (samples == null)
            return null;
        WavData src = null;
        while (src == null)
            src = getSample(Misc.random(samples), this.minDuration);
        return src;
    }

    protected WavData trimSample(WavData src) {
        boolean randomOffset = params.offset == Offset.RANDOM;
        double dur = params.randomDuration ? Misc.random(minDuration, maxDuration) : maxDuration;
        dur = roundToBPM(dur);
        int len = src.getFrame(dur);
        if (src.numFrames > len && len > 0) {
            WavData out = null;
            while (out == null || out.duration < minDuration) {
                int startFrame = randomOffset ? Misc.random(src.numFrames - len) : 0;
                startFrame = roundToBPM(src, startFrame);
                out = new WavData(src, startFrame, len);
                out.trimSilence(Misc.getAmplitude(-60));
            }
            return out;
        } else
            return new WavData(src);
    }

    protected WavData processSample(WavData wav) {
        wav.setPeakAmplitude(1);
        wav.setSampleRate(Math.max(44100, wav.sampleRate));
        double shortDur = 0.01;
        double longDur = wav.duration * 0.9;
        switch (params.fade) {
            case IN:
                wav = new FadeEffect(1, 0, shortDur, FadeEffect.Shape.QUADRATIC).process(wav);
                wav = new FadeEffect(0, 1, longDur, FadeEffect.Shape.LINEAR).process(wav);
                break;
            case OUT:
                wav = new FadeEffect(0, 1, shortDur, FadeEffect.Shape.QUADRATIC).process(wav);
                wav = new FadeEffect(1, 0, longDur, FadeEffect.Shape.LINEAR).process(wav);
                break;
            case RANDOM_STEREO:
            case RANDOM_MONO: {
                int numFrames = wav.numFrames;
                int shortFrame = wav.getFrame(shortDur);
                double[][] ampFactors = new double[wav.channels][numFrames];
                boolean mono = params.fade == Fade.RANDOM_MONO;
                for (int c = 0; c < (mono ? 1 : ampFactors.length); c++) {
                    int startFrame = 0;
                    double startFactor = 0;
                    while (startFrame < numFrames) {
                        int endFrame;
                        if (startFrame == 0)
                            endFrame = shortFrame;
                        else {
                            int randFrame = roundToBPM(wav, Misc.random(0, numFrames));
                            if (randFrame == 0)
                                randFrame = numFrames;
                            endFrame = startFrame + randFrame;
                        }
                        if (endFrame >= numFrames - shortFrame)
                            endFrame = numFrames;
                        double endFactor = endFrame == numFrames ? 0 : Misc.random(0.0, 1.0);
                        for (int f = startFrame; f < endFrame; f++) {
                            double lerp = (double) (f - startFrame) / (endFrame - startFrame);
                            ampFactors[c][f] = (1 - lerp) * startFactor + lerp * endFactor;
                        }
                        startFrame = endFrame;
                        startFactor = endFactor;
                    }
                }

                // Use the same amplification factor for each channel
                if (mono) {
                    for (int c = 1; c < ampFactors.length; c++)
                        System.arraycopy(ampFactors[0], 0, ampFactors[c], 0, ampFactors[0].length);
                }

                AmplitudeModulator mod = new AmplitudeModulator();
                mod.setAmplitudeFactors(ampFactors);
                wav = mod.process(wav);
                wav.setPeakAmplitude(1);
                break;
            }
        }
        wav.setPeakAmplitude(Misc.random(0.5, 1));
        wav = new FadeEffect(0, 1, 0.05, FadeEffect.Shape.QUADRATIC).process(wav);
        wav = new FadeEffect(1, 0, 0.05, FadeEffect.Shape.QUADRATIC).process(wav);
        return wav;
    }

    protected void setProgressMessage(TaskCallback cb, String msg) {
        if (cb instanceof MessageCallback)
            ((MessageCallback) cb).setMessage(msg);
        else
            Log.d(msg);
    }

    protected SBSMSEffect getRandomPitchEffect(WavData wav) {
        int middleC = Misc.getNoteValue("C4");
        SBSMSEffect sbsms = new SBSMSEffect();
        int numFrames = wav.numFrames;
        float[] pitchArray = new float[numFrames];
        int rollMin = wav.getFrame(minDuration);
        int rollMax = wav.getFrame(maxDuration);
        int startFrame = 0;
        while (startFrame < numFrames) {
            int endFrame = startFrame + (rollMin == rollMax ? rollMin
                    : roundToBPM(wav, Misc.random(rollMin, rollMax)));
            if (endFrame >= numFrames)
                endFrame = numFrames;

            double pitch = Misc.getNoteFrequency(middleC + Misc.random(-12, 12)) / MIDDLE_C;
            for (int f = startFrame; f < endFrame; f++)
                pitchArray[f] = (float) pitch;

            startFrame = endFrame;
        }
        sbsms.setPitchArray(pitchArray);
        //sbsms.setPitch(Misc.getNoteFrequency(middleC + Misc.random(-12, 12)) / MIDDLE_C);
        return sbsms;
    }

    private int roundToBPM(WavData wav, int frame) {
        if (params.bpm <= 0)
            return frame;
        return wav.getFrame(roundToBPM(wav.getTime(frame)));
    }

    private double roundToBPM(double time) {
        if (params.bpm <= 0)
            return time;
        time = Misc.roundToBPM(time, params.bpm);
        return time;
    }

    public static GenInstrument create(AudioEditor editor) {
        MidiDevice device = editor.getMidi().getLastConnectedDevice();
        if (device == null) {
            DialogUtils.errorDialog("No MIDI Device", "No MIDI devices connected");
            return null;
        }

        MidiDeviceType type = MidiDeviceType.getType(device);
        if (type == MidiDeviceType.KEYBOARD)
            return new GenKeyboardInstrument(editor);
        else if (type == MidiDeviceType.DRUMS)
            return new GenDrumInstrument(editor);

        DialogUtils.errorDialog("Unsupported MIDI device", "No supported MIDI devices connected");
        return null;
    }

    /**
     * Convert a file to waveform format
     * @param musicFile Music file
     * @return WAV file
     */
    private File convertToWav(File musicFile) {
        File tmp = new File(editor.getTempDirectory(), "wavconv");
        if (!tmp.exists() && !tmp.mkdirs()) {
            Log.e("Failed to make temporary directory: " + tmp);
            return musicFile;
        }

        File wavFile = new File(tmp, musicFile.getName() + ".wav");
        if (wavFile.exists())
            return wavFile;

        String command = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "ffmpeg",
                    "-i", "file:" + musicFile.getAbsolutePath(),
                    "-c:a", "pcm_s16le", "-y",
                    wavFile.getAbsolutePath()
            });
            p.waitFor();
            p.destroy();
            return wavFile;
        } catch (Exception e) {
            Log.e("Failed to run " + command, e);
        }
        return musicFile;
    }
}
