package software.blob.audio.util;

import software.blob.ui.util.FileUtils;
import software.blob.ui.util.Log;

import java.io.FilenameFilter;
import java.util.*;
import java.util.List;

/**
 * Miscellaneous utility functions exclusive to AudioBlob
 */
public class Misc {

    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final Map<String, Integer> NOTE_TO_SEMITONE = new HashMap<>();
    static {
        for (int i = 0; i < NOTE_NAMES.length; i++)
            NOTE_TO_SEMITONE.put(NOTE_NAMES[i], i);
    }

    // Frequency corresponding to A above middle C on a piano (Stuttgart pitch)
    public static final double A4 = 440d;

    public static final double EPSILON = 1e-5;

    private static final double LOG_2 = Math.log(2.0);

    public static final Set<String> MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "wav", "mp3", "mp4", "mkv", "mov", "avi", "ogg", "mpg", "3gp", "flac", "m4a", "wmv", "ts", "webm", "flv"
    ));
    public static final FilenameFilter MEDIA_FILTER = (dir, name) -> {
        String ext = FileUtils.getExtension(name);
        return MEDIA_EXTS.contains(ext);
    };

    private static final Random random = new Random();

    /**
     * Get a random number between a min and max bound
     * @param min Minimum bound (inclusive)
     * @param max Maximum bound (exclusive)
     * @return Random number
     */
    public static int random(int min, int max) {
        if (min > max)
            return random(max, min);
        return min + random.nextInt(max - min);
    }

    public static int random(int max) {
        return random.nextInt(max);
    }

    /**
     * Get a random number between a min and max bound
     * @param min Minimum bound (inclusive)
     * @param max Maximum bound (exclusive)
     * @return Random floating-point number
     */
    public static double random(double min, double max) {
        if (min > max)
            return random(max, min);
        return min + (random.nextDouble() * (max - min));
    }

    /**
     * Roll against chance
     * @param chance Chance (between 0 and 1)
     * @return True if yes
     */
    public static boolean roll(double chance) {
        return chance >= 1 || chance > 0 && random.nextDouble() < chance;
    }

    /**
     * Pull a random entry from a list
     * @param list List to pull from
     * @param <T> List type
     * @return Random entry
     */
    public static <T> T random(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    @SafeVarargs
    public static <T> T random(T... array) {
        return array[random.nextInt(array.length)];
    }

    public static <T> T weightedRandom(T[] values, double... weights) {
        double total = 0;
        for (double d : weights)
            total += d;
        double roll = random.nextDouble() * total;
        double ceil = 0;
        for (int i = 0; i < values.length && i < weights.length; i++) {
            ceil += weights[i];
            if (roll < ceil)
                return values[i];
        }
        return null;
    }

    /**
     * Log base 2 calculation
     * @param v Value
     * @return Log2
     */
    public static double log2(double v) {
        return Math.log(v) / LOG_2;
    }

    /**
     * Get the midi note that corresponds to a given frequency
     * @param freq Input frequency
     * @return Midi note (un-rounded)
     */
    public static double getNoteValue(double freq) {
        return 69.0 + (12.0 * Misc.log2(freq / A4));
    }

    /**
     * Get the frequency for a given note
     * @param noteValue Note value
     * @return Frequency in Hz
     */
    public static double getNoteFrequency(double noteValue) {
        return A4 * Math.pow(2.0, (noteValue - 69.0) / 12.0);
    }

    /**
     * Get the frequency for a given semitone and octave
     * @param semitone Semitone
     * @param octave Octave
     * @return Frequency in Hz
     */
    public static double getNoteFrequency(int semitone, int octave) {
        return getNoteFrequency((octave + 1) * 12 + semitone);
    }

    /**
     * Automatically tune a frequency to the closest midi note frequency
     * @param freq Input frequency
     * @return Tuned frequency
     */
    public static double autoTune(double freq) {
        return getNoteFrequency(Math.round(getNoteValue(freq)));
    }

    /**
     * Get the name of a note given its index
     * @param noteIndex Note index (midi note % 12)
     * @return Note name or "N/A" if out-of-bounds
     */
    public static String indexToNoteName(int noteIndex) {
        return noteIndex >= 0 && noteIndex < NOTE_NAMES.length ? NOTE_NAMES[noteIndex] : "N/A";
    }

    /**
     * Get the name of a note given its value
     * @param note Note value (12 * octave + note index)
     * @return Note name including the octave
     */
    public static String getNoteName(int note) {
        return indexToNoteName(note % 12) + ((note / 12) - 1);
    }

    /**
     * Get the name of a note given its value + the minimum velocity of that note
     * @param note Note value
     * @param minVelocity Minimum velocity at which the note is played
     * @return Note name + velocity
     */
    public static String getNoteName(int note, int minVelocity) {
        String noteName = getNoteName(note);
        if (minVelocity > 0)
            noteName += " " + minVelocity;
        return noteName;
    }

    /**
     * Get note value given a note name
     * @param note Note name (note + octave)
     * @return Note value
     */
    public static int getNoteValue(String note) {
        try {
            if (note == null || note.isEmpty())
                return 0;
            for (int i = 0; i < note.length(); i++) {
                char c = note.charAt(i);
                if (c >= '0' && c <= '9') {
                    Integer semitone = NOTE_TO_SEMITONE.get(note.substring(0, i));
                    int octave = Integer.parseInt(note.substring(i));
                    return (octave + 1) * 12 + semitone;
                }
            }
        } catch (Exception e) {
            Log.e("Failed to parse note: " + note, e);
        }
        return 0;
    }

    /**
     * Get a semitone given a note name
     * @param note Note name (without octave)
     * @return Semitone
     */
    public static Integer getSemitone(String note) {
        return NOTE_TO_SEMITONE.get(note);
    }

    /**
     * Check if a note is a flat vs. a sharp
     * @param note Note value
     * @return True if note is sharp
     */
    public static boolean isNoteSharp(double note) {
        return indexToNoteName((int) note % 12).contains("#");
    }

    /**
     * Given an amplitude, get the decibel value
     * @param amp Amplitude (0 to 1)
     * @return Decibels (-160 to 0)
     */
    public static double getDecibels(double amp) {
        double dB = Math.pow(amp, 2);
        if(dB <= 0)
            return -160; // Default minimum decibels
        else
            return 10.0 * Math.log10(dB);
    }

    /**
     * Given a decibel value, get the amplitude
     * @param dB Decibels (-160 to 0)
     * @return Amplitude (0 to 1)
     */
    public static double getAmplitude(double dB) {
        dB = Math.pow(10, (dB / 10));
        return Math.sqrt(dB);
    }

    public static double max(double... values) {
        double max = Integer.MIN_VALUE;
        for (double v : values)
            max = Math.max(max, v);
        return max;
    }

    public static double min(double... values) {
        double min = Integer.MAX_VALUE;
        for (double v : values)
            min = Math.min(min, v);
        return min;
    }

    public static int min(int... values) {
        int min = Integer.MAX_VALUE;
        for (int v : values)
            min = Math.min(min, v);
        return min;
    }

    public static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    public static int parseInt(String value, int defVal) {
        try {
            value = value.replace(",", "");
            return Integer.parseInt(value.trim());
        } catch (Exception ignore) {}
        return defVal;
    }

    public static float parseFloat(String value, float defVal) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignore) {}
        return defVal;
    }

    public static double parseDouble(String value, double defVal) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignore) {}
        return defVal;
    }

    public static boolean hasBits(int value, int bits) {
        return (value & bits) == bits;
    }

    /**
     * Round a time value to the nearest BPM
     * @param time Time in seconds
     * @param bpm Beats per minute
     * @return Rounded time
     */
    public static double roundToBPM(double time, int bpm) {
        double interval = 60d / bpm;
        return roundToNearest(time, interval);
    }

    /**
     * Round a value to the nearest multiple of X
     * @param value Value to round
     * @param nearest Nearest multiple
     * @return Rounded value
     */
    public static double roundToNearest(double value, double nearest) {
        return (int) Math.round(value / nearest) * nearest;
    }

    /**
     * Uppercase the first character in a string
     * @param str String
     * @return String with uppercase first letter
     */
    public static String upperFirst(String str) {
        if (str == null || str.isEmpty())
            return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Uppercase the first character for all the words in a string
     * @param str String
     * @return String with uppercase first letter
     */
    public static String upperWords(String str) {
        String[] words = str.split(" ");
        for (int i = 0; i < words.length; i++)
            words[i] = upperFirst(words[i]);
        return String.join(" ", words);
    }
}
