package software.blob.audio.thread.callback;

import software.blob.audio.wave.WavData;

import java.util.ArrayList;
import java.util.List;

/**
 * A task that contains multiple sub-tasks
 */
public class MultiTaskCallback implements TaskCallback {

    private static final int MAX = 100;

    private final TaskCallback parent;
    private final List<SubTaskCallback> subCallbacks;

    public MultiTaskCallback(TaskCallback parent, int numTasks) {
        this.parent = parent;
        this.subCallbacks = new ArrayList<>(numTasks);

        int totalProg = numTasks * MAX;
        for (int i = 0; i < numTasks; i++)
            this.subCallbacks.add(new SubTaskCallback(this, i * MAX, MAX, totalProg));
    }

    public SubTaskCallback getCallback(int index) {
        return index >= 0 && index < this.subCallbacks.size() ? this.subCallbacks.get(index) : null;
    }

    public boolean onProgress(int index, int prog, int max) {
        SubTaskCallback cb = getCallback(index);
        return cb != null && cb.onProgress(prog, max);
    }

    @Override
    public boolean onProgress(int prog, int max) {
        return parent == null || parent.onProgress(prog, max);
    }

    @Override
    public void onFinished(List<WavData> results) {
        if (parent != null)
            parent.onFinished(results);
    }
}
