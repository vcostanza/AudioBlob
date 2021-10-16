package software.blob.audio.ui.editor.pitchcurve;

import org.json.JSONArray;
import org.json.JSONObject;
import software.blob.audio.effects.sbsms.SBSMSEffect;
import software.blob.audio.effects.volume.AmplitudeModulator;
import software.blob.audio.effects.volume.FadeEffect;
import software.blob.audio.ui.editor.EditorPoint;
import software.blob.audio.ui.editor.instruments.InstrumentSample;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.audio.util.IDGenerator;
import software.blob.audio.util.Misc;
import software.blob.audio.util.SortedList;
import software.blob.audio.wave.WavData;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * A collection of pitch samples that is typically represented as a curve or line
 */
public class PitchCurve extends SortedList<PitchSample> implements IDrawBounds {

    private static final Comparator<PitchSample> SORT_TIME = Comparator.comparingDouble(o -> o.time);

    private static final IDGenerator ID_GEN = new IDGenerator();

    // Instance ID
    public transient final long id = ID_GEN.createID();
    private final transient int hash = Objects.hash(id);

    public final EditorPoint pos = new EditorPoint();
    private double maxNote, maxTime;
    private double minAmp, maxAmp;

    // Cached WAV to speed up playback processing
    private transient PitchCurveWav cache;

    public PitchCurve() {
        super(SORT_TIME);
    }

    public PitchCurve(boolean autoSort) {
        this();
        setAutoSort(autoSort);
    }

    public PitchCurve(Collection<? extends PitchSample> c) {
        super(c, SORT_TIME, true);
    }

    public PitchCurve(int initialCapacity) {
        super(initialCapacity, SORT_TIME);
    }

    public PitchCurve(JSONObject json) {
        this(json.getJSONArray("samples").length());
        pos.set(json.getDouble("time"), json.getDouble("note"));
        setAutoSort(false);
        JSONArray arr = json.getJSONArray("samples");
        for (int i = 0; i < arr.length(); i++)
            add(new PitchSample(arr.getJSONObject(i)));
        setAutoSort(true);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("time", pos.time);
        json.put("note", pos.note);
        JSONArray arr = new JSONArray(size());
        for (PitchSample sample : this)
            arr.put(sample.toJSON());
        json.put("samples", arr);
        return json;
    }

    /**
     * Set the position of this curve
     * @param pos Position
     */
    public void setPos(EditorPoint pos) {
        this.pos.set(pos);
    }

    /**
     * Set the position of this curve
     * @param time Time code
     * @param note Note value
     */
    public void setPos(double time, double note) {
        this.pos.set(time, note);
    }

    /**
     * Update the bounds of this pitch curve
     */
    public void updateBounds() {
        maxTime = maxNote = -Double.MAX_VALUE;
        for (PitchSample sample : this) {
            maxNote = Math.max(maxNote, sample.note);
            maxTime = Math.max(maxTime, sample.time);
            minAmp = Math.min(minAmp, sample.amplitude);
            maxAmp = Math.max(maxAmp, sample.amplitude);
        }
    }

    /**
     * Check if a sample is within the bounds of this curve
     * @param time Time code
     * @param note Note value
     * @return True if within bounds
     */
    public boolean inBounds(double time, double note) {
        return time >= getMinTime() && time <= getMaxTime()
                && note >= getMinNote() && note <= getMaxNote();
    }

    /**
     * Check if a sample is within the bounds of this curve
     * @param sample Pitch sample
     * @return True if within bounds
     */
    public boolean inBounds(PitchSample sample) {
        return inBounds(sample.time, sample.note);
    }

    /**
     * Offset samples relative to the minimum boundaries
     */
    public void normalize() {
        double minTime = Double.MAX_VALUE;
        double minNote = Double.MAX_VALUE;
        for (PitchSample sample : this) {
            minTime = Math.min(minTime, sample.time);
            minNote = Math.min(minNote, sample.note);
        }
        for (PitchSample sample : this) {
            sample.time -= minTime;
            sample.note -= minNote;
        }
        setPos(minTime, minNote);
        maxTime -= minTime;
        maxNote -= minNote;
    }

    /**
     * Split this curve up by its sample times based on the "direction" of time
     * Note: This only works on unsorted curves - see {@link #setAutoSort(boolean)}
     * @return Sorted pitch curves split up by time direction
     */
    public PitchCurveList splitByTimeDirection() {
        PitchCurveList ret = new PitchCurveList(false);
        PitchCurve curve = new PitchCurve(false);
        Boolean lastFwd = null;
        PitchSample last = null;
        for (PitchSample sample : this) {
            if (last != null) {

                // If the sample has the same time code then ignore
                if (Double.compare(sample.time, last.time) == 0)
                    continue;

                // Check current time direction
                boolean fwd = sample.time > last.time;

                // If we changed direction
                if (lastFwd != null && fwd != lastFwd) {
                    // End this curve and start a new one
                    last = new PitchSample(last);
                    curve.setAutoSort(true);
                    curve.normalize();
                    ret.add(curve);
                    curve = new PitchCurve(false);
                    curve.add(last);
                }

                lastFwd = fwd;
            }
            curve.add(sample);
            last = sample;
        }

        // Add the last curve
        curve.setAutoSort(true);
        curve.normalize();
        ret.add(curve);

        // Finished
        ret.setAutoSort(true);
        return ret;
    }

    /**
     * Apply pitch shifting and amplitude correction to a given sample
     * @param src Wav sample
     * @param baseFreq Base frequency of the sample
     * @param maxAmp Maximum amplitude
     * @return Wav data with applied effects
     */
    public WavData apply(WavData src, double baseFreq, double maxAmp) {
        // Cached wav
        if (this.cache != null && this.cache.equals(this, src, baseFreq, maxAmp))
            return this.cache;

        WavData wav = src;

        sort();
        float[] pitches = new float[wav.numFrames];
        double[] amps = new double[wav.numFrames];
        PitchSample last = null;
        int lastFrame = 0;
        double lastPitch = 0;
        for (PitchSample sample : this) {
            int frame = (int) Math.floor(sample.time * wav.sampleRate);
            frame = Misc.clamp(frame, 0, pitches.length);
            double pitch = Misc.getNoteFrequency(sample.note + pos.note) / baseFreq;
            if (last != null) {
                boolean constantPitch = Double.compare(lastPitch, pitch) == 0;
                boolean constantAmp = Double.compare(last.amplitude, sample.amplitude) == 0;
                if (constantPitch)
                    Arrays.fill(pitches, lastFrame, frame, (float) pitch);
                if (constantAmp)
                    Arrays.fill(amps, lastFrame, frame, sample.amplitude);
                if (!constantPitch || !constantAmp) {
                    int frameLen = frame - lastFrame;
                    for (int f = lastFrame; f < frame; f++) {
                        double interp = (double) (f - lastFrame) / frameLen;
                        if (!constantPitch)
                            pitches[f] = (float) ((lastPitch * (1 - interp)) + (pitch * interp));
                        if (!constantAmp)
                            amps[f] = (last.amplitude * (1 - interp)) + (sample.amplitude * interp);
                    }
                }
            }
            lastFrame = frame;
            lastPitch = pitch;
            last = sample;
        }
        if (last != null && lastFrame < wav.numFrames) {
            Arrays.fill(pitches, lastFrame, wav.numFrames, (float) lastPitch);
            Arrays.fill(amps, lastFrame, wav.numFrames, last.amplitude);
        }

        // Pitch correction
        SBSMSEffect sbsms = new SBSMSEffect();
        sbsms.setPitchArray(pitches);
        wav = sbsms.process(wav);
        wav.setPeakAmplitude(maxAmp);

        // Amplitude correction
        AmplitudeModulator ampMod = new AmplitudeModulator();
        ampMod.setAmplitudeFactors(amps);
        wav = ampMod.process(wav);

        // Fades for smoothing
        double fadeLength = 0.05;
        FadeEffect fadeIn = new FadeEffect(0, 1, fadeLength, FadeEffect.Shape.QUADRATIC);
        FadeEffect fadeOut = new FadeEffect(1, 0, fadeLength, FadeEffect.Shape.QUADRATIC);
        wav = fadeIn.process(wav);
        wav = fadeOut.process(wav);

        return this.cache = new PitchCurveWav(this, src, baseFreq, maxAmp, wav);
    }

    /**
     * Apply pitch shifting and amplitude correction to a given sample
     * @param sample Instrument sample
     * @param maxAmp Maximum amplitude
     * @return Wav data with applied effects
     */
    public WavData apply(InstrumentSample sample, double maxAmp) {
        return apply(sample.getWav(), sample.frequency, maxAmp);
    }

    /**
     * Invalidate cached WAV data
     */
    public void invalidate() {
        this.cache = null;
    }

    @Override
    public boolean sort() {
        if (super.sort()) {
            updateBounds();
            return true;
        }
        return false;
    }

    @Override
    public double getMinTime() {
        return pos.time;
    }

    @Override
    public double getMaxTime() {
        return pos.time + maxTime;
    }

    @Override
    public double getMinNote() {
        return pos.note;
    }

    @Override
    public double getMaxNote() {
        return pos.note + maxNote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PitchCurve that = (PitchCurve) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
