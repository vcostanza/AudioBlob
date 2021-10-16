package software.blob.audio.audacity.frequency;

/**
 * Frequency and amplitude at a given timestamp
 */
public class FrequencySample {

    // Frequency
    public double frequency;

    // Amplitude
    public double amplitude;

    // Timestamp in seconds
    public double time;

    public FrequencySample(double frequency, double amplitude, double time) {
        this.frequency = frequency;
        this.amplitude = amplitude;
        this.time = time;
    }

    public FrequencySample(FrequencySample other) {
        this(other.frequency, other.amplitude, other.time);
    }

    @Override
    public String toString() {
        return "Frequency = " + this.frequency + " | Amplitude = " + this.amplitude + " | Time = " + this.time;
    }
}
