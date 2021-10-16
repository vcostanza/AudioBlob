package software.blob.audio.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An {@link ArrayList} that is automatically sorted by a set comparator
 */
public class SortedList<E> extends ArrayList<E> {

    private final Comparator<E> comparator;
    private boolean sorted;
    private boolean autoSort = true;

    public SortedList(Comparator<E> comparator) {
        super();
        this.comparator = comparator;
    }

    public SortedList(Collection<? extends E> c, Comparator<E> comparator, boolean autoSort) {
        super(c);
        this.comparator = comparator;
        this.autoSort = autoSort;
        onModified();
    }

    public SortedList(int initialCapacity, Comparator<E> comparator) {
        super(initialCapacity);
        this.comparator = comparator;
    }

    /**
     * Set auto-sorting on or off
     * @param autoSort True to enable
     */
    public void setAutoSort(boolean autoSort) {
        this.autoSort = autoSort;
        if (autoSort)
            sort();
    }

    /**
     * Sort the content of this list by the primary {@link Comparator}
     * @return True if the list was sorted by this call, false if unmodified
     */
    public boolean sort() {
        if (!sorted) {
            super.sort(comparator);
            return sorted = true;
        }
        return false;
    }

    @Override
    public void sort(Comparator<? super E> c) {
        throw new IllegalStateException("Cannot sort elements by non-primary comparator");
    }

    @Override
    public E set(int index, E element) {
        E ret = super.set(index, element);
        onModified();
        return ret;
    }

    @Override
    public boolean add(E e) {
        boolean ret = super.add(e);
        onModified();
        return ret;
    }

    @Override
    public void add(int index, E e) {
        super.add(index, e);
        onModified();
    }

    @Override
    public E remove(int index) {
        E ret = super.remove(index);
        onModified();
        return ret;
    }

    @Override
    public boolean remove(Object o) {
        boolean ret = super.remove(o);
        onModified();
        return ret;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean ret = super.addAll(c);
        onModified();
        return ret;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean ret = super.addAll(index, c);
        onModified();
        return ret;
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
        onModified();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean ret = super.removeAll(c);
        onModified();
        return ret;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean ret = super.retainAll(c);
        onModified();
        return ret;
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        boolean ret = super.removeIf(filter);
        onModified();
        return ret;
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        super.replaceAll(operator);
        onModified();
    }

    /**
     * Called when the list has been modified in some way
     */
    protected void onModified() {
        sorted = false;
        if (autoSort)
            sort();
    }
}
