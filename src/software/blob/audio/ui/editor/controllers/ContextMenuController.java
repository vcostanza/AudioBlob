package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.AudioEditor;

import javax.swing.*;
import java.awt.*;

/**
 * Right-click context menu
 */
public class ContextMenuController extends EditorController {

    private JPopupMenu contextMenu;

    public ContextMenuController(AudioEditor editor) {
        super(editor);
    }

    /**
     * Show the context menu
     * @param window Window to show the context menu in, usually {@link AudioEditor#getFrame()}
     * @param view Component to show in the context menu
     */
    public void show(Window window, Component view) {
        final JPopupMenu existing = contextMenu;
        if (existing != null)
            existing.setVisible(false);
        contextMenu = null;
        if (view == null)
            return;
        Point loc = MouseInfo.getPointerInfo().getLocation();
        loc.x -= window.getX();
        loc.y -= window.getY();
        this.contextMenu = new JPopupMenu();
        this.contextMenu.add(view);
        this.contextMenu.show(window, loc.x, loc.y);
    }

    public void hide() {
        show(null, null);
    }
}
