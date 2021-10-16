package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorMouseEvent;
import software.blob.audio.ui.editor.events.EditorMouseListener;
import software.blob.ui.view.View;
import software.blob.ui.util.Log;

import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles mouse controls
 */
public class MouseController extends EditorController
        implements MouseListener, MouseMotionListener, MouseWheelListener {

    private static final int CLICK = 0, DOUBLE_CLICK = 1, PRESS = 2, RELEASE = 3,
            ENTER = 4, EXIT = 5, DRAG = 6, MOVE = 7, WHEEL = 8;

    private static final Method[] MOUSE_EVENTS = {
            findMethod("onMouseClicked"),
            findMethod("onMouseDoubleClicked"),
            findMethod("onMousePressed"),
            findMethod("onMouseReleased"),
            findMethod("onMouseEntered"),
            findMethod("onMouseExited"),
            findMethod("onMouseDragged"),
            findMethod("onMouseMoved"),
            findMethod("onMouseWheel")
    };

    private static Method findMethod(String name) {
        try {
            return EditorMouseListener.class.getMethod(name, EditorMouseEvent.class);
        } catch (Exception e) {
            Log.e("Failed to find method: " + name, e);
        }
        return null;
    }

    private final List<EditorMouseListener> listeners = new ArrayList<>();
    private final Map<Integer, Long> lastClickTimes = new HashMap<>();

    public MouseController(AudioEditor editor) {
        super(editor);
        editor.addMouseListener(this);
        editor.addMouseMotionListener(this);
        editor.addMouseWheelListener(this);
    }

    @Override
    public void refreshListeners() {
        listeners.clear();
        listeners.addAll(editor.getEditorListeners(EditorMouseListener.class));
    }

    private boolean handleMouseEvent(int type, MouseEvent e) {
        EditorMouseEvent evt = new EditorMouseEvent(editor, e);
        Method method = MOUSE_EVENTS[type];
        try {
            for (EditorMouseListener l : listeners) {
                Object ret = method.invoke(l, evt);
                if (Boolean.TRUE.equals(ret)) {
                    if (type == MOVE)
                        method = MOUSE_EVENTS[EXIT];
                    else
                        return true;
                }
            }
        } catch (Exception exc) {
            Log.e("Failed to invoke mouse event", exc);
        }
        return false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        long time = System.currentTimeMillis();
        int button = e.getButton();
        Long lastTime = lastClickTimes.get(button);
        if (lastTime != null && time - lastTime < View.DBL_CLICK_INTERVAL)
            handleMouseEvent(DOUBLE_CLICK, e);
        else
            handleMouseEvent(CLICK, e);
        lastClickTimes.put(button, time);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        handleMouseEvent(PRESS, e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        handleMouseEvent(RELEASE, e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        handleMouseEvent(ENTER, e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        handleMouseEvent(EXIT, e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        handleMouseEvent(DRAG, e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        handleMouseEvent(MOVE, e);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        handleMouseEvent(WHEEL, e);
    }
}
