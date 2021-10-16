package software.blob.audio.ui.editor.view;

/**
 * Interface for draw bounds defined by time and note value
 */
public interface IDrawBounds {

    /**
     * Get the minimum time code bound
     * @return Time in seconds
     */
    double getMinTime();

    /**
     * Get the maximum time code bound
     * @return Time in seconds
     */
    double getMaxTime();

    /**
     * Get the minimum note value
     * @return Note value
     */
    double getMinNote();

    /**
     * Get the maximum note value
     * @return Note value
     */
    double getMaxNote();

    /**
     * Test if these bounds intersect with another bounds
     * @param other Other draw bounds
     * @return True if bounds intersect
     */
    default boolean intersects(IDrawBounds other) {
        return !(getMaxTime() < other.getMinTime() || getMinTime() > other.getMaxTime()
                || getMaxNote() < other.getMinNote() || getMinNote() > other.getMaxNote());
    }

    /**
     * Test if a note/time is contained within the draw bounds
     * @param time Time code (seconds)
     * @param note Note value
     * @return True if contained
     */
    default boolean contains(double time, double note) {
        return time >= getMinTime() && time < getMaxTime() && note >= getMinNote() && note < getMaxNote();
    }
}
