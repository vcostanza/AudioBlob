package software.blob.audio.ui.editor.view;

import java.util.Objects;

/**
 * Default implementation for draw bounds
 */
public class DrawBounds implements IDrawBounds {

    public double minTime, maxTime;
    public double minNote, maxNote;

    public DrawBounds() {
    }

    public DrawBounds(double minTime, double minNote, double maxTime, double maxNote) {
        this.minTime = minTime;
        this.minNote = minNote;
        this.maxTime = maxTime;
        this.maxNote = maxNote;
    }

    public DrawBounds(IDrawBounds other) {
        this(other.getMinTime(), other.getMinNote(), other.getMaxTime(), other.getMaxNote());
    }

    @Override
    public double getMinTime() {
        return minTime;
    }

    @Override
    public double getMaxTime() {
        return maxTime;
    }

    @Override
    public double getMinNote() {
        return minNote;
    }

    @Override
    public double getMaxNote() {
        return maxNote;
    }

    public boolean equals(IDrawBounds that) {
        return Double.compare(that.getMinTime(), minTime) == 0
                && Double.compare(that.getMaxTime(), maxTime) == 0
                && Double.compare(that.getMinNote(), minNote) == 0
                && Double.compare(that.getMaxNote(), maxNote) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IDrawBounds))
            return false;
        return equals((IDrawBounds) o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minTime, maxTime, minNote, maxNote);
    }
}
