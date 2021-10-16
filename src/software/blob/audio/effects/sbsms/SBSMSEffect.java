package software.blob.audio.effects.sbsms;

import software.blob.audio.effects.AudioEffect;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import java.util.Arrays;

/**
 * Process audio using the SBSMS (Sub-band Sinusoidal Modeling) library which
 * allows changing between ranges of tempo and pitch in high quality, at the
 * expense of longer processing time
 */
public class SBSMSEffect extends AudioEffect {

    static {
        System.loadLibrary("sbsms");
        System.loadLibrary("sbsmsjni");
    }

    private double startTempo, endTempo, startPitch, endPitch;
    private float[] pitchArray;

    public SBSMSEffect() {
        setTempo(1);
        setPitch(1);
    }

    public SBSMSEffect(SBSMSEffect other) {
        setTempoRange(other.startTempo, other.endTempo);
        setPitchRange(other.startPitch, other.endPitch);
        setPitchArray(other.pitchArray != null ? Arrays.copyOf(other.pitchArray, other.pitchArray.length) : null);
    }

    /**
     * Set the tempo factor range
     * @param startTempo Start tempo (1 = normal, 0.5 = 50% speed, 2 = 200% speed)
     * @param endTempo End tempo
     */
    public void setTempoRange(double startTempo, double endTempo) {
        this.startTempo = startTempo;
        this.endTempo = endTempo;
    }

    /**
     * Set the tempo factor (playback rate)
     * @param tempo Tempo (1 = normal, 0.5 = 50% speed, 2 = 200% speed)
     */
    public void setTempo(double tempo) {
        setTempoRange(tempo, tempo);
    }

    /**
     * Set the pitch factor range
     * @param startPitch Start pitch (1 = normal)
     * @param endPitch End pitch
     */
    public void setPitchRange(double startPitch, double endPitch) {
        this.startPitch = startPitch;
        this.endPitch = endPitch;
    }

    /**
     * Set the pitch factor
     * @param pitch Pitch
     */
    public void setPitch(double pitch) {
        setPitchRange(pitch, pitch);
    }

    /**
     * Set an array of pitch values per sample
     * Input WAV data MUST have the same number of samples as this array, if set
     * @param pitchArray Array of pitch values
     */
    public void setPitchArray(float[] pitchArray) {
        this.pitchArray = pitchArray;
    }

    @Override
    public WavData process(WavData inWav) {
        double[][] output;
        if (this.pitchArray != null) {
            if (this.pitchArray.length != inWav.numFrames) {
                Log.e("Pitch array to input sample size mismatch: "
                        + this.pitchArray.length + " != " + inWav.numFrames);
                return null;
            }
            output = process(inWav.samples, startTempo, endTempo, pitchArray);
        } else {
            if (startTempo == 1 && endTempo == 1 && startPitch == 1 && endPitch == 1)
                return inWav;
            output = process(inWav.samples, startTempo, endTempo, startPitch, endPitch);
        }
        WavData wav = new WavData(output, inWav.sampleRate);
        wav.name = inWav.name;
        return wav;
    }

    private static native double[][] process(double[][] samples, double startTempo, double endTempo, double startPitch, double endPitch);
    private static native double[][] process(double[][] samples, double startTempo, double endTempo, float[] pitchArray);
}
