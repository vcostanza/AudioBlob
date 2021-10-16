package software.blob.audio.ui.editor.layers;

import software.blob.audio.playback.AudioPlayer;
import software.blob.audio.ui.editor.EditorMode;
import software.blob.audio.ui.editor.AudioEditor;
import software.blob.audio.ui.editor.controllers.SettingsController;
import software.blob.audio.ui.editor.events.PlaybackListener;
import software.blob.audio.ui.editor.events.EditorMouseListener;
import software.blob.audio.ui.editor.track.Track;
import software.blob.ui.theme.DarkTheme;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * A view that makes up part of the {@link AudioEditor}
 */
public abstract class EditorLayer implements EditorMouseListener, PlaybackListener {

    // Fonts
    private static final Map<String, Font> fontCache = new HashMap<>();

    // Cursors
    private static final Map<Integer, Cursor> cursorCache = new HashMap<>();

    protected final AudioEditor editor;
    protected final SettingsController settings;
    protected final Preferences prefs = Preferences.userNodeForPackage(getClass());
    protected Cursor cursor;

    protected EditorLayer(AudioEditor editor) {
        this.editor = editor;
        this.settings = editor.getSettings();
    }

    /**
     * Get the ID of this layer
     * @return ID string
     */
    public String getID() {
        return null;
    }

    /**
     * Get the display name for this layer
     * @return Layer name
     */
    public abstract String getName();

    public abstract void paint(Graphics2D g);

    protected EditorMode getMode() {
        return editor.getMode();
    }

    protected int getEditorMargin() {
        return editor.getEditorMargin();
    }

    protected int getX(double time) {
        return editor.getX(time);
    }

    protected int getY(double note) {
        return editor.getY(note);
    }

    protected double getTime(int x) {
        return editor.getTime(x);
    }

    protected double getNote(int y) {
        return editor.getNote(y);
    }

    protected double getFrequency(int y) {
        return editor.getFrequency(y);
    }

    protected AudioPlayer getAudioPlayer() {
        return editor.getAudioPlayer();
    }

    protected Track getSelectedTrack() {
        return editor.getTracks().getSelected();
    }

    public Track.Layer getTrackLayer(Track track) {
        return track.getLayer(getID());
    }

    protected Shape clipMargin(Graphics2D g) {
        Shape oldClip = g.getClip();
        int margin = getEditorMargin();
        g.setClip(margin, margin, getWidth() - margin, getHeight() - margin);
        return oldClip;
    }

    /* Fonts */

    protected static Font getFont(String name, int style, int size) {
        String fontHash = name + "_" + style + "_" + size;
        Font font = fontCache.get(fontHash);
        if (font == null)
            fontCache.put(fontHash, font = new Font(name, style, size));
        return font;
    }

    protected static Font getFont(String name, int size) {
        return getFont(name, Font.PLAIN, size);
    }

    protected static Font getFont(int size) {
        return getFont(DarkTheme.FONT_DEFAULT_NAME, size);
    }

    /* Cursors */

    protected static Cursor getCursor(int cursor) {
        Cursor c = cursorCache.get(cursor);
        if (c == null)
            cursorCache.put(cursor, c = new Cursor(cursor));
        return c;
    }

    protected void setCursor(int cursor) {
        setCursor(getCursor(cursor));
    }

    protected void toggleCursor(int cursor, boolean on) {
        setCursor(on ? getCursor(cursor) : null);
    }

    public void setCursor(Cursor cursor) {
        if (this.cursor != cursor) {
            this.cursor = cursor;
            editor.updateCursor();
        }
    }

    public Cursor getCursor() {
        return cursor;
    }

    /* Overrides */

    public Dimension getSize() {
        return editor.getSize();
    }

    public int getWidth() {
        return editor.getWidth();
    }

    public int getHeight() {
        return editor.getHeight();
    }

    public void repaint() {
        editor.repaint();
    }

    /* Utility methods */

    protected boolean withinBox(int x1, int y1, int x2, int y2, int radius) {
        return Math.abs(x1 - x2) <= radius && Math.abs(y1 - y2) <= radius;
    }

    protected boolean withinBox(int x, int y, Point p, int radius) {
        return withinBox(x, y, p.x, p.y, radius);
    }

    protected boolean withinRadius(int x1, int y1, int x2, int y2, int radius) {
        return withinBox(x1, y1, x2, y2, radius) && Math.hypot(x1 - x2, y1 - y2) <= radius;
    }

    protected boolean withinRadius(int x, int y, Point p, int radius) {
        return withinRadius(x, y, p.x, p.y, radius);
    }
}
