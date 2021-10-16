package software.blob.audio.ui.editor.view;

import software.blob.ui.view.*;
import software.blob.ui.view.layout.LayoutParams;
import software.blob.ui.view.layout.LinearLayout;
import software.blob.ui.view.listener.HoverListener;
import software.blob.ui.util.ColorUtils;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Button that is displayed on the sidebar
 */
public class SidebarButton extends LinearLayout implements HoverListener {

    // Margin between the icon and the text
    private static final int ICON_TEXT_MARGIN = 4;

    private final ImageView icon;
    private final TextView text;

    private Color defaultBG, hoverBG, selectedBG;
    private boolean hover, selected;

    public SidebarButton(AttributeSet attrs) {
        super(attrs);

        setOrientation(LinearLayout.VERTICAL);
        attrs.put("gravity", "center_horizontal");

        int iconWidth = attrs.getDimension("iconWidth", 32);
        int iconHeight = attrs.getDimension("iconHeight", 32);
        this.icon = new ImageView(attrs.getString("src", ""));
        this.icon.setPreferredSize(new Dimension(iconWidth, iconHeight));

        AttributeSet textAttrs = new AttributeSet(attrs, "text", "textSize", "textColor");
        textAttrs.put("rotation", "90");
        this.text = new TextView(textAttrs);

        LayoutParams iconLP = new LayoutParams();
        iconLP.setMargins(0, 0, ICON_TEXT_MARGIN, 0);
        add(this.icon, iconLP);
        add(this.text, new LayoutParams());

        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setDefaultBackground(attrs.getColor("background", new Color(0, 0, 0, 0)));
        setOnHoverListener(this);
        setSelected(attrs.getBoolean("selected", false));
    }

    public void setDefaultBackground(Color bg) {
        this.defaultBG = bg;
        bg = bg.darker();
        this.hoverBG = ColorUtils.getColor(bg, 128);
        this.selectedBG = ColorUtils.getColor(bg, 192);
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            updateStyle();
        }
    }

    @Override
    public void onHoverStart(View view, MouseEvent event) {
        hover = true;
        updateStyle();
    }

    @Override
    public void onHoverEnd(View view, MouseEvent event) {
        hover = false;
        updateStyle();
    }

    private void updateStyle() {
        Color bg = selected ? selectedBG : (hover ? hoverBG : defaultBG);
        setBackground(bg);
    }
}
