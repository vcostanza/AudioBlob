package software.blob.audio.ui.editor.instruments;

import software.blob.audio.util.Misc;
import software.blob.ui.util.FileUtils;
import software.blob.ui.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Instrument sample that uses multiple WAV files
 */
public class InstrumentMultiSample extends InstrumentSample {

    private final File[] files;
    private final Map<File, SampleWav> wavs = new HashMap<>();

    public InstrumentMultiSample(int note, int velocity, File[] files) {
        super(note, velocity);
        this.files = files;
    }

    public InstrumentMultiSample(int note, int velocity, File dir) {
        this(note, velocity, FileUtils.listFiles(dir, "wav"));
    }

    /**
     * Return a random WAV from the directory
     * @return Random WAV
     */
    @Override
    public synchronized SampleWav getWav() {
        File f = Misc.random(files);
        SampleWav wav = wavs.get(f);
        if (wav == null) {
            try {
                wavs.put(f, wav = loadWav(f));
            } catch (Exception e) {
                Log.e("Failed to load WAV file: " + f, e);
            }
        }
        return wav;
    }
}
