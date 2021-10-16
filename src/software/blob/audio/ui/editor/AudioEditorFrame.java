package software.blob.audio.ui.editor;

import software.blob.audio.apps.AudioBlobApp;

import javax.swing.*;

/**
 * The {@link JFrame} that holds AudioBlob
 */
public class AudioEditorFrame extends JFrame {

    public AudioEditorFrame() {
        super(AudioBlobApp.APP_NAME);
    }

    @Override
    public void setTitle(String title) {
        if (!title.endsWith(AudioBlobApp.APP_NAME))
            title += " \u2013 " + AudioBlobApp.APP_NAME;
        super.setTitle(title);
    }
}
