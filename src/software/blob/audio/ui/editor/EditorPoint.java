package software.blob.audio.ui.editor;

/**
 * A point within the {@link AudioEditor}
 */
public class EditorPoint {

    public double time;
    public double note;

    public EditorPoint() {
    }

    public EditorPoint(double time, double note) {
        set(time, note);
    }

    public EditorPoint(EditorPoint other) {
        set(other);
    }

    public void set(double time, double note) {
        this.time = time;
        this.note = note;
    }

    public void set(EditorPoint other) {
        set(other.time, other.note);
    }
}
