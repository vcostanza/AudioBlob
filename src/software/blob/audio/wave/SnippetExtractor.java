package software.blob.audio.wave;

import java.util.ArrayList;
import java.util.List;

/**
 * Extract clips out of a track which are separated by silence
 */
public class SnippetExtractor {

    private double minAmp;
    private double minSilenceDuration;
    private double minSnippetDuration;

    public SnippetExtractor() {
        setMinimumAmplitude(0.05);
        setMinimumSilenceDuration(0.05);
        setMinimumSnippetDuration(0.1);
    }

    /**
     * Set the minimum amplitude threshold, where anything quieter is considered silence
     * @param minAmp Minimum amplitude
     */
    public void setMinimumAmplitude(double minAmp) {
        this.minAmp = minAmp;
    }

    /**
     * Set the minimum duration for a period of silence
     * @param seconds Seconds
     */
    public void setMinimumSilenceDuration(double seconds) {
        this.minSilenceDuration = seconds;
    }

    /**
     * Set the minimum duration for an audio snippet
     * @param seconds Seconds
     */
    public void setMinimumSnippetDuration(double seconds) {
        this.minSnippetDuration = seconds;
    }

    /**
     * Split up a clip by moments of silence
     * @param wav Wav data to split
     * @param maxResults Maximum number of results (-1 to ignore)
     * @return List of audio snippets
     */
    public List<WavSnippet> splitBySilence(WavData wav, int maxResults) {

        List<WavSnippet> ret = new ArrayList<>();

        // Invalid parameters
        if (minSilenceDuration <= 0 || minSnippetDuration <= 0)
            return ret;

        int snippetStart = -1, snippetEnd = -1;
        int lastSnipEnd = -1;
        int silentFrame = -1;
        int minSilenceFrames = wav.getFrame(minSilenceDuration);
        int minSnippetFrames = wav.getFrame(minSnippetDuration);
        boolean lastSilent = false;
        for (int f = 0; f <= wav.numFrames; f++) {

            // End of the sample
            boolean end = f == wav.numFrames;

            // Check if the current frame has a low enough amplitude
            boolean silent = true;
            if (!end) {
                for (int c = 0; c < wav.channels; c++) {
                    if (Math.abs(wav.samples[c][f]) > minAmp) {
                        silent = false;
                        break;
                    }
                }
            }

            // Frame is silent
            if (silent) {

                // Mark start of silence
                if (silentFrame == -1)
                    silentFrame = f;

                // Mark end of snippet if the last frame wasn't silent
                if (!lastSilent)
                    snippetEnd = f;

                // Check if we hit a significant period of silence or the end of the sample
                if (end || f - silentFrame >= minSilenceFrames) {

                    if (snippetStart == -1)
                        snippetStart = 0;

                    // If the last snippet was long enough, add it
                    if (snippetEnd - snippetStart >= minSnippetFrames) {

                        // Find zero crossings
                        int startZero = wav.findZeroCrossing(snippetStart, lastSnipEnd);
                        int endZero = wav.findZeroCrossing(snippetEnd, f);
                        if (startZero == -1)
                            startZero = snippetStart;
                        if (endZero == -1)
                            endZero = snippetEnd;

                        ret.add(new WavSnippet(wav, startZero, endZero));
                        if (maxResults > 0 && ret.size() == maxResults)
                            return ret;
                        lastSnipEnd = endZero;
                    }

                    // Reset windows
                    snippetStart = snippetEnd = -1;
                    silentFrame = f;
                }
            } else {
                // Mark start of snippet
                if (snippetStart == -1)
                    snippetStart = f;

                // Reset silence window
                silentFrame = -1;
            }

            lastSilent = silent;
        }

        if (ret.isEmpty())
            ret.add(new WavSnippet(wav, 0, wav.numFrames));

        return ret;
    }

    public List<WavSnippet> splitBySilence(WavData wav) {
        return splitBySilence(wav, -1);
    }

    public WavSnippet trim(WavData wav) {
        List<WavSnippet> ret = splitBySilence(wav, 1);
        return !ret.isEmpty() ? ret.get(0) : new WavSnippet(wav, 0, wav.numFrames);
    }
}
