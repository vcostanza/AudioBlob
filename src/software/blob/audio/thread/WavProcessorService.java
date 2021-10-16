package software.blob.audio.thread;

import software.blob.audio.thread.callback.TaskCallback;
import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Service for processing wav data on multiple threads
 */
public class WavProcessorService {

    private final int numThreads = Runtime.getRuntime().availableProcessors();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads, new ThreadFactory() {
        private int num = 1;
        @Override
        public Thread newThread(Runnable r) {
            Thread thr = new Thread(r);
            thr.setName("WavProcessorService-" + (num++));
            thr.setDaemon(true);
            return thr;
        }
    });

    private final boolean autoShutdown;

    public WavProcessorService(boolean autoShutdown) {
        this.autoShutdown = autoShutdown;
    }

    public WavProcessorService() {
        this(true);
    }

    /**
     * Get the maximum number of threads available
     * @return Thread count
     */
    public int getNumThreads() {
        return numThreads;
    }

    /**
     * Execute a set of wav processor tasks and wait for them all to finish
     * @param tasks Tasks to execute
     * @param callback Task callback (optional)
     * @return List of output wav containers
     */
    public List<WavData> execute(final List<? extends WavProcessorTask> tasks, final TaskCallback callback) {

        // Setup progress listener
        final int progMax = tasks.size();
        final int[] prog = {0};
        final boolean[] canceled = {false};
        final WavProcessorTask.OnTaskFinished onTaskFinish = task -> {
            synchronized (prog) {
                prog[0]++;
                if (callback != null && !callback.onProgress(prog[0], progMax)) {
                    // Progress callback has signalled to stop processing tasks
                    for (WavProcessorTask t : tasks)
                        t.cancel();
                    canceled[0] = true;
                }
            }
        };

        // Execute all tasks
        Collection<Future<WavData>> waits = new LinkedList<>();
        for (WavProcessorTask task : tasks) {
            task.setFinishedCallback(onTaskFinish);
            try {
                waits.add(threadPool.submit(task));
            } catch (Exception e) {
                Log.e("Failed to submit task", e);
            }
        }

        // Wait for all tasks to finish
        List<WavData> results = new ArrayList<>();
        for (Future<WavData> wait : waits) {
            if (canceled[0])
                break;
            try {
                WavData result = wait.get();
                if (result != null)
                    results.add(result);
            } catch (Exception e) {
                Log.e("Thread failed to complete", e);
            }
        }

        // Finish up
        if (!canceled[0] && callback != null)
            callback.onFinished(results);

        if (autoShutdown)
            shutdown();

        return results;
    }

    public List<WavData> execute(List<? extends WavProcessorTask> tasks) {
        return execute(tasks, null);
    }

    /**
     * Execute a set of wav processor tasks asynchronously
     * @param tasks Tasks to execute
     * @param callback Task callback
     */
    public void executeAsync(final List<? extends WavProcessorTask> tasks, final TaskCallback callback) {
        submit(() -> execute(tasks, callback));
    }

    public void submit(Runnable r) {
        threadPool.submit(r);
    }

    /**
     * Shut down the thread pool
     */
    public void shutdown() {
        threadPool.shutdown();
    }
}
