package software.blob.audio.ui.editor.view;

import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorModeListener;
import software.blob.ui.view.AttributeSet;
import software.blob.ui.view.ImageButton;
import software.blob.ui.view.View;
import software.blob.ui.view.listener.ClickListener;
import software.blob.ui.util.Log;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * Image button that toggles editor mode when pressed
 */
public class EditorModeButton extends ImageButton implements ClickListener, EditorModeListener {

    private final EditorMode mode;

    private AudioEditor editor;
    private Color defaultBG, selectBG;

    public EditorModeButton(AttributeSet attrs) {
        super(attrs);

        EditorMode mode = null;
        String modeStr = attrs.getString("mode", null);
        try {
            mode = EditorMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            Log.e("Failed to parse invalid editor mode: " + modeStr);
        }
        this.mode = mode;
        setOnClickListener(this);
    }

    public void setEditor(AudioEditor editor) {
        this.editor = editor;
        if (editor != null)
            onEditorModeChanged(editor.getMode());
    }

    @Override
    public void setBackground(Color c) {
        super.setBackground(c);
        if (c != null) {
            defaultBG = c;
            selectBG = c.darker();
        }
    }

    @Override
    public void onClick(View view, MouseEvent event) {
        if (this.mode != null && this.editor != null)
            this.editor.setMode(this.mode);
    }

    @Override
    public void onEditorModeChanged(EditorMode mode) {
        setCurrentBackground(super.defaultBG = (mode == this.mode ? selectBG : defaultBG));
    }
}
