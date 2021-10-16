package software.blob.audio.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard deviation calculator
 */
public class StandardDeviation {

    private final List<Double> values = new ArrayList<>();
    private double avg = Double.NaN;
    private double std = Double.NaN;

    /**
     * Add a value to the calculator
     * @param value Value to add
     */
    public void add(double value) {
        values.add(value);
        avg = std = Double.NaN;
    }

    /**
     * Get the average value of the set
     * @return Average value
     */
    public double getAverage() {
        if (Double.isNaN(avg)) {
            avg = 0;
            for (double d : values)
                avg += d;
            avg /= values.size();
        }
        return avg;
    }

    /**
     * Get the standard deviation
     * @return Standard deviation value
     */
    public double getSTD() {
        if (Double.isNaN(std)) {
            double avg = getAverage();
            std = 0;
            for (double d : values)
                std += Math.pow(d - avg, 2);
            std = Math.sqrt(std / values.size());
        }
        return std;
    }
}
