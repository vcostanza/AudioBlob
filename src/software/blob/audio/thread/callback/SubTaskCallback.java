package software.blob.audio.thread.callback;

import software.blob.audio.wave.WavData;

import java.util.List;

/**
 * A {@link TaskCallback} with a parent callback that can be used to track progress for certain subroutines
 */
public class SubTaskCallback implements TaskCallback {

    private final TaskCallback parent;
    private final int startProg, maxProg, totalProg;

    public SubTaskCallback(TaskCallback parent, int startProg, int maxProg, int totalProg) {
        this.parent = parent;
        this.startProg = startProg;
        this.maxProg = maxProg;
        this.totalProg = totalProg;
    }

    @Override
    public boolean onProgress(int prog, int max) {
        prog = this.startProg + (int) (((double) prog / max) * this.maxProg);
        return parent == null || parent.onProgress(prog, totalProg);
    }

    @Override
    public void onFinished(List<WavData> results) {
        // Override
    }
}
