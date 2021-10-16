package software.blob.audio.wave;

/**
 * A snippet of WAV data that maintains a reference to the start time of the parent WAV
 */
public class WavSnippet extends WavData {

    public int startFrame, endFrame;
    public double startTime, endTime;

    public WavSnippet(WavData parent, int startFrame, int endFrame) {
        super();
        this.name = parent.name;
        this.channels = parent.channels;
        this.sampleRate = parent.sampleRate;
        this.startFrame = startFrame;
        this.startTime = getTime(startFrame);
        this.endFrame = endFrame;
        this.endTime = getTime(endFrame);
        this.numFrames = endFrame - startFrame;
        this.duration = getTime(endFrame) - getTime(startFrame);
        this.samples = new double[this.channels][this.numFrames];
        for (int c = 0; c < this.channels; c++)
            System.arraycopy(parent.samples[c], startFrame, this.samples[c], 0, this.numFrames);
    }

    public WavSnippet(WavData parent) {
        super(parent);
        this.startFrame = 0;
        this.endFrame = parent.numFrames;
        this.startTime = 0;
        this.endTime = parent.duration;
    }

    @Override
    public String toString() {
        return "Start: " + startTime + " | End: " + endTime + " (Duration: " + (endTime - startTime) + ")";
    }
}
