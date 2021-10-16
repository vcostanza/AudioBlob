package software.blob.audio.ui.editor.instruments;

import org.json.JSONArray;
import org.json.JSONObject;
import software.blob.audio.ui.editor.midi.MidiNote;
import software.blob.ui.util.FileUtils;
import software.blob.audio.util.JSONUtils;
import software.blob.audio.util.Misc;
import software.blob.ui.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Sample-based instrument
 */
public class Instrument {

    // Amount to quiet each sample to prevent clipping
    public static final double DEFAULT_MAX_AMPLITUDE = 0.3;

    public static final Comparator<Instrument> SORT_NAME = (i1, i2) -> {
        String n1 = i1.getName();
        String n2 = i2.getName();
        if (n1 == null) n1 = "";
        if (n2 == null) n2 = "";
        return n1.compareToIgnoreCase(n2);
    };
    public static final Comparator<InstrumentSample> SORT_FREQUENCIES = Comparator.comparingInt((InstrumentSample o) -> o.note)
            .thenComparingInt(o -> o.velocity);

    protected File file;
    protected String name;
    protected final transient String uid = UUID.randomUUID().toString();
    protected final List<InstrumentSample> samples = new ArrayList<>();
    protected final Map<Integer, InstrumentSample> noteVelToSample = new HashMap<>();
    protected final Map<Integer, List<InstrumentSample>> noteToSamples = new HashMap<>();
    protected boolean sorted;
    protected boolean interpolate;
    protected boolean velocityBasedAmp = true;
    protected int sampleRate;
    protected int channels;
    protected double maxDuration = Double.MAX_VALUE;
    protected double maxAmplitude = DEFAULT_MAX_AMPLITUDE;

    public Instrument() {
        this.file = null;
    }

    /**
     * Load instrument from a directory of samples or a JSON-based metadata file
     * @param file Sample directory or metadata file
     * @throws IllegalArgumentException Directory doesn't exist or is empty
     */
    public Instrument(File file) throws IllegalArgumentException {
        if (!file.exists())
            throw new IllegalArgumentException("Failed to load sample file/directory: " + file);
        this.file = file;
        this.name = file != null ? FileUtils.stripExtension(file) : "Instrument";

        if (file.isDirectory()) {
            // Map samples based on their file name
            addSamples(file);
        } else {
            try {
                File dir = file.getParentFile();
                JSONObject json = JSONUtils.readObject(file);

                // Instrument name
                if (json.has("name"))
                    this.name = json.getString("name");

                // Desired max duration, sample rate, and channels
                this.maxDuration = json.has("duration") ? json.getDouble("duration") : Double.MAX_VALUE;
                this.sampleRate = json.has("sampleRate") ? json.getInt("sampleRate") : -1;
                this.channels = json.has("channels") ? json.getInt("channels") : -1;
                this.maxAmplitude = json.has("maxAmplitude") ? json.getDouble("maxAmplitude") : DEFAULT_MAX_AMPLITUDE;

                // Whether interpolation is supported
                if (json.has("interpolate"))
                    this.interpolate = json.getBoolean("interpolate");

                // Velocity affects the amplitude of the sample
                if (json.has("velocityBasedAmp"))
                    this.velocityBasedAmp = json.getBoolean("velocityBasedAmp");

                // Samples directory
                if (json.has("path")) {
                    String samplePath = json.getString("path");
                    addSamples(FileUtils.getFile(dir, samplePath));
                }

                if (json.has("samples")) {
                    JSONArray sampleArr = json.getJSONArray("samples");
                    for (int i = 0; i < sampleArr.length(); i++) {
                        JSONObject sampJson = sampleArr.getJSONObject(i);

                        String noteStr = sampJson.has("note") ? sampJson.getString("note") : null;
                        if (noteStr == null)
                            continue;

                        // Path to sample file or directory
                        InstrumentSample sample;
                        if (sampJson.has("path")) {
                            File sampleFile = FileUtils.getFile(dir, sampJson.getString("path"));
                            sample = addSample(noteStr, sampleFile);
                            if (sample == null)
                                Log.e("Sample not found at path: " + sampleFile);
                        } else {
                            int[] noteVel = getNoteVelocity(noteStr);
                            sample = noteVelToSample.get(getNoteKey(noteVel[0], noteVel[1]));
                        }

                        if (sample == null)
                            continue;

                        // Loop start and end frame
                        if (sampJson.has("loopFrames")) {
                            JSONArray loopArr = sampJson.getJSONArray("loopFrames");
                            if (loopArr.length() == 2)
                                sample.setLoopFrames(loopArr.getInt(0), loopArr.getInt(1));
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to read sample metadata", e);
            }
        }

        // Sort samples by frequency
        sort();
    }

    /**
     * Get the name of this instrument
     * By default this is the file name w/out extension
     * @return Instrument name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get path to an instrument
     * @return Path to instrument directory or metadata file
     */
    public String getPath() {
        return file != null ? file.getAbsolutePath() : null;
    }

    /**
     * Get a UUID representation of this instrument's path
     * @return Path UUID or null if path isn't set
     */
    public String getPathUID() {
        String path = getPath();
        if (path == null)
            return null;
        return UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Get the UID for this instrument
     * Typically this is the path UID, but if the instrument has no path
     * a randomly generated UID is returned
     * @return Instrument UID
     */
    public String getUID() {
        String pathUID = getPathUID();
        return pathUID != null ? pathUID : this.uid;
    }

    /**
     * Set whether this instrument supports pitch interpolation between samples
     * @param interpolate True to interpolate sample pitch between notes
     */
    public void setInterpolate(boolean interpolate) {
        this.interpolate = interpolate;
    }

    /**
     * Check whether this instrument supports pitch interpolation between samples
     * @return True if interpolation is supported
     */
    public boolean isInterpolationSupported() {
        return this.interpolate;
    }

    /**
     * Check whether velocity affects the amplitude of the samples in this instrument
     * @return True if velocity determines the amplitude of the samples
     */
    public boolean isVelocityBasedAmp() {
        return this.velocityBasedAmp;
    }

    /**
     * Get the maximum amplitude for notes
     * @return Max amplitude (0 to 1)
     */
    public double getMaxAmplitude() {
        return maxAmplitude;
    }

    /**
     * Set the desired output sample rate for this instrument
     * @param sampleRate Sample rate
     */
    public void setSampleRate(int sampleRate) {
        if (this.sampleRate != sampleRate) {
            this.sampleRate = sampleRate;
            synchronized (samples) {
                for (InstrumentSample sample : samples)
                    sample.setSampleRate(sampleRate);
            }
        }
    }

    /**
     * Set the desired number of output channels for this instrument
     * @param channels Channel count
     */
    public void setChannels(int channels) {
        if (this.channels != channels) {
            this.channels = channels;
            synchronized (samples) {
                for (InstrumentSample sample : samples)
                    sample.setChannels(channels);
            }
        }
    }

    /**
     * Add sample to the list
     * @param noteStr String containing the note and optionally a velocity
     * @param f Sample file
     * @return Instrument sample
     */
    public InstrumentSample addSample(String noteStr, File f) {

        int[] noteVel = getNoteVelocity(noteStr);

        InstrumentSample sample;
        if (f.isFile())
            sample = new InstrumentSample(noteVel[0], noteVel[1], f);
        else
            sample = new InstrumentMultiSample(noteVel[0], noteVel[1], f);

        addSample(sample);
        return sample;
    }

    public InstrumentSample addSample(File f) {
        String noteName = FileUtils.stripExtension(f);
        return addSample(noteName, f);
    }

    public void addSample(InstrumentSample sample) {
        if (sample.note < 0)
            return;

        // Set desired WAV parameters
        sample.setMaxDuration(maxDuration);
        sample.setSampleRate(sampleRate);
        sample.setChannels(channels);

        int noteKey = sample.getKey();
        synchronized (samples) {
            List<InstrumentSample> nts = noteToSamples.computeIfAbsent(sample.note, k -> new ArrayList<>());
            InstrumentSample existing = noteVelToSample.remove(noteKey);
            if (existing != null) {
                samples.remove(existing);
                nts.remove(existing);
            }
            nts.add(sample);
            samples.add(sample);
            noteVelToSample.put(noteKey, sample);
            sorted = false;
        }
    }

    /**
     * Add all samples in a directory
     * @param dir Directory
     */
    public void addSamples(File dir) {
        for (File f : FileUtils.listFiles(dir, "wav"))
            addSample(f);
    }

    /**
     * Get samples in this instrument
     * @return Samples
     */
    public List<InstrumentSample> getSamples() {
        return new ArrayList<>(samples);
    }

    /**
     * Get sample count in this instrument
     * @return Sample count
     */
    public int getSampleCount() {
        return samples.size();
    }

    /**
     * Sort samples by frequency
     */
    public void sort() {
        synchronized (samples) {
            if (!sorted) {
                samples.sort(SORT_FREQUENCIES);
                for (Map.Entry<Integer, List<InstrumentSample>> e : noteToSamples.entrySet())
                    e.getValue().sort(SORT_FREQUENCIES);
                sorted = true;
            }
        }
    }

    /**
     * Find a sample closest to a given note
     * @param note Note value
     * @param minVelocity Minimum velocity
     * @return Instrument sample
     */
    public InstrumentSample getSample(int note, int minVelocity) {
        InstrumentSample sample = noteVelToSample.get(getNoteKey(note, minVelocity));
        if (sample == null) {
            List<InstrumentSample> nts = noteToSamples.get(note);
            if (nts != null)
                sample = findSampleByVelocity(nts, minVelocity);
            else
                sample = findSampleByNote(note, minVelocity);
        }
        return sample;
    }

    /**
     * Get all samples for a given note (any velocity)
     * @param note Note value
     * @return List of instrument samples
     */
    public List<InstrumentSample> getSamples(int note) {
        return findSamplesByNote(note);
    }

    /**
     * Find sample by a note value and velocity
     * @param note Note value
     * @param velocity Velocity
     * @return Sample
     */
    public InstrumentSample findSampleByNote(int note, int velocity) {
        return findSampleByVelocity(findSamplesByNote(note), velocity);
    }

    public InstrumentSample findSampleByNote(int note, double amplitude) {
        return findSampleByNote(note, (int) Math.round(amplitude * MidiNote.MAX_VELOCITY));
    }

    public InstrumentSample findSampleByVelocity(List<InstrumentSample> samples, int velocity) {
        InstrumentSample last = null;
        for (InstrumentSample sample : samples) {
            if (velocity < sample.velocity)
                return last;
            last = sample;
        }
        return last;
    }

    /**
     * Find a sample closest to the given note
     * @param note Note value
     * @return Sample
     */
    public List<InstrumentSample> findSamplesByNote(int note) {
        sort();
        List<InstrumentSample> range = new ArrayList<>();
        boolean firstIter = true;
        boolean addNextRange = false;
        for (int i = 0; i < samples.size(); i++) {
            InstrumentSample cur = samples.get(i);
            InstrumentSample next = i < samples.size() - 1 ? samples.get(i + 1) : null;
            range.add(cur);

            if (next != null && cur.note == next.note)
                continue;

            if (addNextRange)
                return range;

            if (firstIter && note < cur.note)
                return range;
            else if (next != null && note >= cur.note && (cur.note == next.note || note < next.note)) {
                double slide = (double) (note - cur.note) / (next.note - cur.note);
                if (slide < 0.5)
                    return range;
                else
                    addNextRange = true;
            }

            if (next != null && next.note > cur.note) {
                range.clear();
                firstIter = false;
            }
        }
        return range;
    }

    public List<InstrumentSample> findSamplesByFrequency(double frequency) {
        return findSamplesByNote((int) Math.round(Misc.getNoteValue(frequency)));
    }

    public InstrumentSample findSampleByFrequency(double frequency, int velocity) {
        return findSampleByVelocity(findSamplesByFrequency(frequency), velocity);
    }

    public InstrumentSample findSampleByFrequency(double frequency, double amplitude) {
        return findSampleByFrequency(frequency, (int) Math.round(amplitude * MidiNote.MAX_VELOCITY));
    }

    /**
     * Save this instrument to a JSON file
     * @param file File to save to (.inst extension)
     * @param savedSampleDir Directory to place newly saved samples
     * @param updatePath True to set the path to this file
     * @return True if successful
     */
    public boolean writeToFile(File file, File savedSampleDir, boolean updatePath) {
        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e("Failed to make directory: " + dir);
            return false;
        }

        file = FileUtils.appendExtension(file, "inst");
        this.file = file;

        JSONObject json = new JSONObject();

        String name = getName();
        if (name != null)
            json.put("name", name);

        if (maxDuration < Double.MAX_VALUE)
            json.put("duration", maxDuration);

        if (sampleRate > 0)
            json.put("sampleRate", sampleRate);

        if (channels > 0)
            json.put("channels", channels);

        if (maxAmplitude > 0)
            json.put("maxAmplitude", maxAmplitude);

        json.put("interpolate", interpolate);
        json.put("velocityBasedAmp", velocityBasedAmp);

        // Find common directory
        File sampleDir = null;
        for (InstrumentSample sample : samples) {
            if (sample.file == null) {
                if (sampleDir == null)
                    sampleDir = savedSampleDir;
            } else {
                File sampleParent = sample.file.getParentFile();
                if (sampleDir == null)
                    sampleDir = sampleParent;
                else if (!sampleDir.equals(sampleParent))
                    sampleDir = null;
            }
        }

        if (sampleDir != null)
            json.put("path", FileUtils.getRelativePath(dir, sampleDir));

        // Save sample data
        JSONArray sampleArr = new JSONArray();
        for (InstrumentSample sample : samples) {
            String noteName = Misc.getNoteName(sample.note);
            if (sample.velocity > 0)
                noteName += " " + sample.velocity;
            File sf = sample.file != null ? sample.file : null;
            SampleWav wav = sample.getWav();
            if (sf == null) {
                // Need to save the wav
                if (wav == null)
                    continue; // Cannot save
                if (!savedSampleDir.exists() && !savedSampleDir.mkdirs()) {
                    Log.e("Failed to make directory: " + savedSampleDir);
                    continue; // Cannot create directory
                }
                sf = new File(savedSampleDir, noteName + ".wav");
                wav.unlock();
                wav.writeToFile(sf);
                wav.lock();
            }

            boolean add = false;
            JSONObject s = new JSONObject();
            s.put("note", noteName);

            if (sf != null && !sf.getParentFile().equals(sampleDir)) {
                s.put("path", FileUtils.getRelativePath(dir, sf));
                add = true;
            }

            if (sample.isLoopable()) {
                JSONArray loopArr = new JSONArray();
                loopArr.put(sample.getStartLoopFrame());
                loopArr.put(sample.getEndLoopFrame());
                s.put("loopFrames", loopArr);
                add = true;
            }

            if (add)
                sampleArr.put(s);
        }

        if (sampleArr.length() > 0)
            json.put("samples", sampleArr);

        try {
            JSONUtils.writeObject(file, json);
            if (updatePath)
                this.file = file;
            return true;
        } catch (Exception e) {
            Log.e("Failed to save instrument to file: " + file, e);
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Instrument that = (Instrument) o;
        return Objects.equals(getUID(), that.getUID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUID());
    }

    /* STATIC */

    public static int getNoteKey(int note, int velocity) {
        return (note * (MidiNote.MAX_VELOCITY + 1)) + velocity;
    }

    public static int[] getNoteVelocity(String str) {
        String[] noteSplit = str.split(" ");
        String noteName = noteSplit[0];

        int noteValue = Misc.getNoteValue(noteName);

        // Velocity check
        int minVelocity = 0;
        if (noteSplit.length > 1)
            minVelocity = Misc.parseInt(noteSplit[1], 0);

        return new int[] {noteValue, minVelocity};
    }
}
