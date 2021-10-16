package software.blob.audio.playback;

import software.blob.audio.wave.WavData;

/**
 * Handle for audio that's being played using the {@link AudioPlayer}
 */
public class AudioHandle {

    public final WavData wav;

    long time = -1;
    int frame = 0;
    double srcVol = 1d;
    double dstVol = 1d;
    VolumeCalc volumeCalc;
    Callback callback;

    AudioHandle(WavData wav) {
        this.wav = wav;
    }

    void incrementFrame() {
        frame++;
        if (wav.isLoopable() && frame == wav.loopEndFrame)
            frame = wav.loopStartFrame;
    }

    void finish() {
        frame = wav.numFrames;
    }

    /**
     * Check if this clip is finished playing
     * @return True if finished
     */
    public boolean isFinished() {
        return frame >= wav.numFrames;
    }

    /**
     * Get the current playback frame
     * @return Frame
     */
    public int getFrame() {
        return frame;
    }

    /**
     * Get the current playback time offset
     * @return Time in seconds
     */
    public double getTime() {
        return wav.getTime(frame);
    }

    /**
     * Set the time offset this clip should begin playing
     * i.e. 2 = start playback 2 seconds into the wav
     * @param time Time in seconds
     */
    public void setTime(double time) {
        frame = Math.max(0, wav.getFrame(time));
        if (frame > 1)
            srcVol = 0;
    }

    /**
     * Get the amount of time remaining in this clip
     * @return Seconds of time remaining
     */
    public double getRemainingTime() {
        return wav.duration - getTime();
    }

    /**
     * Set the desired volume of this audio
     * @param vol Volume (0 to 1)
     */
    public void setVolume(double vol) {
        if (time == -1 && frame == 0)
            this.srcVol = vol;
        this.dstVol = vol;
    }

    /**
     * Set the volume calculator
     * This allows you to dynamically specify the current volume for this handle
     * @param volumeCalc Volume calculator
     */
    public void setVolumeCalculator(VolumeCalc volumeCalc) {
        this.volumeCalc = volumeCalc;
        setVolume(getVolume());
    }

    /**
     * Get the current volume
     * @return Volume (0 to 1)
     */
    public double getVolume() {
        return this.volumeCalc != null ? this.volumeCalc.getVolume() : this.dstVol;
    }

    // Calculates volume for a given audio handle
    public interface VolumeCalc {

        /**
         * Get the current volume
         * @return Volume level (0 to 1)
         */
        double getVolume();
    }

    /**
     * Set event callback that's invoked during playback
     * @param cb Event callback
     */
    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    // Event callback
    public interface Callback {

        /**
         * Audio sample playback has incremented
         */
        void onPlayback(double time, int frame);

        /**
         * Audio sample has finished playing
         */
        void onPlaybackFinished();
    }
}
