package software.blob.audio.wave;

import software.blob.ui.util.Log;

import java.util.Arrays;

/**
 * {@link WavData} that has a modifiable read-only flag
 * Obviously this doesn't prevent direct modification of the fields.
 * It's more intended to serve as a canary.
 */
public class LockableWavData extends WavData {

    private boolean locked;

    public LockableWavData(WavData src) {
        this.file = src.file;
        this.name = src.name;
        this.channels = src.channels;
        this.samples = src.samples;
        this.sampleRate = src.sampleRate;
        this.numFrames = src.numFrames;
        this.duration = src.duration;
        this.loopStartFrame = src.loopStartFrame;
        this.loopEndFrame = src.loopEndFrame;
        this.randomStart = src.randomStart;
    }

    public LockableWavData(LockableWavData src) {
        this((WavData) src);
        this.locked = src.locked;
    }

    /**
     * Lock this wav data (flag as read-only)
     */
    public void lock() {
        locked = true;
    }

    /**
     * Unlock this wav data (flag as writable)
     */
    public void unlock() {
        locked = false;
    }

    /**
     * Check if this wav data is locked
     * @return True if locked (read-only)
     */
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void setSampleRate(int sampleRate) {
        if (checkUnlocked())
            super.setSampleRate(sampleRate);
    }

    @Override
    public void setLoopFrames(int startFrame, int endFrame) {
        if (checkUnlocked())
            super.setLoopFrames(startFrame, endFrame);
    }

    @Override
    public void setRandomStart(boolean randomStart) {
        if (checkUnlocked())
            super.setRandomStart(randomStart);
    }

    @Override
    public void pad(int numFrames, boolean end) {
        if (checkUnlocked())
            super.pad(numFrames, end);
    }

    @Override
    public void padLoop(int startFrame, int endFrame, int numFrames) {
        if (checkUnlocked())
            super.padLoop(startFrame, endFrame, numFrames);
    }

    @Override
    public void append(WavData other) {
        if (checkUnlocked())
            super.append(other);
    }

    @Override
    public void mix(WavData other, int startFrame, int numFrames) {
        if (checkUnlocked())
            super.mix(other, startFrame, numFrames);
    }

    @Override
    public void crossFade(WavData other, int startFrame) {
        if (checkUnlocked())
            super.crossFade(other, startFrame);
    }

    @Override
    public void setChannels(int numChannels) {
        if (checkUnlocked())
            super.setChannels(numChannels);
    }

    @Override
    public void setPeakAmplitude(double newPeak, int startFrame, int endFrame) {
        if (checkUnlocked())
            super.setPeakAmplitude(newPeak, startFrame, endFrame);
    }

    @Override
    public void trim(int startFrame, int numFrames) {
        if (checkUnlocked())
            super.trim(startFrame, numFrames);
    }

    @Override
    public boolean clampAmplitude(int startFrame, int endFrame) {
        return checkUnlocked() && super.clampAmplitude(startFrame, endFrame);
    }

    @Override
    public void multiply(double factor, int startFrame, int endFrame) {
        if (checkUnlocked())
            super.multiply(factor, startFrame, endFrame);
    }

    @Override
    public void trimSilence(double minAmp) {
        if (checkUnlocked())
            super.trimSilence(minAmp);
    }

    @Override
    public void reverse() {
        if (checkUnlocked())
            super.reverse();
    }

    private boolean checkUnlocked() {
        if (isLocked()) {
            Throwable thr = new Throwable();
            StackTraceElement[] stack = thr.getStackTrace();
            stack = Arrays.copyOfRange(stack, 1, stack.length);
            thr.setStackTrace(stack);
            StackTraceElement caller = stack[0];
            Log.w("Attempted to " + caller.getMethodName() + " on locked wav: " + this.name, thr);
            return false;
        }
        return true;
    }
}
