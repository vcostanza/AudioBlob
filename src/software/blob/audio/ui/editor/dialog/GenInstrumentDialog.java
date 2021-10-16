package software.blob.audio.ui.editor.dialog;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.ui.theme.DarkTheme;
import software.blob.ui.util.DialogUtils;
import software.blob.ui.view.TextView;
import software.blob.ui.view.View;
import software.blob.ui.view.dialog.LayoutDialog;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LayoutInflater;
import software.blob.ui.view.listener.ClickListener;
import software.blob.ui.view.listener.HoverListener;

import javax.sound.midi.MidiDevice;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Dialog that allows the user to generate an instrument using either
 * {@link GenInstrumentFileDialog} or {@link GenInstrumentSampleDialog}
 */
public class GenInstrumentDialog extends LayoutDialog implements ClickListener, HoverListener {

    private final AudioEditor editor;
    private final TextView titleTxt;
    private final View fileBtn, sampleBtn;
    private InstrumentDialog.Callback callback;

    public GenInstrumentDialog(AudioEditor editor, Window window) {
        super(window);
        this.editor = editor;

        // TODO: Tile button dialog

        InflatedLayout inf = LayoutInflater.inflate("gen_instrument_dialog");

        titleTxt = inf.findByName("title");
        titleTxt.setText("Generate Instrument");

        fileBtn = inf.findByName("btn_file");
        initButton(fileBtn);

        sampleBtn = inf.findByName("btn_sample");
        initButton(sampleBtn);

        okBtn.setVisible(false);

        setView(inf.getRoot());
    }

    public GenInstrumentDialog(AudioEditor editor) {
        this(editor, editor.getFrame());
    }

    public GenInstrumentDialog setCallback(InstrumentDialog.Callback cb) {
        callback = cb;
        return this;
    }

    @Override
    public LayoutDialog showDialog(Runnable onOk) {
        // Must have a MIDI device connected to use this feature
        MidiDevice device = editor.getMidi().getLastConnectedDevice();
        if (device == null) {
            DialogUtils.errorDialog("No MIDI Device", "Please connect a MIDI device to use this feature.");
            return this;
        }

        // If there are no instruments loaded then default to generating from file
        if (editor.getTracks().getInstruments().isEmpty()) {
            onClick(fileBtn, null);
            return this;
        }

        return super.showDialog(onOk);
    }

    @Override
    public void onClick(View view, MouseEvent event) {
        if (view == fileBtn) {
            dismiss();
            new GenInstrumentFileDialog(editor)
                    .setWindow(window)
                    .setCallback(callback)
                    .showDialog();
        } else if (view == sampleBtn) {
            dismiss();
            new GenInstrumentSampleDialog(editor, window)
                    .setCallback(callback)
                    .showDialog();
        }
    }

    @Override
    public void onHoverStart(View view, MouseEvent event) {
        view.setBackground(DarkTheme.GRAY_96);
    }

    @Override
    public void onHoverEnd(View view, MouseEvent event) {
        view.setBackground(DarkTheme.GRAY_85);
    }

    private void initButton(View btn) {
        btn.setOnHoverListener(this);
        btn.setOnClickListener(this);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}
