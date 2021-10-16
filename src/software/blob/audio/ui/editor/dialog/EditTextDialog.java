package software.blob.audio.ui.editor.dialog;

import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.EditText;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.LayoutParams;
import software.blob.ui.view.layout.LinearLayout;

import java.awt.*;

/**
 * Simple dialog containing a single text input
 */
public class EditTextDialog extends LayoutDialog {

    private final EditText txt;
    private boolean emptyTextAllowed;

    public EditTextDialog(Window window, EditText txt) {
        super(window);
        this.txt = txt;
        LinearLayout ll = new LinearLayout(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        ll.add(txt, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setView(ll);
        setSize(250, 150);
    }

    public EditTextDialog(Window window) {
        this(window, new EditText());
    }

    /**
     * Set the text content
     * @param text Text content
     * @return Dialog
     */
    public EditTextDialog setText(String text) {
        txt.setText(text);
        return this;
    }

    /**
     * Set the text hint
     * @param hint Text hint
     * @return Dialog
     */
    public EditTextDialog setHint(String hint) {
        txt.setHint(hint);
        return this;
    }

    /**
     * Select all text - useful for quick input edits
     * @return Dialog
     */
    public EditTextDialog selectAll() {
        txt.selectAll();
        return this;
    }

    /**
     * Set whether this dialog accepts empty text input
     * @param allowed True if this dialog allows empty text
     *                False to prevent {@link #onOK()} with an error dialog
     * @return Dialog
     */
    public EditTextDialog setEmptyTextAllowed(boolean allowed) {
        this.emptyTextAllowed = allowed;
        return this;
    }

    /**
     * Add a text-changed listener
     * @param l Text-changed listener
     * @return Dialog
     */
    public EditTextDialog addTextChangedListener(EditText.OnTextChangedListener l) {
        txt.addTextChangedListener(l);
        return this;
    }

    /**
     * Get the text content
     * @return Text content
     */
    public String getText() {
        return txt.getText();
    }

    @Override
    protected void onOK() {
        if (!this.emptyTextAllowed && getText().isEmpty()) {
            DialogUtils.errorDialog(getTitle(), "Please enter some text.");
            return;
        }
        super.onOK();
    }
}
