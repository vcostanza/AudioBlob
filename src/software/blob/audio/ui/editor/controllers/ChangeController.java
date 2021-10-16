package software.blob.audio.ui.editor.controllers;

import software.blob.audio.ui.editor.EditorProject;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.events.EditorProjectListener;
import software.blob.ui.util.Log;

import java.util.Stack;

/**
 * Handles undo and redo actions
 */
public class ChangeController extends EditorController implements EditorProjectListener {

    /**
     * Handles execution and undo for a change
     */
    public interface Change {

        /**
         * Execute the change
         */
        void execute();

        /**
         * Undo the change
         */
        void undo();
    }

    private final Stack<Change> undoStack = new Stack<>();
    private final Stack<Change> redoStack = new Stack<>();

    public ChangeController(AudioEditor editor) {
        super(editor);
    }

    /**
     * Add a change to the stack
     * @param change Change to add
     */
    public void add(Change change) {
        addImpl(change);
        editor.updateDuration();
    }

    private void addImpl(Change change) {
        undoStack.push(change);
        redoStack.clear();
        //printChangeStack();
    }

    /**
     * Add a change to the stack and execute it
     * @param change Change to add and execute
     */
    public void execute(Change change) {
        addImpl(change);
        change.execute();
        editor.updateDuration();
    }

    /**
     * Undo the last action
     */
    public void undo() {
        if (canUndo()) {
            Change change = undoStack.pop();
            change.undo();
            redoStack.push(change);
            editor.updateDuration();
            //printChangeStack();
        }
    }

    /**
     * Redo the first available action
     */
    public void redo() {
        if (canRedo()) {
            Change change = redoStack.pop();
            change.execute();
            undoStack.push(change);
            editor.updateDuration();
            //printChangeStack();
        }
    }

    /**
     * Check if we can undo
     * @return True if the user can undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Check if we can redo
     * @return True if the user can redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Clear undo and redo stacks
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        //printChangeStack();
    }

    @Override
    public void onLoadProject(EditorProject project) {
        // Clear undo/redo stack when a new project is loaded
        clear();
    }

    private void printChangeStack() {
        Log.d("-----------UNDO-----------");
        for (Change ch : undoStack)
            Log.d(changeString(ch));
        Log.d("-----------REDO-----------");
        for (Change ch : redoStack)
            Log.d(changeString(ch));
        Log.d("--------------------------");
    }

    private String changeString(Change change) {
        return change.getClass().getSimpleName();
    }
}
