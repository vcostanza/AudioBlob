package software.blob.audio.ui.editor.pitchcurve;

import org.json.JSONArray;
import org.json.JSONObject;
import software.blob.audio.audacity.frequency.FrequencySample;
import software.blob.audio.audacity.frequency.FrequencyStats;
import software.blob.audio.effects.sbsms.SBSMSEffect;
import software.blob.audio.effects.volume.AmplitudeModulator;
import software.blob.audio.effects.volume.FadeEffect;
import software.blob.audio.ui.editor.instruments.Instrument;
import software.blob.audio.ui.editor.instruments.InstrumentSample;
import software.blob.audio.ui.editor.view.IDrawBounds;
import software.blob.audio.wave.WavData;
import software.blob.audio.util.Misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A collection of pitch samples that is typically represented as a curve or line
 */
public class LegacyPitchCurve implements Iterable<PitchSample>, IDrawBounds {

    // 160 samples per second
    public static final double INTERVAL_DEFAULT = 1d / 160d;

    private static final Comparator<PitchSample> SORT_TIME = Comparator.comparingDouble(o -> o.time);

    public final double interval;
    public final List<PitchSample> samples;

    public double minNote, maxNote, noteRange;
    public double minTime, maxTime, timeRange;
    public double minAmp, maxAmp;

    public LegacyPitchCurve() {
        this(INTERVAL_DEFAULT);
    }

    public LegacyPitchCurve(double interval) {
        this.interval = interval;
        this.samples = new ArrayList<>();
    }

    public LegacyPitchCurve(FrequencyStats stats) {
        interval = stats.interval;
        samples = new ArrayList<>(stats.getSampleCount());
        for (FrequencySample sample : stats)
            samples.add(new PitchSample(sample));
        updateBounds();
    }

    public LegacyPitchCurve(LegacyPitchCurve other) {
        this.interval = other.interval;
        this.samples = new ArrayList<>(other.size());
        for (PitchSample s : other)
            this.samples.add(new PitchSample(s));
        updateBounds();
    }

    public void add(PitchSample sample) {
        this.samples.add(sample);
    }

    public void add(LegacyPitchCurve segment) {
        this.samples.addAll(segment.samples);
    }

    public PitchSample get(int idx) {
        return idx >= 0 && idx < samples.size() ? samples.get(idx) : null;
    }

    public PitchSample remove(int idx) {
        return idx >= 0 && idx < samples.size() ? samples.remove(idx) : null;
    }

    public boolean removeAll(LegacyPitchCurve curves) {
        return curves != null && samples.removeAll(curves.samples);
    }

    public LegacyPitchCurve removeSegment(double minTime, double maxTime, double minNote, double maxNote) {
        LegacyPitchCurve removed = new LegacyPitchCurve(this.interval);
        for (int i = 0; i < samples.size(); i++) {
            PitchSample sample = samples.get(i);
            if (sample.time > maxTime)
                break;
            if (sample.time >= minTime && sample.note >= minNote && sample.note <= maxNote)
                removed.add(samples.remove(i--));
        }
        removed.updateBounds();
        return removed;
    }

    public LegacyPitchCurve removeSegment(double startTime, double endTime) {
        return removeSegment(startTime, endTime, minNote, maxNote);
    }

    public LegacyPitchCurve removeSegment(LegacyPitchCurve mask) {
        return removeSegment(mask.minTime, mask.maxTime, mask.minNote, mask.maxNote);
    }

    public int size() {
        return this.samples.size();
    }

    public boolean isEmpty() {
        return this.samples.isEmpty();
    }

    public void clear() {
        this.samples.clear();
    }

    public boolean contains(double time, double note) {
        return time >= minTime && time <= maxTime && note >= minNote && note <= maxNote;
    }

    public boolean contains(PitchSample sample) {
        return contains(sample.time, sample.note);
    }

    public void updateBounds() {
        minTime = minNote = minAmp = Double.MAX_VALUE;
        maxNote = maxTime = maxAmp = -Double.MAX_VALUE;
        for (PitchSample sample : samples) {
            minTime = Math.min(minTime, sample.time);
            maxTime = Math.max(maxTime, sample.time);
            minNote = Math.min(minNote, sample.note);
            maxNote = Math.max(maxNote, sample.note);
            double amp = Math.abs(sample.amplitude);
            minAmp = Math.min(minAmp, amp);
            maxAmp = Math.max(maxAmp, amp);
        }
        timeRange = maxTime - minTime;
        noteRange = maxNote - minNote;
        samples.sort(SORT_TIME);
    }

    public FrequencyStats getFrequencyStats() {
        FrequencyStats stats = new FrequencyStats();
        stats.interval = this.interval;
        for (PitchSample s : this)
            stats.add(new FrequencySample(Misc.getNoteFrequency(s.note), s.amplitude, s.time));
        stats.update();
        return stats;
    }

    public List<LegacyPitchCurve> split(double startTime, double endTime) {
        List<LegacyPitchCurve> ret = new ArrayList<>();
        if (isEmpty())
            return ret;

        startTime -= interval;
        LegacyPitchCurve segment = new LegacyPitchCurve(this.interval);
        double interval = this.interval + Misc.EPSILON;
        PitchSample last = null;
        for (int i = 0; i <= size(); i++) {
            PitchSample cur = get(i);
            if (cur != null) {
                if (cur.time < startTime)
                    continue;
                if (cur.time > endTime)
                    cur = null;
            }
            if (cur == null || last != null && cur.time - last.time > interval) {
                segment.updateBounds();
                ret.add(segment);
                segment = new LegacyPitchCurve(this.interval);
            }
            last = cur;
            if (cur != null)
                segment.samples.add(cur);
            else
                break;
        }
        return ret;
    }

    public List<LegacyPitchCurve> split() {
        return split(minTime, maxTime);
    }

    public WavData generateWav(Instrument instrument) {
        FrequencyStats stats = getFrequencyStats();
        InstrumentSample sample = instrument.findSampleByNote((int) Math.round((minNote + maxNote) / 2), (maxAmp + minAmp) / 2);
        WavData wav = sample.getWav();
        double[] freq = stats.getFrequencyPerFrame(wav.sampleRate);
        double[] amps = stats.getAmplitudePerFrame(wav.sampleRate);
        float[] pitches = new float[freq.length];
        for (int i = 0; i < freq.length; i++)
            pitches[i] = (float) (freq[i] / sample.frequency);

        // Crop to stats
        wav = new WavData(wav);
        if (wav.numFrames > freq.length)
            wav.trim(0, freq.length);
        else if (wav.numFrames < freq.length) {
            if (wav.isLoopable())
                wav.padLoop(freq.length - wav.numFrames);
            else
                wav.pad(freq.length - wav.numFrames);
        }

        // Pitch correction
        SBSMSEffect sbsms = new SBSMSEffect();
        sbsms.setPitchArray(pitches);
        wav = sbsms.process(wav);
        wav.setPeakAmplitude(1);

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

        return wav;
    }

    @Override
    public Iterator<PitchSample> iterator() {
        return samples.iterator();
    }

    public LegacyPitchCurve(JSONObject json) {
        this.interval = json.getDouble("interval");

        JSONArray sampleArr = json.getJSONArray("samples");
        this.samples = new ArrayList<>(sampleArr.length());
        for (int i = 0; i < sampleArr.length(); i++)
            this.samples.add(new PitchSample(sampleArr.getJSONObject(i)));

        updateBounds();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("interval", this.interval);

        JSONArray sampleArr = new JSONArray();
        for (PitchSample sample : this.samples)
            sampleArr.put(sample.toJSON());
        json.put("samples", sampleArr);

        return json;
    }

    @Override
    public double getMinTime() {
        return this.minTime;
    }

    @Override
    public double getMaxTime() {
        return this.maxTime;
    }

    @Override
    public double getMinNote() {
        return this.minNote;
    }

    @Override
    public double getMaxNote() {
        return this.maxNote;
    }

    /**
     * Upgrade legacy pitch curves to a {@link PitchCurveList}
     * @return Pitch curve list
     */
    public PitchCurveList upgrade() {
        List<LegacyPitchCurve> curves = split();
        PitchCurveList list = new PitchCurveList(curves.size());
        for (LegacyPitchCurve lpc : curves) {
            for (PitchSample s : lpc) {
                s.time -= lpc.minTime;
                s.note -= lpc.minNote;
            }
            list.add(new PitchCurve(lpc.samples));
        }
        return list;
    }
}
