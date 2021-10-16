package software.blob.audio.ui;

import software.blob.audio.thread.callback.FinishCallback;
import software.blob.audio.thread.callback.MessageCallback;
import software.blob.audio.wave.WavData;
import software.blob.ui.view.dialog.ProgressDialog;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Shows progress in a dialog
 */
public class DialogProgressCallback implements MessageCallback {

    private final ProgressDialog dialog;
    private final FinishCallback callback;

    private boolean canceled = false;
    private Runnable onCancel;

    public DialogProgressCallback(Window owner, String title, FinishCallback callback) {
        this.callback = callback;
        dialog = new ProgressDialog(owner);
        dialog.setTitle(title);
        dialog.setOnCancelListener(() -> {
            canceled = true;
            if (onCancel != null)
                onCancel.run();
        });
        dialog.showDialog();
    }

    /**
     * Set the task that is executed when this dialog is cancelled
     * @param onCancel Cancel task
     */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @Override
    public void setMessage(final String message) {
        SwingUtilities.invokeLater(() -> dialog.setMessage(message));
    }

    @Override
    public boolean onProgress(final int prog, final int max) {
        if (canceled)
            return false;
        SwingUtilities.invokeLater(() -> dialog.setProgress(prog, max));
        return true;
    }

    @Override
    public void onFinished(List<WavData> results) {
        SwingUtilities.invokeLater(dialog::dismiss);
        if (callback != null)
            callback.onFinished(results);
    }
}
