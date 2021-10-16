package software.blob.audio.thread.callback;

import software.blob.audio.wave.WavData;
import software.blob.ui.util.Log;

import java.util.List;

/**
 * Progress callback that logs the current progress value in 1% increments
 */
public class LogProgressCallback implements MessageCallback {

    private final String tag;
    private String message;
    private int lastPercent;

    public LogProgressCallback(String tag, String message) {
        this.tag = tag; // Log tag
        this.message = message; // Message next to percentage
    }

    @Override
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean onProgress(int prog, int max) {
        int pcnt = Math.round(((float) prog / max) * 100);
        if (lastPercent != pcnt) {
            Log.d(tag, message + " (" + pcnt + "%)");
            lastPercent = pcnt;
        }
        return true;
    }

    @Override
    public void onFinished(List<WavData> results) {
    }
}
