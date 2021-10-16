package software.blob.audio.ui.editor.view;

import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.view.track.TrackButton;
import software.blob.audio.ui.editor.view.track.TrackLayerButton;
import software.blob.audio.util.Misc;
import software.blob.ui.view.AttributeSet;
import software.blob.ui.view.ImageButton;
import software.blob.ui.view.TextView;
import software.blob.ui.view.View;
import software.blob.ui.view.layout.AbstractLayout;
import software.blob.ui.view.layout.InflatedLayout;
import software.blob.ui.view.layout.LinearLayout;
import software.blob.ui.view.listener.ClickListener;
import software.blob.ui.view.listener.DoubleClickListener;
import software.blob.ui.view.listener.HoverListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Button representing a layer with volume and visibility options
 * See {@link TrackButton} and {@link TrackLayerButton}
 */
public abstract class ControlButton extends LinearLayout implements
        ClickListener, DoubleClickListener, HoverListener, MouseWheelListener {

    protected AudioEditor editor;
    protected Controller ctrl;

    // Views
    protected TextView nameTxt;
    protected ImageButton volBtn, vizBtn;

    // Background
    protected Color defaultBG, selectedBG;
    protected boolean hovered;

    protected ControlButton(AttributeSet attrs) {
        super(attrs);
    }

    @Override
    public void onFinishInflate(InflatedLayout inf) {
        nameTxt = findChildByName("name");

        volBtn = findChildByName("volume_toggle");
        volBtn.setOnClickListener(this);
        volBtn.addMouseWheelListener(this);

        vizBtn = findChildByName("visibility_toggle");
        vizBtn.setOnClickListener(this);
        vizBtn.setEnabled(true);

        setOnClickListener(this);
        setOnDoubleClickListener(this);
        setOnHoverListener(this);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    /**
     * Refresh the view for this button
     * @param editor Audio editor instance
     * @param ctrl Audio controller
     */
    public void refresh(AudioEditor editor, Controller ctrl) {
        this.editor = editor;
        this.ctrl = ctrl;
        refresh();
    }

    protected void refresh() {
        if (editor != null)
            editor.repaint();
        if (ctrl != null)
            nameTxt.setText(ctrl.getName());
        refreshStyle();
        refreshVolumeButton();
        refreshVisibilityButton();
    }

    @Override
    public void onClick(View view, MouseEvent e) {
        if (ctrl == null)
            return;
        int btn = e.getButton();
        if (view == this && btn == MouseEvent.BUTTON1) {
            ctrl.setSelected(true);
        } else if (view == volBtn) {
            if (btn == MouseEvent.BUTTON1)
                setLayerMuted(!ctrl.isMuted());
            else if (btn == MouseEvent.BUTTON3) {
                // Right-click to bring up volume slider
                final JSlider slider = new JSlider(JSlider.VERTICAL, 0, 10, (int) (ctrl.getVolume() * 10));
                slider.setPaintTicks(false);
                slider.setPreferredSize(new Dimension(10, 100));
                slider.addChangeListener(e1 -> setLayerVolume(slider.getValue() / 10d));
                editor.showContextMenu(slider);
            }
        } else if (view == vizBtn && btn == MouseEvent.BUTTON1)
            setLayerVisible(!ctrl.isVisible());
    }

    @Override
    public void onDoubleClick(View view, MouseEvent event) {
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (ctrl != null && e.getComponent() == volBtn) {
            double amount = e.getWheelRotation() > 0 ? -0.1 : 0.1;
            setLayerVolume(Misc.clamp(ctrl.getVolume() + amount, 0, 1));
        }
    }

    @Override
    public void onHoverStart(View view, MouseEvent event) {
        hovered = true;
        refreshStyle();
    }

    @Override
    public void onHoverEnd(View view, MouseEvent event) {
        hovered = false;
        refreshStyle();
    }

    protected void setDefaultBackground(Color color) {
        color = color.darker();
        this.defaultBG = color.darker();
        this.selectedBG = color;
        refreshStyle();
    }

    protected void refreshStyle() {
        boolean selected = ctrl != null && ctrl.isSelected();
        setBackground(selected ? selectedBG : defaultBG);
        nameTxt.setForeground(selected || hovered ? Color.WHITE : Color.LIGHT_GRAY);
    }

    protected void refreshVolumeButton() {
        volBtn.setImage(getSpeakerIcon());
    }

    private String getSpeakerIcon() {
        if (ctrl == null || ctrl.isMuted())
            return "speaker_0";
        String icon = "speaker_4";
        if (ctrl.isSelected()) {
            double vol = ctrl.getVolume();
            if (vol > 0)
                icon = "speaker_1";
            if (vol > 0.33)
                icon = "speaker_2";
            if (vol > 0.67)
                icon = "speaker_3";
        }
        return icon;
    }

    protected void refreshVisibilityButton() {
        String icon = "visibility_0";
        if (ctrl != null && ctrl.isVisible()) {
            if (ctrl.isSelected())
                icon = "visibility_2";
            else
                icon = "visibility_1";
        }
        vizBtn.setImage(icon);
    }

    protected void setLayerVolume(double volume) {
        if (ctrl != null && ctrl.getVolume() != volume) {
            ctrl.setVolume(volume);
            ctrl.setMuted(false);
            refreshVolumeButton();
        }
    }

    protected void setLayerMuted(boolean muted) {
        if (ctrl != null && ctrl.isMuted() != muted) {
            ctrl.setMuted(muted);
            refreshVolumeButton();
        }
    }

    protected void setLayerVisible(boolean visible) {
        if (ctrl != null && ctrl.isVisible() != visible) {
            ctrl.setVisible(visible);
            refreshVisibilityButton();
        }
    }

    /**
     * Controller that has a set name, volume, and visibility
     */
    protected interface Controller {

        /**
         * Get the name of this controller
         * @return Name
         */
        String getName();

        /**
         * Select this controller
         * @param selected True if selected
         */
        void setSelected(boolean selected);

        /**
         * Check if this controller is selected
         * @return True if selected
         */
        default boolean isSelected() {
            return false;
        }

        /**
         * Set the volume for this controller
         * @param volume Volume (0 to 1)
         */
        void setVolume(double volume);

        /**
         * Get the volume for this controller
         * @return Volume (0 to 1)
         */
        default double getVolume() {
            return 1;
        }

        /**
         * Mute this controller or not
         * @param mute True to mute
         */
        void setMuted(boolean mute);

        /**
         * Check if this controller is muted (while remembering existing volume)
         * @return True if muted
         */
        default boolean isMuted() {
            return false;
        }

        /**
         * Set the visibility of this controller
         * @param visible True if visible
         */
        void setVisible(boolean visible);

        /**
         * Check if this controller is visible
         * @return True if visible
         */
        default boolean isVisible() {
            return true;
        }
    }
}
